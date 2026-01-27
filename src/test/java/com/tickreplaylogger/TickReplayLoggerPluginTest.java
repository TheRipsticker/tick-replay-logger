package com.tickreplaylogger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TickReplayLoggerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TickReplayLoggerPlugin.class);
		RuneLite.main(args);
	}
}
