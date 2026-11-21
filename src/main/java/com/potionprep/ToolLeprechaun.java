package com.potionprep;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * What the player has stored with a tool leprechaun, and the bottomless
 * compost bucket's remaining uses, from the game's varbits.
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
		out.values().removeIf(n -> n <= 0);
		return out;
	}

	/** Compost in the bottomless bucket: 1 compost, 2 super, 3 ultra, 0 unknown. */
	static int bucketType(IntUnaryOperator varbit)
	{
		return varbit.applyAsInt(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
	}

	/** Uses left in the bottomless compost bucket (0 if empty or unknown). */
	static int bucketUses(IntUnaryOperator varbit)
	{
		return varbit.applyAsInt(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY);
	}
}
