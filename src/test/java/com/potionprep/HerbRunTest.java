package com.potionprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.potionprep.Farm.AllotmentSeed;
import com.potionprep.Farm.FlowerSeed;
import com.potionprep.Farm.HerbSeed;
import com.potionprep.Farm.Patch;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class HerbRunTest
{
	private static HerbRun run(boolean compost, FlowerSeed flower)
	{
		FarmPlan.Settings s = FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(flower)
			.allotment(AllotmentSeed.NONE).patches(EnumSet.of(Patch.FALADOR, Patch.WEISS))
			.overrides(Collections.emptyMap()).build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.LIMPWURT_SEED, 10);
		bank.put(ItemID.RANARR_SEED, 10);
		if (compost)
		{
			bank.put(ItemID.BUCKET_ULTRACOMPOST, 10);
		}
		FarmPlan plan = FarmPlan.compute(s, bank, new HashSet<>(), 99);
		return HerbRun.from(plan, s, 0);
	}

	@Test
	public void stopIsDoneOncePlantedAndComposted()
	{
		HerbRun r = run(true, FlowerSeed.LIMPWURT);
		assertEquals(Patch.FALADOR, r.current().getPatch());
		assertEquals(2, r.current().getPlots().size());
		r.onMessage("You treat the herb patch with ultracompost.");
		r.onMessage("You plant a Ranarr seed in the herb patch.");
		assertEquals(Patch.FALADOR, r.current().getPatch());
		r.onMessage("You plant a Limpwurt seed in the flower patch.");
		// Flower still needs compost.
		assertEquals(Patch.FALADOR, r.current().getPatch());
		r.onMessage("This flower patch has already been treated with ultracompost.");
		assertEquals(Patch.WEISS, r.current().getPatch());
	}

	@Test
	public void withoutCompostPlantingIsEnough()
	{
		HerbRun r = run(false, FlowerSeed.NONE);
		assertFalse(r.isCompost());
		r.onMessage("You plant a Ranarr seed in the herb patch.");
		assertEquals(Patch.WEISS, r.current().getPatch());
		r.skip();
		assertTrue(r.finished());
	}

	@Test
	public void outOfHerbSeedsOnlyPatchesWithSomethingToPickStay()
	{
		FarmPlan.Settings s = FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(FlowerSeed.NONE)
			.allotment(AllotmentSeed.NONE).patches(EnumSet.of(Patch.FALADOR, Patch.CATHERBY, Patch.ARDOUGNE))
			.overrides(Collections.emptyMap()).build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.RANARR_SEED, 1);
		Map<Patch, PatchStatus> status = new java.util.EnumMap<>(Patch.class);
		status.put(Patch.CATHERBY, new PatchStatus(PatchStatus.State.EMPTY, 0));
		status.put(Patch.FALADOR, new PatchStatus(PatchStatus.State.READY, 0));
		FarmPlan plan = FarmPlan.compute(s, bank, new HashSet<>(),
			FarmPlan.Account.builder().farming(99).patchStatus(status).build());
		HerbRun r = HerbRun.from(plan, s, 0);
		// Ardougne gets the one seed; Catherby is empty with none left, so it's dropped;
		// Falador still has herbs to pick.
		assertEquals(2, r.getStops().size());
		assertEquals(Patch.ARDOUGNE, r.getStops().get(0).getPatch());
		assertEquals(Patch.FALADOR, r.getStops().get(1).getPatch());
		assertEquals("Pick", HerbRun.status(r.getStops().get(1).getPlots().get(0), false));
	}

	@Test
	public void otherMessagesAreIgnored()
	{
		HerbRun r = run(true, FlowerSeed.NONE);
		assertFalse(r.onMessage("You pick a Grimy ranarr weed."));
	}

	@Test
	public void farmingGuildAllotmentsAreTheirOwnStop()
	{
		FarmPlan.Settings s = FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(FlowerSeed.LIMPWURT)
			.allotment(AllotmentSeed.SNAPE_GRASS).patches(EnumSet.of(Patch.FARMING_GUILD))
			.overrides(Collections.emptyMap()).build();
		Map<Integer, Integer> seeds = new HashMap<>();
		seeds.put(ItemID.LIMPWURT_SEED, 10);
		seeds.put(ItemID.SNAPE_GRASS_SEED, 30);
		HerbRun r = HerbRun.from(FarmPlan.compute(s, seeds, new HashSet<>(), 99), s, 0);
		assertEquals(2, r.getStops().size());
		assertEquals(1, r.getStops().get(0).getPlots().size());
		assertEquals("Farming Guild allotments", r.getStops().get(1).getName());
		assertEquals(3, r.getStops().get(1).getPlots().size());
		assertEquals(null, r.getStops().get(1).getTeleport());
	}

	@Test
	public void liveHerbStateDrivesTheSteps()
	{
		HerbRun r = run(false, FlowerSeed.NONE);
		r.onHerbState(PatchStatus.task(37)); // ranarr ready
		HerbRun.Plot herb = r.current().getPlots().get(0);
		assertEquals("pick the herbs, plant ranarr seed", HerbRun.todo(herb, false));
		r.onHerbState(PatchStatus.task(171)); // dead
		assertEquals("clear the dead herbs (spade), plant ranarr seed", HerbRun.todo(herb, false));
		r.onHerbState(PatchStatus.task(3)); // raked clean
		assertEquals("plant ranarr seed", HerbRun.todo(herb, false));
		r.onHerbState(PatchStatus.task(32)); // planted
		assertEquals(Patch.WEISS, r.current().getPatch());
	}

	@Test
	public void runningOutOfCompostKeepsTheRunGoing()
	{
		HerbRun r = run(true, FlowerSeed.NONE);
		r.onMessage("You plant a Ranarr seed in the herb patch.");
		// Planted but not composted: still at Falador.
		assertEquals(Patch.FALADOR, r.current().getPatch());
		assertTrue(r.setCompostCarried(false));
		assertEquals(Patch.WEISS, r.current().getPatch());
		assertEquals("Plant", HerbRun.status(r.current().getPlots().get(0), r.isCompost()));
	}

	@Test
	public void compostGoesOnBeforeOrAfterTheSeed()
	{
		HerbRun r = run(true, FlowerSeed.NONE);
		HerbRun.Plot herb = r.current().getPlots().get(0);
		assertEquals("Plant + compost", HerbRun.status(herb, r.isCompost()));
		// Seed first, then compost.
		r.onMessage("You plant a Ranarr seed in the herb patch.");
		assertEquals("Compost", HerbRun.status(herb, r.isCompost()));
		assertEquals(Patch.FALADOR, r.current().getPatch());
		r.onMessage("You treat the herb patch with ultracompost.");
		assertEquals(Patch.WEISS, r.current().getPatch());
		// Compost first (here by Fertile Soil), then seed.
		herb = r.current().getPlots().get(0);
		r.onMessage("The herb patch has been treated with supercompost.");
		assertEquals("Plant", HerbRun.status(herb, r.isCompost()));
		r.onMessage("You plant a Ranarr seed in the herb patch.");
		assertTrue(r.finished() || r.current().getPatch() != Patch.WEISS);
	}

	@Test
	public void allotmentsOnlyWhileSeedsLast()
	{
		FarmPlan.Settings s = FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(FlowerSeed.NONE)
			.allotment(AllotmentSeed.SNAPE_GRASS).patches(EnumSet.of(Patch.FALADOR, Patch.CATHERBY))
			.overrides(Collections.emptyMap()).build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.SNAPE_GRASS_SEED, 2);
		HerbRun r = HerbRun.from(FarmPlan.compute(s, bank, new HashSet<>(), 99), s, 0);
		// 2 seeds can't plant an allotment (3 each): herb plots only.
		assertEquals(1, r.getStops().get(0).getPlots().size());
		bank.put(ItemID.SNAPE_GRASS_SEED, 9);
		r = HerbRun.from(FarmPlan.compute(s, bank, new HashSet<>(), 99), s, 0);
		assertEquals(3, r.getStops().get(0).getPlots().size());
		assertEquals(2, r.getStops().get(1).getPlots().size());
	}

	@Test
	public void plotsWithoutSeedsDontHoldUpTheStop()
	{
		HerbRun r = run(false, FlowerSeed.LIMPWURT);
		Map<HerbRun.PlotType, Integer> carried = new java.util.EnumMap<>(HerbRun.PlotType.class);
		carried.put(HerbRun.PlotType.HERB, 5);
		r.setSeedsCarried(carried);
		assertEquals("No seeds", HerbRun.status(r.current().getPlots().get(1), false));
		r.onMessage("You plant a Ranarr seed in the herb patch.");
		assertEquals(Patch.WEISS, r.current().getPatch());
	}

	@Test
	public void leavingAPlantedStopMovesOn()
	{
		HerbRun r = run(true, FlowerSeed.LIMPWURT);
		r.onPosition(true);
		r.onMessage("You plant a Ranarr seed in the herb patch.");
		// Herb planted, flower and compost left: still Falador while you're there.
		r.onPosition(true);
		assertEquals(Patch.FALADOR, r.current().getPatch());
		assertTrue(r.onPosition(false));
		assertEquals(Patch.WEISS, r.current().getPatch());
	}

	@Test
	public void notVisitedYetDoesntMoveOn()
	{
		HerbRun r = run(true, FlowerSeed.NONE);
		assertFalse(r.onPosition(false));
		assertEquals(Patch.FALADOR, r.current().getPatch());
	}
}
