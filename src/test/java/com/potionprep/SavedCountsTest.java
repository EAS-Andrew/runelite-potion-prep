package com.potionprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class SavedCountsTest
{
	@Test
	public void roundTrips()
	{
		Map<Integer, Integer> counts = new HashMap<>();
		counts.put(5295, 27);
		counts.put(207, 2072);
		assertEquals(counts, PotionPrepPlugin.decode(PotionPrepPlugin.encode(counts)));
		assertEquals(new HashMap<>(), PotionPrepPlugin.decode(PotionPrepPlugin.encode(new HashMap<>())));
	}

	@Test
	public void nothingOrJunkIsNull()
	{
		assertNull(PotionPrepPlugin.decode(null));
		assertNull(PotionPrepPlugin.decode(""));
		assertNull(PotionPrepPlugin.decode("12:x"));
	}
}
