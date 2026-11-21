package com.potionprep;

import com.potionprep.Farm.Patch;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Where each herb patch is, for the run's hint arrow and "you're there" check. */
final class PatchPlaces
{
	private static final Map<Patch, WorldPoint> POINTS = new EnumMap<>(Patch.class);

	static
	{
		POINTS.put(Patch.FARMING_GUILD, new WorldPoint(1238, 3726, 0));
		POINTS.put(Patch.HOSIDIUS, new WorldPoint(1738, 3550, 0));
		POINTS.put(Patch.CIVITAS, new WorldPoint(1581, 3094, 0));
		POINTS.put(Patch.ARDOUGNE, new WorldPoint(2670, 3374, 0));
		POINTS.put(Patch.CATHERBY, new WorldPoint(2813, 3463, 0));
		POINTS.put(Patch.FALADOR, new WorldPoint(3058, 3311, 0));
		POINTS.put(Patch.MORYTANIA, new WorldPoint(3605, 3529, 0));
		POINTS.put(Patch.TROLL_STRONGHOLD, new WorldPoint(2826, 3694, 0));
		POINTS.put(Patch.WEISS, new WorldPoint(2847, 3931, 0));
		POINTS.put(Patch.HARMONY, new WorldPoint(3789, 2837, 0));
	}

	/** Where a location's flower and allotments are, when they aren't by the herb patch; else null. */
	static WorldPoint otherPlots(Patch p)
	{
		return p == Patch.FARMING_GUILD ? new WorldPoint(1266, 3730, 0) : null;
	}

	/** Close enough to count as being at the plots (the guild's two spots are 28 tiles apart). */
	private static final int NEAR = 25;

	private PatchPlaces()
	{
	}

	static WorldPoint point(Patch p)
	{
		return POINTS.get(p);
	}

	static boolean near(WorldPoint at, WorldPoint player)
	{
		return at != null && player != null && at.getPlane() == player.getPlane() && at.distanceTo2D(player) <= NEAR;
	}
}
