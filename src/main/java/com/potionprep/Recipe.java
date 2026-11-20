package com.potionprep;

import java.util.Arrays;
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

	private Recipe(int level, boolean altForm, int yield, Node tool, String stepFormat, Input... inputs)
	{
		this.level = level;
		this.altForm = altForm;
		this.yield = yield;
		this.tool = tool;
		this.stepFormat = stepFormat;
		this.inputs = Arrays.asList(inputs);
	}

	static Input in(Node node, int qty)
	{
		return new Input(node, qty);
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
