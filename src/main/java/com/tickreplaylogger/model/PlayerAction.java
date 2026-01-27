package com.tickreplaylogger.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a player action that occurred during a tick.
 * This includes eating, drinking, prayer switches, gear switches, and attacks.
 */
@Data
@Builder
public class PlayerAction
{
	private final ActionType type;
	private final String description;
	private final String target;      // For attacks: NPC name. For gear: item name. For prayers: prayer name.
	private final String itemName;    // For eat/drink/equip actions

	public enum ActionType
	{
		EAT_FOOD,
		DRINK_POTION,
		PRAYER_ACTIVATED,
		PRAYER_DEACTIVATED,
		PRAYER_DRAINED,      // Prayer disabled by running out of points or NPC attack
		GEAR_EQUIPPED,
		GEAR_UNEQUIPPED,
		ATTACK,
		SPECIAL_ATTACK
	}

	/**
	 * Create a food eating action
	 */
	public static PlayerAction ate(String foodName)
	{
		return PlayerAction.builder()
			.type(ActionType.EAT_FOOD)
			.itemName(foodName)
			.description("Ate " + foodName)
			.build();
	}

	/**
	 * Create a potion drinking action
	 */
	public static PlayerAction drank(String potionName)
	{
		return PlayerAction.builder()
			.type(ActionType.DRINK_POTION)
			.itemName(potionName)
			.description("Drank " + potionName)
			.build();
	}

	/**
	 * Create a prayer activation action
	 */
	public static PlayerAction prayerOn(String prayerName)
	{
		return PlayerAction.builder()
			.type(ActionType.PRAYER_ACTIVATED)
			.target(prayerName)
			.description("Activated " + prayerName)
			.build();
	}

	/**
	 * Create a prayer deactivation action
	 */
	public static PlayerAction prayerOff(String prayerName)
	{
		return PlayerAction.builder()
			.type(ActionType.PRAYER_DEACTIVATED)
			.target(prayerName)
			.description("Deactivated " + prayerName)
			.build();
	}

	/**
	 * Create a prayer drained/disabled action (e.g., ran out of prayer points, Vorkath purple)
	 */
	public static PlayerAction prayerDrained(String prayerName, String reason)
	{
		String desc = reason != null && !reason.isEmpty()
			? prayerName + " disabled (" + reason + ")"
			: prayerName + " disabled";
		return PlayerAction.builder()
			.type(ActionType.PRAYER_DRAINED)
			.target(prayerName)
			.description(desc)
			.build();
	}

	/**
	 * Create a gear equip action
	 */
	public static PlayerAction equipped(String itemName)
	{
		return PlayerAction.builder()
			.type(ActionType.GEAR_EQUIPPED)
			.itemName(itemName)
			.description("Equipped " + itemName)
			.build();
	}

	/**
	 * Create a gear unequip action
	 */
	public static PlayerAction unequipped(String itemName)
	{
		return PlayerAction.builder()
			.type(ActionType.GEAR_UNEQUIPPED)
			.itemName(itemName)
			.description("Unequipped " + itemName)
			.build();
	}

	/**
	 * Create an attack action
	 */
	public static PlayerAction attacked(String targetName, String weaponName)
	{
		return PlayerAction.builder()
			.type(ActionType.ATTACK)
			.target(targetName)
			.itemName(weaponName)
			.description("Attacked " + targetName + " (" + weaponName + ")")
			.build();
	}

	/**
	 * Create a special attack action
	 */
	public static PlayerAction specialAttack(String weaponName)
	{
		return PlayerAction.builder()
			.type(ActionType.SPECIAL_ATTACK)
			.itemName(weaponName)
			.description("Special attack (" + weaponName + ")")
			.build();
	}
}
