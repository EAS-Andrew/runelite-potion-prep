package com.potionprep;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/** Reads the parts of {@link FarmPlan.Account} that come from the game. Client thread only. */
final class AccountReader
{
	/** Quests the herb run cares about (patch access and teleport spells). */
	static final Quest[] QUESTS = {
		Quest.PRIEST_IN_PERIL, Quest.MY_ARMS_BIG_ADVENTURE, Quest.MAKING_FRIENDS_WITH_MY_ARM,
		Quest.CHILDREN_OF_THE_SUN, Quest.PLAGUE_CITY, Quest.EADGARS_RUSE, Quest.LUNAR_DIPLOMACY,
		Quest.THE_GREAT_BRAIN_ROBBERY, Quest.TWILIGHTS_PROMISE,
	};

	static final int[] POUCH_TYPES = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};
	static final int[] POUCH_AMOUNTS = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5,
		VarbitID.RUNE_POUCH_QUANTITY_6};

	/** Varbits that change the account snapshot (besides the leprechaun ones). */
	static final int[] VARBITS;

	static
	{
		int[] v = new int[POUCH_TYPES.length * 2 + 2];
		// Spellbook, diaries and house location change what's usable.
		System.arraycopy(POUCH_TYPES, 0, v, 0, POUCH_TYPES.length);
		System.arraycopy(POUCH_AMOUNTS, 0, v, POUCH_TYPES.length, POUCH_AMOUNTS.length);
		v[v.length - 2] = VarbitID.SPELLBOOK;
		v[v.length - 1] = VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE;
		v = java.util.Arrays.copyOf(v, v.length + 3);
		v[v.length - 3] = VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE;
		v[v.length - 2] = VarbitID.POH_HOUSE_LOCATION;
		v[v.length - 1] = VarbitID.FARMING_BLOCKWEEDS;
		VARBITS = v;
	}

	private AccountReader()
	{
	}

	/** Runs a client script per quest, so call it outside other scripts (e.g. ClientTick). */
	static Set<Quest> quests(Client client)
	{
		Set<Quest> done = EnumSet.noneOf(Quest.class);
		for (Quest q : QUESTS)
		{
			if (q.getState(client) == QuestState.FINISHED)
			{
				done.add(q);
			}
		}
		return done;
	}

	/** Fairy rings work once you've reached the Fairy Godfather in Fairytale II (quest varbit 40+). */
	static boolean fairyRings(Client client)
	{
		return client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST) >= 40;
	}

	static FarmPlan.Account read(Client client, Set<Quest> quests, Boolean fairyRings, Set<String> portals,
		Set<Integer> worn, boolean ownsPouch)
	{
		return FarmPlan.Account.builder()
			.fairyRings(fairyRings)
			.kandarinDiary(client.getVarbitValue(VarbitID.KANDARIN_DIARY_ELITE_COMPLETE) == 1 ? 3
				: client.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1 ? 2
				: client.getVarbitValue(VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE) == 1 ? 1 : 0)
			// FARMING_BLOCKWEEDS: 0 not bought, 1 off, 2 on.
			.autoweed(client.getVarbitValue(VarbitID.FARMING_BLOCKWEEDS) == 2)
			.kourendEasy(client.getVarbitValue(VarbitID.KOUREND_DIARY_EASY_COMPLETE) == 1)
			.kourendHard(client.getVarbitValue(VarbitID.KOUREND_DIARY_HARD_COMPLETE) == 1)
			.lumbridgeElite(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1)
			.houseLocation(client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION))
			.portals(portals)
			.farming(client.getRealSkillLevel(Skill.FARMING))
			.magic(client.getBoostedSkillLevel(Skill.MAGIC))
			.spellbook(client.getVarbitValue(VarbitID.SPELLBOOK))
			.quests(quests)
			.moryElite(client.getVarbitValue(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE) == 1)
			.leprechaun(ToolLeprechaun.read(client::getVarbitValue))
			.pouch(ownsPouch ? pouch(client) : new HashMap<>())
			.staffElements(staffElements(worn))
			.build();
	}

	private static Map<Integer, Integer> pouch(Client client)
	{
		Map<Integer, Integer> out = new HashMap<>();
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runes == null)
		{
			return out;
		}
		for (int i = 0; i < POUCH_TYPES.length; i++)
		{
			int type = client.getVarbitValue(POUCH_TYPES[i]);
			int amount = client.getVarbitValue(POUCH_AMOUNTS[i]);
			int id = type > 0 ? runes.getIntValue(type) : -1;
			if (id > 0 && amount > 0)
			{
				out.merge(id, amount, Integer::sum);
			}
		}
		return out;
	}

	/** Air 1, water 2, earth 4, fire 8, as in {@link Farm.Rune}. */
	static int staffElements(Set<Integer> worn)
	{
		int bits = 0;
		for (int id : worn)
		{
			bits |= staff(id);
		}
		return bits;
	}

	private static int staff(int id)
	{
		switch (id)
		{
			case ItemID.STAFF_OF_AIR:
			case ItemID.AIR_BATTLESTAFF:
			case ItemID.MYSTIC_AIR_STAFF:
				return 1;
			case ItemID.STAFF_OF_WATER:
			case ItemID.WATER_BATTLESTAFF:
			case ItemID.MYSTIC_WATER_STAFF:
				return 2;
			case ItemID.STAFF_OF_EARTH:
			case ItemID.EARTH_BATTLESTAFF:
			case ItemID.MYSTIC_EARTH_STAFF:
				return 4;
			case ItemID.STAFF_OF_FIRE:
			case ItemID.FIRE_BATTLESTAFF:
			case ItemID.MYSTIC_FIRE_STAFF:
				return 8;
			case ItemID.MIST_BATTLESTAFF:
			case ItemID.MYSTIC_MIST_BATTLESTAFF:
				return 1 | 2;
			case ItemID.DUST_BATTLESTAFF:
			case ItemID.MYSTIC_DUST_BATTLESTAFF:
				return 1 | 4;
			case ItemID.MUD_BATTLESTAFF:
			case ItemID.MYSTIC_MUD_STAFF:
				return 2 | 4;
			case ItemID.SMOKE_BATTLESTAFF:
			case ItemID.MYSTIC_SMOKE_BATTLESTAFF:
				return 1 | 8;
			case ItemID.STEAM_BATTLESTAFF:
			case ItemID.MYSTIC_STEAM_BATTLESTAFF:
			case ItemID.STEAM_BATTLESTAFF_PRETTY:
			case ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY:
				return 2 | 8;
			case ItemID.LAVA_BATTLESTAFF:
			case ItemID.MYSTIC_LAVA_STAFF:
			case ItemID.LAVA_BATTLESTAFF_PRETTY:
			case ItemID.MYSTIC_LAVA_STAFF_PRETTY:
				return 4 | 8;
			// Tomes (off-hand) count as unlimited runes of their element.
			case ItemID.TOME_OF_FIRE:
				return 8;
			case ItemID.TOME_OF_WATER:
				return 2;
			case ItemID.TOME_OF_EARTH:
				return 4;
			case ItemID.TWINFLAME_STAFF:
				return 2 | 8;
			case ItemID.SHADOWFLAME_QUADRANT:
				return 1 | 2 | 4 | 8;
			default:
				return 0;
		}
	}
}
