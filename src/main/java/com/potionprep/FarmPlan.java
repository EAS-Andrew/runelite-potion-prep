package com.potionprep;

import com.potionprep.Farm.AllotmentSeed;
import com.potionprep.Farm.FlowerSeed;
import com.potionprep.Farm.HerbSeed;
import com.potionprep.Farm.Patch;
import com.potionprep.Farm.Teleport;
import com.potionprep.PotionPlan.Severity;
import com.potionprep.PotionPlan.Step;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.gameval.ItemID;

/** A herb run: which patches, how to reach them, and what to bring. Pure logic. */
@Getter
public final class FarmPlan
{
	@Value
	@Builder
	public static class Settings
	{
		HerbSeed herb;
		FlowerSeed flower;
		AllotmentSeed allotment;
		Set<Patch> patches;
		/** Teleport picked by hand per patch; missing means automatic. */
		Map<Patch, Teleport> overrides;
	}

	/** What the game says about the player, beyond item counts. Unknown values never lock anything. */
	@Value
	@Builder(toBuilder = true)
	public static class Account
	{
		/** Real Farming level, 0 if unknown. */
		int farming;
		/** Current (boosted) Magic level, 0 if unknown. */
		int magic;
		/** Spellbook varbit, -1 if unknown. */
		@Builder.Default
		int spellbook = -1;
		/** Finished quests, or null if unknown. */
		Set<Quest> quests;
		/** Morytania elite diary done, or null if unknown. */
		Boolean moryElite;
		/** Items stored with a tool leprechaun. */
		@Builder.Default
		Map<Integer, Integer> leprechaun = Collections.emptyMap();
		/** Bottomless compost bucket uses left, 0 if unknown. */
		int bucketUses;
		/** Bottomless bucket compost: 1 compost, 2 super, 3 ultra, 0 unknown. */
		int bucketType;
		/** Runes in a rune pouch, item id to quantity. */
		@Builder.Default
		Map<Integer, Integer> pouch = Collections.emptyMap();
		/** Elements an equipped staff provides, as {@link Farm.Rune#getElement()} bits. */
		int staffElements;
		/** Fairytale II far enough along to use fairy rings, or null if unknown. */
		Boolean fairyRings;
		/** Lumbridge elite diary done (fairy rings without a staff), or null if unknown. */
		Boolean lumbridgeElite;
		/** POH_HOUSE_LOCATION value, -1 if unknown. */
		@Builder.Default
		int houseLocation = -1;
		/** Portal destinations in your house (seen or set in config), or null if unknown. */
		Set<String> portals;
		/** Spirit trees you've grown, by key (see {@link Farm.Teleport#getPortal()}). */
		@Builder.Default
		Set<String> spiritTrees = Collections.emptySet();
		/** Your house has a spirit tree (reach spirit trees via your house). */
		boolean spiritHouse;
		/** What each patch is doing, from Time Tracking; missing means unknown. */
		@Builder.Default
		Map<Patch, PatchStatus> patchStatus = Collections.emptyMap();
		/** Leave out patches that are still growing. */
		@Builder.Default
		boolean skipGrowing = true;
		/** Epoch seconds, for "ready in". */
		long now;
		/** Kandarin diary tiers done past easy (0 none, 1 medium, 2 hard, 3 elite): Catherby yield. */
		int kandarinDiary;
		/** Quetzal whistle charges from its last chat message, -1 if unknown. */
		@Builder.Default
		int whistleCharges = -1;
		/** Xeric's talisman charges from its last chat message, -1 if unknown. */
		@Builder.Default
		int xericCharges = -1;
		/** Tithe Farm's Auto-weed is on: patches never need raking. */
		boolean autoweed;
		/** Kourend easy diary: Hosidius can't get diseased. */
		boolean kourendEasy;
		/** Kourend hard diary: more herbs at Hosidius and the Farming Guild. */
		boolean kourendHard;
		/** Seed vault contents, or empty if it hasn't been opened. */
		@Builder.Default
		Map<Integer, Integer> seedVault = Collections.emptyMap();

		boolean done(Quest q)
		{
			return q == null || quests == null || quests.contains(q);
		}
	}

	@Value
	public static class Stop
	{
		Patch patch;
		/** null when the patch has no teleport options at all. */
		Teleport teleport;
		/** Item to bring for {@link #teleport}, -1 for spells. */
		int itemId;
		/** You can use the teleport (own it, or can cast it). */
		boolean owned;
		boolean manual;
		/** Why the patch can't be used (level, quest, diary), or null. */
		String lockReason;
		/** What the automatic choice would be. */
		Teleport auto;
		/** Why the chosen teleport can't be used, or null. */
		String problem;
		/** What the patch is doing, or null if unknown. */
		PatchStatus status;
		/** Left out of this run because it's still growing. */
		boolean growing;

