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
	public void eachAllotmentIsCreditedByTheOneUsed()
	{
		FarmPlan.Settings s = FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(FlowerSeed.NONE)
			.allotment(AllotmentSeed.SNAPE_GRASS).patches(EnumSet.of(Patch.FALADOR))
			.overrides(Collections.emptyMap()).build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.SNAPE_GRASS_SEED, 6);
		bank.put(ItemID.RANARR_SEED, 1);
		bank.put(ItemID.BUCKET_ULTRACOMPOST, 3);
		HerbRun r = HerbRun.from(FarmPlan.compute(s, bank, new HashSet<>(), 99), s, 0);
		HerbRun.Stop st = r.current();
		// The south allotment (varbit 2) planted then composted: the same plot, not the next one.
		r.onMessage("You plant 3 snape grass seeds in the allotment.", 2);
		r.onMessage("You treat the allotment with ultracompost.", 2);
		HerbRun.Plot south = st.getPlots().get(1);
		HerbRun.Plot north = st.getPlots().get(2);
		assertEquals(2, south.getVarbit());
		assertTrue(south.isPlanted() && south.isComposted());
		assertFalse(north.isPlanted() || north.isComposted());
		r.onMessage("You treat the allotment with ultracompost.", 1);
		assertEquals(1, north.getVarbit());
		assertTrue(north.isComposted());
		assertFalse(north.isPlanted());
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

	@Test
	public void compostComesBackOnWhenYouGetMore()
	{
		HerbRun r = run(true, FlowerSeed.NONE);
		assertTrue(r.setCompostCarried(false));
		assertFalse(r.isCompost());
		assertTrue(r.setCompostCarried(true));
		assertTrue(r.isCompost());
	}

	@Test
	public void picksAreCounted()
	{
		HerbRun r = run(false, FlowerSeed.NONE);
		r.onMessage("You pick a Grimy ranarr weed.");
		r.onMessage("You pick a Grimy ranarr weed.");
		assertEquals(2, r.getPicked());
	}

	@Test
	public void fertileSoilKeepsTheStopOpenAfterPlanting()
	{
		FarmPlan.Settings s = FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(FlowerSeed.NONE)
			.allotment(AllotmentSeed.NONE).patches(EnumSet.of(Patch.FALADOR, Patch.CATHERBY))
			.overrides(Collections.emptyMap()).build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.RANARR_SEED, 2);
		bank.put(ItemID.EARTHRUNE, 100);
		bank.put(ItemID.ASTRALRUNE, 100);
		bank.put(ItemID.NATURERUNE, 100);
		bank.put(ItemID.LAWRUNE, 100);
		bank.put(ItemID.WATERRUNE, 100);
		FarmPlan plan = FarmPlan.compute(s, bank, new HashSet<>(),
			FarmPlan.Account.builder().farming(99).magic(99).spellbook(Farm.LUNAR).build());
		assertTrue(plan.isFertileSoil());
		HerbRun r = HerbRun.from(plan, s, 0);
		assertTrue(r.isCompost());
		Patch first = r.current().getPatch();
		r.onMessage("You plant a Ranarr seed in the herb patch.");
		// Seeded first: still here until Fertile Soil goes on.
		assertEquals(first, r.current().getPatch());
		r.onMessage("The herb patch has been treated with supercompost.");
		assertTrue(r.finished() || r.current().getPatch() != first);
	}

	@Test
	public void emptyAllotmentsBringYouBackWhileTheHerbGrows()
	{
		FarmPlan.Settings s = FarmPlan.Settings.builder().herb(HerbSeed.RANARR).flower(FlowerSeed.NONE)
			.allotment(AllotmentSeed.SNAPE_GRASS).patches(EnumSet.of(Patch.HOSIDIUS, Patch.FALADOR))
			.overrides(Collections.emptyMap()).build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.RANARR_SEED, 5);
		bank.put(ItemID.SNAPE_GRASS_SEED, 12);
		Map<Patch, PatchStatus> status = new java.util.EnumMap<>(Patch.class);
		status.put(Patch.HOSIDIUS, new PatchStatus(PatchStatus.State.GROWING, Long.MAX_VALUE));
		status.put(Patch.FALADOR, new PatchStatus(PatchStatus.State.GROWING, Long.MAX_VALUE));
		Map<Patch, int[]> side = new java.util.EnumMap<>(Patch.class);
		side.put(Patch.HOSIDIUS, new int[]{-1, 3, 138}); // one empty, one snape grass ready to pick
		side.put(Patch.FALADOR, new int[]{-1, 130, 65}); // both snape grass growing
		FarmPlan plan = FarmPlan.compute(s, bank, new HashSet<>(),
			FarmPlan.Account.builder().farming(99).patchStatus(status).sidePlots(side).build());
		HerbRun r = HerbRun.from(plan, s, 0);
		assertEquals(1, r.getStops().size());
		assertEquals(Patch.HOSIDIUS, r.getStops().get(0).getPatch());
		assertEquals(2, r.getStops().get(0).getPlots().size());
		assertFalse(r.getStops().get(0).hasHerb());
	}
}
