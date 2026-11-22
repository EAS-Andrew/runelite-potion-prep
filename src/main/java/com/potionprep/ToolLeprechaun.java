package com.potionprep;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * What the player has stored with a tool leprechaun, from the game's
 * varbits. These follow the account, so they read the same at any patch.
 */
final class ToolLeprechaun
{
	static final int[] VARBITS = {
		VarbitID.FARMING_TOOLS_RAKE, VarbitID.FARMING_TOOLS_EXTRARAKES,
		VarbitID.FARMING_TOOLS_DIBBER, VarbitID.FARMING_TOOLS_EXTRADIBBERS,
		VarbitID.FARMING_TOOLS_SPADE, VarbitID.FARMING_TOOLS_EXTRASPADES,
		VarbitID.FARMING_TOOLS_SECATEURS, VarbitID.FARMING_TOOLS_EXTRASECATEURS, VarbitID.FARMING_TOOLS_FAIRYSECATEURS,
		VarbitID.FARMING_TOOLS_COMPOST, VarbitID.FARMING_TOOLS_EXTRACOMPOST,
		VarbitID.FARMING_TOOLS_SUPERCOMPOST, VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST,
		VarbitID.FARMING_TOOLS_ULTRACOMPOST,
		VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY,
		VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE,
	};

	private ToolLeprechaun()
	{
	}

	/** Item id to quantity stored with the leprechaun. */
	static Map<Integer, Integer> read(IntUnaryOperator varbit)
	{
		Map<Integer, Integer> out = new HashMap<>();
		// Extra-slot varbits count in steps: 2 per step for tools, 256 for compost.
		out.put(ItemID.RAKE, varbit.applyAsInt(VarbitID.FARMING_TOOLS_RAKE) + 2 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRARAKES));
		out.put(ItemID.DIBBER, varbit.applyAsInt(VarbitID.FARMING_TOOLS_DIBBER) + 2 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRADIBBERS));
		out.put(ItemID.SPADE, varbit.applyAsInt(VarbitID.FARMING_TOOLS_SPADE) + 2 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRASPADES));
		int secateurs = varbit.applyAsInt(VarbitID.FARMING_TOOLS_SECATEURS) + 2 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRASECATEURS);
		out.put(ItemID.FAIRY_ENCHANTED_SECATEURS, varbit.applyAsInt(VarbitID.FARMING_TOOLS_FAIRYSECATEURS) == 1 ? secateurs : 0);
		out.put(ItemID.BUCKET_COMPOST, varbit.applyAsInt(VarbitID.FARMING_TOOLS_COMPOST) + 256 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRACOMPOST));
		out.put(ItemID.BUCKET_SUPERCOMPOST, varbit.applyAsInt(VarbitID.FARMING_TOOLS_SUPERCOMPOST) + 256 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST));
		out.put(ItemID.BUCKET_ULTRACOMPOST, varbit.applyAsInt(VarbitID.FARMING_TOOLS_ULTRACOMPOST));
		out.put(ItemID.PLANT_CURE, varbit.applyAsInt(VarbitID.FARMING_TOOLS_PLANTCURE));
		// The bottomless bucket, if stored: 1 empty, 2-4 holding compost.
		int bucket = storedBucket(varbit);
		if (bucket >= 2)
		{
			out.put(Farm.BOTTOMLESS_BUCKET, 1);
		}
		else if (bucket == 1)
		{
			out.put(ItemID.BOTTOMLESS_COMPOST_BUCKET, 1);
		}
		out.values().removeIf(n -> n <= 0);
		return out;
	}

	/** The stored bottomless bucket: 0 none, 1 empty, 2 compost, 3 super, 4 ultra. */
	static int storedBucket(IntUnaryOperator varbit)
	{
		return varbit.applyAsInt(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
	}

	/** Uses in the stored bottomless bucket (0 when none is stored). */
	static int storedBucketUses(IntUnaryOperator varbit)
	{
		return varbit.applyAsInt(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY);
	}

	/** Compost stored here you can take out at any patch: buckets, or a bucket with some in it. */
	static boolean hasCompost(Map<Integer, Integer> stored)
	{
		for (int id : Farm.COMPOST)
		{
			if (stored.getOrDefault(id, 0) > 0)
			{
				return true;
			}
		}
		return stored.getOrDefault(Farm.BOTTOMLESS_BUCKET, 0) > 0;
	}
}