		public boolean isLocked()
		{
			return lockReason != null;
		}
	}

	@Value
	public static class Supply
	{
		String name;
		int itemId;
		int have;
		int need;
		/** Where it is, when not the bank or inventory (e.g. "Leprechaun"), else null. */
		String where;

		Supply(String name, int itemId, int have, int need)
		{
			this(name, itemId, have, need, null);
		}

		Supply(String name, int itemId, int have, int need, String where)
		{
			this.name = name;
			this.itemId = itemId;
			this.have = have;
			this.need = need;
			this.where = where;
		}
	}

	private final List<Stop> stops;
	private final List<Supply> supplies;
	private final List<Step> steps;
	private final List<int[]> layoutRows;
	/** Herb being planted. */
	private final HerbSeed herb;
	/** Grimy herbs expected from this run's plantings, 0 if unknown. */
	private final double expectedHerbs;
	/** Snape grass and limpwurt roots expected from the allotments and flower patches planted. */
	private double expectedSnape;
	private double expectedLimpwurt;

	/** How many of each item to withdraw (seeds, compost, runes, tablets), for the bank overlay. */
	private final Map<Integer, Integer> withdraw;

	private FarmPlan(List<Stop> stops, List<Supply> supplies, List<Step> steps, List<int[]> layoutRows,
		HerbSeed herb, double expectedHerbs, Map<Integer, Integer> withdraw)
	{
		this.withdraw = withdraw;
		this.stops = stops;
		this.supplies = supplies;
		this.steps = steps;
		this.layoutRows = layoutRows;
		this.herb = herb;
		this.expectedHerbs = expectedHerbs;
	}

	/** For tests: no quests, spells or storage known. */
	public static FarmPlan compute(Settings s, Map<Integer, Integer> counts, Set<Integer> worn, int farming)
	{
		return compute(s, counts, worn, Account.builder().farming(farming).build());
	}

	public static FarmPlan compute(Settings s, Map<Integer, Integer> counts, Set<Integer> worn, int farming,
		Map<Integer, Integer> leprechaun, int bucketUses, int bucketType)
	{
		return compute(s, counts, worn, Account.builder().farming(farming).leprechaun(leprechaun)
			.bucketUses(bucketUses).bucketType(bucketType).build());
	}

