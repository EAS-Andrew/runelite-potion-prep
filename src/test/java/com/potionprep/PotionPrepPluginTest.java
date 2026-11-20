package com.potionprep;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Dev launcher: ./gradlew runClient */
public class PotionPrepPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PotionPrepPlugin.class);
		RuneLite.main(args);
	}
}
