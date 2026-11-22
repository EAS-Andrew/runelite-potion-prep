package com.potionprep;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class PotionPlanTest
{
	private static Map<Integer, Integer> counts(int... pairs)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put(pairs[i], pairs[i + 1]);
		}
		return m;
	}

	private static String steps(PotionPlan plan)
	{
		return plan.getSteps().stream().map(PotionPlan.Step::getText).collect(Collectors.joining(" | "));
	}

	@Test
	public void xpIncludesPrepSteps()
	{
		Map<Integer, Integer> c = new java.util.HashMap<>();
		c.put(ItemID.UNIDENTIFIED_RANARR, 10);
		c.put(ItemID.VIAL_WATER, 10);
		c.put(ItemID.SNAPE_GRASS, 10);
		// 10 x clean (7.5) + 10 x mix (87.5); the unf step gives none.
		assertEquals(950.0, PotionPlan.xp(Potion.PRAYER, c, 99), 0.001);
	}

	@Test
	public void oneRowPerTaskWithInputsButNotOutput()
	{
		List<int[]> rows = PotionPlan.layoutRows(Potion.SUPERANTIPOISON);
		assertEquals(4, rows.size());
		assertArrayEquals(new int[]{ItemID.UNIDENTIFIED_IRIT}, rows.get(0));
		assertArrayEquals(new int[]{ItemID.IRIT_LEAF, ItemID.VIAL_WATER}, rows.get(1));
		assertArrayEquals(new int[]{ItemID.UNICORN_HORN, ItemID.PESTLE_AND_MORTAR}, rows.get(2));
		assertArrayEquals(new int[]{ItemID.IRITVIAL, ItemID.UNICORN_HORN_DUST}, rows.get(3));
	}

	@Test
	public void plainSecondaryHasNoTaskRowOfItsOwn()
	{
		List<int[]> rows = PotionPlan.layoutRows(Potion.PRAYER);
		assertEquals(3, rows.size());
		assertArrayEquals(new int[]{ItemID.RANARRVIAL, ItemID.SNAPE_GRASS}, rows.get(2));
	}

	@Test
	public void staminaTasksChainThroughSuperEnergy()
	{
		List<int[]> rows = PotionPlan.layoutRows(Potion.STAMINA);
		assertEquals(4, rows.size());
		assertArrayEquals(new int[]{ItemID.AVANTOEVIAL, ItemID.MORTMYREMUSHROOM}, rows.get(2));
		assertArrayEquals(new int[]{ItemID._1DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._4DOSE2ENERGY,
			ItemID.AMYLASE}, rows.get(3));
		assertFalse(PotionPlan.layoutItems(Potion.STAMINA).contains(ItemID._4DOSESTAMINA));
	}

	@Test
	public void rowsFitTheBankAndSkipTheFinishedPotion()
	{
		for (Potion p : Potion.values())
		{
			for (int[] row : PotionPlan.layoutRows(p))
			{
				assertTrue(p.name(), row.length >= 1 && row.length <= PotionPlan.BANK_COLUMNS);
			}
			for (int id : p.getProduct().rowIds())
			{
				assertFalse(p.name(), PotionPlan.layoutItems(p).contains(id));
			}
		}
	}

	@Test
	public void readyWhenUnfAndSecondaryInHand()
	{
		PotionPlan plan = PotionPlan.compute(Potion.PRAYER, counts(ItemID.RANARRVIAL, 14, ItemID.SNAPE_GRASS, 20), 99);
		assertEquals(14, plan.getMakeable());
		assertEquals(14, plan.getMakeableNow());
		String s = steps(plan);
		assertTrue(s, s.contains("Mix 14 prayer potions"));
		assertTrue(s, s.contains("Short 6 ranarr weed"));
	}

	@Test
	public void cleansAndMixesGrimyHerbs()
	{
		PotionPlan plan = PotionPlan.compute(Potion.PRAYER, counts(ItemID.UNIDENTIFIED_RANARR, 10, ItemID.RANARR_WEED, 4,
			ItemID.VIAL_WATER, 100, ItemID.SNAPE_GRASS, 100), 99);
		assertEquals(14, plan.getMakeable());
		assertEquals(0, plan.getMakeableNow());
		String s = steps(plan);
		assertTrue(s, s.contains("Clean 10 grimy ranarr weed"));
		assertTrue(s, s.contains("Make 14 ranarr (unf)"));
		assertTrue(s, s.contains("Short 86 ranarr weed"));
	}

	@Test
	public void vialsLimit()
	{
		PotionPlan plan = PotionPlan.compute(Potion.PRAYER, counts(ItemID.RANARR_WEED, 20, ItemID.VIAL_WATER, 5, ItemID.SNAPE_GRASS, 20), 99);
		assertEquals(5, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("Short 15 vial of water"));
	}

	@Test
	public void nonCrushableSecondaryNeverGetsACrushStep()
	{
		// The bug in the screenshot: super energy suggested crushing mort myre fungus.
		PotionPlan plan = PotionPlan.compute(Potion.SUPER_ENERGY, counts(ItemID.AVANTOEVIAL, 314, ItemID.MORTMYREMUSHROOM, 957,
			ItemID.AVANTOE, 110, ItemID.VIAL_WATER, 4240, ItemID.UNIDENTIFIED_AVANTOE, 2148), 99);
		assertEquals(957, plan.getMakeable());
		assertEquals(314, plan.getMakeableNow());
		String s = steps(plan);
		assertFalse(s, s.contains("Crush"));
		assertTrue(s, s.contains("Clean 533 grimy avantoe"));
		assertTrue(s, s.contains("Make 643 avantoe (unf)"));
		assertTrue(s, s.contains("Short 1,615 mort myre fungus"));
	}

	@Test
	public void hugeCountsDoNotOverflow()
	{
		PotionPlan plan = PotionPlan.compute(Potion.SUPER_ENERGY, counts(ItemID.AVANTOEVIAL, Integer.MAX_VALUE, ItemID.MORTMYREMUSHROOM, 10), 99);
		assertEquals(10, plan.getMakeable());
	}

	@Test
	public void crushesRawSecondaryOnlyWithPestle()
	{
		Map<Integer, Integer> c = counts(ItemID.LANTADYMEVIAL, 10, ItemID.BLUE_DRAGON_SCALE, 10);
		PotionPlan noPestle = PotionPlan.compute(Potion.ANTIFIRE, c, 99);
		assertEquals(0, noPestle.getMakeable());
		assertTrue(steps(noPestle), steps(noPestle).contains("No pestle and mortar"));

		c.put(ItemID.PESTLE_AND_MORTAR, 1);
		PotionPlan withPestle = PotionPlan.compute(Potion.ANTIFIRE, c, 99);
		assertEquals(10, withPestle.getMakeable());
		assertTrue(steps(withPestle), steps(withPestle).contains("Crush 10 blue dragon scale"));
	}

	@Test
	public void staminaChainsThroughSuperEnergy()
	{
		// 2 super energy(4) = 8 doses on hand, plus 2 unf + 2 fungus = 6 more doses.
		PotionPlan plan = PotionPlan.compute(Potion.STAMINA, counts(ItemID._4DOSE2ENERGY, 2, ItemID.AVANTOEVIAL, 2,
			ItemID.MORTMYREMUSHROOM, 2, ItemID.AMYLASE, 100), 99);
		// Whole potions: 2 stamina(4) from the held ones, 2 stamina(3) from the new ones.
		assertEquals(4, plan.getMakeable());
		assertEquals(2, plan.getMakeableNow());
		assertEquals("Stamina potion", plan.getLabel());
		assertEquals("2 x (4), 2 x (3)", plan.getDoseMix());
		assertEquals("Stamina potion", Potion.STAMINA.potionName());
		assertEquals("Prayer potion(3)", Potion.PRAYER.potionName());
		String s = steps(plan);
		assertTrue(s, s.contains("Mix 2 super energies"));
		assertTrue(s, s.contains("Add amylase to 14 doses"));
	}

	@Test
	public void superCombatNeedsAllThreeSupers()
	{
		PotionPlan plan = PotionPlan.compute(Potion.SUPER_COMBAT, counts(ItemID._4DOSE2ATTACK, 5, ItemID._4DOSE2STRENGTH, 5,
			ItemID._4DOSE2DEFENSE, 3, ItemID.TORSTOL, 10), 99);
		assertEquals(3, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("Super defence: short 10 cadantine, 10 vial of water, 10 white berries"));
	}

	@Test
	public void levelTooLow()
	{
		PotionPlan plan = PotionPlan.compute(Potion.ZAMORAK_BREW, counts(ItemID.UNIDENTIFIED_TORSTOL, 5, ItemID.VIAL_WATER, 5, ItemID.JANGERBERRIES, 5), 60);
		assertEquals(0, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("Needs 78 Herblore"));
	}

	@Test
	public void potionStorageCountsAddToBank()
	{
		// PotionStorageReader keys storage by the 1-dose id with total doses,
		// and unf potions by their own id. Bank has 1 super energy(4) = 4 doses.
		Map<Integer, Integer> c = counts(ItemID._4DOSE2ENERGY, 1, ItemID.AMYLASE, 100);
		c.merge(ItemID._1DOSE2ENERGY, 37, Integer::sum);   // 37 doses in storage
		PotionPlan plan = PotionPlan.compute(Potion.STAMINA, c, 99);
		// Storage withdraws at any dose, so its 37 doses read as 9 (4)s and a (1).
		assertEquals(11, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("Add amylase to 41 doses"));

		Map<Integer, Integer> unf = counts(ItemID.SNAPE_GRASS, 50);
		unf.merge(ItemID.RANARRVIAL, 12, Integer::sum);       // 12 unf in storage
		assertEquals(12, PotionPlan.compute(Potion.PRAYER, unf, 99).getMakeable());
	}

	@Test
	public void noPotionStorageUnlockFallsBackToBank()
	{
		// Without the unlock the reader returns nothing; bank counts alone apply.
		PotionPlan plan = PotionPlan.compute(Potion.PRAYER, counts(ItemID.RANARRVIAL, 5, ItemID.SNAPE_GRASS, 50), 99);
		assertEquals(5, plan.getMakeable());
	}

	@Test
	public void pieUnlocksPotionsWithinFourLevels()
	{
		// Saradomin brew is level 81.
		assertTrue(PotionPlan.needsPie(Potion.SARADOMIN_BREW, 77, 77));
		assertEquals(81, PotionPlan.effectiveLevel(Potion.SARADOMIN_BREW, 77, 77));
		assertFalse(PotionPlan.needsPie(Potion.SARADOMIN_BREW, 76, 76));   // pie isn't enough
		assertFalse(PotionPlan.needsPie(Potion.SARADOMIN_BREW, 77, 81));   // already boosted
		assertFalse(PotionPlan.needsPie(Potion.SARADOMIN_BREW, 81, 81));   // no boost needed

		Map<Integer, Integer> c = counts(ItemID.TOADFLAXVIAL, 5, ItemID.CRUSHED_BIRD_NEST, 5, ItemID.HALF_BOTANICAL_PIE, 1);
		PotionPlan plan = PotionPlan.compute(Potion.SARADOMIN_BREW, c, PotionPlan.effectiveLevel(Potion.SARADOMIN_BREW, 77, 77), true);
		assertEquals(5, plan.getMakeable());
		assertEquals("Eat a botanical pie (+4)", plan.getSteps().get(0).getText());
		assertEquals(1, plan.getRows().get(0).getHave());

		PotionPlan noPie = PotionPlan.compute(Potion.SARADOMIN_BREW, counts(ItemID.TOADFLAXVIAL, 5, ItemID.CRUSHED_BIRD_NEST, 5), 81, true);
		assertEquals("Need a botanical pie (+4)", noPie.getSteps().get(0).getText());
	}

	// --- Torstol potion (unf) ---

	@Test
	public void superCombatTakesTorstolUnfToo()
	{
		PotionPlan plan = PotionPlan.compute(Potion.SUPER_COMBAT, counts(ItemID._4DOSE2ATTACK, 5, ItemID._4DOSE2STRENGTH, 5,
			ItemID._4DOSE2DEFENSE, 5, ItemID.TORSTOLVIAL, 2, ItemID.TORSTOL, 1), 99);
		assertEquals(3, plan.getMakeable());
		assertEquals(3, plan.getMakeableNow());
		assertTrue(steps(plan), steps(plan).contains("Short 2 torstol"));
		assertTrue(plan.getRows().stream().anyMatch(r -> r.getItemId() == ItemID.TORSTOLVIAL && r.getHave() == 2));
		// The unf is only a stand-in: never made from torstol for this.
		assertFalse(steps(plan), steps(plan).contains("(unf)"));
		assertTrue(PotionPlan.layoutItems(Potion.SUPER_COMBAT).contains(ItemID.TORSTOLVIAL));
		// No "make torstol (unf)" task row.
		assertFalse(PotionPlan.layoutRows(Potion.SUPER_COMBAT).stream().anyMatch(r -> Arrays.equals(r, new int[]{ItemID.TORSTOL, ItemID.VIAL_WATER})));
	}

	@Test
	public void antiVenomPlusTakesTorstolUnf()
	{
		PotionPlan plan = PotionPlan.compute(Potion.ANTI_VENOM_PLUS, counts(ItemID.ANTIVENOM4, 4, ItemID.TORSTOLVIAL, 3), 99);
		assertEquals(3, plan.getMakeable());
		assertEquals("Anti-venom+(4)", plan.getLabel());
	}

	// --- Dose counting ---

	@Test
	public void doseAtATimeKeepsTheBaseDose()
	{
		PotionPlan plan = PotionPlan.compute(Potion.STAMINA, counts(ItemID._3DOSE2ENERGY, 100, ItemID.AMYLASE, 300), 99);
		assertEquals(100, plan.getMakeable());
		assertEquals("Stamina potion(3)", plan.getLabel());
		assertEquals(100, PotionPlan.makeable(Potion.STAMINA, counts(ItemID._3DOSE2ENERGY, 100, ItemID.AMYLASE, 300), 99));
		assertEquals(7650.0, PotionPlan.xp(Potion.STAMINA, counts(ItemID._3DOSE2ENERGY, 100, ItemID.AMYLASE, 300), 99), 0.001);
	}

	@Test
	public void underFourDosesStillCounts()
	{
		PotionPlan plan = PotionPlan.compute(Potion.EXTENDED_ANTIFIRE, counts(ItemID._2DOSE1ANTIDRAGON, 1, ItemID.LAVA_SHARD, 2), 99);
		assertEquals(1, plan.getMakeable());
		assertEquals("Extended antifire(2)", plan.getLabel());
		assertTrue(steps(plan), steps(plan).contains("Add shards to 2 doses"));
	}

	@Test
	public void wholePotionsOnlyWhenShortOfSecondary()
	{
		// 5 scales cover one potion(4); the (3) needs 3 more.
		PotionPlan plan = PotionPlan.compute(Potion.EXTENDED_ANTIFIRE, counts(ItemID._4DOSE1ANTIDRAGON, 1, ItemID._3DOSE1ANTIDRAGON, 1,
			ItemID.LAVA_SHARD, 5), 99);
		assertEquals(1, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("Add shards to 4 doses"));
	}

	@Test
	public void antidotePlusPlusComesOutAsFourDoses()
	{
		PotionPlan plan = PotionPlan.compute(Potion.ANTI_VENOM, counts(ItemID.UNFINISHED_ANTIDOTE__, 2, ItemID.MAGIC_ROOTS, 2,
			ItemID.SNAKEBOSS_SCALE, 1000), 99);
		assertEquals(2, plan.getMakeable());
		assertEquals("Anti-venom(4)", plan.getLabel());
	}

	// --- Decanting to (4) ---

	@Test
	public void superCombatDecantsOtherDoses()
	{
		PotionPlan plan = PotionPlan.compute(Potion.SUPER_COMBAT, counts(ItemID._3DOSE2ATTACK, 4, ItemID._4DOSE2STRENGTH, 3,
			ItemID._4DOSE2DEFENSE, 3, ItemID.TORSTOL, 5), 99);
		assertEquals(3, plan.getMakeable());
		PotionPlan.Step decant = plan.getSteps().stream().filter(st -> st.getText().equals("Decant to (4)")).findFirst().orElse(null);
		assertTrue(steps(plan), decant != null);
		assertTrue(decant.getHint(), decant.getHint().startsWith("Super attack."));

		PotionPlan fours = PotionPlan.compute(Potion.SUPER_COMBAT, counts(ItemID._4DOSE2ATTACK, 3, ItemID._4DOSE2STRENGTH, 3,
			ItemID._4DOSE2DEFENSE, 3, ItemID.TORSTOL, 5), 99);
		assertFalse(steps(fours), steps(fours).contains("Decant"));
	}

	@Test
	public void decantComesAfterMakingTheThrees()
	{
		PotionPlan plan = PotionPlan.compute(Potion.SUPER_ANTIFIRE, counts(ItemID.LANTADYMEVIAL, 4, ItemID.DRAGON_SCALE_DUST, 4,
			ItemID.CRUSHED_DRAGON_BONES, 10), 99);
		// 4 antifire(3) = 12 doses = 3 super antifire(4).
		assertEquals(3, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).startsWith("Mix 4 antifire potions | Decant to (4) | Mix 3 super antifires | "));
	}

	// --- Herblore cape ---

	@Test
	public void capePutsGrimyStraightIntoUnf()
	{
		Map<Integer, Integer> c = counts(ItemID.UNIDENTIFIED_RANARR, 10, ItemID.VIAL_WATER, 10, ItemID.SNAPE_GRASS, 10,
			ItemID.SKILLCAPE_HERBLORE, 1);
		PotionPlan plan = PotionPlan.compute(Potion.PRAYER, c, 99);
		assertEquals(10, plan.getMakeable());
		assertEquals("Make 10 ranarr (unf) | Mix 10 prayer potions", steps(plan));
		// The unf step still gives the cleaning XP.
		assertEquals(950.0, PotionPlan.xp(Potion.PRAYER, c, 99), 0.001);

		List<int[]> rows = PotionPlan.layoutRows(Potion.PRAYER, c);
		assertEquals(2, rows.size());
		assertArrayEquals(new int[]{ItemID.UNIDENTIFIED_RANARR, ItemID.RANARR_WEED, ItemID.VIAL_WATER}, rows.get(0));
	}

	@Test
	public void capeKeepsCleaningForHerbsUsedAsTheyAre()
	{
		Map<Integer, Integer> c = counts(ItemID._4DOSE2ATTACK, 1, ItemID._4DOSE2STRENGTH, 1, ItemID._4DOSE2DEFENSE, 1,
			ItemID.UNIDENTIFIED_TORSTOL, 1, ItemID.SKILLCAPE_HERBLORE_TRIMMED, 1);
		PotionPlan plan = PotionPlan.compute(Potion.SUPER_COMBAT, c, 99);
		assertTrue(steps(plan), steps(plan).contains("Clean 1 grimy torstol"));
		assertTrue(PotionPlan.layoutRows(Potion.SUPER_COMBAT, c).stream().anyMatch(r -> Arrays.equals(r, new int[]{ItemID.UNIDENTIFIED_TORSTOL})));
	}

	@Test
	public void capeHoldsAPestle()
	{
		Map<Integer, Integer> c = counts(ItemID.LANTADYMEVIAL, 10, ItemID.BLUE_DRAGON_SCALE, 10, ItemID.SKILLCAPE_HERBLORE, 1);
		PotionPlan plan = PotionPlan.compute(Potion.ANTIFIRE, c, 99);
		assertEquals(10, plan.getMakeable());
		assertEquals("Take a pestle from your cape | Crush 10 blue dragon scale | Mix 10 antifire potions", steps(plan));
	}

	// --- Layout ---

	@Test
	public void superCombatTaskStaysOneRow()
	{
		List<int[]> rows = PotionPlan.layoutRows(Potion.SUPER_COMBAT);
		assertArrayEquals(new int[]{ItemID._4DOSE2ATTACK, ItemID._4DOSE2STRENGTH, ItemID._4DOSE2DEFENSE, ItemID.TORSTOL, ItemID.TORSTOLVIAL},
			rows.get(rows.size() - 1));
		// Super attack: grimy, clean, unf, mix, then decant its (1)-(3).
		assertArrayEquals(new int[]{ItemID.IRITVIAL, ItemID.EYE_OF_NEWT}, rows.get(2));
		assertArrayEquals(new int[]{ItemID._1DOSE2ATTACK, ItemID._2DOSE2ATTACK, ItemID._3DOSE2ATTACK}, rows.get(3));
	}

	@Test
	public void noTaskWrapsPastTheBank()
	{
		// Rows that wrap come out as a full row and then a short one; no task needs that.
		for (Potion p : Potion.values())
		{
			for (int[] row : PotionPlan.layoutRows(p))
			{
				assertTrue(p.name(), row.length < PotionPlan.BANK_COLUMNS);
			}
		}
	}

	@Test
	public void boughtDustSkipsTheCrushRow()
	{
		List<int[]> rows = PotionPlan.layoutRows(Potion.SUPERANTIPOISON, counts(ItemID.UNICORN_HORN_DUST, 5));
		assertEquals(3, rows.size());
		assertFalse(PotionPlan.layoutItems(Potion.SUPERANTIPOISON, counts(ItemID.UNICORN_HORN_DUST, 5)).contains(ItemID.UNICORN_HORN));
		// Holding horns too keeps the row.
		assertEquals(4, PotionPlan.layoutRows(Potion.SUPERANTIPOISON, counts(ItemID.UNICORN_HORN_DUST, 5, ItemID.UNICORN_HORN, 1)).size());
	}

	// --- New potions ---

	@Test
	public void everyPotionHasAnIconAndAName()
	{
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (Potion p : Potion.values())
		{
			assertTrue(p.name(), p.getProduct().getId() > 0);
			assertTrue(p.name(), ids.add(p.getProduct().getId()));
			assertTrue(p.name(), p.getLevel() >= 1 && p.getLevel() <= 99);
		}
	}

	@Test
	public void prayerRegenerationFromGrimyHuasca()
	{
		PotionPlan plan = PotionPlan.compute(Potion.PRAYER_REGENERATION, counts(ItemID.UNIDENTIFIED_HUASCA, 5, ItemID.VIAL_WATER, 5,
			ItemID.ALDARIUM, 5), 99);
		assertEquals(5, plan.getMakeable());
		assertEquals("Clean 5 grimy huasca | Make 5 huasca (unf) | Mix 5 prayer regeneration potions", steps(plan));
		assertEquals(5 * 11.8 + 5 * 132, PotionPlan.xp(Potion.PRAYER_REGENERATION, counts(ItemID.UNIDENTIFIED_HUASCA, 5,
			ItemID.VIAL_WATER, 5, ItemID.ALDARIUM, 5), 99), 0.001);
	}

	@Test
	public void divineFromCrystalShards()
	{
		// One shard is 10 dust: enough for 2 super attack(4) and 2 doses over.
		PotionPlan plan = PotionPlan.compute(Potion.DIVINE_SUPER_ATTACK, counts(ItemID._4DOSE2ATTACK, 3, ItemID.PRIF_CRYSTAL_SHARD, 1,
			ItemID.PESTLE_AND_MORTAR, 1), 99);
		assertEquals(2, plan.getMakeable());
		assertEquals("Divine super attack(4)", plan.getLabel());
		assertTrue(steps(plan), steps(plan).contains("Crush 1 crystal shard"));
	}

	@Test
	public void sanfewChainsThroughBothMixtures()
	{
		PotionPlan plan = PotionPlan.compute(Potion.SANFEW_SERUM, counts(ItemID._4DOSE2RESTORE, 2, ItemID.UNICORN_HORN_DUST, 2,
			ItemID.SNAKE_WEED, 2, ItemID.NAIL_BEAST_NAIL, 2), 99);
		assertEquals(2, plan.getMakeable());
		assertEquals("Add horn dust to 2 restores | Add snake weed to 2 mixtures | Mix 2 sanfew serums", steps(plan));
		assertEquals(2 * (57 + 63 + 72), PotionPlan.xp(Potion.SANFEW_SERUM, counts(ItemID._4DOSE2RESTORE, 2, ItemID.UNICORN_HORN_DUST, 2,
			ItemID.SNAKE_WEED, 2, ItemID.NAIL_BEAST_NAIL, 2), 99), 0.001);
	}

	@Test
	public void bastionWithCapeTakesGrimyCadantine()
	{
		PotionPlan plan = PotionPlan.compute(Potion.BASTION, counts(ItemID.UNIDENTIFIED_CADANTINE, 3, ItemID.VIAL_BLOOD, 3,
			ItemID.WINE_OF_ZAMORAK, 3, ItemID.SKILLCAPE_HERBLORE, 1), 99);
		assertEquals(3, plan.getMakeable());
		assertEquals("Make 3 cadantine blood (unf) | Mix 3 bastion potions", steps(plan));
	}

	@Test
	public void sailingPotionsFromCoral()
	{
		PotionPlan plan = PotionPlan.compute(Potion.HAEMOSTATIC_DRESSING, counts(ItemID.CORAL_ELKHORN, 2, ItemID.VIAL_WATER, 2,
			ItemID.SQUID_PASTE, 2, ItemID.COTTON_YARN, 2), 99);
		assertEquals(2, plan.getMakeable());
		assertEquals("Haemostatic dressing(3)", plan.getLabel());
		assertEquals(2, PotionPlan.compute(Potion.ARMADYL_BREW, counts(ItemID.UMBRALVIAL, 2, ItemID.RAINBOW_CRAB_PASTE, 5), 99).getMakeable());
	}

	@Test
	public void forgottenBrewTakesTwentyEssencePerDose()
	{
		PotionPlan plan = PotionPlan.compute(Potion.FORGOTTEN_BREW, counts(ItemID._3DOSEANCIENTBREW, 2, ItemID.ANCIENT_ESSENCE, 120), 99);
		assertEquals(2, plan.getMakeable());
		assertEquals("Forgotten brew(3)", plan.getLabel());
	}

	@Test
	public void weaponPoisonPlusFromCoconutMilk()
	{
		Map<Integer, Integer> c = counts(ItemID.VIAL_COCONUT_MILK, 3, ItemID.CACTUS_SPINE, 3, ItemID.RED_SPIDERS_EGGS, 3);
		PotionPlan plan = PotionPlan.compute(Potion.WEAPON_POISON_PLUS, c, 99);
		assertEquals(3, plan.getMakeable());
		assertEquals("Make 3 weapon poison+ (unf) | Mix 3 weapon poison(+)", steps(plan));
		assertEquals(570.0, PotionPlan.xp(Potion.WEAPON_POISON_PLUS, c, 99), 0.001);
	}

	@Test
	public void weaponPoisonPlusPlusTakesEitherNightshade()
	{
		Map<Integer, Integer> c = counts(ItemID.VIAL_COCONUT_MILK, 5, ItemID.NIGHTSHADE, 2, ItemID.STACKABLE_NIGHTSHADE, 2,
			ItemID.POISONIVY_BERRIES, 5);
		PotionPlan plan = PotionPlan.compute(Potion.WEAPON_POISON_PLUS_PLUS, c, 99);
		assertEquals(4, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("Short 1 cave nightshade"));
		assertTrue(PotionPlan.layoutItems(Potion.WEAPON_POISON_PLUS_PLUS).contains(ItemID.STACKABLE_NIGHTSHADE));
		assertEquals(0, PotionPlan.compute(Potion.WEAPON_POISON_PLUS_PLUS, c, 81).getMakeable());
	}

	// --- Extended super antifire: two routes ---

	@Test
	public void usualRouteWhenThatIsWhatYouHold()
	{
		PotionPlan plan = PotionPlan.compute(Potion.EXTENDED_SUPER_ANTIFIRE, counts(ItemID._4DOSE3ANTIDRAGON, 2, ItemID.LAVA_SHARD, 8), 99);
		assertEquals(2, plan.getMakeable());
		assertEquals("Extended super antifire(4)", plan.getLabel());
		assertEquals("Add shards to 8 doses", steps(plan));
	}

	@Test
	public void bonesRouteWhenItMakesMore()
	{
		// One super antifire(3) and shards make 1; three extended antifire(4) and bones make 3.
		Map<Integer, Integer> c = counts(ItemID._3DOSE3ANTIDRAGON, 1, ItemID.LAVA_SHARD, 3,
			ItemID._4DOSE2ANTIDRAGON, 3, ItemID.CRUSHED_DRAGON_BONES, 3);
		PotionPlan plan = PotionPlan.compute(Potion.EXTENDED_SUPER_ANTIFIRE, c, 99);
		assertEquals(3, plan.getMakeable());
		assertEquals("Extended super antifire(4)", plan.getLabel());
		assertTrue(steps(plan), steps(plan).startsWith("Add bones to 3 ext. antifires"));
		assertEquals(3, PotionPlan.makeable(Potion.EXTENDED_SUPER_ANTIFIRE, c, 99));
		assertEquals(540.0, PotionPlan.xp(Potion.EXTENDED_SUPER_ANTIFIRE, c, 99), 0.001);
		assertTrue(plan.getRows().stream().anyMatch(r -> r.getItemId() == ItemID._4DOSE2ANTIDRAGON));
		assertFalse(plan.getRows().stream().anyMatch(r -> r.getItemId() == ItemID._4DOSE3ANTIDRAGON));
	}

	@Test
	public void layoutFollowsTheChosenRoute()
	{
		Map<Integer, Integer> c = counts(ItemID._4DOSE2ANTIDRAGON, 3, ItemID.CRUSHED_DRAGON_BONES, 3);
		List<int[]> rows = PotionPlan.layoutRows(Potion.EXTENDED_SUPER_ANTIFIRE, c);
		assertArrayEquals(new int[]{ItemID._4DOSE2ANTIDRAGON, ItemID.CRUSHED_DRAGON_BONES}, rows.get(rows.size() - 1));
		// Extended antifire's own task, then decanting its (1)-(3), come before it.
		assertArrayEquals(new int[]{ItemID._1DOSE2ANTIDRAGON, ItemID._2DOSE2ANTIDRAGON, ItemID._3DOSE2ANTIDRAGON}, rows.get(rows.size() - 2));
		assertFalse(PotionPlan.layoutItems(Potion.EXTENDED_SUPER_ANTIFIRE, c).contains(ItemID._4DOSE3ANTIDRAGON));
		for (int[] row : rows)
		{
			assertTrue(row.length < PotionPlan.BANK_COLUMNS);
		}

		// Nothing known: the usual route, shards on super antifire.
		List<int[]> usual = PotionPlan.layoutRows(Potion.EXTENDED_SUPER_ANTIFIRE);
		assertArrayEquals(new int[]{ItemID._1DOSE3ANTIDRAGON, ItemID._2DOSE3ANTIDRAGON, ItemID._3DOSE3ANTIDRAGON, ItemID._4DOSE3ANTIDRAGON,
			ItemID.LAVA_SHARD}, usual.get(usual.size() - 1));
		// Potion storage collapsing still sees both routes' potions.
		assertTrue(PotionPlan.ingredients(Potion.EXTENDED_SUPER_ANTIFIRE).contains(Catalog.EXTENDED_ANTIFIRE));
	}

	@Test
	public void shortfallNamesTheRouteYouStarted()
	{
		// Extended antifire but no bones: neither route makes any, so report what the bones route lacks.
		PotionPlan plan = PotionPlan.compute(Potion.EXTENDED_SUPER_ANTIFIRE, counts(ItemID._4DOSE2ANTIDRAGON, 5), 99);
		assertEquals(0, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("Short 5 crushed superior dragon bones"));

		// Holding nothing: the usual route.
		PotionPlan none = PotionPlan.compute(Potion.EXTENDED_SUPER_ANTIFIRE, counts(), 99);
		assertTrue(none.getRows().stream().anyMatch(r -> r.getItemId() == ItemID.LAVA_SHARD));
	}

	@Test
	public void superiorBonesCrushWithAPestle()
	{
		PotionPlan plan = PotionPlan.compute(Potion.EXTENDED_SUPER_ANTIFIRE, counts(ItemID._4DOSE2ANTIDRAGON, 2,
			ItemID.DRAGON_BONES_SUPERIOR, 2, ItemID.PESTLE_AND_MORTAR, 1), 99);
		assertEquals(2, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).startsWith("Crush 2 superior dragon bones | Add bones to 2 ext. antifires"));
	}
}