	/**
	 * @param counts bank plus inventory, item id to quantity
	 * @param worn   item ids currently worn
	 */
	public static FarmPlan compute(Settings s, Map<Integer, Integer> counts, Set<Integer> worn, Account acct)
	{
		Predicate<Integer> owned = owned(counts, worn);
		Map<Integer, Integer> leprechaun = acct.getLeprechaun();
		int farming = acct.getFarming();
		List<Step> steps = new ArrayList<>();

		// --- Stops: pick a teleport per patch, budgeting runes and tablets across the whole run ---
		Runes runes = new Runes(counts, acct);
		Map<Integer, Integer> tabletsUsed = new HashMap<>();
		// Tablets the chosen teleports use, even ones you're short of.
		Map<Integer, Integer> tabletsNeeded = new HashMap<>();
		List<Stop> stops = new ArrayList<>();
		int herbPatches = 0;
		int skipped = 0;
		List<Patch> planted = new ArrayList<>();
		long nextReady = 0;
		int flowerPatches = 0;
		int allotments = 0;
		for (Patch p : Patch.values())
		{
			if (!s.getPatches().contains(p))
			{
				continue;
			}
			String lock = lockReason(p, acct);
			Teleport manual = s.getOverrides().get(p);
			Teleport auto = null;
			for (Teleport t : p.getTeleports())
			{
				if (problem(t, owned, counts, tabletsUsed, runes, acct) == null)
				{
					auto = t;
					break;
				}
			}
			Teleport pick = manual != null ? manual : auto != null ? auto : firstItem(p);
			String problem = pick == null ? "No teleport" : problem(pick, owned, counts, tabletsUsed, runes, acct);
			int id = itemFor(pick, owned, acct);
			PatchStatus status = acct.getPatchStatus().get(p);
			boolean growing = lock == null && acct.isSkipGrowing() && status != null
				&& status.getState() == PatchStatus.State.GROWING;
			stops.add(new Stop(p, pick, id, problem == null, manual != null, lock, auto, problem, status, growing));

			if (lock != null)
			{
				continue;
			}
			if (growing)
			{
				nextReady = nextReady == 0 ? status.getReadyAt() : Math.min(nextReady, status.getReadyAt());
				skipped++;
				continue;
			}
			// Commit what this teleport uses so later patches see what's left. A
			// spell's runes are listed even when short, so you know what to get.
			if (pick != null && pick.getSpell() != null)
			{
				runes.spend(pick.getSpell());
			}
			else if (pick != null && problem == null && id != -1 && Farm.consumable(id))
			{
				tabletsUsed.merge(id, 1, Integer::sum);
			}
			if (lock == null && !growing && pick != null && pick.getSpell() == null && id != -1 && Farm.consumable(id))
			{
				tabletsNeeded.merge(id, 1, Integer::sum);
			}
			if (pick == null || problem != null)
			{
				steps.add(new Step(Severity.WARNING, p.getName() + ": " + (problem != null ? lowerFirst(problem) : "no teleport"),
					options(p)));
			}
			herbPatches++;
			planted.add(p);
			flowerPatches += p.isFlower() ? 1 : 0;
			allotments += p.getAllotments();
		}

		// --- Supplies ---
		List<Supply> supplies = new ArrayList<>();
		Set<Integer> seedIds = new LinkedHashSet<>();
		Map<Integer, Integer> vault = acct.getSeedVault();
		seed(s.getHerb(), s.getHerb().getName() + " seed", herbPatches, counts, vault, farming, supplies, steps, seedIds);
		boolean flowers = s.getFlower() != FlowerSeed.NONE && flowerPatches > 0;
		if (flowers)
		{
			seed(s.getFlower(), s.getFlower().getName() + " seed", flowerPatches, counts, vault, farming, supplies, steps, seedIds);
		}
		boolean allot = s.getAllotment() != AllotmentSeed.NONE && allotments > 0;
		if (allot)
		{
			seed(s.getAllotment(), s.getAllotment().getName() + " seed", allotments * Farm.SEEDS_PER_ALLOTMENT,
				counts, vault, farming, supplies, steps, seedIds);
		}

		int plots = herbPatches + (flowers ? flowerPatches : 0) + (allot ? allotments : 0);
		List<Integer> toolRow = new ArrayList<>();
		int compostId = compost(plots, counts, owned, leprechaun, acct.getBucketUses(), acct.getBucketType(), supplies, steps, toolRow);

		// A rake is only for weeds: none with Tithe Farm's Auto-weed on, otherwise
		// only on an empty patch (or one whose state isn't known).
		boolean weeds = false;
		for (Patch p : planted)
		{
			PatchStatus st = acct.getPatchStatus().get(p);
			weeds |= !acct.isAutoweed() && (st == null || st.getState() == PatchStatus.State.EMPTY);
		}
		for (int tool : Farm.TOOLS)
		{
			if (tool == ItemID.RAKE && !weeds)
			{
				continue;
			}
			String name = tool == ItemID.RAKE ? "Rake" : tool == ItemID.DIBBER ? "Seed dibber" : "Spade";
			boolean stored = leprechaun.getOrDefault(tool, 0) > 0;
			boolean have = owned.test(tool);
			supplies.add(new Supply(name, tool, have || stored ? 1 : 0, 1, !have && stored ? "Leprechaun" : null));
			if (!have && !stored)
			{
				steps.add(new Step(Severity.WARNING, "No " + lower(name), "Or store one with a tool leprechaun"));
			}
			// Tools kept at a leprechaun aren't needed from the bank.
			if (have || !stored)
			{
				toolRow.add(tool);
			}
		}
		if (owned.test(Farm.MAGIC_SECATEURS))
		{
			supplies.add(new Supply("Magic secateurs", Farm.MAGIC_SECATEURS, 1, 1));
			toolRow.add(Farm.MAGIC_SECATEURS);
		}
		else if (leprechaun.getOrDefault(Farm.MAGIC_SECATEURS, 0) > 0)
		{
			supplies.add(new Supply("Magic secateurs", Farm.MAGIC_SECATEURS, 1, 1, "Leprechaun"));
		}
		if (compostId != -1)
		{
			toolRow.add(compostId);
		}

		// Runes for the spells on this run.
		List<Integer> runeRow = runes.supplies(supplies, steps);

		// --- Bank layout: tools, seeds, runes, then teleports in run order ---
		List<int[]> rows = new ArrayList<>();
		addRow(rows, toolRow);
		addRow(rows, new ArrayList<>(seedIds));
		addRow(rows, runeRow);
		Set<Integer> teleIds = new LinkedHashSet<>();
		for (Stop st : stops)
		{
			// Locked or still-growing patches aren't on this run.
			if (!st.isLocked() && !st.isGrowing() && st.getItemId() != -1)
			{
				teleIds.add(st.getItemId());
			}
		}
		addRow(rows, new ArrayList<>(teleIds));

		if (skipped > 0)
		{
			steps.add(0, new Step(Severity.ACTION, skipped + (skipped == 1 ? " patch" : " patches") + " still growing"
				+ (acct.getNow() > 0 ? ", next ready in " + minutes(nextReady - acct.getNow()) : ""),
				"Left out of this run. Turn off \"Skip growing patches\" in the plugin config to include them."));
		}
		if (herbPatches == 0)
		{
			steps.add(0, new Step(Severity.WARNING, skipped > 0 ? "Nothing ready yet" : "No patches selected", null));
		}
		double expected = expectedHerbs(s.getHerb(), planted, acct, compostTier(compostId, acct), owned, leprechaun);
		Map<Integer, Integer> withdraw = new HashMap<>(tabletsNeeded);
		withdraw.putAll(runes.allocate());
		Set<Integer> runeIds = new java.util.HashSet<>();
		for (Farm.Rune r : Farm.Rune.values())
		{
			runeIds.add(r.getItemId());
		}
		for (Supply sup : supplies)
		{
			if (runeIds.contains(sup.getItemId()))
			{
				continue;
			}
			// Counted things only: seeds, compost buckets, runes. Tools and the bucket are just "bring it".
			boolean counted = sup.getNeed() > 1 || sup.getItemId() == s.getHerb().getSeedId();
			if (counted && sup.getItemId() != Farm.BOTTOMLESS_BUCKET && !"Staff".equals(sup.getWhere()))
			{
				withdraw.merge(sup.getItemId(), sup.getNeed(), Integer::sum);
			}
		}
		FarmPlan plan = new FarmPlan(stops, supplies, steps, rows, s.getHerb(), expected, withdraw);
		if (acct.getFarming() > 0)
		{
			boolean secateurs = owned.test(Farm.MAGIC_SECATEURS) || leprechaun.getOrDefault(Farm.MAGIC_SECATEURS, 0) > 0;
			int tier = compostTier(compostId, acct);
			// Only as many plantings as you have seeds for.
			if (allot)
			{
				int seedsHave = counts.getOrDefault(s.getAllotment().getSeedId(), 0) + vault.getOrDefault(s.getAllotment().getSeedId(), 0);
				int n = Math.min(allotments, seedsHave / Farm.SEEDS_PER_ALLOTMENT);
				plan.expectedSnape = n * Yield.snapeGrass(acct.getFarming(), secateurs, tier);
			}
			if (flowers)
			{
				int seedsHave = counts.getOrDefault(s.getFlower().getSeedId(), 0) + vault.getOrDefault(s.getFlower().getSeedId(), 0);
				int n = Math.min(flowerPatches, seedsHave);
				plan.expectedLimpwurt = n * Yield.limpwurt(acct.getFarming(), secateurs);
			}
		}
		return plan;
	}

