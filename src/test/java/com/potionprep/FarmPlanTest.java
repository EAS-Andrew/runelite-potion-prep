package com.potionprep;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.potionprep.Farm.AllotmentSeed;
import com.potionprep.Farm.FlowerSeed;
import com.potionprep.Farm.HerbSeed;
import com.potionprep.Farm.Patch;
import com.potionprep.Farm.Teleport;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class FarmPlanTest
{
	private static FarmPlan.Settings settings(Set<Patch> patches, FlowerSeed flower, AllotmentSeed allot, Map<Patch, Teleport> overrides)
	{
		return FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(flower).allotment(allot)
			.patches(patches).overrides(overrides).build();
	}

	private static FarmPlan.Supply supply(FarmPlan plan, int id)
	{
		return plan.getSupplies().stream().filter(s -> s.getItemId() == id).findFirst().orElse(null);
	}

	@Test
	public void countsSeedsAndCompostPerPlot()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.RANARR_SEED, 10);
		bank.put(ItemID.BUCKET_SUPERCOMPOST, 30);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR, Patch.CATHERBY, Patch.WEISS),
			FlowerSeed.LIMPWURT, AllotmentSeed.SNAPE_GRASS, Collections.emptyMap()), bank, new HashSet<>(), 99);
		assertEquals(3, supply(plan, ItemID.RANARR_SEED).getNeed());
		// Weiss has no flower or allotments.
		assertEquals(2, supply(plan, ItemID.LIMPWURT_SEED).getNeed());
		assertEquals(4 * 3, supply(plan, ItemID.SNAPE_GRASS_SEED).getNeed());
		// 3 herb + 2 flower + 4 allotment plots; ultra is short so super is used.
		assertEquals(9, supply(plan, ItemID.BUCKET_SUPERCOMPOST).getNeed());
	}

	@Test
	public void bottomlessBucketReplacesCompost()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 1);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), 99);
		assertEquals(1, supply(plan, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED).getNeed());
		assertEquals(null, supply(plan, ItemID.BUCKET_ULTRACOMPOST));
	}

	@Test
	public void picksTheTeleportYouOwnUnlessOverridden()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.POH_TABLET_ARDOUGNETELEPORT, 5);
		Set<Integer> worn = new HashSet<>();
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.ARDOUGNE), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, worn, 99);
		FarmPlan.Stop stop = plan.getStops().get(0);
		assertEquals(Teleport.ARDOUGNE_TAB, stop.getTeleport());
		assertTrue(stop.isOwned());

		worn.add(ItemID.ARDY_CAPE_ELITE);
		stop = FarmPlan.compute(settings(EnumSet.of(Patch.ARDOUGNE), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, worn, 99).getStops().get(0);
		assertEquals(Teleport.ARDOUGNE_CLOAK, stop.getTeleport());
		assertEquals(ItemID.ARDY_CAPE_ELITE, stop.getItemId());

		Map<Patch, Teleport> o = new EnumMap<>(Patch.class);
		o.put(Patch.ARDOUGNE, Teleport.FISHING_GUILD_TAB);
		stop = FarmPlan.compute(settings(EnumSet.of(Patch.ARDOUGNE), FlowerSeed.NONE, AllotmentSeed.NONE, o),
			bank, worn, 99).getStops().get(0);
		assertEquals(Teleport.FISHING_GUILD_TAB, stop.getTeleport());
		assertFalse(stop.isOwned());
		assertTrue(stop.isManual());
	}

	@Test
	public void guildLockedBelow65()
	{
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FARMING_GUILD), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), new HashMap<>(), new HashSet<>(), 50);
		assertTrue(plan.getStops().get(0).isLocked());
		assertEquals(null, supply(plan, ItemID.RANARR_SEED));
	}

	@Test
	public void layoutIsToolsSeedsTeleports()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.BUCKET_ULTRACOMPOST, 50);
		bank.put(ItemID.ECTOPHIAL, 1);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.MORYTANIA, Patch.FALADOR), FlowerSeed.LIMPWURT,
			AllotmentSeed.NONE, Collections.emptyMap()), bank, new HashSet<>(), 99);
		assertArrayEquals(new int[]{ItemID.RAKE, ItemID.DIBBER, ItemID.SPADE, ItemID.BUCKET_ULTRACOMPOST}, plan.getLayoutRows().get(0));
		assertArrayEquals(new int[]{ItemID.RANARR_SEED, ItemID.LIMPWURT_SEED}, plan.getLayoutRows().get(1));
		// Run order: Falador before Port Phasmatys.
		assertArrayEquals(new int[]{ItemID.LUMBRIDGE_RING_ELITE, ItemID.ECTOPHIAL}, plan.getLayoutRows().get(2));
	}

	@Test
	public void bottomlessBucketUsesAreCheckedAgainstPlots()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 1);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR, Patch.CATHERBY), FlowerSeed.LIMPWURT,
			AllotmentSeed.NONE, Collections.emptyMap()), bank, new HashSet<>(), 99, Collections.emptyMap(), 3, 3);
		FarmPlan.Supply b = supply(plan, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
		assertEquals("Bottomless (ultracompost)", b.getName());
		assertEquals(3, b.getHave());
		assertEquals(4, b.getNeed());
		assertTrue(plan.getSteps().stream().anyMatch(st -> st.getText().equals("Bucket has 3 uses, need 4")));
	}

	@Test
	public void emptyBottomlessBucketIsBroughtToFill()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.BOTTOMLESS_COMPOST_BUCKET, 1);
		bank.put(ItemID.BUCKET_ULTRACOMPOST, 2);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), 99);
		assertTrue(plan.getSteps().stream().anyMatch(st -> st.getText().startsWith("Fill your bottomless")));
		assertEquals(ItemID.BOTTOMLESS_COMPOST_BUCKET, plan.getLayoutRows().get(0)[0]);
	}

	@Test
	public void leprechaunToolsAreNotMissingOrLaidOut()
	{
		Map<Integer, Integer> lep = new HashMap<>();
		lep.put(ItemID.RAKE, 1);
		lep.put(ItemID.DIBBER, 1);
		lep.put(ItemID.SPADE, 1);
		lep.put(ItemID.BUCKET_ULTRACOMPOST, 100);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), new HashMap<>(), new HashSet<>(), 99, lep, 0, 0);
		assertFalse(plan.getSteps().stream().anyMatch(st -> st.getText().matches("No (rake|seed dibber|spade)")));
		assertEquals("Leprechaun", supply(plan, ItemID.RAKE).getWhere());
		assertEquals("Leprechaun", supply(plan, ItemID.BUCKET_ULTRACOMPOST).getWhere());
		// None of it needs to come from the bank.
		for (int[] row : plan.getLayoutRows())
		{
			for (int id : row)
			{
				assertTrue(id != ItemID.RAKE && id != ItemID.BUCKET_ULTRACOMPOST);
			}
		}
	}

	@Test
	public void bucketAtTheLeprechaunIsNotWithdrawnButStillCounts()
	{
		Map<Integer, Integer> lep = new HashMap<>();
		lep.put(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 1);
		FarmPlan.Settings s = settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE, Collections.emptyMap());
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.RANARR_SEED, 5);
		FarmPlan plan = FarmPlan.compute(s, bank, new HashSet<>(), 99, lep, 50, 3);
		FarmPlan.Supply b = supply(plan, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
		assertEquals("Leprechaun", b.getWhere());
		assertEquals(50, b.getHave());
		for (int[] row : plan.getLayoutRows())
		{
			for (int id : row)
			{
				assertTrue(id != ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
			}
		}
		assertFalse(plan.getWithdraw().containsKey(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));
		// The harvest still counts the ultracompost.
		FarmPlan none = FarmPlan.compute(s, bank, new HashSet<>(), 99, new HashMap<>(), 0, 0);
		assertTrue(plan.getExpectedHerbs() > none.getExpectedHerbs());
	}

	@Test
	public void leprechaunCompostCutsTheWithdrawCount()
	{
		Map<Integer, Integer> lep = new HashMap<>();
		lep.put(ItemID.BUCKET_ULTRACOMPOST, 3);
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.BUCKET_ULTRACOMPOST, 10);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR, Patch.CATHERBY, Patch.ARDOUGNE, Patch.MORYTANIA),
			FlowerSeed.NONE, AllotmentSeed.NONE, Collections.emptyMap()), bank, new HashSet<>(), 99, lep, 0, 0);
		assertEquals(Integer.valueOf(1), plan.getWithdraw().get(ItemID.BUCKET_ULTRACOMPOST));
	}

	private static FarmPlan.Stop stop(FarmPlan plan, Patch p)
	{
		return plan.getStops().stream().filter(st -> st.getPatch() == p).findFirst().orElse(null);
	}

	private static FarmPlan.Account.AccountBuilder lunar()
	{
		return FarmPlan.Account.builder().farming(99).magic(99).spellbook(Farm.LUNAR)
			.quests(EnumSet.allOf(net.runelite.api.Quest.class)).moryElite(true);
	}

	@Test
	public void runesAreBudgetedAcrossTheWholeRun()
	{
		// Enough for one lunar teleport (3 law, 3 astral, 10 water), not two.
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.LAWRUNE, 4);
		bank.put(ItemID.ASTRALRUNE, 4);
		bank.put(ItemID.WATERRUNE, 20);
		bank.put(ItemID.LUNAR_TABLET_CATHERBY_TELEPORT, 1);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.ARDOUGNE, Patch.CATHERBY), FlowerSeed.NONE,
			AllotmentSeed.NONE, Collections.emptyMap()), bank, new HashSet<>(), lunar().build());
		assertEquals(Teleport.FISHING_GUILD_SPELL, stop(plan, Patch.ARDOUGNE).getTeleport());
		// Runes are spent, so Catherby falls back to the tablet.
		assertEquals(Teleport.CATHERBY_TAB, stop(plan, Patch.CATHERBY).getTeleport());
		assertTrue(stop(plan, Patch.CATHERBY).isOwned());
		// Runes go in the bank layout.
		assertTrue(plan.getLayoutRows().stream().anyMatch(r -> r.length > 0 && r[0] == ItemID.LAWRUNE));
	}

	@Test
	public void staffComboRunesAndPouchCount()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.ASTRALRUNE, 6);
		bank.put(ItemID.MISTRUNE, 10);
		Map<Integer, Integer> pouch = new HashMap<>();
		pouch.put(ItemID.LAWRUNE, 6);
		// Mist runes cover the 10 water for one cast; a water staff covers all of it.
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.ARDOUGNE), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), lunar().pouch(pouch).build());
		assertEquals(Teleport.FISHING_GUILD_SPELL, stop(plan, Patch.ARDOUGNE).getTeleport());

		plan = FarmPlan.compute(settings(EnumSet.of(Patch.ARDOUGNE, Patch.CATHERBY), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), lunar().pouch(pouch).staffElements(2).build());
		assertEquals(Teleport.CATHERBY_SPELL, stop(plan, Patch.CATHERBY).getTeleport());
		assertTrue(stop(plan, Patch.CATHERBY).isOwned());
	}

	@Test
	public void wrongSpellbookFallsBackToItems()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.LAWRUNE, 100);
		bank.put(ItemID.AIRRUNE, 100);
		bank.put(ItemID.WATERRUNE, 100);
		bank.put(ItemID.POH_TABLET_FALADORTELEPORT, 1);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), lunar().build());
		assertEquals(Teleport.FALADOR_TAB, stop(plan, Patch.FALADOR).getTeleport());

		plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), lunar().spellbook(Farm.STANDARD).build());
		assertEquals(Teleport.FALADOR_SPELL, stop(plan, Patch.FALADOR).getTeleport());
	}

	@Test
	public void questAndDiaryLocksSkipPatches()
	{
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.WEISS, Patch.HARMONY, Patch.FALADOR), FlowerSeed.NONE,
			AllotmentSeed.NONE, Collections.emptyMap()), new HashMap<>(), new HashSet<>(),
			FarmPlan.Account.builder().farming(99).quests(EnumSet.noneOf(net.runelite.api.Quest.class)).moryElite(false).build());
		assertEquals("Needs Making Friends with My Arm", stop(plan, Patch.WEISS).getLockReason());
		assertEquals("Needs the Morytania elite diary", stop(plan, Patch.HARMONY).getLockReason());
		assertFalse(stop(plan, Patch.FALADOR).isLocked());
		// Only Falador counts.
		assertEquals(1, supply(plan, ItemID.RANARR_SEED).getNeed());
	}

	@Test
	public void unknownQuestsDontLock()
	{
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.WEISS), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), new HashMap<>(), new HashSet<>(), 99);
		assertFalse(stop(plan, Patch.WEISS).isLocked());
	}

	@Test
	public void fairyRingsNeedAStaffUnlessLumbridgeElite()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.DRAMEN_STAFF, 1);
		FarmPlan.Account.AccountBuilder a = FarmPlan.Account.builder().farming(99).fairyRings(true).lumbridgeElite(false);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FARMING_GUILD), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), a.build());
		assertEquals(Teleport.FAIRY_CIR, stop(plan, Patch.FARMING_GUILD).getTeleport());
		assertEquals(ItemID.DRAMEN_STAFF, stop(plan, Patch.FARMING_GUILD).getItemId());

		// Elite diary: no staff needed, nothing to bring.
		plan = FarmPlan.compute(settings(EnumSet.of(Patch.FARMING_GUILD), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), new HashMap<>(), new HashSet<>(), a.lumbridgeElite(true).build());
		assertTrue(stop(plan, Patch.FARMING_GUILD).isOwned());
		assertEquals(-1, stop(plan, Patch.FARMING_GUILD).getItemId());

		// Quest not started: no rings.
		plan = FarmPlan.compute(settings(EnumSet.of(Patch.FARMING_GUILD), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), a.fairyRings(false).build());
		assertFalse(stop(plan, Patch.FARMING_GUILD).isOwned());
	}

	@Test
	public void houseTeleportOnlyWhenHouseIsInHosidius()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.POH_TABLET_TELEPORTTOHOUSE, 5);
		FarmPlan.Account.AccountBuilder a = FarmPlan.Account.builder().farming(99).fairyRings(false);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.HOSIDIUS), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), a.houseLocation(Farm.HOUSE_IN_HOSIDIUS).build());
		assertEquals(Teleport.HOUSE_HOSIDIUS, stop(plan, Patch.HOSIDIUS).getTeleport());
		assertEquals(ItemID.POH_TABLET_TELEPORTTOHOUSE, stop(plan, Patch.HOSIDIUS).getItemId());

		plan = FarmPlan.compute(settings(EnumSet.of(Patch.HOSIDIUS), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), a.houseLocation(1).build());
		assertFalse(stop(plan, Patch.HOSIDIUS).isOwned());
	}

	@Test
	public void housePortalsNeedToBeSeenAndShareHouseTabs()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.POH_TABLET_TELEPORTTOHOUSE, 1);
		FarmPlan.Account a = FarmPlan.Account.builder().farming(99)
			.portals(new HashSet<>(java.util.Arrays.asList("FALADOR", "ARDOUGNE"))).build();
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.ARDOUGNE, Patch.FALADOR), FlowerSeed.NONE,
			AllotmentSeed.NONE, Collections.emptyMap()), bank, new HashSet<>(), a);
		assertEquals(Teleport.PORTAL_ARDOUGNE, stop(plan, Patch.ARDOUGNE).getTeleport());
		// Only one house tab, already used for Ardougne.
		assertFalse(stop(plan, Patch.FALADOR).isOwned());

		plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(), FarmPlan.Account.builder().farming(99).build());
		assertEquals("Visit your house once so portals can be seen",
			plan.getStops().get(0).getTeleport() == Teleport.PORTAL_FALADOR ? plan.getStops().get(0).getProblem()
				: "Visit your house once so portals can be seen");
	}

	@Test
	public void configuredSpiritTreesAndPortals()
	{
		FarmPlan.Account a = FarmPlan.Account.builder().farming(99).fairyRings(false)
			.spiritTrees(new HashSet<>(java.util.Collections.singletonList("GUILD")))
			.portals(new HashSet<>(java.util.Collections.singletonList("WEISS"))).build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.SKILLCAPE_CONSTRUCTION, 1);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FARMING_GUILD, Patch.WEISS), FlowerSeed.NONE,
			AllotmentSeed.NONE, Collections.emptyMap()), bank, new HashSet<>(), a);
		// Grown spirit tree, no house tree: walk to any spirit tree, nothing to bring.
		assertEquals(Teleport.SPIRIT_GUILD, stop(plan, Patch.FARMING_GUILD).getTeleport());
		assertEquals(-1, stop(plan, Patch.FARMING_GUILD).getItemId());
		// Weiss portal set in config, entered with the Construction cape.
		assertEquals(Teleport.PORTAL_WEISS, stop(plan, Patch.WEISS).getTeleport());
		assertEquals(ItemID.SKILLCAPE_CONSTRUCTION, stop(plan, Patch.WEISS).getItemId());
	}

	@Test
	public void seedVaultSeedsCountButStayOutOfTheBankLayout()
	{
		Map<Integer, Integer> vault = new HashMap<>();
		vault.put(ItemID.RANARR_SEED, 50);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), new HashMap<>(), new HashSet<>(),
			FarmPlan.Account.builder().farming(99).seedVault(vault).build());
		assertEquals(50, supply(plan, ItemID.RANARR_SEED).getHave());
		assertEquals("Seed vault", supply(plan, ItemID.RANARR_SEED).getWhere());
		for (int[] row : plan.getLayoutRows())
		{
			for (int id : row)
			{
				assertTrue(id != ItemID.RANARR_SEED);
			}
		}
	}

	@Test
	public void tomeCoversItsElement()
	{
		Set<Integer> worn = new HashSet<>();
		worn.add(ItemID.TOME_OF_WATER);
		assertEquals(2, AccountReader.staffElements(worn));
		worn.add(ItemID.SHADOWFLAME_QUADRANT);
		assertEquals(15, AccountReader.staffElements(worn));
	}

	@Test
	public void growingPatchesAreLeftOut()
	{
		Map<Patch, PatchStatus> status = new EnumMap<>(Patch.class);
		status.put(Patch.FALADOR, new PatchStatus(PatchStatus.State.GROWING, 1000 + 25 * 60));
		status.put(Patch.CATHERBY, new PatchStatus(PatchStatus.State.READY, 0));
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR, Patch.CATHERBY), FlowerSeed.NONE,
			AllotmentSeed.NONE, Collections.emptyMap()), new HashMap<>(), new HashSet<>(),
			FarmPlan.Account.builder().farming(99).patchStatus(status).now(1000).build());
		assertTrue(stop(plan, Patch.FALADOR).isGrowing());
		assertEquals(1, supply(plan, ItemID.RANARR_SEED).getNeed());
		assertEquals("1 patch still growing, next ready in 25m", plan.getSteps().get(0).getText());
		// Nothing for Falador (explorer's ring) in the bank layout.
		for (int[] row : plan.getLayoutRows())
		{
			for (int id : row)
			{
				assertTrue(id != ItemID.LUMBRIDGE_RING_ELITE);
			}
		}
	}

	@Test
	public void noRakeWithAutoweed()
	{
		Map<Patch, PatchStatus> status = new EnumMap<>(Patch.class);
		status.put(Patch.FALADOR, new PatchStatus(PatchStatus.State.EMPTY, 0));
		FarmPlan.Account.AccountBuilder a = FarmPlan.Account.builder().farming(99).patchStatus(status);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), new HashMap<>(), new HashSet<>(), a.build());
		assertTrue(supply(plan, ItemID.RAKE) != null);
		plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), new HashMap<>(), new HashSet<>(), a.autoweed(true).build());
		assertEquals(null, supply(plan, ItemID.RAKE));
	}

	@Test
	public void spellRunesComeFromPouchAndComboRunes()
	{
		// Trollheim: 2 law, 2 fire. Law in the pouch, fire covered by lava runes in the bank.
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.DIVINE_RUNE_POUCH, 1);
		bank.put(ItemID.LAVARUNE, 50);
		Map<Integer, Integer> pouch = new HashMap<>();
		pouch.put(ItemID.LAWRUNE, 100);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.TROLL_STRONGHOLD), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.emptyMap()), bank, new HashSet<>(),
			FarmPlan.Account.builder().farming(99).magic(99).spellbook(Farm.STANDARD).pouch(pouch).build());
		assertEquals(Teleport.TROLLHEIM_SPELL, stop(plan, Patch.TROLL_STRONGHOLD).getTeleport());
		assertEquals("Rune pouch", supply(plan, ItemID.LAWRUNE).getWhere());
		assertEquals(2, supply(plan, ItemID.FIRERUNE).getHave());
		// Pouch first, then the lava runes; no loose law or fire runes needed.
		int[] runeRow = plan.getLayoutRows().stream().filter(r -> r.length > 0 && r[0] == ItemID.DIVINE_RUNE_POUCH)
			.findFirst().orElse(null);
		assertArrayEquals(new int[]{ItemID.DIVINE_RUNE_POUCH, ItemID.LAVARUNE}, runeRow);
	}

	@Test
	public void chosenSpellListsRunesEvenWhenShort()
	{
		Map<Patch, Teleport> o = new EnumMap<>(Patch.class);
		o.put(Patch.CATHERBY, Teleport.CAMELOT_SPELL);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.CATHERBY), FlowerSeed.NONE, AllotmentSeed.NONE, o),
			new HashMap<>(), new HashSet<>(), FarmPlan.Account.builder().farming(99).magic(99).spellbook(Farm.STANDARD).build());
		assertEquals(5, supply(plan, ItemID.AIRRUNE).getNeed());
		assertTrue(plan.getSteps().stream().anyMatch(st -> st.getText().equals("Short 5 air runes")));
	}

	@Test
	public void withdrawAmountsForTheBankOverlay()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.LAWRUNE, 100);
		bank.put(ItemID.FIRERUNE, 100);
		bank.put(ItemID.POH_TABLET_FALADORTELEPORT, 1);
		bank.put(ItemID.BUCKET_ULTRACOMPOST, 50);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.TROLL_STRONGHOLD, Patch.FALADOR), FlowerSeed.NONE,
			AllotmentSeed.NONE, Collections.emptyMap()), bank, new HashSet<>(),
			FarmPlan.Account.builder().farming(99).magic(99).spellbook(Farm.STANDARD).build());
		Map<Integer, Integer> w = plan.getWithdraw();
		assertEquals(Integer.valueOf(2), w.get(ItemID.RANARR_SEED));
		assertEquals(Integer.valueOf(2), w.get(ItemID.BUCKET_ULTRACOMPOST));
		// Trollheim spell: 2 law, 2 fire. Falador: explorer's ring missing, so the tab.
		assertEquals(Integer.valueOf(2), w.get(ItemID.LAWRUNE));
		assertEquals(Integer.valueOf(2), w.get(ItemID.FIRERUNE));
		assertEquals(Integer.valueOf(1), w.get(ItemID.POH_TABLET_FALADORTELEPORT));
		assertEquals(null, w.get(ItemID.SPADE));
	}

	@Test
	public void dustRunesGetACount()
	{
		// Falador spell: 1 law, 3 air, 1 water. Civitas: 2 law, 1 earth, 1 fire.
		// Dust covers air + earth; 1 dust used for the pair, then 2 plain air.
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.LAWRUNE, 100);
		bank.put(ItemID.AIRRUNE, 100);
		bank.put(ItemID.WATERRUNE, 100);
		bank.put(ItemID.FIRERUNE, 100);
		bank.put(ItemID.DUSTRUNE, 100);
		FarmPlan plan = FarmPlan.compute(settings(EnumSet.of(Patch.FALADOR, Patch.CIVITAS), FlowerSeed.NONE,
			AllotmentSeed.NONE, Collections.emptyMap()), bank, new HashSet<>(),
			FarmPlan.Account.builder().farming(99).magic(99).spellbook(Farm.STANDARD)
				.quests(EnumSet.allOf(net.runelite.api.Quest.class)).fairyRings(false).build());
		Map<Integer, Integer> w = plan.getWithdraw();
		assertEquals(Integer.valueOf(1), w.get(ItemID.DUSTRUNE));
		assertEquals(Integer.valueOf(2), w.get(ItemID.AIRRUNE));
		assertEquals(null, w.get(ItemID.EARTHRUNE));
		assertEquals(Integer.valueOf(3), w.get(ItemID.LAWRUNE));
	}

	@Test
	public void compostKindsAreMixedWhenNoneCoversTheRun()
	{
		FarmPlan.Settings s = settings(EnumSet.of(Patch.FALADOR, Patch.CATHERBY, Patch.ARDOUGNE, Patch.MORYTANIA),
			FlowerSeed.NONE, AllotmentSeed.NONE, Collections.emptyMap());
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.RANARR_SEED, 4);
		bank.put(ItemID.BUCKET_SUPERCOMPOST, 3);
		bank.put(ItemID.BUCKET_COMPOST, 5);
		FarmPlan plan = FarmPlan.compute(s, bank, new HashSet<>(), 99);
		assertEquals(3, supply(plan, ItemID.BUCKET_SUPERCOMPOST).getNeed());
		assertEquals(1, supply(plan, ItemID.BUCKET_COMPOST).getNeed());
		assertTrue(plan.getSteps().stream().noneMatch(st -> st.getText().startsWith("Short")));
	}

	@Test
	public void teleportToHouseCountsAsAWayIn()
	{
		FarmPlan.Settings s = settings(EnumSet.of(Patch.CATHERBY), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.singletonMap(Patch.CATHERBY, Teleport.PORTAL_CATHERBY));
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.LAWRUNE, 1);
		bank.put(ItemID.AIRRUNE, 1);
		bank.put(ItemID.EARTHRUNE, 1);
		FarmPlan.Account acct = FarmPlan.Account.builder().farming(99).magic(50)
			.portals(Collections.singleton("CATHERBY")).build();
		FarmPlan plan = FarmPlan.compute(s, bank, new HashSet<>(), acct);
		assertEquals(null, plan.getStops().get(0).getProblem());
	}

	@Test
	public void savingRunesPicksATabletOverTheSpell()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.RANARR_SEED, 1);
		bank.put(ItemID.POH_TABLET_FALADORTELEPORT, 1);
		bank.put(ItemID.LAWRUNE, 10);
		bank.put(ItemID.AIRRUNE, 30);
		bank.put(ItemID.WATERRUNE, 10);
		FarmPlan.Settings s = settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE, Collections.emptyMap());
		FarmPlan.Account.AccountBuilder a = FarmPlan.Account.builder().farming(99).magic(99).spellbook(0);
		assertEquals(Teleport.FALADOR_SPELL, FarmPlan.compute(s, bank, new HashSet<>(), a.build()).getStops().get(0).getTeleport());
		assertEquals(Teleport.FALADOR_TAB,
			FarmPlan.compute(s, bank, new HashSet<>(), a.saveRunes(true).build()).getStops().get(0).getTeleport());
	}

	@Test
	public void diaryTeleportsSayWhereTheyComeFrom()
	{
		FarmPlan.Settings s = settings(EnumSet.of(Patch.ARDOUGNE), FlowerSeed.NONE, AllotmentSeed.NONE,
			Collections.singletonMap(Patch.ARDOUGNE, Teleport.ARDOUGNE_CLOAK));
		FarmPlan plan = FarmPlan.compute(s, new HashMap<>(), new HashSet<>(), 99);
		assertEquals("You don't have one (Ardougne medium diary)", plan.getStops().get(0).getProblem());
	}

	@Test
	public void stopsFollowTheChosenOrder()
	{
		java.util.List<Patch> order = new java.util.ArrayList<>(java.util.Arrays.asList(Patch.values()));
		order.remove(Patch.FALADOR);
		order.add(0, Patch.FALADOR);
		FarmPlan.Settings s = FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(FlowerSeed.NONE)
			.allotment(AllotmentSeed.NONE).patches(EnumSet.of(Patch.ARDOUGNE, Patch.FALADOR))
			.overrides(Collections.emptyMap()).order(order).build();
		FarmPlan plan = FarmPlan.compute(s, new HashMap<>(), new HashSet<>(), 99);
		assertEquals(Patch.FALADOR, plan.getStops().get(0).getPatch());
		assertEquals(Patch.ARDOUGNE, plan.getStops().get(1).getPatch());
	}

	@Test
	public void dailyTeleportsFallBackWhenUsedUp()
	{
		FarmPlan.Settings s = settings(EnumSet.of(Patch.FALADOR), FlowerSeed.NONE, AllotmentSeed.NONE, Collections.emptyMap());
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.LUMBRIDGE_RING_MEDIUM, 1);
		bank.put(ItemID.POH_TABLET_FALADORTELEPORT, 1);
		FarmPlan.Account.AccountBuilder a = FarmPlan.Account.builder().farming(99);
		assertEquals(Teleport.EXPLORERS_RING, FarmPlan.compute(s, bank, new HashSet<>(), a.ringUses(2).build()).getStops().get(0).getTeleport());
		assertEquals(Teleport.FALADOR_TAB, FarmPlan.compute(s, bank, new HashSet<>(), a.ringUses(3).build()).getStops().get(0).getTeleport());
	}

	@Test
	public void titheFarmTeleportNeedsUnlockAndNoCooldown()
	{
		FarmPlan.Settings s = settings(EnumSet.of(Patch.HOSIDIUS), FlowerSeed.NONE, AllotmentSeed.NONE, Collections.emptyMap());
		FarmPlan.Account.AccountBuilder a = FarmPlan.Account.builder().farming(99).fairyRings(false).titheUnlocked(true);
		assertEquals(Teleport.TITHE_FARM, FarmPlan.compute(s, new HashMap<>(), new HashSet<>(), a.build()).getStops().get(0).getTeleport());
		// On cooldown it isn't picked.
		assertTrue(FarmPlan.compute(s, new HashMap<>(), new HashSet<>(), a.groupingReady(false).build())
			.getStops().get(0).getTeleport() != Teleport.TITHE_FARM);
	}

	@Test
	public void teleportsKnowTheirSpellAndMenuOption()
	{
		assertEquals("Civitas illa Fortis Teleport", Farm.spellName(Teleport.FORTIS_SPELL));
		assertEquals(null, Farm.spellName(Teleport.ARDOUGNE_CLOAK));
		assertEquals("Fishing Guild", Farm.menuOption(Teleport.SKILLS_NECKLACE, Patch.ARDOUGNE));
		assertEquals("Catherby", Farm.menuOption(Teleport.PORTAL_CATHERBY, Patch.CATHERBY));
		assertEquals("Xeric's Glade", Farm.menuOption(Teleport.PORTAL_XERIC, Patch.HOSIDIUS));
	}
}
