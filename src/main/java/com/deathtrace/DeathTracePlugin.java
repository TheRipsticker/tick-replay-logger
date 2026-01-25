package com.deathtrace;

import com.deathtrace.model.DamageEvent;
import com.deathtrace.model.DeathRecord;
import com.deathtrace.model.MovementData;
import com.deathtrace.model.PlayerAction;
import com.deathtrace.model.TickData;
import com.deathtrace.ui.DeathTracePanel;
import com.deathtrace.util.DeathStorage;
import com.deathtrace.util.TickRecorder;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.VarPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "Death Trace",
	description = "Records tick-by-tick combat actions for post-death analysis",
	tags = {"combat", "pvm", "learning", "death", "trace", "analysis"}
)
public class DeathTracePlugin extends Plugin
{
	private static final int DEFAULT_TICK_CAPACITY = 50;
	private static final int DEFAULT_MAX_DEATHS = 3;

	@Inject
	private Client client;

	@Inject
	private DeathTraceConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private SpriteManager spriteManager;

	private TickRecorder tickRecorder;
	private DeathStorage deathStorage;
	private DeathTracePanel panel;
	private NavigationButton navButton;

	// Track what NPC is currently attacking the player (for damage source attribution)
	private String lastAttackerName = "Unknown";

	// Track player position for movement detection
	private WorldPoint lastPlayerPosition = null;

	// Track prayers for detecting forced deactivation (drained/disabled by NPC)
	private List<String> lastTickActivePrayers = new ArrayList<>();
	private int lastTickPrayerPoints = 0;
	private final java.util.Set<String> manuallyDeactivatedPrayers = new java.util.HashSet<>();

	// Track special attack energy for detecting spec usage
	private int lastSpecialAttackPercent = 100;

	@Override
	protected void startUp() throws Exception
	{
		tickRecorder = new TickRecorder(config.ticksToRecord());
		deathStorage = new DeathStorage(config.maxDeathsToStore());

		// Create panel
		panel = new DeathTracePanel(deathStorage, spriteManager);
		panel.setTickRecorder(tickRecorder);

		// Create navigation button with panel icon
		final BufferedImage icon = ImageUtil.loadImageResource(DeathTracePlugin.class, "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Death Trace")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		log.info("Death Trace plugin started! Recording last {} ticks, storing {} deaths.",
			config.ticksToRecord(), config.maxDeathsToStore());
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);

		if (tickRecorder != null)
		{
			tickRecorder.clear();
		}
		log.info("Death Trace plugin stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Reset tick counter on login
			if (tickRecorder != null)
			{
				tickRecorder.resetTickCounter();
				log.info("Player logged in - tick recorder reset");
			}
			lastAttackerName = "Unknown";
			lastPlayerPosition = null;
			lastTickActivePrayers.clear();
			lastTickPrayerPoints = 0;
			manuallyDeactivatedPrayers.clear();
			lastSpecialAttackPercent = 100;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Clear data on logout
			if (tickRecorder != null)
			{
				tickRecorder.clear();
				log.debug("Player logged out - tick recorder cleared");
			}
			lastPlayerPosition = null;
			lastTickActivePrayers.clear();
			lastTickPrayerPoints = 0;
			manuallyDeactivatedPrayers.clear();
			lastSpecialAttackPercent = 100;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("deathtrace"))
		{
			return;
		}

		if (event.getKey().equals("ticksToRecord"))
		{
			int newCapacity = config.ticksToRecord();
			if (tickRecorder != null)
			{
				tickRecorder.resize(newCapacity);
			}
		}
		else if (event.getKey().equals("maxDeathsToStore"))
		{
			int newMax = config.maxDeathsToStore();
			if (deathStorage != null)
			{
				deathStorage.setMaxDeaths(newMax);
				// Refresh panel in case deaths were pruned
				if (panel != null)
				{
					panel.refresh();
				}
			}
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null || actor != localPlayer)
		{
			return;
		}

		// Player died! Record the death
		log.info("PLAYER DIED! Recording death trace...");

		// Get the tick history
		List<TickData> tickHistory = tickRecorder.getTickHistory();

		if (tickHistory.isEmpty())
		{
			log.warn("No tick data recorded before death");
			return;
		}

		// Determine killer (last attacker)
		String killer = lastAttackerName;

		// Try to get location
		WorldPoint location = localPlayer.getWorldLocation();
		String locationStr = location != null ?
			"(" + location.getX() + ", " + location.getY() + ")" : "Unknown";

		// Calculate total damage received
		int totalDamage = DeathRecord.calculateTotalDamage(tickHistory);

		// Create death record
		DeathRecord death = DeathRecord.builder()
			.timestamp(System.currentTimeMillis())
			.killerName(killer)
			.location(locationStr)
			.ticks(new ArrayList<>(tickHistory))
			.finalHp(0)
			.totalDamageReceived(totalDamage)
			.build();

