package com.potionprep;

import com.potionprep.Farm.Patch;
import com.potionprep.Farm.Teleport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * A herb run in progress, Quest Helper style: one stop at a time, each with
 * the plots to plant there. A plot is done once it's planted and, if you
 * brought compost, composted. Progress comes from the game's own messages.
 */
@Getter
public class HerbRun
{
	public enum PlotType
	{
		HERB("Herb"), FLOWER("Flower"), ALLOTMENT("Allotment");

		@Getter
		private final String label;

		PlotType(String label)
		{
			this.label = label;
		}
	}

	@Getter
	public static class Plot
	{
		private final PlotType type;
		private final String seed;
		private boolean planted;
		private boolean composted;
		/** For the herb plot: what the patch needs right now (read live at the patch), or null. */
		private PatchStatus.Task task;
		/** Not enough seeds with you to plant it: it doesn't hold up the stop. */
		private boolean noSeeds;
		/** Planned without a seed (you ran out): only there to pick or cure what's growing. */
		private boolean harvestOnly;
		/** For an allotment, the farming varbit of the one it turned out to be, once you've used it; -1 until then. */
		private int varbit = -1;

		/** Seeds one planting uses. */
		int seedsPer()
		{
			return type == PlotType.ALLOTMENT ? Farm.SEEDS_PER_ALLOTMENT : 1;
		}

		Plot(PlotType type, String seed)
		{
			this.type = type;
			this.seed = seed;
		}
	}

	@Getter
	public static class Stop
	{
		private final Patch patch;
		/** Shown name, e.g. "Farming Guild allotments". */
		private final String name;
		/** Where the plots are, for the route and hint arrow. */
		private final net.runelite.api.coords.WorldPoint point;
		/** How to get here; null for a short walk from the stop before. */
		private final Teleport teleport;
		/** Item for the teleport, -1 for spells, staff-free fairy rings and walks. */
		private final int itemId;
		private final List<Plot> plots;
		/** You've been here this run. */
		private boolean visited;
		/** For a fairy ring: how to reach one (see {@link FarmPlan.Stop#getRingWay}). */
		private int ringWay = -1;

		Stop ring(int way)
		{
			ringWay = way;
			return this;
		}

		Stop(Patch patch, String name, net.runelite.api.coords.WorldPoint point, Teleport teleport, int itemId, List<Plot> plots)
		{
			this.patch = patch;
			this.name = name;
			this.point = point;
			this.teleport = teleport;
			this.itemId = itemId;
			this.plots = plots;
		}

		boolean hasHerb()
		{
			return !plots.isEmpty() && plots.get(0).type == PlotType.HERB;
		}

		boolean done(boolean compost)
		{
			for (Plot p : plots)
			{
				if (p.noSeeds && !p.planted)
				{
					continue;
				}
				if (!p.planted || compost && !p.composted)
				{
					return false;
				}
			}
			return true;
		}
	}

	// "You plant a Ranarr seed in the herb patch." / "You plant 3 snape grass seeds in the allotment."
	private static final Pattern PLANT = Pattern.compile("You plant .+ in the (herb patch|flower patch|allotment)",
		Pattern.CASE_INSENSITIVE);
	// "You treat the herb patch with ultracompost." / "This allotment has already been treated with compost."
	// / "The herb patch has been treated with supercompost." (Fertile Soil). Before or after planting.
	private static final Pattern COMPOST = Pattern.compile(
		"(?:You treat the|This|The) (herb patch|flower patch|allotment)(?: with| has (?:already )?been (?:treated|fertilised) with) (?:ultra|super|)compost",
		Pattern.CASE_INSENSITIVE);

	private final List<Stop> stops;
	/** The seed each kind of plot was planned with, so changing the config mid-run doesn't change the run. */
	private final java.util.Map<PlotType, Integer> seedIds = new java.util.EnumMap<>(PlotType.class);
	/** You have compost (carried or at the leprechaun), so plots also need composting. Turns off if you run out. */
	private boolean compost;
	/** The run started with compost, so it can come back on if you get more. */
	private final boolean planned;
	private final long startedAt;
	private int index;
	/** Stops skipped rather than done. */
	private int skipped;

	HerbRun(List<Stop> stops, boolean compost, long startedAt)
	{
		this.stops = Collections.unmodifiableList(stops);
		this.compost = compost;
		this.planned = compost;
		this.startedAt = startedAt;
	}

