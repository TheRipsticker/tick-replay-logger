package com.deathtrace.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Represents a complete death record - the snapshot of ticks leading up to death.
 */
@Data
@Builder
public class DeathRecord
{
	// When the death occurred
	private final long timestamp;

	// What killed the player
	private final String killerName;

	// Where the death occurred (region/location name if available)
	private final String location;

	// The tick data leading up to death (in chronological order, oldest first)
	private final List<TickData> ticks;

	// Final HP (should be 0)
	private final int finalHp;

	// Total damage received in the recorded ticks
	private final int totalDamageReceived;

	/**
	 * Get human-readable time since death (e.g., "3 minutes ago")
	 */
	public String getTimeSinceDeath()
	{
		long now = System.currentTimeMillis();
		long diff = now - timestamp;

		long seconds = diff / 1000;
		long minutes = seconds / 60;
		long hours = minutes / 60;

		if (hours > 0)
		{
			return hours + (hours == 1 ? " hour ago" : " hours ago");
		}
		else if (minutes > 0)
		{
			return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
		}
		else
		{
			return "Just now";
		}
	}

	/**
	 * Get a summary string for display in the death list
	 */
	public String getSummary()
	{
		return killerName + " - " + getTimeSinceDeath();
	}

	/**
	 * Calculate total damage received from the ticks
	 */
	public static int calculateTotalDamage(List<TickData> ticks)
	{
		int total = 0;
		for (TickData tick : ticks)
		{
			if (tick.getDamageEvents() != null)
			{
				for (DamageEvent event : tick.getDamageEvents())
				{
					if (event.getDirection() == DamageEvent.DamageDirection.RECEIVED)
					{
						total += event.getAmount();
					}
				}
			}
		}
		return total;
	}
}
