package com.potionprep;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class PathOverlayTest
{
	@Test
	public void hullDropsInsidePoints()
	{
		java.awt.Polygon p = PathOverlay.hull(new ArrayList<>(Arrays.asList(
			new int[]{0, 0}, new int[]{10, 0}, new int[]{10, 10}, new int[]{0, 10}, new int[]{5, 5}, new int[]{3, 7})));
		assertEquals(4, p.npoints);
		assertEquals(true, p.contains(5, 5));
	}
}
