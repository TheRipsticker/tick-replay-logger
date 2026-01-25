package com.deathtrace.model;

import lombok.Builder;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents all data captured for a single game tick.
 * This is the fundamental unit of recording - everything is organized by tick.
 */
@Data
@Builder
public class TickData
{
	// Tick identification
	private final int tickNumber;
	private final long timestamp;

	// Player state snapshot at end of tick
	private int currentHp;
	private int maxHp;
	private int currentPrayer;
	private int maxPrayer;

	// Actions that occurred during this tick
	@Builder.Default
	private List<PlayerAction> playerActions = new ArrayList<>();

	// Damage events during this tick
	@Builder.Default
	private List<DamageEvent> damageEvents = new ArrayList<>();

	// Movement data for this tick
	private MovementData movement;

	// Active prayers at the end of this tick
	@Builder.Default
	private List<String> activePrayers = new ArrayList<>();

	public void addPlayerAction(PlayerAction action)
	{
		if (playerActions == null)
		{
			playerActions = new ArrayList<>();
		}
		playerActions.add(action);
	}

	public void addDamageEvent(DamageEvent event)
	{
		if (damageEvents == null)
		{
			damageEvents = new ArrayList<>();
		}
		damageEvents.add(event);
	}
}
