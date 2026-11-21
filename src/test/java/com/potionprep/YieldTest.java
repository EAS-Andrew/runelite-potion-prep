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
}
