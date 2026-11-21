package com.potionprep;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.api.gameval.ItemID;

/**
 * Herblore gear, laid out in the bank like the worn-equipment tab:
 * <pre>
 *        head
 *  cape  neck
 *        body
 *        legs
 *  hands
 * </pre>
 * Only gear the player owns is shown; the variant in the bank wins (e.g.
 * either state of the prescription goggles).
 */
final class Gear
{
	static final int WIDTH = 3;

	private enum Slot
	{
		HEAD(0, 1, ItemID.MM_ALCHEMIST_HAT, ItemID.MM_ALCHEMIST_HAT_ALT),
		CAPE(1, 0, ItemID.SKILLCAPE_HERBLORE_TRIMMED, ItemID.SKILLCAPE_HERBLORE),
		NECK(1, 1, ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED, ItemID.AMULET_OF_CHEMISTRY),
		BODY(2, 1, ItemID.MM_ALCHEMIST_BODY, ItemID.MM_ALCHEMIST_BODY_ALT),
		LEGS(3, 1, ItemID.MM_ALCHEMIST_LEGS, ItemID.MM_ALCHEMIST_LEGS_ALT),
		HANDS(4, 0, ItemID.MM_ALCHEMIST_GLOVES);

		final int row;
		final int col;
		final int[] ids; // best first

		Slot(int row, int col, int... ids)
		{
			this.row = row;
			this.col = col;
			this.ids = ids;
		}
	}

	private Gear()
	{
	}

	/** Every gear id, for clearing old state. */
	static List<Integer> allIds()
	{
		List<Integer> out = new ArrayList<>();
		for (Slot s : Slot.values())
		{
			for (int id : s.ids)
			{
				out.add(id);
			}
		}
		return out;
	}

	/**
	 * A slot is shown when the player owns gear for it anywhere: in the bank,
	 * inventory, worn, or as a bank placeholder. If the item isn't in the bank,
	 * Bank Tags draws it faded, like a placeholder.
	 *
	 * @param inBank item id is in the bank (quantity > 0)
	 * @param owned  item id is worn, in the inventory, or a bank placeholder
	 * @return rows of {@link Gear#WIDTH} ids (-1 = empty), empty rows dropped.
	 */
	static List<int[]> rows(Predicate<Integer> inBank, Predicate<Integer> owned)
	{
		int[][] grid = new int[5][WIDTH];
		for (int[] r : grid)
		{
			java.util.Arrays.fill(r, -1);
		}
		for (Slot s : Slot.values())
		{
			grid[s.row][s.col] = pick(s.ids, inBank);
			if (grid[s.row][s.col] == -1)
			{
				grid[s.row][s.col] = pick(s.ids, owned);
			}
		}
		List<int[]> out = new ArrayList<>();
		for (int[] r : grid)
		{
			if (r[0] != -1 || r[1] != -1 || r[2] != -1)
			{
				out.add(r);
			}
		}
		return out;
	}

	private static int pick(int[] ids, Predicate<Integer> test)
	{
		for (int id : ids)
		{
			if (test.test(id))
			{
				return id;
			}
		}
		return -1;
	}

	/**
	 * First bank row the gear block can start on without overlapping the
	 * potion rows' occupied cells (left {@link Gear#WIDTH} columns).
	 */
	static int startRow(List<boolean[]> occupied, List<int[]> gear)
	{
		for (int g = 0; ; g++)
		{
			boolean clash = false;
			for (int r = 0; r < gear.size() && !clash; r++)
			{
				int row = g + r;
				if (row >= occupied.size())
				{
					break;
				}
				for (int c = 0; c < WIDTH; c++)
				{
					clash |= gear.get(r)[c] != -1 && occupied.get(row)[c];
				}
			}
			if (!clash)
			{
				return g;
			}
		}
	}
}
