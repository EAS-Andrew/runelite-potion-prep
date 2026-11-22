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
		// Wearing a trimmed cape: the banked untrimmed one still wins. The
		// gloves and labcoat are cosmetic, so they never show.
		List<int[]> rows = Gear.rows(id -> bank.getOrDefault(id, 0) > 0,
			id -> id == ItemID.SKILLCAPE_HERBLORE_TRIMMED || id == ItemID.MM_ALCHEMIST_BODY_ALT);
		assertEquals(2, rows.size());
		assertArrayEquals(new int[]{-1, ItemID.MM_ALCHEMIST_HAT, -1}, rows.get(0));
		assertArrayEquals(new int[]{ItemID.SKILLCAPE_HERBLORE, ItemID.AMULET_OF_CHEMISTRY, -1}, rows.get(1));
	}

	@Test
	public void unchargedAlchemistsAmuletBeatsChemistry()
	{
		List<int[]> rows = Gear.rows(id -> id == ItemID.AMULET_OF_CHEMISTRY_IMBUED_UNCHARGED || id == ItemID.AMULET_OF_CHEMISTRY, id -> false);
		assertArrayEquals(new int[]{-1, ItemID.AMULET_OF_CHEMISTRY_IMBUED_UNCHARGED, -1}, rows.get(0));
		assertEquals(false, Gear.allIds().contains(ItemID.MM_ALCHEMIST_GLOVES));
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

	@Test
	public void runItemsGoInFreeSlotsAfterSavedGear()
	{
		// Slots: 1 cape, 2 neck, 3 weapon, 12 ring.
		java.util.Map<Integer, Integer> slot = new HashMap<>();
		slot.put(100, 3); // magic secateurs
		slot.put(200, 1); // Ardougne cloak
		slot.put(300, 2); // skills necklace
		slot.put(301, 2); // Xeric's talisman (neck taken by then)
		slot.put(400, 12); // Explorer's ring
		int[] saved = new int[14];
		java.util.Arrays.fill(saved, -1);
		saved[1] = 900; // graceful cape: saved gear keeps the cape slot
		java.util.List<int[]> rows = java.util.Arrays.asList(
			new int[]{10, 11, 100}, // rake, spade, secateurs
			new int[]{300, 301, 200, 400, 50}); // teleports in run order, 50 a tablet
		Gear.Worn w = Gear.wear(saved, rows, id -> slot.getOrDefault(id, -1));
		assertEquals(900, w.slots[1]);
		assertEquals(300, w.slots[2]);
		assertEquals(100, w.slots[3]);
		assertEquals(400, w.slots[12]);
		assertEquals(2, w.rows.size());
		org.junit.Assert.assertArrayEquals(new int[]{10, 11}, w.rows.get(0));
		org.junit.Assert.assertArrayEquals(new int[]{301, 200, 50}, w.rows.get(1));
	}
}
