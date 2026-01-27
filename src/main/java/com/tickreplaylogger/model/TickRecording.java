package com.tickreplaylogger.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Represents a complete tick recording - a snapshot of ticks (e.g., leading up to death or saved manually).
 */
@Data
@Builder
public class TickRecording
{
	// When the recording was created
	private final long timestamp;

	// Context label (e.g., killer name for deaths, or custom label for saved recordings)
	private final String killerName;

	// Where the recording was made (region/location name if available)
	private final String location;

	// The tick data (in chronological order, oldest first)
	private final List<TickData> ticks;

	// Final HP (0 for deaths, current HP for live snapshots)
	private final int finalHp;

	// Total damage received in the recorded ticks
	private final int totalDamageReceived;

	/**
	 * Get human-readable time since recording (e.g., "3 minutes ago")
	 */
	public String getTimeSinceRecording()
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
	 * Get a summary string for display in the recording list
	 */
	public String getSummary()
	{
		return killerName + " - " + getTimeSinceRecording();
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
