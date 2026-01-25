package com.deathtrace.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents movement data for a tick.
 * Tracks intended movement vs actual movement completed.
 * Does NOT store raw coordinates - only actionable movement info.
 */
@Data
@Builder
public class MovementData
{
	private final int tilesIntended;    // How far player clicked to move
	private final int tilesCompleted;   // How far player actually moved
	private final boolean wasCancelled; // Was movement interrupted by another action

	/**
	 * Create movement data for completed movement
	 */
	public static MovementData completed(int tiles)
	{
		return MovementData.builder()
			.tilesIntended(tiles)
			.tilesCompleted(tiles)
			.wasCancelled(false)
			.build();
	}

	/**
	 * Create movement data for cancelled movement
	 */
	public static MovementData cancelled(int tilesIntended, int tilesCompleted)
	{
		return MovementData.builder()
			.tilesIntended(tilesIntended)
			.tilesCompleted(tilesCompleted)
			.wasCancelled(true)
			.build();
	}

	/**
	 * Create movement data for no movement this tick
	 */
	public static MovementData none()
	{
		return MovementData.builder()
			.tilesIntended(0)
			.tilesCompleted(0)
			.wasCancelled(false)
			.build();
	}

	/**
	 * Get a human-readable description
	 */
	public String getDescription()
	{
		if (tilesIntended == 0 && tilesCompleted == 0)
		{
			return null; // No movement, don't display anything
		}

		if (!wasCancelled)
		{
			return "Moved " + tilesCompleted + " tiles";
		}
		else if (tilesCompleted == 0)
		{
			return "Move cancelled (0 of " + tilesIntended + " tiles)";
		}
		else
		{
			return "Moved " + tilesIntended + " tiles (cancelled after " + tilesCompleted + ")";
		}
	}

	/**
	 * Check if there was any movement or movement intent
	 */
	public boolean hasMovement()
	{
		return tilesIntended > 0 || tilesCompleted > 0;
	}
}
