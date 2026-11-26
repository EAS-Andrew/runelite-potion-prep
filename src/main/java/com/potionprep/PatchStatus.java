package com.potionprep;

import com.potionprep.Farm.Patch;
import java.util.EnumMap;
import java.util.Map;
import lombok.Value;
import net.runelite.api.gameval.ItemID;
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
	/** The herb growing, ready or diseased there, or null (empty, dead, weeds, goutweed, unknown). */
	Farm.HerbSeed crop;

	PatchStatus(State state, long readyAt)
	{
		this(state, readyAt, null);
	}

	PatchStatus(State state, long readyAt, Farm.HerbSeed crop)
	{
		this.state = state;
		this.readyAt = readyAt;
		this.crop = crop;
	}

	/** Herbs in the order of their growing values (Time Tracking's PatchImplementation.HERB). */
	private static final Farm.HerbSeed[] BY_VALUE = {Farm.HerbSeed.GUAM, Farm.HerbSeed.MARRENTILL, Farm.HerbSeed.TARROMIN,
		Farm.HerbSeed.HARRALANDER, Farm.HerbSeed.RANARR, Farm.HerbSeed.TOADFLAX, Farm.HerbSeed.IRIT, Farm.HerbSeed.AVANTOE,
		Farm.HerbSeed.HUASCA, Farm.HerbSeed.KWUARM, Farm.HerbSeed.SNAPDRAGON, Farm.HerbSeed.CADANTINE, Farm.HerbSeed.LANTADYME,
		Farm.HerbSeed.DWARF_WEED, Farm.HerbSeed.TORSTOL};
	/** Diseased values come in threes from 128, guam to torstol (huasca's are 173 to 175). */
	private static final Farm.HerbSeed[] DISEASED = {Farm.HerbSeed.GUAM, Farm.HerbSeed.MARRENTILL, Farm.HerbSeed.TARROMIN,
		Farm.HerbSeed.HARRALANDER, Farm.HerbSeed.RANARR, Farm.HerbSeed.TOADFLAX, Farm.HerbSeed.IRIT, Farm.HerbSeed.AVANTOE,
		Farm.HerbSeed.KWUARM, Farm.HerbSeed.SNAPDRAGON, Farm.HerbSeed.CADANTINE, Farm.HerbSeed.LANTADYME,
		Farm.HerbSeed.DWARF_WEED, Farm.HerbSeed.TORSTOL};

	/** The herb a herb patch varbit value shows (growing, ready or diseased), or null. */
	static Farm.HerbSeed crop(int value)
	{
		for (int i = 0; i < BY_VALUE.length; i++)
		{
			// Each herb has 4 growing values then 3 harvestable ones.
			if (value >= GROWING_STARTS[i] && value <= GROWING_STARTS[i] + 6)
			{
				return BY_VALUE[i];
			}
		}
		if (value >= 128 && value <= 169)
		{
			return DISEASED[(value - 128) / 3];
		}
		return value >= 173 && value <= 175 ? Farm.HerbSeed.HUASCA : null;
	}

	/** Herbs grow in 20 minute ticks over 5 stages. */
	static final int TICK_MINUTES = 20;
	static final int STAGES = 5;

	/** Start of each herb's 4 growing values (guam .. avantoe, huasca, kwuarm .. torstol, goutweed). */
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
		Farm.HerbSeed crop = crop(value);
		if (state != State.GROWING)
		{
			return new PatchStatus(state, 0, state == State.READY || state == State.DISEASED ? crop : null);
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
		return readyAt <= now ? new PatchStatus(State.READY, 0, crop) : new PatchStatus(State.GROWING, readyAt, crop);
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

	/**
	 * What an allotment's or flower patch's varbit value means (Time Tracking's PatchImplementation):
	 * R rake, P empty and raked, G growing, K pick, C cure, X clear. Unused values read as weeds.
	 */
	private static final String ALLOTMENT_TASKS = ""
		+ "RRRPRRGGGGKKKGGGGKKKGGGGKKKGGGGKKKGGGGGGKKKGGGGGGKKKGGGGGGGGKKKG"
		+ "GGGGGGGGGGRRRGGGGRRRGGGGRRRGGGGRRRGGGGGGRRRGGGGGGRRRGGGGGGGGRRRR"
		+ "GGGGGGGCCCKKKRCCCRRRRCCCRRRRCCCRRRRCCCCCRRRRCCCCCRRRRCCCCCCCRRRR"
		+ "RXXXCCCXXXCCCRXXXXXXRXXXRRRRXXXRRRRXXXXXRRRRXXXXXRRRRXXXXXXXRRRR";
	private static final String FLOWER_TASKS = ""
		+ "RRRPRRRRGGGGKGGGGKGGGGKGGGGKGGGGKGGGGGGGGKRRRRRRRRRRRRRRRRRRRRRR"
		+ "RRRRRRRRGGGGRGGGGRGGGGRGGGGRGGGGRRRRRGGGGRRRRRRRRRRRRRRRRRRRRRRR"
		+ "RRRRRRRRRCCCRRCCCRRCCCRRCCCRRCCCRRRRRRCCCRRRRRRRRRRRRRRRRRRRRRRR"
		+ "RRRRRRRRRXXXXRXXXXRXXXXRXXXXRXXXXRRRRRXXXXRRRRRRRRRRRRRRRRRRRRRR";

	/** What a ready patch gives when picked, from its live varbit value; -1 if it isn't ready or it's unknown. */
	static int picks(HerbRun.PlotType type, int value)
	{
		if (type == HerbRun.PlotType.HERB)
		{
			Farm.HerbSeed herb = task(value) == Task.PICK ? crop(value) : null;
			return herb != null ? herb.getGrimyId() : -1;
		}
		boolean allotment = type == HerbRun.PlotType.ALLOTMENT;
		if (sideTask(allotment, value) != Task.PICK)
		{
			return -1;
		}
		int[][] ready = allotment
			? new int[][]{{10, 12, ItemID.POTATO}, {17, 19, ItemID.ONION}, {24, 26, ItemID.CABBAGE}, {31, 33, ItemID.TOMATO},
				{40, 42, ItemID.SWEETCORN}, {49, 51, ItemID.STRAWBERRY}, {60, 62, ItemID.WATERMELON}, {138, 140, ItemID.SNAPE_GRASS}}
			: new int[][]{{12, 12, ItemID.MARIGOLD}, {17, 17, ItemID.ROSEMARY}, {22, 22, ItemID.NASTURTIUM}, {27, 27, ItemID.WOADLEAF},
				{32, 32, ItemID.LIMPWURT_ROOT}, {41, 41, ItemID.WHITELILLY}};
		for (int[] r : ready)
		{
			if (value >= r[0] && value <= r[1])
			{
				return r[2];
			}
		}
		return -1;
	}

	/** What to do at an allotment or flower patch right now, from its live varbit value. */
	static Task sideTask(boolean allotment, int value)
	{
		if (value < 0 || value > 255)
		{
			return Task.RAKE;
		}
		switch ((allotment ? ALLOTMENT_TASKS : FLOWER_TASKS).charAt(value))
		{
			case 'P':
				return Task.PLANT;
			case 'G':
				return Task.GROWING;
			case 'K':
				return Task.PICK;
			case 'C':
				return Task.CURE;
			case 'X':
				return Task.CLEAR;
			default:
				return Task.RAKE;
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

	/** Varbits of each location's {flower, allotment 1, allotment 2} (Time Tracking's FarmingWorld). */
	private static final Map<Patch, int[]> SIDE = new EnumMap<>(Patch.class);

	static
	{
		int[] usual = {VarbitID.FARMING_TRANSMIT_C, VarbitID.FARMING_TRANSMIT_A, VarbitID.FARMING_TRANSMIT_B};
		for (Patch p : new Patch[]{Patch.HOSIDIUS, Patch.CIVITAS, Patch.ARDOUGNE, Patch.CATHERBY, Patch.FALADOR, Patch.MORYTANIA})
		{
			SIDE.put(p, usual);
		}
		SIDE.put(Patch.FARMING_GUILD, new int[]{VarbitID.FARMING_TRANSMIT_H, VarbitID.FARMING_TRANSMIT_C, VarbitID.FARMING_TRANSMIT_D});
	}

	/** A location's {flower, allotment 1, allotment 2} varbits, or empty where there are none. */
	static int[] sideVarbits(Patch p)
	{
		int[] v = SIDE.get(p);
		return v != null ? v : new int[0];
	}

	/** Flower and allotment values Time Tracking last saw, per location: {flower, allotment 1, allotment 2}, -1 unknown. */
	static Map<Patch, int[]> readSide(ConfigManager config)
	{
		Map<Patch, int[]> out = new EnumMap<>(Patch.class);
		SIDE.forEach((patch, varbits) ->
		{
			int region = KEYS.get(patch)[0];
			int[] values = new int[varbits.length];
			for (int i = 0; i < varbits.length; i++)
			{
				values[i] = -1;
				String stored = config.getRSProfileConfiguration("timetracking", region + "." + varbits[i]);
				if (stored != null)
				{
					try
					{
						values[i] = Integer.parseInt(stored.split(":")[0]);
					}
					catch (NumberFormatException ignored)
					{
						// Unknown.
					}
				}
			}
			out.put(patch, values);
		});
		return out;
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
