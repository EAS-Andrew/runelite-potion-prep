package com.potionprep;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class GearTest
{
	@Test
	public void onlyBankedUnwornGearInEquipmentShape()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.MM_ALCHEMIST_HAT, 1);
		bank.put(ItemID.AMULET_OF_CHEMISTRY, 5);
		bank.put(ItemID.MM_ALCHEMIST_GLOVES, 1);
		bank.put(ItemID.SKILLCAPE_HERBLORE, 1);
		// Wearing a trimmed cape: the banked untrimmed one still wins;
		// the labcoat is only a placeholder, so it shows faded.
		List<int[]> rows = Gear.rows(id -> bank.getOrDefault(id, 0) > 0,
			id -> id == ItemID.SKILLCAPE_HERBLORE_TRIMMED || id == ItemID.MM_ALCHEMIST_BODY_ALT);
		assertEquals(4, rows.size());
		assertArrayEquals(new int[]{-1, ItemID.MM_ALCHEMIST_HAT, -1}, rows.get(0));
		assertArrayEquals(new int[]{ItemID.SKILLCAPE_HERBLORE, ItemID.AMULET_OF_CHEMISTRY, -1}, rows.get(1));
		assertArrayEquals(new int[]{-1, ItemID.MM_ALCHEMIST_BODY_ALT, -1}, rows.get(2));
		assertArrayEquals(new int[]{ItemID.MM_ALCHEMIST_GLOVES, -1, -1}, rows.get(3));
	}

	@Test
	public void nothingOwned()
	{
		assertEquals(0, Gear.rows(id -> false, id -> false).size());
	}

	@Test
	public void movesBelowWideRows()
	{
		List<boolean[]> occupied = new ArrayList<>();
		occupied.add(new boolean[]{false, false, false, true, true, true, true, true});
		occupied.add(new boolean[]{true, true, true, true, true, true, true, true});
		List<int[]> gear = new ArrayList<>();
		gear.add(new int[]{-1, 1, -1});
		gear.add(new int[]{-1, 2, -1});
		assertEquals(2, Gear.startRow(occupied, gear));
		occupied.remove(1);
		assertEquals(0, Gear.startRow(occupied, gear));
	}

	@Test
	public void savedGearUsesEquipmentTabShape()
	{
		int[] slots = new int[14];
		java.util.Arrays.fill(slots, -1);
		slots[0] = ItemID.GRACEFUL_HOOD;
		slots[1] = ItemID.GRACEFUL_CAPE;
		slots[4] = ItemID.GRACEFUL_TOP;
		slots[7] = ItemID.GRACEFUL_LEGS;
		slots[9] = ItemID.GRACEFUL_GLOVES;
		slots[10] = ItemID.GRACEFUL_BOOTS;
		List<int[]> rows = Gear.fromSlots(slots);
		assertEquals(5, rows.size());
		assertArrayEquals(new int[]{-1, ItemID.GRACEFUL_HOOD, -1}, rows.get(0));
		assertArrayEquals(new int[]{ItemID.GRACEFUL_CAPE, -1, -1}, rows.get(1));
		assertArrayEquals(new int[]{-1, ItemID.GRACEFUL_TOP, -1}, rows.get(2));
		assertArrayEquals(new int[]{-1, ItemID.GRACEFUL_LEGS, -1}, rows.get(3));
		assertArrayEquals(new int[]{ItemID.GRACEFUL_GLOVES, ItemID.GRACEFUL_BOOTS, -1}, rows.get(4));
	}
}