	/** 0 none, 1 compost, 2 super, 3 ultra. */
	private static int compostTier(int compostId, Account acct)
	{
		if (compostId == Farm.BOTTOMLESS_BUCKET)
		{
			return acct.getBucketType() > 0 ? acct.getBucketType() : 2;
		}
		return compostId == ItemID.BUCKET_ULTRACOMPOST ? 3 : compostId == ItemID.BUCKET_SUPERCOMPOST ? 2
			: compostId == ItemID.BUCKET_COMPOST ? 1 : 0;
	}

	static double expectedHerbs(HerbSeed herb, List<Patch> planted, Account acct, int compost,
		Predicate<Integer> owned, Map<Integer, Integer> leprechaun)
	{
		if (acct.getFarming() <= 0)
		{
			return 0;
		}
		double item = (owned.test(Farm.MAGIC_SECATEURS) || leprechaun.getOrDefault(Farm.MAGIC_SECATEURS, 0) > 0 ? 0.10 : 0)
			+ (owned.test(ItemID.SKILLCAPE_FARMING) || owned.test(ItemID.SKILLCAPE_FARMING_TRIMMED)
			|| owned.test(ItemID.SKILLCAPE_MAX) ? 0.05 : 0);
		double total = 0;
		for (Patch p : planted)
		{
			double diary = p == Patch.CATHERBY ? 0.05 * acct.getKandarinDiary()
				: (p == Patch.HOSIDIUS || p == Patch.FARMING_GUILD) && acct.isKourendHard() ? 0.05 : 0;
			boolean diseaseFree = p == Patch.TROLL_STRONGHOLD || p == Patch.WEISS || p == Patch.HARMONY
				|| p == Patch.HOSIDIUS && acct.isKourendEasy();
			total += Yield.expected(herb.getLowSave(), acct.getFarming(), item + diary, compost, diseaseFree);
		}
		return total;
	}