	/** Build a run from a plan: every patch on it, in order, with its plots. */
	static HerbRun from(FarmPlan plan, FarmPlan.Settings settings, long now)
	{
		// Flowers and allotments only while the seeds last, in run order.
		// Herbs too: once they run out, a patch is only worth the trip to pick or cure it.
		int herbSeeds = seedsHave(plan, settings.getHerb().getSeedId());
		int flowerSeeds = seedsHave(plan, settings.getFlower().getSeedId());
		int allotSeeds = seedsHave(plan, settings.getAllotment().getSeedId());
		List<Stop> stops = new ArrayList<>();
		for (FarmPlan.Stop st : plan.getStops())
		{
			if (st.isLocked() || st.isGrowing() && !st.isSideOnly())
			{
				continue;
			}
			Patch p = st.getPatch();
			List<Plot> plots = new ArrayList<>();
			Plot herb = new Plot(PlotType.HERB, settings.getHerb().getName() + " seed");
			plots.add(herb);
			if (st.isSideOnly())
			{
				// The herb is still growing: only the empty flower or allotments here.
				herb.noSeeds = true;
				herb.harvestOnly = true;
			}
			else if (herbSeeds >= 1)
			{
				herbSeeds--;
			}
			else
			{
				herb.noSeeds = true;
				herb.harvestOnly = true;
			}
			boolean herbWorthIt = !st.isSideOnly() && (!herb.harvestOnly || worthPicking(st.getStatus()));
			List<Plot> others = new ArrayList<>();
			if (st.isFlowerToDo() && flowerSeeds >= 1)
			{
				flowerSeeds--;
				others.add(new Plot(PlotType.FLOWER, settings.getFlower().getName() + " seed"));
			}
			if (settings.getAllotment() != Farm.AllotmentSeed.NONE)
			{
				for (int i = 0; i < st.getAllotmentsToDo() && allotSeeds >= Farm.SEEDS_PER_ALLOTMENT; i++)
				{
					allotSeeds -= Farm.SEEDS_PER_ALLOTMENT;
					others.add(new Plot(PlotType.ALLOTMENT, settings.getAllotment().getName() + " seeds"));
				}
			}
			net.runelite.api.coords.WorldPoint apart = PatchPlaces.otherPlots(p);
			if (!herbWorthIt && others.isEmpty())
			{
				// No seed and nothing growing to pick: nothing to do here.
				continue;
			}
			if (st.isSideOnly())
			{
				if (others.isEmpty())
				{
					continue;
				}
				stops.add(new Stop(p, p.getName() + (apart != null ? " allotments" : ""), apart != null ? apart : PatchPlaces.point(p),
					st.getTeleport(), st.getItemId(), others).ring(st.getRingWay()));
			}
			else if (apart != null && !others.isEmpty())
			{
				// The flower and allotments here are a walk from the herb patch: their own stop.
				if (herbWorthIt)
				{
					stops.add(new Stop(p, p.getName(), PatchPlaces.point(p), st.getTeleport(), st.getItemId(), plots).ring(st.getRingWay()));
				}
				stops.add(new Stop(p, p.getName() + " allotments", apart, herbWorthIt ? null : st.getTeleport(),
					herbWorthIt ? -1 : st.getItemId(), others).ring(st.getRingWay()));
			}
			else
			{
				plots.addAll(others);
				stops.add(new Stop(p, p.getName(), PatchPlaces.point(p), st.getTeleport(), st.getItemId(), plots).ring(st.getRingWay()));
			}
		}
		boolean compost = plan.isFertileSoil()
			|| plan.getSupplies().stream().anyMatch(s -> isCompost(s.getItemId()) && s.getHave() > 0);
		HerbRun run = new HerbRun(stops, compost, now);
		run.fertileSoil = plan.isFertileSoil();
		run.expected = plan.getExpectedHerbs();
		run.seedIds.put(PlotType.HERB, settings.getHerb().getSeedId());
		run.seedIds.put(PlotType.FLOWER, settings.getFlower().getSeedId());
		run.seedIds.put(PlotType.ALLOTMENT, settings.getAllotment().getSeedId());
		return run;
	}

	/**
	 * Without a seed, a herb patch is still worth visiting if something's
	 * growing there to pick or cure, or if we don't know what it's doing.
	 */
	static boolean worthPicking(PatchStatus status)
	{
		if (status == null)
		{
			return true;
		}
		switch (status.getState())
		{
			case EMPTY:
			case DEAD:
				return false;
			default:
				return true;
		}
	}

