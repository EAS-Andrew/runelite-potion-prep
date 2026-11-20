package com.potionprep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/**
 * Walks a potion's recipe tree against what the player holds: how many can
 * be made, which prep steps (clean, unf, crush, intermediate potions) get
 * there, and what is short. No client access, so it is unit-testable.
 */
@Getter
public final class PotionPlan
{
	@Value
	public static class Row
	{
		String name;
		int itemId;
		int have;
		boolean doses;
	}

	public enum Severity
	{
		ACTION,
		WARNING
	}

	@Value
	public static class Step
	{
		Severity severity;
		String text;
		/** Extra detail for the tooltip, or null. */
		String hint;
	}

	private static final int MAX_UNITS = 1_000_000;

	/** A botanical pie (whole or half) boosts Herblore by 4. */
	public static final int PIE_BOOST = 4;
	public static final int[] PIE_IDS = {ItemID.BOTANICAL_PIE, ItemID.HALF_BOTANICAL_PIE};

	/**
	 * True when your current level is too low for this potion but a
	 * botanical pie on top of your real level would be enough.
	 */
	public static boolean needsPie(Potion potion, int realLevel, int boostedLevel)
	{
		return realLevel > 0 && boostedLevel < potion.getLevel() && realLevel + PIE_BOOST >= potion.getLevel();
	}

	/** The level to plan with: boosted, or real + pie when a pie would unlock it. */
	public static int effectiveLevel(Potion potion, int realLevel, int boostedLevel)
	{
		return needsPie(potion, realLevel, boostedLevel) ? realLevel + PIE_BOOST : boostedLevel;
	}

	private final Potion potion;
	private final List<Row> rows;
	private final List<Step> steps;
	/** Final crafts possible once all prep is done. */
	private final int makeable;
	/** Final crafts possible with no prep at all. */
	private final int makeableNow;

	private PotionPlan(Potion potion, List<Row> rows, List<Step> steps, int makeable, int makeableNow)
	{
		this.potion = potion;
		this.rows = Collections.unmodifiableList(rows);
		this.steps = Collections.unmodifiableList(steps);
		this.makeable = makeable;
		this.makeableNow = makeableNow;
	}

	/**
	 * Every ingredient in the tree, in the order you work through it: each
	 * input's own chain first (grimy, clean, vial, unf, raw, secondary...),
	 * tools last. The finished potion itself is not included.
	 */
	static List<Node> ingredients(Potion potion)
	{
		Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Node> out = new ArrayList<>();
		List<Node> tools = new ArrayList<>();
		for (Recipe.Input in : potion.getProduct().getRecipe().getInputs())
		{
			visit(in.getNode(), seen, out, tools);
		}
		for (Node t : tools)
		{
			if (seen.add(t))
			{
				out.add(t);
			}
		}
		return out;
	}

	private static void visit(Node node, Set<Node> seen, List<Node> out, List<Node> tools)
	{
		if (seen.contains(node))
		{
			return;
		}
		Recipe r = node.getRecipe();
		if (r != null)
		{
			for (Recipe.Input in : r.getInputs())
			{
				visit(in.getNode(), seen, out, tools);
			}
			if (r.getTool() != null && !tools.contains(r.getTool()))
			{
				tools.add(r.getTool());
			}
		}
		if (seen.add(node))
		{
			out.add(node);
		}
	}

	static final int BANK_COLUMNS = 8;

	/**
	 * The bank layout: one row per task, in the order you do them. Each row
	 * holds everything that task needs (inputs, then any tool) but not what
	 * it makes; an output only reappears as an input on a later row. Potions
	 * fill their slots with doses (1)-(4). Rows wider than the bank wrap.
	 *
	 * Superantipoison: [grimy irit] / [irit, vial] / [unicorn horn, pestle]
	 * / [irit (unf), unicorn horn dust].
	 */
	public static List<int[]> layoutRows(Potion potion)
	{
		Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		List<int[]> rows = new ArrayList<>();
		Recipe fin = potion.getProduct().getRecipe();
		for (Recipe.Input in : fin.getInputs())
		{
			addTasks(in.getNode(), seen, rows);
		}
		addRow(rows, taskIds(fin));
		return rows;
	}