	/** "1h 5m" / "12m". */
	static String minutes(long seconds)
	{
		long m = Math.max(1, (seconds + 59) / 60);
		return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
	}

	static String lockReason(Patch p, Account acct)
	{
		if (acct.getFarming() > 0 && acct.getFarming() < p.getLevel())
		{
			return "Needs " + p.getLevel() + " Farming";
		}
		if (!acct.done(p.getQuest()))
		{
			return "Needs " + p.getQuest().getName();
		}
		if (p.isMoryElite() && Boolean.FALSE.equals(acct.getMoryElite()))
		{
			return "Needs the Morytania elite diary";
		}
		return null;
	}

	/** Why a teleport can't be used right now, or null if it can. */
	private static String problem(Teleport t, Predicate<Integer> owned, Map<Integer, Integer> counts,
		Map<Integer, Integer> tabletsUsed, Runes runes, Account acct)
	{
		Farm.Spell spell = t.getSpell();
		if (spell != null)
		{
			if (acct.getSpellbook() >= 0 && acct.getSpellbook() != spell.getBook())
			{
				return "Needs the " + bookName(spell.getBook()) + " spellbook";
			}
			if (acct.getMagic() > 0 && acct.getMagic() < spell.getLevel())
			{
				return "Needs " + spell.getLevel() + " Magic";
			}
			if (!acct.done(spell.getQuest()))
			{
				return "Needs " + spell.getQuest().getName();
			}
			return runes.canAfford(spell) ? null : "Not enough runes";
		}
		switch (t.getKind())
		{
			case FAIRY:
				if (Boolean.FALSE.equals(acct.getFairyRings()))
				{
					return "Needs Fairytale II (started)";
				}
				if (Boolean.TRUE.equals(acct.getLumbridgeElite()))
				{
					return null;
				}
				return bestId(t, owned) == -1 ? "Needs a dramen or lunar staff" : null;
			case HOUSE:
				if (acct.getHouseLocation() >= 0 && acct.getHouseLocation() != Farm.HOUSE_IN_HOSIDIUS)
				{
					return "Your house isn't in Hosidius";
				}
				break;
			case PORTAL:
				if (acct.getPortals() == null)
				{
					return "Visit your house once, or set your portals in the plugin config";
				}
				if (!acct.getPortals().contains(t.getPortal()))
				{
					return "No " + t.getName().replace("House portal: ", "") + " portal (set it in the plugin config if you have one)";
				}
				break;
			case SPIRIT:
				if (!acct.getSpiritTrees().contains(t.getPortal()))
				{
					return "Set it in the plugin config if you've grown one";
				}
				if (!acct.done(Quest.TREE_GNOME_VILLAGE))
				{
					return "Needs Tree Gnome Village";
				}
				if (t == Teleport.SPIRIT_GUILD && acct.getFarming() > 0 && acct.getFarming() < 85)
				{
					return "Needs 85 Farming";
				}
				if (!acct.isSpiritHouse())
				{
					// Walk to any spirit tree; nothing to bring.
					return null;
				}
				break;
			default:
				break;
		}
		int id = bestId(t, owned);
		if (id == -1)
		{
			return t.getKind() == Farm.Teleport.Kind.ITEM ? "You don't have one" : "No way into your house";
		}
		if (t == Teleport.QUETZAL_WHISTLE && id != ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE && acct.getWhistleCharges() == 0
			|| t == Teleport.XERICS_TALISMAN && acct.getXericCharges() == 0)
		{
			return "Out of charges";
		}
		if (Farm.consumable(id) && counts.getOrDefault(id, 0) <= tabletsUsed.getOrDefault(id, 0))
		{
			return "Not enough tablets";
		}
		return null;
	}

	/** The item to bring for a teleport, or -1 (spells, staff-free fairy rings). */
	private static int itemFor(Teleport t, Predicate<Integer> owned, Account acct)
	{
		if (t == null || t.getSpell() != null || t.getIds().length == 0)
		{
			return -1;
		}
		if (t.getKind() == Farm.Teleport.Kind.FAIRY && Boolean.TRUE.equals(acct.getLumbridgeElite())
			|| t.getKind() == Farm.Teleport.Kind.SPIRIT && !acct.isSpiritHouse())
		{
			return -1;
		}
		int id = bestId(t, owned);
		return id != -1 ? id : t.getIds()[0];
	}

