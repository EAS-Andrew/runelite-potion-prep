package com.potionprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class WalkPathTest
{
	@Test
	public void openGroundTakesTheRouteWithFewestTurns()
	{
		int[][] flags = new int[40][40];
		List<int[]> path = WalkPath.find(flags, 2, 2, 20, 10, 1);
		// Beside the target: 17 steps (Chebyshev), as before.
		assertEquals(18, path.size());
		int turns = 0;
		for (int i = 1; i < path.size() - 1; i++)
		{
			int dx1 = path.get(i)[0] - path.get(i - 1)[0];
			int dy1 = path.get(i)[1] - path.get(i - 1)[1];
			int dx2 = path.get(i + 1)[0] - path.get(i)[0];
			int dy2 = path.get(i + 1)[1] - path.get(i)[1];
			turns += dx1 != dx2 || dy1 != dy2 ? 1 : 0;
		}
		// One diagonal run and one straight run, not a zigzag.
		assertTrue("turns: " + turns, turns <= 1);
	}
}
