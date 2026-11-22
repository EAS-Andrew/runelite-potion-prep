package com.potionprep;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Value;

/** One crafting action: inputs (+ optional tool) to {@link #yield} units of a product. */
@Getter
public final class Recipe
{
	@Value
	public static class Input
	{
		Node node;
		int qty;
		/**
		 * Items that can stand in for {@link #node} if already held, e.g. a
		 * torstol potion (unf) in place of a torstol. Never made for this.
		 */
		List<Node> alts;
	}

	private final int level;
	/**
	 * True when the product is just another form of the input (cleaning a
	 * herb, crushing a horn). Shortfalls are then reported on the product,
	 * "short 5 ranarr weed" rather than "short 5 grimy ranarr weed".
	 */
	private final boolean altForm;
	private final int yield;
	private final List<Input> inputs;
	private final Node tool;
	/** Step text; %d is the number of crafts. */
	private final String stepFormat;
	/** Herblore XP per craft. */
	private double xp;
	/** An unf potion the Herblore cape can make straight from the grimy herb. */
	private boolean fromGrimy;

	private Recipe(int level, boolean altForm, int yield, Node tool, String stepFormat, Input... inputs)
	{
		this.level = level;
		this.altForm = altForm;
		this.yield = yield;
		this.tool = tool;
		this.stepFormat = stepFormat;
		this.inputs = Arrays.asList(inputs);
	}

	/** Sets the XP per craft. */
	Recipe xp(double xp)
	{
		this.xp = xp;
		return this;
	}

	/** Marks an unf recipe whose herb the Herblore cape lets you add grimy. */
	Recipe fromGrimy()
	{
		this.fromGrimy = true;
		return this;
	}

	/**
	 * Made a dose at a time: one dosed input per craft, and the product keeps
	 * that potion's dose (super energy(3) makes stamina(3)).
	 */
	public boolean isPerDose()
	{
		return yield == 1 && !inputs.isEmpty() && inputs.get(0).getNode().isDosed() && inputs.get(0).getQty() == 1;
	}

	/** Takes this dosed input as whole (4)s, so other doses must be decanted first. */
	public static boolean needsFull(Input in)
	{
		return in.getNode().isDosed() && in.getQty() == 4;
	}

	static Input in(Node node, int qty)
	{
		return new Input(node, qty, Collections.emptyList());
	}

	/** Like {@link #in}, but any of {@code alts} already held is used first. */
	static Input inAny(Node node, int qty, Node... alts)
	{
		return new Input(node, qty, Arrays.asList(alts));
	}

	static Recipe clean(Node grimy, int level)
	{
		return new Recipe(level, true, 1, null, "Clean %d " + Catalog.lower(grimy.getName()), in(grimy, 1));
	}

	static Recipe crush(Node raw, int yield, Node pestle)
	{
		return new Recipe(1, true, yield, pestle, "Crush %d " + Catalog.lower(raw.getName()), in(raw, 1));
	}

	static Recipe mix(int level, int yield, String stepFormat, Input... inputs)
	{
		return new Recipe(level, false, yield, null, stepFormat, inputs);
	}
}
