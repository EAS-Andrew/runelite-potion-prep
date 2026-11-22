package com.potionprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class YieldTest
{
	@Test
	public void ranarrAt99WithUltraAndSecateurs()
	{
		// floor(80 * 1.1 + 1) / 256 = 89 / 256.
		assertEquals(89 / 256.0, Yield.chanceToSave(39, 99, 0.10), 1e-9);
		double free = Yield.expected(39, 99, 0.10, 3, true);
		assertEquals(6 / (1 - 89 / 256.0), free, 1e-9);
		// Disease risk with ultracompost: 3/128 per cycle, 3 cycles.
		assertEquals(free * Math.pow(1 - 3 / 128.0, 3), Yield.expected(39, 99, 0.10, 3, false), 1e-9);
	}

	@Test
	public void moreCompostMoreHerbs()
	{
		assertTrue(Yield.expected(71, 85, 0, 0, false) < Yield.expected(71, 85, 0, 3, false));
	}

	@Test
	public void snapeGrassAndLimpwurt()
	{
		// Level 99, no secateurs: save chance 196/256 (76.6%), 3 lives with no compost.
		assertEquals(3 / (1 - 196 / 256.0), Yield.snapeGrass(99, false, 0), 1e-9);
		assertTrue(Yield.snapeGrass(99, true, 3) > Yield.snapeGrass(99, false, 3));
		// Limpwurt: 3 + avg of 0..9 at 91+, 3 + avg of 0..1 at 11.
		assertEquals(3 + 4.5, Yield.limpwurt(99, false), 1e-9);
		assertEquals(3.5, Yield.limpwurt(11, false), 1e-9);
		assertEquals(3, Yield.limpwurt(5, false), 1e-9);
	}

	@Test
	public void diaryIsAFlatBonusAfterItems()
	{
		// 99 Farming, secateurs and cape: floor(80 * 1.15) = 92, Kandarin elite +25, then +1 = 118.
		assertEquals(118 / 256.0, Yield.chanceToSave(39, 99, 0.15, 25, false), 1e-9);
		// Kourend hard: 92 + 10 + 1.
		assertEquals(103 / 256.0, Yield.chanceToSave(39, 99, 0.15, 10, false), 1e-9);
		// Attas last: floor(102 * 1.05) = 107, + 1.
		assertEquals(108 / 256.0, Yield.chanceToSave(39, 99, 0.15, 10, true), 1e-9);
	}

	@Test
	public void belowNinetyNineFloorsEachHalf()
	{
		// Ranarr at level 50: floor(39 * 49/98) = 19, floor(80 * 49/98) = 40, + 1.
		assertEquals(60 / 256.0, Yield.chanceToSave(39, 50, 0), 1e-9);
	}
}
