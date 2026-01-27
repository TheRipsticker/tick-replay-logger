package com.tickreplaylogger.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a damage event - either damage received or dealt.
 * Critically includes the SOURCE of the damage for post-death analysis.
 */
@Data
@Builder
public class DamageEvent
{
	private final DamageDirection direction;  // RECEIVED or DEALT
	private final int amount;
	private final DamageType damageType;
	private final String source;              // Who/what caused the damage

	public enum DamageDirection
	{
		RECEIVED,  // Player took damage
		DEALT      // Player dealt damage
	}

	public enum DamageType
	{
		MELEE,
		RANGED,
		MAGIC,
		TYPELESS,  // Includes poison, venom, environmental
		POISON,
		VENOM,
		UNKNOWN
	}

	/**
	 * Create a damage received event
	 */
	public static DamageEvent received(int amount, DamageType type, String source)
	{
		return DamageEvent.builder()
			.direction(DamageDirection.RECEIVED)
			.amount(amount)
			.damageType(type)
			.source(source != null ? source : "Unknown source")
			.build();
	}

	/**
	 * Create a damage dealt event
	 */
	public static DamageEvent dealt(int amount, DamageType type, String target)
	{
		return DamageEvent.builder()
			.direction(DamageDirection.DEALT)
			.amount(amount)
			.damageType(type)
			.source(target != null ? target : "Unknown target")
			.build();
	}

	/**
	 * Get a human-readable description
	 */
	public String getDescription()
	{
		// Only show damage type if it's something specific (poison, venom)
		// Otherwise just show the amount and source
		if (direction == DamageDirection.RECEIVED)
		{
			if (damageType == DamageType.POISON || damageType == DamageType.VENOM)
			{
				return "Received " + amount + " " + damageType.name().toLowerCase() + " damage";
			}
			return "Received " + amount + " damage from " + source;
		}
		else
		{
			return "Dealt " + amount + " damage to " + source;
		}
	}
}
