package com.potionprep;

import static org.junit.Assert.assertEquals;
import com.potionprep.PatchStatus.State;
import org.junit.Test;

public class PatchStatusTest
{
	@Test
	public void decodesHerbValues()
	{
		assertEquals(State.EMPTY, PatchStatus.decode(0));
		assertEquals(State.EMPTY, PatchStatus.decode(3));
		assertEquals(State.GROWING, PatchStatus.decode(32)); // ranarr seed
		assertEquals(State.GROWING, PatchStatus.decode(35));
		assertEquals(State.READY, PatchStatus.decode(36)); // ranarr, 3 lives
		assertEquals(State.READY, PatchStatus.decode(38));
		assertEquals(State.GROWING, PatchStatus.decode(39)); // toadflax
		assertEquals(State.READY, PatchStatus.decode(66)); // huasca
		assertEquals(State.EMPTY, PatchStatus.decode(67));
		assertEquals(State.READY, PatchStatus.decode(109)); // torstol
		assertEquals(State.DISEASED, PatchStatus.decode(140));
		assertEquals(State.DEAD, PatchStatus.decode(171));
		assertEquals(State.READY, PatchStatus.decode(197)); // goutweed
		assertEquals(State.DEAD, PatchStatus.decode(202));
		assertEquals(State.UNKNOWN, PatchStatus.decode(120));
	}

	@Test
	public void predictsWhenGrowingHerbsAreReady()
	{
		long seen = 1_800_000_000L - 1_800_000_000L % 1200; // on a 20 min tick
		// Just planted (stage 0): 4 more ticks.
		PatchStatus s = PatchStatus.of(32, seen, null, null, seen + 60);
		assertEquals(State.GROWING, s.getState());
		assertEquals(seen + 4 * 1200, s.getReadyAt());
		// Long since: predicted ready.
		assertEquals(State.READY, PatchStatus.of(32, seen, null, null, seen + 5 * 1200).getState());
	}
}