	/** Seeds you have of a kind (bank, inventory, seed vault), from the plan. */
	private static int seedsHave(FarmPlan plan, int seedId)
	{
		if (seedId == -1)
		{
			return 0;
		}
		return plan.getSupplies().stream().filter(s -> s.getItemId() == seedId).mapToInt(FarmPlan.Supply::getHave)
			.findFirst().orElse(0);
	}

	private static boolean isCompost(int id)
	{
		if (id == Farm.BOTTOMLESS_BUCKET)
		{
			return true;
		}
		for (int c : Farm.COMPOST)
		{
			if (c == id)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether you have compost to hand: carried, or stored with the tool
	 * leprechaun (there's one at every patch). Running out mid-run stops plots
	 * waiting for it, so the run carries on with planting only.
	 *
	 * @return true if anything changed
	 */
	boolean setCompostCarried(boolean carried)
	{
		if (carried)
		{
			// Got some again (e.g. took more from the leprechaun): plots want it again.
			boolean changed = planned && !compost;
			compost |= planned;
			return changed;
		}
		if (!compost)
		{
			return false;
		}
		compost = false;
		Stop stop = current();
		if (stop != null && stop.done(false))
		{
			index++;
		}
		return true;
	}

	/**
	 * Seeds you're carrying, by plot type. Plots there aren't enough seeds
	 * for are marked "No seeds" so they don't hold up the stop.
	 *
	 * @return true if anything changed
	 */
	boolean setSeedsCarried(java.util.Map<PlotType, Integer> carried)
	{
		Stop stop = current();
		if (stop == null)
		{
			return false;
		}
		java.util.Map<PlotType, Integer> left = new java.util.EnumMap<>(PlotType.class);
		left.putAll(carried);
		boolean changed = false;
		for (Plot p : stop.plots)
		{
			if (p.planted)
			{
				continue;
			}
			int have = left.getOrDefault(p.type, 0);
			boolean none = have < p.seedsPer();
			if (!none)
			{
				left.put(p.type, have - p.seedsPer());
			}
			changed |= p.noSeeds != none;
			p.noSeeds = none;
		}
		if (changed && stop.done(compost))
		{
			index++;
		}
		return changed;
	}

	/**
	 * Where you are relative to the current stop. Arriving marks it visited;
	 * leaving a visited stop once its herb is planted (or can't be) moves the
	 * run on, so plots you chose not to do don't hold it up.
	 *
	 * @return true if the run moved on
	 */
	boolean onPosition(boolean atStop)
	{
		Stop stop = current();
		if (stop == null)
		{
			return false;
		}
		if (atStop)
		{
			stop.visited = true;
			return false;
		}
		if (!stop.visited)
		{
			return false;
		}
		Plot first = stop.plots.get(0);
		if (first.planted || first.noSeeds || !stop.hasHerb())
		{
			index++;
			return true;
		}
		return false;
	}

	/** The seed a kind of plot is planted with on this run, -1 if none. */
	int seedId(PlotType type)
	{
		return seedIds.getOrDefault(type, -1);
	}

	/** The stop in hand, or null when the run is over. */
	public Stop current()
	{
		return index < stops.size() ? stops.get(index) : null;
	}

	public boolean finished()
	{
		return index >= stops.size();
	}

	public void skip()
	{
		if (!finished())
		{
			index++;
			skipped++;
		}
	}

	/**
	 * A game message while at the current stop. Credits the plot it names;
	 * moves on when the stop is done.
	 *
	 * @return true if anything changed
	 */
	/** Composting by casting Fertile Soil: no compost item to run out of. */
	@Getter
	private boolean fertileSoil;

	/** Herbs picked on this run, one per "You pick a Grimy ..." message. */
	@Getter
	private int picked;
	/** Herbs the plan expects this run's planting to give, picked next run. */
	@Getter
	private double expected;

	private static final Pattern PICK = Pattern.compile("^You pick (?:a|an|\\d+) grimy ", Pattern.CASE_INSENSITIVE);

	boolean onMessage(String message)
	{
		return onMessage(message, -1);
	}

	/**
	 * @param varbit the farming varbit of the patch you last used something on, or -1:
	 *               says which of a location's allotments a message was about
	 */
	boolean onMessage(String message, int varbit)
	{
		if (PICK.matcher(message).find())
		{
			picked++;
		}
		Stop stop = current();
		if (stop == null)
		{
			return false;
		}
		boolean changed = false;
		Matcher m = PLANT.matcher(message);
		if (m.find())
		{
			changed = credit(stop, type(m.group(1)), true, varbit);
		}
		m = COMPOST.matcher(message);
		if (m.find())
		{
			changed |= credit(stop, type(m.group(1)), false, varbit);
		}
		if (changed && stop.done(compost))
		{
			index++;
		}
		return changed;
	}

	/**
	 * The herb patch's live state at the current stop. Growing means it's
	 * planted, so this also catches plantings without a chat message.
	 *
	 * @return true if anything changed
	 */
	boolean onHerbState(PatchStatus.Task task)
	{
		Stop stop = current();
		if (stop == null || !stop.hasHerb())
		{
			return false;
		}
		Plot herb = stop.plots.get(0);
		boolean changed = herb.task != task;
		herb.task = task;
		if (task == PatchStatus.Task.GROWING && !herb.planted)
		{
			herb.planted = true;
			changed = true;
		}
		if (changed && stop.done(compost))
		{
			index++;
		}
		return changed;
	}

	/**
	 * Marks a plot of this type as having had that step: the allotment it was
	 * done to when known, else the first still missing it.
	 */
	private static boolean credit(Stop stop, PlotType type, boolean planted, int varbit)
	{
		if (type == PlotType.ALLOTMENT && varbit != -1)
		{
			// The allotment used before (or the first not yet tied to one) is this one.
			Plot to = stop.plots.stream().filter(p -> p.type == type && p.varbit == varbit).findFirst()
				.orElseGet(() -> stop.plots.stream().filter(p -> p.type == type && p.varbit == -1
					&& (planted ? !p.planted : !p.composted)).findFirst()
				.orElseGet(() -> stop.plots.stream().filter(p -> p.type == type && p.varbit == -1).findFirst().orElse(null)));
			if (to != null)
			{
				to.varbit = varbit;
				boolean changed = planted ? !to.planted : !to.composted;
				if (planted)
				{
					to.planted = true;
				}
				else
				{
					to.composted = true;
				}
				return changed;
			}
		}
		for (Plot p : stop.plots)
		{
			if (p.type != type)
			{
				continue;
			}
			if (planted && !p.planted)
			{
				p.planted = true;
				return true;
			}
			if (!planted && !p.composted)
			{
				p.composted = true;
				return true;
			}
		}
		return false;
	}

	private static PlotType type(String place)
	{
		switch (place.toLowerCase(Locale.ROOT))
		{
			case "flower patch":
				return PlotType.FLOWER;
			case "allotment":
				return PlotType.ALLOTMENT;
			default:
				return PlotType.HERB;
		}
	}

	/** A word or two for a plot: Pick, Clear, Cure, Rake, then what's left of planting and composting, or Done. */
	static String status(Plot p, boolean compost)
	{
		if (p.noSeeds && !p.planted)
		{
			if (p.task == PatchStatus.Task.CURE)
			{
				return "Cure";
			}
			// Ran out before this patch: it's here for the pick, if there is one.
			return p.harvestOnly && (p.task == null || p.task == PatchStatus.Task.PICK) ? "Pick" : "No seeds";
		}
		if (!p.planted && p.task != null)
		{
			switch (p.task)
			{
				case PICK:
					return "Pick";
				case CLEAR:
					return "Clear";
				case CURE:
					return "Cure";
				case RAKE:
					return "Rake";
				default:
					break;
			}
		}
		// Compost and seed go on in either order.
		boolean needsCompost = compost && !p.composted;
		if (!p.planted)
		{
			return needsCompost ? "Plant + compost" : "Plant";
		}
		return needsCompost ? "Compost" : "Done";
	}

	/** Something left to do at a plot: not finished, and not out of seeds with nothing to pick. */
	static boolean needsVisit(Plot p, boolean compost)
	{
		String st = status(p, compost);
		return !st.equals("Done") && !st.equals("No seeds");
	}

	/** What's left at a plot, e.g. "pick, compost, plant ranarr seed". */
	static String todo(Plot p, boolean compost)
	{
		List<String> left = new ArrayList<>();
		if (p.task != null && p.task != PatchStatus.Task.PLANT && p.task != PatchStatus.Task.GROWING && !p.planted)
		{
			left.add(Catalog.lower(p.task.getText()));
		}
		if (compost && !p.composted)
		{
			left.add("compost");
		}
		if (!p.planted)
		{
			left.add("plant " + Catalog.lower(p.seed));
		}
		return String.join(", ", left);
	}
}
