package com.potionprep;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(PotionPrepConfig.GROUP)
public interface PotionPrepConfig extends Config
{
	String GROUP = "potionprep";

	@Range(min = 1, max = 8)
	@ConfigItem(
		keyName = "column",
		name = "Bank column",
		description = "Which bank column the ingredients go in. 8 is the right-most column.",
		position = 1
	)
	default int column()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "autoOpen",
		name = "Open on bank",
		description = "Show the selected potion's ingredients whenever you open the bank.",
		position = 2
	)
	default boolean autoOpen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "potion",
		name = "",
		description = "",
		hidden = true
	)
	default String potion()
	{
		return "";
	}
}
