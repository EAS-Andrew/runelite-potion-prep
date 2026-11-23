package com.potionprep;

import static org.junit.Assert.assertEquals;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class RingWayTest
{
	@Test
	public void houseRingWithACapeBeatsTheCloak()
	{
		Set<Integer> owned = Set.of(ItemID.SKILLCAPE_CONSTRUCTION, ItemID.ARDY_CAPE_EASY);
		assertEquals(ItemID.SKILLCAPE_CONSTRUCTION, Farm.ringWay(owned::contains, true));
		assertEquals(ItemID.ARDY_CAPE_EASY, Farm.ringWay(owned::contains, false));
	}

	@Test
	public void houseRingWithoutACapeFallsBackToTheCloak()
	{
		Set<Integer> owned = Set.of(ItemID.ARDY_CAPE_HARD);
		assertEquals(ItemID.ARDY_CAPE_HARD, Farm.ringWay(owned::contains, true));
		assertEquals(-1, Farm.ringWay(Set.<Integer>of()::contains, true));
	}

	@Test
	public void directionsMatchTheWay()
	{
		assertEquals("Teleport to your house, use the fairy ring, dial CIR",
			Farm.viaRing(ItemID.SKILLCAPE_MAX, Farm.Teleport.FAIRY_CIR));
		assertEquals("Ardougne cloak: Monastery Teleport, walk east to the fairy ring, dial CIR",
			Farm.viaRing(ItemID.ARDY_CAPE_EASY, Farm.Teleport.FAIRY_CIR));
	}
}
