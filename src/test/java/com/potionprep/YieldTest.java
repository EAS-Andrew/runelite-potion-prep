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
}
