package com.potionprep;

import java.util.Map;
import lombok.Getter;

/**
 * One item in a recipe tree. Potions are "dosed": counted in doses across
 * their 1-4 dose variants, since doses can be decanted freely.
 */
@Getter
public final class Node
{
	private final String name;
	/** Main item id; for dosed items the 4-dose id. */
	private final int id;
	/** Ids indexed by dose - 1, or null for non-dosed items. */
	private final int[] doseIds;
	/** Where to get it when it cannot be made, or null. */
	private final String source;
	private Recipe recipe;

	private Node(String name, int id, int[] doseIds, String source)
	{
		this.name = name;
		this.id = id;
		this.doseIds = doseIds;
		this.source = source;
	}

	static Node item(String name, int id, String source)
	{
		return new Node(name, id, null, source);
	}

	static Node dosed(String name, int d1, int d2, int d3, int d4)
	{
		return new Node(name, d4, new int[]{d1, d2, d3, d4}, null);
	}

	Node madeBy(Recipe recipe)
	{
		this.recipe = recipe;
		return this;
	}

	public boolean isDosed()
	{
		return doseIds != null;
	}

	/** Ids for this item's bank row: (1) (2) (3) (4) left to right for potions. */
	public int[] rowIds()
	{
		return isDosed() ? doseIds.clone() : new int[]{id};
	}

	/** Units held: item count, or total doses. */
	public int count(Map<Integer, Integer> counts)
	{
		if (!isDosed())
		{
			return counts.getOrDefault(id, 0);
		}
		int doses = 0;
		for (int d = 0; d < 4; d++)
		{
			doses += (d + 1) * counts.getOrDefault(doseIds[d], 0);
		}
		return doses;
	}
}