	private static String bookName(int book)
	{
		return book == Farm.LUNAR ? "Lunar" : book == Farm.ARCEUUS ? "Arceuus" : "standard";
	}

	/** When nothing works, show the first item option (something to get), else the first option. */
	private static Teleport firstItem(Patch p)
	{
		for (Teleport t : p.getTeleports())
		{
			if (t.getSpell() == null)
			{
				return t;
			}
		}
		return p.getTeleports().length > 0 ? p.getTeleports()[0] : null;
	}

	static Predicate<Integer> owned(Map<Integer, Integer> counts, Set<Integer> worn)
	{
		return id -> counts.getOrDefault(id, 0) > 0 || worn.contains(id);
	}

	/** The preferred id of a teleport that the player owns, or -1. */
	static int bestId(Teleport t, Predicate<Integer> owned)
	{
		for (int id : t.getIds())
		{
			if (owned.test(id))
			{
				return id;
			}
		}
		return -1;
	}

	private static String lowerFirst(String s)
	{
		return Catalog.lower(s);
	}

	/**
	 * Rune budget for the run: plain runes (bank, inventory, pouch), an
	 * equipped staff for its elements, and combination runes for either of
	 * their elements.
	 */
	static final class Runes
	{
		private final Map<Integer, Integer> counts;
		private final Account acct;
		private final Map<Farm.Rune, Integer> spent = new EnumMap<>(Farm.Rune.class);

		Runes(Map<Integer, Integer> counts, Account acct)
		{
			this.counts = counts;
			this.acct = acct;
		}

		private int plain(Farm.Rune r)
		{
			return counts.getOrDefault(r.getItemId(), 0) + acct.getPouch().getOrDefault(r.getItemId(), 0);
		}

		private boolean staffed(Farm.Rune r)
		{
			return r.getElement() != 0 && (acct.getStaffElements() & r.getElement()) != 0;
		}

		boolean canAfford(Farm.Spell spell)
		{
			Map<Farm.Rune, Integer> need = new EnumMap<>(spent);
			spell.getCost().forEach((r, n) -> need.merge(r, n, Integer::sum));
			return shortfall(need).isEmpty();
		}

		void spend(Farm.Spell spell)
		{
			spell.getCost().forEach((r, n) -> spent.merge(r, n, Integer::sum));
		}

		/** Runes still missing after plain runes, staff and combination runes. */
		Map<Farm.Rune, Integer> shortfall(Map<Farm.Rune, Integer> need)
		{
			Map<Farm.Rune, Integer> left = new EnumMap<>(Farm.Rune.class);
			for (Map.Entry<Farm.Rune, Integer> e : need.entrySet())
			{
				int n = staffed(e.getKey()) ? 0 : Math.max(0, e.getValue() - plain(e.getKey()));
				if (n > 0)
				{
					left.put(e.getKey(), n);
				}
			}
			for (int[] combo : Farm.COMBO_RUNES)
			{
				int have = counts.getOrDefault(combo[0], 0) + acct.getPouch().getOrDefault(combo[0], 0);
				int use = 0;
				for (Farm.Rune r : Farm.Rune.values())
				{
					if ((combo[1] & r.getElement()) != 0)
					{
						use = Math.max(use, left.getOrDefault(r, 0));
					}
				}
				use = Math.min(use, have);
				for (Farm.Rune r : Farm.Rune.values())
				{
					if ((combo[1] & r.getElement()) != 0 && left.containsKey(r))
					{
						int n = left.get(r) - use;
						if (n > 0)
						{
							left.put(r, n);
						}
						else
						{
							left.remove(r);
						}
					}
				}
			}
			return left;
		}