		// Save to storage
		deathStorage.addDeath(death);

		// Refresh the panel to show the new death
		if (panel != null)
		{
			panel.refresh();
		}

		log.info("Death recorded: Killed by {} at {}. {} ticks captured, {} total damage received.",
			killer, locationStr, tickHistory.size(), totalDamage);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		// Track when an NPC starts attacking the player
		Actor source = event.getSource();
		Actor target = event.getTarget();

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// If an NPC is now targeting the player, remember it as potential damage source
		if (source instanceof NPC && target == localPlayer)
		{
			lastAttackerName = ((NPC) source).getName();
			if (lastAttackerName == null)
			{
				lastAttackerName = "Unknown NPC";
			}
			log.debug("Now being attacked by: {}", lastAttackerName);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Actor actor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null)
		{
			return;
		}

		int damage = hitsplat.getAmount();
		int hitsplatType = hitsplat.getHitsplatType();

		// Skip 0 damage unless it's a block (which we still want to track)
		if (damage == 0 && hitsplatType != HitsplatID.BLOCK_ME && hitsplatType != HitsplatID.BLOCK_OTHER)
		{
			return;
		}

		// Damage received by the player
		if (actor == localPlayer && hitsplat.isMine())
		{
			if (!config.trackDamageReceived())
			{
				return;
			}

			DamageEvent.DamageType damageType = determineDamageType(hitsplat);
			String source = determineDamageSource();

			DamageEvent damageEvent = DamageEvent.received(damage, damageType, source);
			tickRecorder.addDamageEvent(damageEvent);

			log.info("DAMAGE: Received {} from {}", damage, source);
		}
		// Damage dealt by the player
		else if (actor instanceof NPC && hitsplat.isMine())
		{
			if (!config.trackDamageDealt())
			{
				return;
			}

			NPC npc = (NPC) actor;
			String targetName = npc.getName();
			if (targetName == null)
			{
				targetName = "Unknown";
			}

			DamageEvent.DamageType damageType = determineDamageType(hitsplat);
			DamageEvent damageEvent = DamageEvent.dealt(damage, damageType, targetName);
			tickRecorder.addDamageEvent(damageEvent);

			log.info("DAMAGE: Dealt {} to {}", damage, targetName);
		}
	}

	/**
	 * Determine the damage type from the hitsplat.
	 */
	private DamageEvent.DamageType determineDamageType(Hitsplat hitsplat)
	{
		int type = hitsplat.getHitsplatType();

		// Check for poison/venom first
		if (type == HitsplatID.POISON)
		{
			return DamageEvent.DamageType.POISON;
		}
		if (type == HitsplatID.VENOM)
		{
			return DamageEvent.DamageType.VENOM;
		}
		if (type == HitsplatID.DISEASE)
		{
			return DamageEvent.DamageType.TYPELESS;
		}

		// For regular damage, we can't always determine the type from the hitsplat alone
		// RuneLite doesn't distinguish melee/range/mage in hitsplat types
		// We'd need to track enemy animations for accurate type detection
		// For now, return UNKNOWN for regular hits
		if (type == HitsplatID.DAMAGE_ME || type == HitsplatID.DAMAGE_OTHER ||
			type == HitsplatID.DAMAGE_ME_CYAN || type == HitsplatID.DAMAGE_OTHER_CYAN ||
			type == HitsplatID.DAMAGE_ME_ORANGE || type == HitsplatID.DAMAGE_OTHER_ORANGE ||
			type == HitsplatID.DAMAGE_ME_YELLOW || type == HitsplatID.DAMAGE_OTHER_YELLOW ||
			type == HitsplatID.DAMAGE_ME_WHITE || type == HitsplatID.DAMAGE_OTHER_WHITE)
		{
			return DamageEvent.DamageType.UNKNOWN;
		}

		if (type == HitsplatID.BLOCK_ME || type == HitsplatID.BLOCK_OTHER)
		{
			return DamageEvent.DamageType.UNKNOWN;
		}

		return DamageEvent.DamageType.TYPELESS;
	}

	/**
	 * Determine the source of damage to the player.
	 * Uses the last known attacker, or checks what's currently interacting with player.
	 */
	private String determineDamageSource()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return lastAttackerName;
		}

		// Check if something is currently interacting with the player
		Actor interacting = localPlayer.getInteracting();

		// Also check NPCs that might be attacking us
		for (NPC npc : client.getNpcs())
		{
			if (npc.getInteracting() == localPlayer)
			{
				String name = npc.getName();
				if (name != null)
				{
					lastAttackerName = name;
					return name;
				}
			}
		}

		return lastAttackerName;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Start new tick
		tickRecorder.startNewTick();

		// Track movement
		WorldPoint currentPosition = localPlayer.getWorldLocation();
		if (config.trackMovement() && lastPlayerPosition != null && currentPosition != null)
		{
			int distance = lastPlayerPosition.distanceTo(currentPosition);
			if (distance > 0)
			{
				MovementData movement = MovementData.completed(distance);
				tickRecorder.setMovement(movement);
			}
		}
		lastPlayerPosition = currentPosition;

		// Capture player state
		int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
		int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);

		// Get active prayers
		List<String> activePrayers = getActivePrayers();

		// Detect forced prayer deactivation (drained by NPC or ran out of prayer points)
		if (config.trackPrayers())
		{
			for (String prayer : lastTickActivePrayers)
			{
				if (!activePrayers.contains(prayer) && !manuallyDeactivatedPrayers.contains(prayer))
				{
					// Prayer was active last tick but not now, and player didn't manually deactivate
					String reason;
					if (currentPrayer == 0 && lastTickPrayerPoints > 0)
					{
						reason = "out of prayer";
					}
					else if (currentPrayer < lastTickPrayerPoints)
					{
						reason = "prayer drained";
					}
					else
					{
						reason = "disabled by attack";
					}

					PlayerAction drainedAction = PlayerAction.prayerDrained(prayer, reason);
					tickRecorder.addPlayerAction(drainedAction);
					log.info("PRAYER DRAINED: {} ({})", prayer, reason);
				}
			}
		}

		// Clear manual deactivations for next tick
		manuallyDeactivatedPrayers.clear();

		// Update tracking for next tick comparison
		lastTickActivePrayers = new ArrayList<>(activePrayers);
		lastTickPrayerPoints = currentPrayer;

		// Update state
		tickRecorder.updateState(currentHp, maxHp, currentPrayer, maxPrayer, activePrayers);

		// Finalize tick
		tickRecorder.finalizeTick();

		// Update live view panel
		if (panel != null)
		{
			panel.onGameTick();
		}

		// Log every 10 ticks to avoid spam
		if (tickRecorder.getCurrentTickNumber() % 10 == 0)
		{
			log.info("Tick {}: HP {}/{}, Prayer {}/{}, Active prayers: {}",
				tickRecorder.getCurrentTickNumber(),
				currentHp, maxHp,
				currentPrayer, maxPrayer,
				activePrayers.isEmpty() ? "none" : String.join(", ", activePrayers));
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		String option = event.getMenuOption();
		String target = event.getMenuTarget();

		// Strip color tags from target (RuneLite adds <col=xxxxx> tags)
		String cleanTarget = stripColorTags(target);

		PlayerAction action = null;

		// Eating food
		if (option.equals("Eat") && config.trackFood())
		{
			action = PlayerAction.ate(cleanTarget);
			log.info("ACTION: Ate {}", cleanTarget);
		}
		// Drinking potions
		else if (option.equals("Drink") && config.trackFood())
		{
			action = PlayerAction.drank(cleanTarget);
			log.info("ACTION: Drank {}", cleanTarget);
		}
		// Activating prayers
		else if (option.equals("Activate") && config.trackPrayers())
		{
			action = PlayerAction.prayerOn(cleanTarget);
			log.info("ACTION: Activated {}", cleanTarget);
		}
		// Deactivating prayers
		else if (option.equals("Deactivate"))
		{
			manuallyDeactivatedPrayers.add(cleanTarget); // Always track for drain detection
			if (config.trackPrayers())
			{
				action = PlayerAction.prayerOff(cleanTarget);
				log.info("ACTION: Deactivated {}", cleanTarget);
			}
		}
		// Equipping gear
		else if ((option.equals("Wear") || option.equals("Wield") || option.equals("Equip")) && config.trackGear())
		{
			action = PlayerAction.equipped(cleanTarget);
			log.info("ACTION: Equipped {}", cleanTarget);
		}
		// Unequipping gear
		else if (option.equals("Remove") && config.trackGear())
		{
			action = PlayerAction.unequipped(cleanTarget);
			log.info("ACTION: Unequipped {}", cleanTarget);
		}
		// Attacking
		else if (option.equals("Attack") && config.trackAttacks())
		{
			// Get current weapon name
			String weapon = getCurrentWeaponName();
			action = PlayerAction.attacked(cleanTarget, weapon);
			log.info("ACTION: Attacked {} with {}", cleanTarget, weapon);
		}

		// Add action to current tick if we captured something
		if (action != null)
		{
			tickRecorder.addPlayerAction(action);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		// Check for special attack energy change
		int currentSpecPercent = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);

		// Special attack energy is stored as 0-1000 (divide by 10 for percentage)
		if (currentSpecPercent < lastSpecialAttackPercent && config.trackSpecialAttacks())
		{
			// Special attack was used
			String weaponName = getCurrentWeaponName();
			PlayerAction specAction = PlayerAction.specialAttack(weaponName);
			tickRecorder.addPlayerAction(specAction);
			log.info("ACTION: Special attack with {}", weaponName);
		}

		lastSpecialAttackPercent = currentSpecPercent;
	}

	/**
	 * Strip RuneLite color tags from strings.
	 * E.g., "<col=ff9040>Shark</col>" -> "Shark"
	 */
	private String stripColorTags(String input)
	{
		if (input == null)
		{
			return "";
		}
		// Remove <col=xxxxxx> and </col> tags
		return input.replaceAll("<col=[^>]*>", "").replaceAll("</col>", "").trim();
	}

	/**
	 * Get the name of the currently equipped weapon.
	 */
	private String getCurrentWeaponName()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return "Unknown";
		}

		// Get weapon from equipment
		net.runelite.api.ItemContainer equipment = client.getItemContainer(net.runelite.api.InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return "Unarmed";
		}

		net.runelite.api.Item weapon = equipment.getItem(net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null)
		{
			return "Unarmed";
		}

		// Get item name from item ID
		return client.getItemDefinition(weapon.getId()).getName();
	}

	/**
	 * Get a list of currently active prayers.
	 * Filters out base prayers when upgraded versions are active.
	 */
	private List<String> getActivePrayers()
	{
		List<String> active = new ArrayList<>();
		java.util.Set<Prayer> activePrayerSet = new java.util.HashSet<>();

		// First pass: collect all active prayers
		for (Prayer prayer : Prayer.values())
		{
			if (client.isPrayerActive(prayer))
			{
				activePrayerSet.add(prayer);
			}
		}

		// Filter out base prayers when upgraded versions are active
		// Check for Ruinous Powers / newer prayer variants by name
		boolean hasDeadeye = false;
		boolean hasMysticVigour = false;
		for (Prayer prayer : activePrayerSet)
		{
			String name = prayer.name().toLowerCase();
			if (name.contains("deadeye"))
			{
				hasDeadeye = true;
			}
			if (name.contains("mystic") && name.contains("vigour"))
			{
				hasMysticVigour = true;
			}
		}

		// Ranged: Eagle Eye < Rigour or Deadeye variant
		if (activePrayerSet.contains(Prayer.RIGOUR) || hasDeadeye)
		{
			activePrayerSet.remove(Prayer.EAGLE_EYE);
			activePrayerSet.remove(Prayer.HAWK_EYE);
			activePrayerSet.remove(Prayer.SHARP_EYE);
		}

		// Magic: Mystic Might < Augury or Mystic Vigour variant
		if (activePrayerSet.contains(Prayer.AUGURY) || hasMysticVigour)
		{
			activePrayerSet.remove(Prayer.MYSTIC_MIGHT);
			activePrayerSet.remove(Prayer.MYSTIC_LORE);
			activePrayerSet.remove(Prayer.MYSTIC_WILL);
		}

		// Melee: Steel Skin/Ultimate Strength/Incredible Reflexes < Piety < Chivalry
		if (activePrayerSet.contains(Prayer.PIETY))
		{
			activePrayerSet.remove(Prayer.CHIVALRY);
			activePrayerSet.remove(Prayer.ULTIMATE_STRENGTH);
			activePrayerSet.remove(Prayer.INCREDIBLE_REFLEXES);
			activePrayerSet.remove(Prayer.STEEL_SKIN);
		}
		else if (activePrayerSet.contains(Prayer.CHIVALRY))
		{
			activePrayerSet.remove(Prayer.ULTIMATE_STRENGTH);
			activePrayerSet.remove(Prayer.INCREDIBLE_REFLEXES);
			activePrayerSet.remove(Prayer.STEEL_SKIN);
		}

		// Convert to formatted names
		for (Prayer prayer : activePrayerSet)
		{
			active.add(formatPrayerName(prayer));
		}

		return active;
	}

	/**
	 * Format prayer enum name to readable string.
	 * E.g., PROTECT_FROM_MELEE -> "Protect from Melee"
	 */
	private String formatPrayerName(Prayer prayer)
	{
		String name = prayer.name().replace("_", " ").toLowerCase();
		// Capitalize first letter of each word
		StringBuilder result = new StringBuilder();
		boolean capitalizeNext = true;
		for (char c : name.toCharArray())
		{
			if (c == ' ')
			{
				capitalizeNext = true;
				result.append(c);
			}
			else if (capitalizeNext)
			{
				result.append(Character.toUpperCase(c));
				capitalizeNext = false;
			}
			else
			{
				result.append(c);
			}
		}
		return result.toString();
	}

	/**
	 * Get the death storage for UI access
	 */
	public DeathStorage getDeathStorage()
	{
		return deathStorage;
	}

	@Provides
	DeathTraceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DeathTraceConfig.class);
	}
}
