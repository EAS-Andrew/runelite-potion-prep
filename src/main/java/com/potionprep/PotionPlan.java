package com.potionprep;

import java.util.ArrayList;
import java.util.Arrays;
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

	/** Herblore cape (either version): grimy herbs go straight into unf potions, and it holds a pestle. */
	public static final int[] CAPE_IDS = {ItemID.SKILLCAPE_HERBLORE, ItemID.SKILLCAPE_HERBLORE_TRIMMED};

	/** True when the counts include a Herblore cape. */
	public static boolean hasCape(Map<Integer, Integer> counts)
	{
		for (int id : CAPE_IDS)
		{
			if (counts.getOrDefault(id, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	private static final String DECANT_WHERE = "Potion storage, Bob Barter (GE) or Zahur";

	private final Potion potion;
	private final List<Row> rows;
	private final List<Step> steps;
	/** Potions you can make once all prep is done. */
	private final int makeable;
	/** Potions you can make with no prep at all. */
	private final int makeableNow;
	/** The potion with its dose, e.g. "Stamina potion(3)", or no dose when they differ. */
	private final String label;
	/** Dose breakdown for potions made a dose at a time, e.g. "10 x (4), 2 x (3)", or null. */
	private final String doseMix;

	private PotionPlan(Potion potion, List<Row> rows, List<Step> steps, Solved all, Solved now)
	{
		this.potion = potion;
		this.rows = Collections.unmodifiableList(rows);
		this.steps = Collections.unmodifiableList(steps);
		this.makeable = all.potions;
		this.makeableNow = now.potions;
		this.label = all.label(potion);
		this.doseMix = all.doseMix(potion);
	}

	/**
	 * Every ingredient in the tree, in the order you work through it: each
	 * input's own chain first (grimy, clean, vial, unf, raw, secondary...),
	 * then any stand-ins for it, tools last. The finished potion itself is
	 * not included.
	 */
	static List<Node> ingredients(Potion potion)
	{
		// Every route's, so nothing the player might use is missed.
		Set<Node> all = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Node> out = new ArrayList<>();
		for (Route route : routes(potion))
		{
			for (Node n : ingredients(potion, route))
			{
				if (all.add(n))
				{
					out.add(n);
				}
			}
		}
		return out;
	}

	/** The ingredients when made by {@code route}. */
	static List<Node> ingredients(Potion potion, Route route)
	{
		Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Node> out = new ArrayList<>();
		List<Node> tools = new ArrayList<>();
		for (Recipe.Input in : route.of(potion.getProduct()).getInputs())
		{
			visit(in, route, seen, out, tools);
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

	private static void visit(Recipe.Input in, Route route, Set<Node> seen, List<Node> out, List<Node> tools)
	{
		visit(in.getNode(), route, seen, out, tools);
		// Stand-ins are only used if held, so their own recipes don't matter here.
		for (Node alt : in.getAlts())
		{
			if (seen.add(alt))
			{
				out.add(alt);
			}
		}
	}

	private static void visit(Node node, Route route, Set<Node> seen, List<Node> out, List<Node> tools)
	{
		if (seen.contains(node))
		{
			return;
		}
		Recipe r = route.of(node);
		if (r != null)
		{
			for (Recipe.Input in : r.getInputs())
			{
				visit(in, route, seen, out, tools);
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

	/** The bank layout with nothing known about what you hold. See {@link #layoutRows(Potion, Map)}. */
	public static List<int[]> layoutRows(Potion potion)
	{
		return layoutRows(potion, null);
	}

	/**
	 * The bank layout: one row per task, in the order you do them. Each row
	 * holds everything that task needs (inputs, then any tool) but not what
	 * it makes; an output only reappears as an input on a later row. Potions
	 * fill their slots with doses (1)-(4). Rows wider than the bank wrap.
	 *
	 * Superantipoison: [grimy irit] / [irit, vial] / [unicorn horn, pestle]
	 * / [irit (unf), unicorn horn dust].
	 *
	 * A recipe that takes whole (4)s (super combat) gets only the (4) slot,
	 * after a "decant" row of that potion's (1)-(3).
	 *
	 * @param counts what you hold, or null. With a Herblore cape the clean
	 *               row folds into the unf row (grimy goes in directly), and
	 *               a crush row is left out when you hold the crushed item
	 *               but none of the raw one. Only the route that makes
	 *               the most from it is laid out (the usual one if null).
	 */
	public static List<int[]> layoutRows(Potion potion, Map<Integer, Integer> counts)
	{
		Route route = counts == null ? Route.USUAL : best(potion, counts, 0, hasCape(counts));
		Layout l = new Layout(potion, counts, route);
		Recipe fin = route.of(potion.getProduct());
		for (Recipe.Input in : fin.getInputs())
		{
			l.addTasks(in);
		}
		l.addRow(l.taskIds(fin));
		return l.rows;
	}

	private static final class Layout
	{
		final List<int[]> rows = new ArrayList<>();
		final Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		final Map<Integer, Integer> counts;
		final boolean cape;
		final Route route;
		/** Clean herbs some recipe takes as they are, so they still need a clean row. */
		final Set<Node> usedClean = Collections.newSetFromMap(new IdentityHashMap<>());

		Layout(Potion potion, Map<Integer, Integer> counts, Route route)
		{
			this.counts = counts;
			this.route = route;
			this.cape = counts != null && hasCape(counts);
			findUsedClean(potion.getProduct(), Collections.newSetFromMap(new IdentityHashMap<>()));
		}

		private void findUsedClean(Node node, Set<Node> done)
		{
			Recipe r = route.of(node);
			if (r == null || !done.add(node))
			{
				return;
			}
			for (Recipe.Input in : r.getInputs())
			{
				if (!r.isFromGrimy() && isClean(in.getNode()))
				{
					usedClean.add(in.getNode());
				}
				findUsedClean(in.getNode(), done);
			}
		}

		void addTasks(Recipe.Input in)
		{
			Node node = in.getNode();
			Recipe r = route.of(node);
			if (r == null || !seen.add(node))
			{
				return;
			}
			for (Recipe.Input sub : r.getInputs())
			{
				addTasks(sub);
			}
			if (!skip(node, r))
			{
				addRow(taskIds(r));
			}
			// Made as (3)s (or bought in any dose), so decant before a recipe that takes (4)s.
			if (Recipe.needsFull(in) && r.getYield() != 4)
			{
				int[] d = node.getDoseIds();
				addRow(Arrays.asList(d[0], d[1], d[2]));
			}
		}

		private boolean skip(Node node, Recipe r)
		{
			if (cape && isClean(node) && !usedClean.contains(node))
			{
				return true;
			}
			// Bought the crushed item and have none of the raw one: no crush task.
			if (counts != null && r.isAltForm() && r.getTool() != null)
			{
				Node raw = r.getInputs().get(0).getNode();
				return raw.count(counts) == 0 && node.count(counts) > 0;
			}
			return false;
		}

		List<Integer> taskIds(Recipe r)
		{
			List<Integer> ids = new ArrayList<>();
			for (Recipe.Input in : r.getInputs())
			{
				Node n = in.getNode();
				if (cape && r.isFromGrimy() && isClean(n) && !usedClean.contains(n))
				{
					ids.add(n.getRecipe().getInputs().get(0).getNode().getId());
				}
				if (Recipe.needsFull(in))
				{
					ids.add(n.getId());
				}
				else
				{
					for (int id : n.rowIds())
					{
						ids.add(id);
					}
				}
				for (Node alt : in.getAlts())
				{
					ids.add(alt.getId());
				}
			}
			if (r.getTool() != null)
			{
				ids.add(r.getTool().getId());
			}
			return ids;
		}

		void addRow(List<Integer> ids)
		{
			for (int i = 0; i < ids.size(); i += BANK_COLUMNS)
			{
				List<Integer> chunk = ids.subList(i, Math.min(ids.size(), i + BANK_COLUMNS));
				rows.add(chunk.stream().mapToInt(Integer::intValue).toArray());
			}
		}
	}

	/** A herb made by cleaning (no tool), as opposed to a crushed secondary. */
	private static boolean isClean(Node n)
	{
		Recipe r = n.getRecipe();
		return r != null && r.isAltForm() && r.getTool() == null;
	}

	/** Every distinct item id in the layout. */
	public static List<Integer> layoutItems(Potion potion)
	{
		return layoutItems(potion, null);
	}

	/** Every distinct item id in {@link #layoutRows(Potion, Map)}. */
	public static List<Integer> layoutItems(Potion potion, Map<Integer, Integer> counts)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (int[] row : layoutRows(potion, counts))
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
		return compute(potion, counts, herbloreLevel, pie, hasCape(counts));
	}

	/** @param cape a Herblore cape is worn or held */
	public static PotionPlan compute(Potion potion, Map<Integer, Integer> counts, int herbloreLevel, boolean pie, boolean cape)
	{
		Route route = best(potion, counts, herbloreLevel, cape);
		Recipe fin = route.of(potion.getProduct());
		List<Node> nodes = ingredients(potion, route);

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
			return new PotionPlan(potion, rows, steps, Solved.NONE, Solved.NONE);
		}

		int bound = bound(base);
		Solved all = solve(potion, route, counts, base, herbloreLevel, cape, true, bound);
		Solved now = solve(potion, route, counts, base, herbloreLevel, cape, false, bound);

		// Prep steps that get you to `makeable`, in the order you would do them.
		Ctx run = all.run;
		boolean pestleFromCape = false;
		for (Map.Entry<Recipe, Integer> e : run.crafts.entrySet())
		{
			Recipe r = e.getKey();
			String decant = decantNeeded(r, e.getValue(), counts, run);
			if (decant != null)
			{
				steps.add(new Step(Severity.ACTION, "Decant to (4)", decant + ". " + DECANT_WHERE));
			}
			if (cape && !pestleFromCape && r.getTool() == Catalog.PESTLE && base.getOrDefault(Catalog.PESTLE, 0) <= 0)
			{
				pestleFromCape = true;
				steps.add(new Step(Severity.ACTION, "Take a pestle from your cape", "Search the Herblore cape"));
			}
			int merged = run.merged.getOrDefault(r, 0);
			int shown = e.getValue() - merged;
			if (shown <= 0)
			{
				// All of this cleaning happens inside the cape's unf step.
				continue;
			}
			String hint = r.isFromGrimy() && cape && mergedInto(r, run) ? "Grimy herbs work with your Herblore cape on" : null;
			steps.add(new Step(Severity.ACTION, String.format(Locale.US, r.getStepFormat().replace("%d", "%,d"), shown), hint));
		}

		// What stops you making more? Aim for what your best-stocked input
		// would allow, and list what that needs.
		int target = 0;
		for (Recipe.Input in : fin.getInputs())
		{
			target = Math.max(target, maxForInput(in, route, base, herbloreLevel, cape, bound));
		}
		Map<String, String> warnings = new LinkedHashMap<>();
		if (target > all.crafts)
		{
			Ctx want = new Ctx(base, herbloreLevel, true, cape, route);
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
		if (all.crafts == 0 && warnings.isEmpty())
		{
			steps.add(new Step(Severity.WARNING, "Nothing found. Open your bank.", null));
		}

		return new PotionPlan(potion, rows, steps, all, now);
	}

	/** True when some of the unf crafts of {@code r} used the cape's grimy shortcut. */
	private static boolean mergedInto(Recipe r, Ctx run)
	{
		for (Recipe.Input in : r.getInputs())
		{
			Recipe ir = in.getNode().getRecipe();
			if (ir != null && run.merged.getOrDefault(ir, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * For a recipe that takes whole (4)s: the inputs you will have to decant
	 * first, e.g. "Super attack, super defence", or null when the (4)s you
	 * hold or make already cover it. Potion storage counts are keyed by the
	 * 1-dose id, so they always read as needing a decant, which storage
	 * does when you withdraw.
	 */
	private static String decantNeeded(Recipe r, int crafts, Map<Integer, Integer> counts, Ctx run)
	{
		StringBuilder sb = new StringBuilder();
		for (Recipe.Input in : r.getInputs())
		{
			if (!Recipe.needsFull(in))
			{
				continue;
			}
			Node n = in.getNode();
			long fours = counts.getOrDefault(n.getId(), 0);
			Recipe made = run.route.of(n);
			if (made != null && made.getYield() == 4)
			{
				fours += run.crafts.getOrDefault(made, 0);
			}
			if ((long) crafts * in.getQty() > fours * 4)
			{
				sb.append(sb.length() == 0 ? n.getName() : ", " + Catalog.lower(n.getName()));
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	/** Just the "can make" number in potions, for the potion grid. */
	public static int makeable(Potion potion, Map<Integer, Integer> counts, int herbloreLevel)
	{
		return yieldOf(potion, counts, herbloreLevel).potions;
	}

	/**
	 * Herblore XP for making everything you can of this potion, including the
	 * prep along the way (cleaning herbs, intermediate potions).
	 */
	public static double xp(Potion potion, Map<Integer, Integer> counts, int herbloreLevel)
	{
		Solved s = yieldOf(potion, counts, herbloreLevel);
		double xp = 0;
		if (s.run != null)
		{
			for (Map.Entry<Recipe, Integer> e : s.run.crafts.entrySet())
			{
				xp += e.getKey().getXp() * e.getValue();
			}
		}
		return xp;
	}

	private static Solved yieldOf(Potion potion, Map<Integer, Integer> counts, int herbloreLevel)
	{
		if (herbloreLevel > 0 && herbloreLevel < potion.getLevel())
		{
			return Solved.NONE;
		}
		boolean cape = hasCape(counts);
		Route route = best(potion, counts, herbloreLevel, cape);
		Map<Node, Integer> base = base(potion, route, counts);
		return solve(potion, route, counts, base, herbloreLevel, cape, true, bound(base));
	}

	private static Map<Node, Integer> base(Potion potion, Route route, Map<Integer, Integer> counts)
	{
		Map<Node, Integer> base = new IdentityHashMap<>();
		for (Node n : ingredients(potion, route))
		{
			base.put(n, Math.min(n.count(counts), MAX_UNITS));
		}
		return base;
	}

	/**
	 * Which recipe each item is made by. Nearly everything has one way; a
	 * few (extended super antifire) have more, and a route picks one each.
	 */
	static final class Route
	{
		static final Route USUAL = new Route(Collections.emptyMap());

		private final Map<Node, Recipe> pick;

		private Route(Map<Node, Recipe> pick)
		{
			this.pick = pick;
		}

		Recipe of(Node n)
		{
			Recipe r = pick.get(n);
			return r != null ? r : n.getRecipe();
		}
	}

	/** Every route for this potion, the usual one first. */
	static List<Route> routes(Potion potion)
	{
		List<Node> multi = new ArrayList<>();
		findMulti(potion.getProduct(), Collections.newSetFromMap(new IdentityHashMap<>()), multi);
		List<Map<Node, Recipe>> picks = new ArrayList<>();
		picks.add(new IdentityHashMap<>());
		for (Node n : multi)
		{
			List<Map<Node, Recipe>> next = new ArrayList<>();
			for (Map<Node, Recipe> p : picks)
			{
				for (Recipe r : n.getRecipes())
				{
					Map<Node, Recipe> q = new IdentityHashMap<>(p);
					q.put(n, r);
					next.add(q);
				}
			}
			picks = next;
		}
		List<Route> out = new ArrayList<>();
		for (Map<Node, Recipe> p : picks)
		{
			out.add(p.isEmpty() ? Route.USUAL : new Route(p));
		}
		return out;
	}

	private static void findMulti(Node node, Set<Node> done, List<Node> multi)
	{
		if (!done.add(node))
		{
			return;
		}
		List<Recipe> all = node.getRecipes();
		if (all.size() > 1)
		{
			multi.add(node);
		}
		for (Recipe r : all)
		{
			for (Recipe.Input in : r.getInputs())
			{
				findMulti(in.getNode(), done, multi);
			}
		}
	}

	/**
	 * The route that makes the most potions from what you hold. On a tie,
	 * the one whose best-stocked input goes furthest (so a shortfall names
	 * the route you've started on), then the usual one.
	 */
	private static Route best(Potion potion, Map<Integer, Integer> counts, int lvl, boolean cape)
	{
		List<Route> routes = routes(potion);
		if (routes.size() == 1)
		{
			return routes.get(0);
		}
		Route best = null;
		int bestPotions = -1;
		int bestReach = -1;
		for (Route route : routes)
		{
			Map<Node, Integer> base = base(potion, route, counts);
			int bound = bound(base);
			int potions = solve(potion, route, counts, base, lvl, cape, true, bound).potions;
			int reach = 0;
			for (Recipe.Input in : route.of(potion.getProduct()).getInputs())
			{
				reach = Math.max(reach, maxForInput(in, route, base, lvl, cape, bound));
			}
			if (potions > bestPotions || potions == bestPotions && reach > bestReach)
			{
				best = route;
				bestPotions = potions;
				bestReach = reach;
			}
		}
		return best;
	}

	/** Search bound: no craft yields more than 10 (a crystal shard), so this comfortably covers it. */
	private static int bound(Map<Node, Integer> base)
	{
		long sum = 1;
		for (int v : base.values())
		{
			sum += (long) v * 10;
		}
		return (int) Math.min(sum, MAX_UNITS * 10L);
	}

	/** Final crafts and the potions they make, with the run that gets there. */
	private static final class Solved
	{
		static final Solved NONE = new Solved(0, 0, new int[5], null, null);

		final int crafts;
		final int potions;
		/** Potions made by dose, index 1-4; only for potions made a dose at a time. */
		final int[] bySize;
		final Ctx run;
		/** The final recipe on the chosen route, or null. */
		final Recipe fin;

		Solved(int crafts, int potions, int[] bySize, Ctx run, Recipe fin)
		{
			this.crafts = crafts;
			this.potions = potions;
			this.bySize = bySize;
			this.run = run;
			this.fin = fin;
		}

		boolean perDose(Potion p)
		{
			Recipe r = fin != null ? fin : p.getProduct().getRecipe();
			return p.getProduct().isDosed() && r.isPerDose();
		}

		String label(Potion p)
		{
			if (!perDose(p))
			{
				// The route decides: extended super antifire from extended antifire is a (4).
				return p.getProduct().isDosed() && fin != null ? p.getName() + "(" + Math.min(4, fin.getYield()) + ")" : p.potionName();
			}
			int size = 0;
			for (int d = 1; d <= 4; d++)
			{
				if (bySize[d] > 0)
				{
					if (size != 0)
					{
						return p.getName();
					}
					size = d;
				}
			}
			return size == 0 ? p.getName() : p.getName() + "(" + size + ")";
		}

		String doseMix(Potion p)
		{
			if (!perDose(p))
			{
				return null;
			}
			StringBuilder sb = new StringBuilder();
			for (int d = 4; d >= 1; d--)
			{
				if (bySize[d] > 0)
				{
					sb.append(sb.length() == 0 ? "" : ", ").append(String.format(Locale.US, "%,d", bySize[d])).append(" x (").append(d).append(")");
				}
			}
			return sb.length() == 0 ? null : sb.toString();
		}
	}

	private static Solved solve(Potion potion, Route route, Map<Integer, Integer> counts, Map<Node, Integer> base, int lvl, boolean cape, boolean allowCraft, int bound)
	{
		Recipe fin = route.of(potion.getProduct());
		int crafts = maxCrafts(fin, route, base, lvl, allowCraft, cape, bound);
		Ctx run = new Ctx(base, lvl, allowCraft, cape, route);
		run.makeFinal(fin, crafts);
		if (!(potion.getProduct().isDosed() && fin.isPerDose()))
		{
			return new Solved(crafts, crafts, new int[5], run, fin);
		}
		// A dose at a time, but a whole potion per go: super energy(3) and
		// three amylase make one stamina(3). Count the whole potions.
		int[] used = take(available(fin.getInputs().get(0).getNode(), counts, run), crafts);
		int doses = 0;
		int potions = 0;
		for (int d = 1; d <= 4; d++)
		{
			doses += d * used[d];
			potions += used[d];
		}
		if (doses < crafts)
		{
			run = new Ctx(base, lvl, allowCraft, cape, route);
			run.makeFinal(fin, doses);
		}
		return new Solved(doses, potions, used, run, fin);
	}

	/**
	 * Potions of {@code x} on hand after the run, by dose (index 1-4): held,
	 * plus made. Doses keyed on the 1-dose id are mostly potion storage,
	 * which withdraws at any dose, so they count as (4)s.
	 */
	private static int[] available(Node x, Map<Integer, Integer> counts, Ctx run)
	{
		int[] out = new int[5];
		int[] ids = x.getDoseIds();
		for (int d = 2; d <= 4; d++)
		{
			out[d] += Math.min(counts.getOrDefault(ids[d - 1], 0), MAX_UNITS);
		}
		int ones = Math.min(counts.getOrDefault(ids[0], 0), MAX_UNITS);
		out[4] += ones / 4;
		if (ones % 4 > 0)
		{
			out[ones % 4]++;
		}
		Recipe r = run.route.of(x);
		int made = r == null ? 0 : run.crafts.getOrDefault(r, 0);
		if (made > 0)
		{
			if (r.isPerDose())
			{
				int[] from = take(available(r.getInputs().get(0).getNode(), counts, run), made);
				for (int d = 1; d <= 4; d++)
				{
					out[d] += from[d];
				}
			}
			else if (r.getYield() >= 1 && r.getYield() <= 4)
			{
				out[r.getYield()] += made;
			}
		}
		return out;
	}

	/** Whole potions covering up to {@code doses}, biggest first. */
	private static int[] take(int[] potions, int doses)
	{
		int[] used = new int[5];
		for (int d = 4; d >= 1; d--)
		{
			int k = Math.min(potions[d], doses / d);
			used[d] = k;
			doses -= k * d;
		}
		return used;
	}

	private static String qty(Node n, int amount)
	{
		String qty = String.format(Locale.US, "%,d", amount);
		return n.isDosed() ? qty + " doses of " + Catalog.lower(n.getName()) : qty + " " + Catalog.lower(n.getName());
	}

	private static int maxCrafts(Recipe fin, Route route, Map<Node, Integer> base, int lvl, boolean allowCraft, boolean cape, int bound)
	{
		int lo = 0;
		int hi = bound;
		while (lo < hi)
		{
			int mid = (lo + hi + 1) >>> 1;
			Ctx c = new Ctx(base, lvl, allowCraft, cape, route);
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
	private static int maxForInput(Recipe.Input in, Route route, Map<Node, Integer> base, int lvl, boolean cape, int bound)
	{
		int lo = 0;
		int hi = bound;
		while (lo < hi)
		{
			int mid = (lo + hi + 1) >>> 1;
			Ctx c = new Ctx(base, lvl, true, cape, route);
			c.needInput(in, in.getQty() * mid);
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
		final boolean cape;
		final Route route;
		/** Cleaning crafts done inside a cape unf step (grimy straight in), so no Clean step of their own. */
		final Map<Recipe, Integer> merged = new IdentityHashMap<>();

		Ctx(Map<Node, Integer> base, int lvl, boolean allowCraft, boolean cape, Route route)
		{
			this.route = route;
			this.pool = new IdentityHashMap<>(base);
			this.lvl = lvl;
			this.allowCraft = allowCraft;
			this.cape = cape;
		}

		/** Held stand-ins first (never made for this), then the input itself. */
		void needInput(Recipe.Input in, int amount)
		{
			int rem = amount;
			for (Node alt : in.getAlts())
			{
				int have = pool.getOrDefault(alt, 0);
				int take = Math.min(have, rem);
				pool.put(alt, have - take);
				rem -= take;
			}
			if (rem > 0)
			{
				need(in.getNode(), rem);
			}
		}

		void makeFinal(Recipe fin, int n)
		{
			if (n <= 0)
			{
				return;
			}
			for (Recipe.Input in : fin.getInputs())
			{
				needInput(in, in.getQty() * n);
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

			Recipe r = route.of(node);
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
				// The Herblore cape holds a pestle and mortar.
				boolean capeTool = cape && r.getTool() == Catalog.PESTLE;
				if (can > 0 && r.getTool() != null && !capeTool && pool.getOrDefault(r.getTool(), 0) <= 0)
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
				Recipe clean = in.getNode().getRecipe();
				boolean grimyIn = cape && r.isFromGrimy() && clean != null && clean.isAltForm() && clean.getTool() == null;
				int before = grimyIn ? this.crafts.getOrDefault(clean, 0) : 0;
				needInput(in, in.getQty() * crafts);
				if (grimyIn)
				{
					int cleaned = this.crafts.getOrDefault(clean, 0) - before;
					if (cleaned > 0)
					{
						merged.merge(clean, cleaned, Integer::sum);
					}
				}
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