	private static void addTasks(Node node, Set<Node> seen, List<int[]> rows)
	{
		Recipe r = node.getRecipe();
		if (r == null || !seen.add(node))
		{
			return;
		}
		for (Recipe.Input in : r.getInputs())
		{
			addTasks(in.getNode(), seen, rows);
		}
		addRow(rows, taskIds(r));
	}

	private static List<Integer> taskIds(Recipe r)
	{
		List<Integer> ids = new ArrayList<>();
		for (Recipe.Input in : r.getInputs())
		{
			for (int id : in.getNode().rowIds())
			{
				ids.add(id);
			}
		}
		if (r.getTool() != null)
		{
			ids.add(r.getTool().getId());
		}
		return ids;
	}

	private static void addRow(List<int[]> rows, List<Integer> ids)
	{
		for (int i = 0; i < ids.size(); i += BANK_COLUMNS)
		{
			List<Integer> chunk = ids.subList(i, Math.min(ids.size(), i + BANK_COLUMNS));
			rows.add(chunk.stream().mapToInt(Integer::intValue).toArray());
		}
	}

	/** Every distinct item id in the layout. */
	public static List<Integer> layoutItems(Potion potion)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (int[] row : layoutRows(potion))
		{
			for (int id : row)
			{
				ids.add(id);
			}
		}
		return new ArrayList<>(ids);
	}

	/**
	 * @param counts        item id to quantity, bank plus inventory
	 * @param herbloreLevel real Herblore level, or 0 if unknown
	 */
	public static PotionPlan compute(Potion potion, Map<Integer, Integer> counts, int herbloreLevel)
	{
		return compute(potion, counts, herbloreLevel, false);
	}

	/**
	 * @param pie the plan assumes a botanical pie; adds it as the first step
	 *            and as an ingredient row
	 */
	public static PotionPlan compute(Potion potion, Map<Integer, Integer> counts, int herbloreLevel, boolean pie)
	{
		Node product = potion.getProduct();
		Recipe fin = product.getRecipe();
		List<Node> nodes = ingredients(potion);

		Map<Node, Integer> base = new IdentityHashMap<>();
		List<Row> rows = new ArrayList<>();
		for (Node n : nodes)
		{
			// Cap so the simulation's arithmetic can never overflow.
			int have = Math.min(n.count(counts), MAX_UNITS);
			base.put(n, have);
			rows.add(new Row(n.getName(), n.getId(), have, n.isDosed()));
		}

		if (pie)
		{
			int pies = counts.getOrDefault(ItemID.BOTANICAL_PIE, 0) + counts.getOrDefault(ItemID.HALF_BOTANICAL_PIE, 0);
			rows.add(0, new Row("Botanical pie", ItemID.BOTANICAL_PIE, pies, false));
		}

		List<Step> steps = new ArrayList<>();
		if (pie)
		{
			int pies = rows.get(0).getHave();
			steps.add(pies > 0
				? new Step(Severity.ACTION, "Eat a botanical pie (+" + PIE_BOOST + ")", "Needs " + potion.getLevel() + " Herblore")
				: new Step(Severity.WARNING, "Need a botanical pie (+" + PIE_BOOST + ")", "Needs " + potion.getLevel() + " Herblore"));
		}
		if (herbloreLevel > 0 && herbloreLevel < fin.getLevel())
		{
			steps.add(new Step(Severity.WARNING, "Needs " + fin.getLevel() + " Herblore", "You have " + herbloreLevel));
			return new PotionPlan(potion, rows, steps, 0, 0);
		}

		// No input yields more than 3 per craft, so this comfortably bounds the search.
		long sum = 1;
		for (int v : base.values())
		{
			sum += (long) v * 3;
		}
		int bound = (int) Math.min(sum, MAX_UNITS * 3L);

		int makeable = maxCrafts(fin, base, herbloreLevel, true, bound);
		int makeableNow = maxCrafts(fin, base, herbloreLevel, false, bound);

		// Prep steps that get you to `makeable`, in the order you would do them.
		Ctx run = new Ctx(base, herbloreLevel, true);
		run.makeFinal(fin, makeable);
		for (Map.Entry<Recipe, Integer> e : run.crafts.entrySet())
		{
			steps.add(new Step(Severity.ACTION, String.format(Locale.US, e.getKey().getStepFormat().replace("%d", "%,d"), e.getValue()), null));
		}

		// What stops you making more? Aim for what your best-stocked input
		// would allow, and list what that needs.
		int target = 0;
		for (Recipe.Input in : fin.getInputs())
		{
			target = Math.max(target, maxForInput(in, base, herbloreLevel, bound));
		}
		Map<String, String> warnings = new LinkedHashMap<>();
		if (target > makeable)
		{
			Ctx want = new Ctx(base, herbloreLevel, true);
			want.makeFinal(fin, target);
			for (Map.Entry<Node, Integer> e : want.shortfalls.entrySet())
			{
				Node n = e.getKey();
				Map<Node, Integer> group = want.groups.get(n);
				if (group != null)
				{
					StringBuilder sb = new StringBuilder(n.getName() + ": short ");
					String sep = "";
					for (Map.Entry<Node, Integer> g : group.entrySet())
					{
						sb.append(sep).append(qty(g.getKey(), g.getValue()));
						sep = ", ";
					}
					warnings.put(sb.toString(), null);
				}
				else
				{
					warnings.put("Short " + qty(n, e.getValue()), n.getSource());
				}
			}
			want.blocked.forEach(b -> warnings.putIfAbsent(b, null));
		}
		run.blocked.forEach(b -> warnings.putIfAbsent(b, null));

		warnings.forEach((text, hint) -> steps.add(new Step(Severity.WARNING, text, hint)));
		if (makeable == 0 && warnings.isEmpty())
		{
			steps.add(new Step(Severity.WARNING, "Nothing found. Open your bank.", null));
		}

		return new PotionPlan(potion, rows, steps, makeable, makeableNow);
	}

	/** Just the "can make" number, for the potion grid. */
	public static int makeable(Potion potion, Map<Integer, Integer> counts, int herbloreLevel)
	{
		Recipe fin = potion.getProduct().getRecipe();
		if (herbloreLevel > 0 && herbloreLevel < fin.getLevel())
		{
			return 0;
		}
		Map<Node, Integer> base = new IdentityHashMap<>();
		long sum = 1;
		for (Node n : ingredients(potion))
		{
			int have = Math.min(n.count(counts), MAX_UNITS);
			base.put(n, have);
			sum += (long) have * 3;
		}
		return maxCrafts(fin, base, herbloreLevel, true, (int) Math.min(sum, MAX_UNITS * 3L));
	}

	private static String qty(Node n, int amount)
	{
		String qty = String.format(Locale.US, "%,d", amount);
		return n.isDosed() ? qty + " doses of " + Catalog.lower(n.getName()) : qty + " " + Catalog.lower(n.getName());
	}

	private static int maxCrafts(Recipe fin, Map<Node, Integer> base, int lvl, boolean allowCraft, int bound)
	{
		int lo = 0;
		int hi = bound;
		while (lo < hi)
		{
			int mid = (lo + hi + 1) >>> 1;
			Ctx c = new Ctx(base, lvl, allowCraft);
			c.makeFinal(fin, mid);
			if (c.shortfalls.isEmpty())
			{
				lo = mid;
			}
			else
			{
				hi = mid - 1;
			}
		}
		return lo;
	}

	/** Most final crafts this one input could supply, ignoring the others. */
	private static int maxForInput(Recipe.Input in, Map<Node, Integer> base, int lvl, int bound)
	{
		int lo = 0;
		int hi = bound;
		while (lo < hi)
		{
			int mid = (lo + hi + 1) >>> 1;
			Ctx c = new Ctx(base, lvl, true);
			c.need(in.getNode(), in.getQty() * mid);
			if (c.shortfalls.isEmpty())
			{
				lo = mid;
			}
			else
			{
				hi = mid - 1;
			}
		}
		return lo;
	}

	/** One simulated attempt: a private copy of the pool, consumed greedily. */
	private static final class Ctx
	{
		final Map<Node, Integer> pool;
		final int lvl;
		final boolean allowCraft;
		final Map<Recipe, Integer> crafts = new LinkedHashMap<>();
		Map<Node, Integer> shortfalls = new LinkedHashMap<>();
		/** Leaf shortages behind a short intermediate potion. */
		final Map<Node, Map<Node, Integer>> groups = new LinkedHashMap<>();
		final Set<String> blocked = new LinkedHashSet<>();

		Ctx(Map<Node, Integer> base, int lvl, boolean allowCraft)
		{
			this.pool = new IdentityHashMap<>(base);
			this.lvl = lvl;
			this.allowCraft = allowCraft;
		}

		void makeFinal(Recipe fin, int n)
		{
			if (n <= 0)
			{
				return;
			}
			for (Recipe.Input in : fin.getInputs())
			{
				need(in.getNode(), in.getQty() * n);
			}
			crafts.merge(fin, n, Integer::sum);
		}

		void need(Node node, int amount)
		{
			int have = pool.getOrDefault(node, 0);
			int take = Math.min(have, amount);
			pool.put(node, have - take);
			int rem = amount - take;
			if (rem == 0)
			{
				return;
			}

			Recipe r = node.getRecipe();
			if (r == null || !allowCraft)
			{
				short_(node, rem);
				return;
			}

			if (lvl > 0 && lvl < r.getLevel())
			{
				blocked.add(capital(r) + ": " + r.getLevel() + " Herblore");
				short_(node, rem);
				return;
			}

			if (r.isAltForm())
			{
				// Cleaning / crushing: limited by the raw form you hold.
				Recipe.Input src = r.getInputs().get(0);
				int crafts = ceilDiv(rem, r.getYield());
				int srcHave = pool.getOrDefault(src.getNode(), 0);
				int can = Math.min(crafts, srcHave / src.getQty());
				if (can > 0 && r.getTool() != null && pool.getOrDefault(r.getTool(), 0) <= 0)
				{
					blocked.add("No " + Catalog.lower(r.getTool().getName()));
					can = 0;
				}
				if (can > 0)
				{
					pool.put(src.getNode(), srcHave - can * src.getQty());
					this.crafts.merge(r, can, Integer::sum);
					int produced = can * r.getYield();
					int used = Math.min(produced, rem);
					pool.merge(node, produced - used, Integer::sum);
					rem -= used;
				}
				if (rem > 0)
				{
					short_(node, rem);
				}
				return;
			}

			int crafts = ceilDiv(rem, r.getYield());
			// Shortages inside an intermediate potion's chain are grouped
			// under that potion, so super combat reads "for super defence:
			// short 10 cadantine" instead of a flat list of leaves.
			Map<Node, Integer> outer = shortfalls;
			Map<Node, Integer> inner = node.isDosed() ? new LinkedHashMap<>() : outer;
			shortfalls = inner;
			for (Recipe.Input in : r.getInputs())
			{
				need(in.getNode(), in.getQty() * crafts);
			}
			shortfalls = outer;
			if (inner != outer && !inner.isEmpty())
			{
				short_(node, rem);
				groups.computeIfAbsent(node, k -> new LinkedHashMap<>()).putAll(inner);
			}
			this.crafts.merge(r, crafts, Integer::sum);
			pool.merge(node, crafts * r.getYield() - rem, Integer::sum);
		}

		private void short_(Node node, int qty)
		{
			shortfalls.merge(node, qty, Integer::sum);
		}

		private static String capital(Recipe r)
		{
			// "Clean %d grimy torstol" -> "Cleaning grimy torstol"
			String s = r.getStepFormat().replace("%d ", "");
			int sp = s.indexOf(' ');
			String verb = sp < 0 ? s : s.substring(0, sp);
			String rest = sp < 0 ? "" : s.substring(sp);
			switch (verb)
			{
				case "Clean":
					return "Cleaning" + rest;
				case "Make":
					return "Making" + rest;
				case "Crush":
					return "Crushing" + rest;
				case "Mix":
					return "Mixing" + rest;
				default:
					return s;
			}
		}

		private static int ceilDiv(int a, int b)
		{
			return (a + b - 1) / b;
		}
	}
}
