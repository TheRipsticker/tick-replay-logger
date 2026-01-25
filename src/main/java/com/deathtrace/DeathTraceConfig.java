package com.deathtrace;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("deathtrace")
public interface DeathTraceConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "General recording settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Event Tracking",
		description = "Toggle which events are recorded",
		position = 1
	)
	String trackingSection = "tracking";

	@ConfigItem(
		keyName = "ticksToRecord",
		name = "Ticks to Record",
		description = "Number of ticks to keep in memory before death",
		position = 0,
		section = generalSection
	)
	default int ticksToRecord()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "maxDeathsToStore",
		name = "Max Deaths to Store",
		description = "Maximum number of death records to keep in storage",
		position = 1,
		section = generalSection
	)
	default int maxDeathsToStore()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "trackMovement",
		name = "Track Movement",
		description = "Record player movement (tiles traveled)",
		position = 0,
		section = trackingSection
	)
	default boolean trackMovement()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackDamageReceived",
		name = "Track Damage Received",
		description = "Record damage taken from enemies",
		position = 1,
		section = trackingSection
	)
	default boolean trackDamageReceived()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackDamageDealt",
		name = "Track Damage Dealt",
		description = "Record damage dealt to enemies",
		position = 2,
		section = trackingSection
	)
	default boolean trackDamageDealt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackFood",
		name = "Track Food/Potions",
		description = "Record eating food and drinking potions",
		position = 3,
		section = trackingSection
	)
	default boolean trackFood()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackPrayers",
		name = "Track Prayers",
		description = "Record prayer activation, deactivation, and drains",
		position = 4,
		section = trackingSection
	)
	default boolean trackPrayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackGear",
		name = "Track Gear Switches",
		description = "Record equipment changes",
		position = 5,
		section = trackingSection
	)
	default boolean trackGear()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackAttacks",
		name = "Track Attacks",
		description = "Record attack actions on enemies",
		position = 6,
		section = trackingSection
	)
	default boolean trackAttacks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackSpecialAttacks",
		name = "Track Special Attacks",
		description = "Record special attack usage",
		position = 7,
		section = trackingSection
	)
	default boolean trackSpecialAttacks()
	{
		return true;
	}
}
