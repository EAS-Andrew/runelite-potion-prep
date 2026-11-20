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
	public void columnFollowsTheProcessAndOmitsTheFinishedPotion()
	{
		assertEquals(Arrays.asList(
			ItemID.UNIDENTIFIED_RANARR, ItemID.RANARR_WEED, ItemID.VIAL_WATER, ItemID.RANARRVIAL, ItemID.SNAPE_GRASS),
			PotionPlan.columnItems(Potion.PRAYER));

		assertEquals(Arrays.asList(
			ItemID.UNIDENTIFIED_LANTADYME, ItemID.LANTADYME, ItemID.VIAL_WATER, ItemID.LANTADYMEVIAL,
			ItemID.BLUE_DRAGON_SCALE, ItemID.DRAGON_SCALE_DUST, ItemID.PESTLE_AND_MORTAR),
			PotionPlan.columnItems(Potion.ANTIFIRE));
	}

	@Test
	public void staminaPutsSuperEnergyDosesOnOneRow()
	{
		List<int[]> rows = PotionPlan.columnRows(Potion.STAMINA);
		assertEquals(7, rows.size());
		assertArrayEquals(new int[]{ItemID.MORTMYREMUSHROOM}, rows.get(4));
		assertArrayEquals(new int[]{ItemID._1DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._4DOSE2ENERGY}, rows.get(5));
		assertArrayEquals(new int[]{ItemID.AMYLASE}, rows.get(6));
		assertFalse(PotionPlan.columnItems(Potion.STAMINA).contains(ItemID._4DOSESTAMINA));
	}

	@Test
	public void noDuplicatesAndNoFinishedPotionInAnyColumn()
	{
		for (Potion p : Potion.values())
		{
			List<Integer> ids = PotionPlan.columnItems(p);
			assertEquals(p.name(), ids.size(), ids.stream().distinct().count());
			assertFalse(p.name(), ids.contains(p.getProduct().getId()));
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
		assertTrue(s, s.contains("Make 14 ranarr weed potions (unf)"));
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
		assertTrue(s, s.contains("Make 643 avantoe potions (unf)"));
		assertTrue(s, s.contains("Short 1615 mort myre fungus"));
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
		assertTrue(steps(noPestle), steps(noPestle).contains("but no pestle and mortar"));

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
		assertEquals("doses", plan.getUnit());
		assertEquals(14, plan.getMakeable());
		assertEquals(8, plan.getMakeableNow());
		String s = steps(plan);
		assertTrue(s, s.contains("Mix 2 super energies"));
		assertTrue(s, s.contains("Add amylase to 14 doses of super energy"));
	}

	@Test
	public void superCombatNeedsAllThreeSupers()
	{
		PotionPlan plan = PotionPlan.compute(Potion.SUPER_COMBAT, counts(ItemID._4DOSE2ATTACK, 5, ItemID._4DOSE2STRENGTH, 5,
			ItemID._4DOSE2DEFENSE, 3, ItemID.TORSTOL, 10), 99);
		assertEquals(3, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("For super defence: short 10 cadantine, 10 vial of water, 10 white berries"));
	}

	@Test
	public void levelTooLow()
	{
		PotionPlan plan = PotionPlan.compute(Potion.ZAMORAK_BREW, counts(ItemID.UNIDENTIFIED_TORSTOL, 5, ItemID.VIAL_WATER, 5, ItemID.JANGERBERRIES, 5), 60);
		assertEquals(0, plan.getMakeable());
		assertTrue(steps(plan), steps(plan).contains("Zamorak brew needs 78 Herblore"));
	}
}
