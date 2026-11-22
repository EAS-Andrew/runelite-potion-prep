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

	/** Equipment slot index (EquipmentInventorySlot) to {row, col} in the equipment-tab shape. */
	private static final int[][] SLOT_POS = {
		{0, 1}, // 0 head
		{1, 0}, // 1 cape
		{1, 1}, // 2 neck
		{2, 0}, // 3 weapon
		{2, 1}, // 4 body
		{2, 2}, // 5 shield
		null, // 6 (arms, unused)
		{3, 1}, // 7 legs
		null, // 8 (hair, unused)
		{4, 0}, // 9 gloves
		{4, 1}, // 10 boots
		null, // 11 (jaw, unused)
		{4, 2}, // 12 ring
		{1, 2}, // 13 ammo
	};

	/**
	 * Saved worn gear laid out like the equipment tab.
	 *
	 * @param slots item id per equipment slot index, -1 for empty
	 * @return rows of {@link Gear#WIDTH} ids (-1 = empty), empty rows dropped
	 */
	static List<int[]> fromSlots(int[] slots)
	{
		int[][] grid = new int[5][WIDTH];
		for (int[] r : grid)
		{
			java.util.Arrays.fill(r, -1);
		}
		for (int i = 0; i < slots.length && i < SLOT_POS.length; i++)
		{
			if (SLOT_POS[i] != null && slots[i] > 0)
			{
				grid[SLOT_POS[i][0]][SLOT_POS[i][1]] = slots[i];
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

	/** Saved gear plus run items that can be worn, and the layout rows without those items. */
	static final class Worn
	{
		/** Item id per equipment slot index, -1 for empty. */
		final int[] slots;
		final List<int[]> rows;

		Worn(int[] slots, List<int[]> rows)
		{
			this.slots = slots;
			this.rows = rows;
		}
	}

	/**
	 * Wear what can be worn: each item in the rows (in order, so tools, then
	 * teleports in run order) moves into its equipment slot if that slot is
	 * still free. The player's saved gear keeps its slots, and an item whose
	 * slot is taken stays in its row.
	 *
	 * @param saved  saved gear per slot, or null for none
	 * @param slotOf equipment slot index for an item, or -1 if it can't be worn
	 */
	static Worn wear(int[] saved, List<int[]> rows, java.util.function.IntUnaryOperator slotOf)
	{
		int[] slots = new int[SLOT_POS.length];
		java.util.Arrays.fill(slots, -1);
		if (saved != null)
		{
			for (int i = 0; i < saved.length && i < slots.length; i++)
			{
				slots[i] = saved[i] > 0 ? saved[i] : -1;
			}
		}
		java.util.Set<Integer> already = new java.util.HashSet<>();
		for (int id : slots)
		{
			already.add(id);
		}
		List<int[]> out = new ArrayList<>();
		for (int[] row : rows)
		{
			List<Integer> keep = new ArrayList<>();
			for (int id : row)
			{
				if (already.contains(id))
				{
					// Already in the saved gear: shown there only.
					continue;
				}
				int slot = id > 0 ? slotOf.applyAsInt(id) : -1;
				if (slot >= 0 && slot < slots.length && SLOT_POS[slot] != null && slots[slot] == -1)
				{
					slots[slot] = id;
					already.add(id);
				}
				else
				{
					keep.add(id);
				}
			}
			if (!keep.isEmpty())
			{
				out.add(keep.stream().mapToInt(Integer::intValue).toArray());
			}
		}
		return new Worn(slots, out);
	}
}
