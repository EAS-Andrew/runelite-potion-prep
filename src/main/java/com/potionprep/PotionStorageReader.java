package com.potionprep;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ScriptID;

/**
 * Reads the bank's potion storage the same way the core Bank Tags plugin
 * does: for every storable potion (finished and unfinished), run the game's
 * POTIONSTORE_DOSES script, which returns how many doses are stored.
 *
 * Potion storage is an unlock. Without it every potion reports 0 doses, so
 * the result is simply empty and counts fall back to bank + inventory.
 *
 * Must run on the client thread, outside of another script (e.g. ClientTick).
 */
final class PotionStorageReader
{
	private PotionStorageReader()
	{
	}

	/**
	 * @return item id to units held in storage, keyed by the potion's 1-dose
	 * item id. For finished potions the value is total doses; for unfinished
	 * potions (one "dose" each) it is the number of potions. Both line up
	 * with {@link Node#count}, which weights each id by its dose.
	 */
	static Map<Integer, Integer> read(Client client)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (int listId : new int[]{EnumID.POTIONSTORE_POTIONS, EnumID.POTIONSTORE_UNFINISHED_POTIONS})
		{
			EnumComposition list = client.getEnum(listId);
			if (list == null)
			{
				continue;
			}
			for (int potionEnumId : list.getIntVals())
			{
				EnumComposition potion = client.getEnum(potionEnumId);
				if (potion == null)
				{
					continue;
				}
				client.runScript(ScriptID.POTIONSTORE_DOSES, potionEnumId);
				int doses = client.getIntStack()[0];
				int oneDoseId = potion.getIntValue(1);
				if (doses > 0 && oneDoseId > 0)
				{
					out.merge(oneDoseId, doses, Integer::sum);
				}
			}
		}
		return out;
	}
}
