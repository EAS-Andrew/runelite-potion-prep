package com.potionprep;

import com.potionprep.Farm.Patch;
import java.util.EnumMap;
import java.util.Map;
import lombok.Value;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

/**
 * What each herb patch was doing when RuneLite's Time Tracking plugin last
 * saw it, and when a growing crop will be ready. Time Tracking stores each
 * patch's varbit value and when it was seen under the "timetracking" config
 * group as "&lt;region&gt;.&lt;varbit&gt;" = "value:epochSeconds".
 *
 * Value ranges and the growth-tick maths follow Time Tracking's
 * PatchImplementation.HERB and FarmingTracker.getTickTime.
 */
@Value
public class PatchStatus
{
	public enum State
	{
		READY, GROWING, DISEASED, DEAD, EMPTY, UNKNOWN
	}

	State state;
	/** When a growing crop is ready, epoch seconds; 0 otherwise. */
	long readyAt;

	/** Herbs grow in 20 minute ticks over 5 stages. */
	static final int TICK_MINUTES = 20;
	static final int STAGES = 5;

	/** Start of each herb's 4 growing values (guam .. torstol, huasca, goutweed). */
	private static final int[] GROWING_STARTS = {4, 11, 18, 25, 32, 39, 46, 53, 60, 68, 75, 82, 89, 96, 103, 192};

	/** The state a herb patch varbit value means, ignoring time. */
	static State decode(int value)
	{
		for (int start : GROWING_STARTS)
		{
			if (value >= start && value <= start + 3)
			{
				return State.GROWING;
			}
			// The 3 values after a crop's growing range are it being harvestable.
			int harvestEnd = start == 192 ? start + 5 : start + 6;
			if (value > start + 3 && value <= harvestEnd)
			{
				return State.READY;
			}
		}
		if (value >= 128 && value <= 169 || value >= 173 && value <= 175 || value >= 198 && value <= 200)
		{
			return State.DISEASED;
		}
		if (value >= 170 && value <= 172 || value >= 201 && value <= 203)
		{
			return State.DEAD;
		}
		if (value >= 0 && value <= 3 || value == 67 || value >= 176 && value <= 191
			|| value >= 204 && value <= 219 || value >= 221 && value <= 255)
		{
			return State.EMPTY;
		}
		return State.UNKNOWN;
	}

	/**
	 * @param seen      when the value was seen, epoch seconds
	 * @param offsetMin Time Tracking's learnt farming tick offset, or null
	 * @param precision how precisely that offset is known (minutes), or null
	 * @param now       epoch seconds
	 */
	static PatchStatus of(int value, long seen, Integer offsetMin, Integer precision, long now)
	{
		State state = decode(value);
		if (state != State.GROWING)
		{
			return new PatchStatus(state, 0);
		}
		int stage = -1;
		for (int start : GROWING_STARTS)
		{
			if (value >= start && value <= start + 3)
			{
				stage = value - start;
			}
		}
		long readyAt = tickTime(STAGES - 1 - stage, seen, offsetMin, precision);
		return readyAt <= now ? new PatchStatus(State.READY, 0) : new PatchStatus(State.GROWING, readyAt);
	}

	/** The growth tick {@code ticks} ticks after the one {@code at} falls in. */
	static long tickTime(int ticks, long at, Integer offsetMin, Integer precision)
	{
		long offset = 0;
		if (offsetMin != null && precision != null && (precision >= TICK_MINUTES || precision >= 40))
		{
			offset = (offsetMin % TICK_MINUTES) * 60L;
		}
		long shifted = at + offset;
		long tick = shifted - shifted % (TICK_MINUTES * 60L);
		return tick + (long) ticks * TICK_MINUTES * 60 - offset;
	}

	/** What to do at a herb patch right now, from its live varbit value. */
	public enum Task
	{
		PICK("Pick the herbs"),
		CLEAR("Clear the dead herbs (spade)"),
		CURE("Cure (plant cure) or clear it"),
		RAKE("Rake the weeds"),
		PLANT("Ready to compost and plant"),
		GROWING("Planted");

		@lombok.Getter
		private final String text;

		Task(String text)
		{
			this.text = text;
		}
	}

	static Task task(int value)
	{
		switch (decode(value))
		{
			case READY:
				return Task.PICK;
			case DEAD:
				return Task.CLEAR;
			case DISEASED:
				return Task.CURE;
			case GROWING:
				return Task.GROWING;
			case EMPTY:
				// 0-2 are weeds part-grown, 3 is raked clean; the other empty values are full weeds.
				return value == 3 ? Task.PLANT : Task.RAKE;
			default:
				return Task.PLANT;
		}
	}

	/** The herb patch's varbit, read while you're at it. */
	static int varbit(Patch p)
	{
		return KEYS.get(p)[1];
	}

	/** Region and varbit Time Tracking files each herb patch under. */
	private static final Map<Patch, int[]> KEYS = new EnumMap<>(Patch.class);

	static
	{
		KEYS.put(Patch.FARMING_GUILD, new int[]{4922, VarbitID.FARMING_TRANSMIT_E});
		KEYS.put(Patch.HOSIDIUS, new int[]{6967, VarbitID.FARMING_TRANSMIT_D});
		KEYS.put(Patch.CIVITAS, new int[]{6192, VarbitID.FARMING_TRANSMIT_D});
		KEYS.put(Patch.ARDOUGNE, new int[]{10548, VarbitID.FARMING_TRANSMIT_D});
		KEYS.put(Patch.CATHERBY, new int[]{11062, VarbitID.FARMING_TRANSMIT_D});
		KEYS.put(Patch.FALADOR, new int[]{12083, VarbitID.FARMING_TRANSMIT_D});
		KEYS.put(Patch.MORYTANIA, new int[]{14391, VarbitID.FARMING_TRANSMIT_D});
		KEYS.put(Patch.TROLL_STRONGHOLD, new int[]{11321, VarbitID.FARMING_TRANSMIT_A});
		KEYS.put(Patch.WEISS, new int[]{11325, VarbitID.FARMING_TRANSMIT_A});
		KEYS.put(Patch.HARMONY, new int[]{15148, VarbitID.FARMING_TRANSMIT_B});
	}

	/** Every herb patch Time Tracking knows about, for the current account. */
	static Map<Patch, PatchStatus> read(ConfigManager config, long now)
	{
		Integer offset = config.getRSProfileConfiguration("timetracking", "farmTickOffset", Integer.class);
		Integer precision = config.getRSProfileConfiguration("timetracking", "farmTickOffsetPrecision", Integer.class);
		Map<Patch, PatchStatus> out = new EnumMap<>(Patch.class);
		KEYS.forEach((patch, key) ->
		{
			String stored = config.getRSProfileConfiguration("timetracking", key[0] + "." + key[1]);
			if (stored == null)
			{
				return;
			}
			String[] parts = stored.split(":");
			try
			{
				if (parts.length == 2)
				{
					out.put(patch, of(Integer.parseInt(parts[0]), Long.parseLong(parts[1]), offset, precision, now));
				}
			}
			catch (NumberFormatException ignored)
			{
				// Not a value we understand: treat the patch as unknown.
			}
		});
		return out;
	}
}
