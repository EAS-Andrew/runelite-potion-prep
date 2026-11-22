package com.potionprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BottomlessBucketTest
{
	@Test
	public void readsCheckFillAndUse()
	{
		BottomlessBucket b = new BottomlessBucket(-1, 0);
		assertTrue(b.onMessage("Your bottomless compost bucket is currently holding 1,204 uses of ultracompost.", null));
		assertEquals(1204, b.getUses());
		assertEquals(3, b.getType());
		b.onMessage("You fill your bottomless compost bucket with 10 buckets of supercompost.", null);
		b.onMessage("Your bottomless compost bucket now contains a total of 20 uses.", null);
		assertEquals(20, b.getUses());
		assertEquals(2, b.getType());
		// A use is counted at the end of the tick, and only if the bucket was used.
		b.onMessage("You treat the herb patch with supercompost.", false);
		assertFalse(b.onTick());
		b.onMessage("You treat the herb patch with supercompost.", true);
		assertTrue(b.onTick());
		assertEquals(19, b.getUses());
		// If the game says what's left that tick, that wins.
		b.onMessage("You treat the herb patch with supercompost.", true);
		b.onMessage("Your bottomless compost bucket has 18 uses of supercompost remaining.", null);
		assertFalse(b.onTick());
		assertEquals(18, b.getUses());
	}

	@Test
	public void readsTheCheckBoxAndLeprechaun()
	{
		BottomlessBucket b = new BottomlessBucket(-1, 0);
		assertTrue(b.onDialog("Your bottomless compost bucket is currently<br>holding 52 uses of compost."));
		assertEquals(52, b.getUses());
		assertEquals(1, b.getType());
		// Stored with ultracompost (type 4) and 40 uses.
		assertTrue(b.onLeprechaun(4, 40));
		assertEquals(40, b.getUses());
		assertEquals(3, b.getType());
		// Taken out: the varbits go to 0, which says nothing.
		assertFalse(b.onLeprechaun(0, 0));
		assertEquals(40, b.getUses());
		assertTrue(b.onInventory(true, false));
		assertEquals(0, b.getUses());
	}
}