		/**
		 * Which rune items to bring for everything spent, item id to amount.
		 * Combination runes that cover two needed elements go first (fewer
		 * slots), then plain runes, then combination runes for what's left.
		 * Elements an equipped staff provides need nothing.
		 */
		Map<Integer, Integer> allocate()
		{
			Map<Farm.Rune, Integer> left = new EnumMap<>(Farm.Rune.class);
			spent.forEach((r, n) ->
			{
				if (!staffed(r))
				{
					left.put(r, n);
				}
			});
			Map<Integer, Integer> use = new HashMap<>();
			Map<Integer, Integer> comboLeft = new HashMap<>();
			for (int[] combo : Farm.COMBO_RUNES)
			{
				comboLeft.put(combo[0], counts.getOrDefault(combo[0], 0) + acct.getPouch().getOrDefault(combo[0], 0));
			}
			// 1. Combination runes where both their elements are needed.
			for (int[] combo : Farm.COMBO_RUNES)
			{
				Farm.Rune a = null;
				Farm.Rune b = null;
				for (Farm.Rune r : Farm.Rune.values())
				{
					if ((combo[1] & r.getElement()) != 0)
					{
						if (a == null)
						{
							a = r;
						}
						else
						{
							b = r;
						}
					}
				}
				int k = Math.min(comboLeft.get(combo[0]), Math.min(left.getOrDefault(a, 0), left.getOrDefault(b, 0)));
				if (k > 0)
				{
					use.merge(combo[0], k, Integer::sum);
					comboLeft.merge(combo[0], -k, Integer::sum);
					left.merge(a, -k, Integer::sum);
					left.merge(b, -k, Integer::sum);
				}
			}
			// 2. Plain runes.
			for (Farm.Rune r : Farm.Rune.values())
			{
				int k = Math.min(plain(r), left.getOrDefault(r, 0));
				if (k > 0)
				{
					use.merge(r.getItemId(), k, Integer::sum);
					left.merge(r, -k, Integer::sum);
				}
			}
			// 3. Combination runes for a single element still short.
			for (int[] combo : Farm.COMBO_RUNES)
			{
				for (Farm.Rune r : Farm.Rune.values())
				{
					if ((combo[1] & r.getElement()) == 0)
					{
						continue;
					}
					int k = Math.min(comboLeft.get(combo[0]), left.getOrDefault(r, 0));
					if (k > 0)
					{
						use.merge(combo[0], k, Integer::sum);
						comboLeft.merge(combo[0], -k, Integer::sum);
						left.merge(r, -k, Integer::sum);
					}
				}
			}
			// Anything still short: ask for the plain rune.
			left.forEach((r, n) ->
			{
				if (n > 0)
				{
					use.merge(r.getItemId(), n, Integer::sum);
				}
			});
			return use;
		}

		/** Adds a supply row per rune the run uses; returns the rune ids to lay out. */
		List<Integer> supplies(List<Supply> supplies, List<Step> steps)
		{
			List<Integer> ids = new ArrayList<>();
			if (spent.isEmpty())
			{
				return ids;
			}
			Map<Farm.Rune, Integer> missing = shortfall(spent);
			Map<Integer, Integer> alloc = allocate();
			for (Map.Entry<Farm.Rune, Integer> e : spent.entrySet())
			{
				Farm.Rune r = e.getKey();
				if (staffed(r))
				{
					supplies.add(new Supply(r.getName(), r.getItemId(), 1, 1, "Staff"));
					continue;
				}
				int need = e.getValue();
				int short_ = missing.getOrDefault(r, 0);
				// What you have toward it, counting combination runes that cover it.
				int have = Math.max(plain(r), need - short_);
				boolean inPouch = acct.getPouch().getOrDefault(r.getItemId(), 0) > 0;
				supplies.add(new Supply(r.getName(), r.getItemId(), have, need,
					counts.getOrDefault(r.getItemId(), 0) == 0 && inPouch ? "Rune pouch" : null));
				if (short_ > 0)
				{
					steps.add(new Step(Severity.WARNING, "Short " + short_ + " " + Catalog.lower(r.getName()) + "s", null));
				}
				// Lay out the rune if some of it is to be used (and isn't just in the pouch),
				// or if you're short of it.
				if (alloc.containsKey(r.getItemId()) && counts.getOrDefault(r.getItemId(), 0) > 0 || short_ > 0)
				{
					ids.add(r.getItemId());
				}
			}
			for (int[] combo : Farm.COMBO_RUNES)
			{
				if (alloc.containsKey(combo[0]) && counts.getOrDefault(combo[0], 0) > 0)
				{
					ids.add(combo[0]);
				}
			}
			// Bring the rune pouch if you have one: it's the tidy way to carry them.
			for (int id : Farm.RUNE_POUCHES)
			{
				if (counts.getOrDefault(id, 0) > 0)
				{
					ids.add(0, id);
					break;
				}
			}
			return ids;
		}
	}


	private static String options(Patch p)
	{
		StringBuilder sb = new StringBuilder("Any of: ");
		String sep = "";
		for (Teleport t : p.getTeleports())
		{
			sb.append(sep).append(t.getName());
			sep = ", ";
		}
		return sb.toString();
	}

	private static void seed(Farm.Seed seed, String name, int need, Map<Integer, Integer> counts,
		Map<Integer, Integer> vault, int farming, List<Supply> supplies, List<Step> steps, Set<Integer> ids)
	{
		if (need <= 0)
		{
			return;
		}
		int banked = counts.getOrDefault(seed.getSeedId(), 0);
		int vaulted = vault.getOrDefault(seed.getSeedId(), 0);
		int have = banked + vaulted;
		// Seeds only in the seed vault come from there, not the bank.
		supplies.add(new Supply(name, seed.getSeedId(), have, need, banked == 0 && vaulted > 0 ? "Seed vault" : null));
		if (banked > 0 || vaulted == 0)
		{
			ids.add(seed.getSeedId());
		}
		if (farming > 0 && farming < seed.getLevel())
		{
			steps.add(new Step(Severity.WARNING, seed.getName() + " needs " + seed.getLevel() + " Farming", null));
		}
		if (have < need)
		{
			steps.add(new Step(Severity.WARNING, "Short " + (need - have) + " " + lower(name) + "s", null));
		}
	}

	/** @return the compost item for the bank layout, or -1 */
	private static int compost(int plots, Map<Integer, Integer> counts, Predicate<Integer> owned,
		Map<Integer, Integer> leprechaun, int bucketUses, int bucketType, List<Supply> supplies, List<Step> steps,
		List<Integer> emptyBucket)
	{
		if (plots <= 0)
		{
			return -1;
		}
		if (owned.test(Farm.BOTTOMLESS_BUCKET))
		{
			String name = bucketType == 3 ? "Bottomless (ultracompost)" : bucketType == 2 ? "Bottomless (supercompost)"
				: bucketType == 1 ? "Bottomless (compost)" : "Bottomless compost bucket";
			// Uses are read from the game; 0 means not known yet.
			if (bucketUses > 0)
			{
				supplies.add(new Supply(name, Farm.BOTTOMLESS_BUCKET, bucketUses, plots));
				if (bucketUses < plots)
				{
					steps.add(new Step(Severity.WARNING, "Bucket has " + bucketUses + " uses, need " + plots, null));
				}
			}
			else
			{
				supplies.add(new Supply(name, Farm.BOTTOMLESS_BUCKET, 1, 1));
			}
			return Farm.BOTTOMLESS_BUCKET;
		}
		if (owned.test(ItemID.BOTTOMLESS_COMPOST_BUCKET))
		{
			steps.add(new Step(Severity.WARNING, "Fill your bottomless compost bucket", "Use compost on it"));
			// Bring it so it can be filled from the compost below.
			emptyBucket.add(ItemID.BOTTOMLESS_COMPOST_BUCKET);
		}
		// Best compost you have enough of (bank, inventory or leprechaun);
		// otherwise whichever you have most of.
		java.util.function.ToIntFunction<Integer> have = id -> counts.getOrDefault(id, 0) + leprechaun.getOrDefault(id, 0);
		int best = -1;
		for (int id : Farm.COMPOST)
		{
			if (have.applyAsInt(id) >= plots)
			{
				best = id;
				break;
			}
		}
		if (best == -1)
		{
			best = Farm.COMPOST[0];
			for (int id : Farm.COMPOST)
			{
				if (have.applyAsInt(id) > have.applyAsInt(best))
				{
					best = id;
				}
			}
		}
		int total = have.applyAsInt(best);
		String name = best == ItemID.BUCKET_ULTRACOMPOST ? "Ultracompost" : best == ItemID.BUCKET_SUPERCOMPOST ? "Supercompost" : "Compost";
		boolean inBank = counts.getOrDefault(best, 0) > 0;
		supplies.add(new Supply(name, best, total, plots,
			leprechaun.getOrDefault(best, 0) > 0 && !inBank ? "Leprechaun" : null));
		if (total < plots)
		{
			steps.add(new Step(Severity.WARNING, "Short " + (plots - total) + " " + lower(name), null));
		}
		return inBank || leprechaun.getOrDefault(best, 0) == 0 ? best : -1;
	}

	private static void addRow(List<int[]> rows, List<Integer> ids)
	{
		for (int i = 0; i < ids.size(); i += PotionPlan.BANK_COLUMNS)
		{
			List<Integer> chunk = ids.subList(i, Math.min(ids.size(), i + PotionPlan.BANK_COLUMNS));
			rows.add(chunk.stream().mapToInt(Integer::intValue).toArray());
		}
	}

	private static String lower(String s)
	{
		return Catalog.lower(s);
	}

	static Set<Patch> defaultPatches()
	{
		// Every patch; ones you can't reach yet are locked automatically.
		return EnumSet.allOf(Patch.class);
	}
}
