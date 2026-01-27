package com.tickreplaylogger;

import com.tickreplaylogger.model.DamageEvent;
import com.tickreplaylogger.model.TickRecording;
import com.tickreplaylogger.model.MovementData;
import com.tickreplaylogger.model.PlayerAction;
import com.tickreplaylogger.model.TickData;
import com.tickreplaylogger.ui.TickReplayLoggerPanel;
import com.tickreplaylogger.util.RecordingStorage;
import com.tickreplaylogger.util.TickRecorder;
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
import com.google.gson.Gson;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "Tick Replay Logger",
	description = "Records tick-by-tick combat actions for post-death analysis and replay",
	tags = {"combat", "pvm", "learning", "death", "trace", "analysis", "tick", "replay", "logger"}
)
public class TickReplayLoggerPlugin extends Plugin
{
	private static final int DEFAULT_TICK_CAPACITY = 50;
	private static final int DEFAULT_MAX_DEATHS = 3;

	@Inject
	private Client client;

	@Inject
	private TickReplayLoggerConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private Gson gson;

	private TickRecorder tickRecorder;
	private RecordingStorage recordingStorage;
	private TickReplayLoggerPanel panel;
	private NavigationButton navButton;

	// Track what NPC is currently attacking the player (for damage source attribution)
	private String lastAttackerName = "Unknown";

	// Track player position for movement detection
	private WorldPoint lastPlayerPosition = null;

	// Track prayers for detecting forced deactivation (drained/disabled by NPC)
	private List<String> lastTickActivePrayers = new ArrayList<>();
	private int lastTickPrayerPoints = 0;
	private final java.util.Set<String> manuallyDeactivatedPrayers = new java.util.HashSet<>();

	// Mutually exclusive prayer groups (only one can be active at a time)
	// All overhead prayers are mutually exclusive (protection, Retribution, Redemption, Smite)
	private static final java.util.Set<String> OVERHEAD_PRAYERS = java.util.Set.of(
		"Protect From Melee", "Protect From Missiles", "Protect From Magic",
		"Retribution", "Redemption", "Smite"
	);

	// Defence boosting prayers (conflict with each other)
	private static final java.util.Set<String> DEFENCE_PRAYERS = java.util.Set.of(
		"Thick Skin", "Rock Skin", "Steel Skin", "Chivalry", "Piety", "Rigour", "Augury"
	);

	// Attack boosting prayers (conflict with Chivalry/Piety)
	private static final java.util.Set<String> ATTACK_PRAYERS = java.util.Set.of(
		"Clarity of Thought", "Improved Reflexes", "Incredible Reflexes", "Chivalry", "Piety"
	);

	// Strength boosting prayers (conflict with Chivalry/Piety)
	private static final java.util.Set<String> STRENGTH_PRAYERS = java.util.Set.of(
		"Burst of Strength", "Superhuman Strength", "Ultimate Strength", "Chivalry", "Piety"
	);

	// Ranged boosting prayers (conflict with Rigour)
	private static final java.util.Set<String> RANGED_PRAYERS = java.util.Set.of(
		"Sharp Eye", "Hawk Eye", "Eagle Eye", "Rigour"
	);

	// Magic boosting prayers (conflict with Augury)
	private static final java.util.Set<String> MAGIC_PRAYERS = java.util.Set.of(
		"Mystic Will", "Mystic Lore", "Mystic Might", "Augury"
	);

	// All conflict groups for checking switches
	private static final java.util.List<java.util.Set<String>> PRAYER_CONFLICT_GROUPS = java.util.List.of(
		OVERHEAD_PRAYERS, DEFENCE_PRAYERS, ATTACK_PRAYERS, STRENGTH_PRAYERS, RANGED_PRAYERS, MAGIC_PRAYERS
	);

	// Track special attack energy for detecting spec usage
	private int lastSpecialAttackPercent = 100;

	// Track pending self-damage from potions (divine, overload, etc.)
	// This prevents double-counting when the hitsplat fires on the next tick
	private int pendingSelfDamage = 0;

	@Override
	protected void startUp() throws Exception
	{
		tickRecorder = new TickRecorder(config.ticksToRecord());
		recordingStorage = new RecordingStorage(config.maxDeathsToStore(), gson);

		// Create panel
		panel = new TickReplayLoggerPanel(recordingStorage, spriteManager, gson);
		panel.setTickRecorder(tickRecorder);
		panel.setLiveViewEnabled(config.enableLiveView());

		// Create navigation button with panel icon
		final BufferedImage icon = ImageUtil.loadImageResource(TickReplayLoggerPlugin.class, "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Tick Replay Logger")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		log.debug("Tick Replay Logger plugin started! Recording last {} ticks, storing {} deaths.",
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
		log.debug("Tick Replay Logger plugin stopped!");
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
				log.debug("Player logged in - tick recorder reset");
			}
			lastAttackerName = "Unknown";
			lastPlayerPosition = null;
			lastTickActivePrayers.clear();
			lastTickPrayerPoints = 0;
			manuallyDeactivatedPrayers.clear();
			lastSpecialAttackPercent = 100;
			pendingSelfDamage = 0;
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
			pendingSelfDamage = 0;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("tickreplaylogger"))
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
			if (recordingStorage != null)
			{
				recordingStorage.setMaxDeaths(newMax);
				// Refresh panel in case deaths were pruned
				if (panel != null)
				{
					panel.refresh();
				}
			}
		}
		else if (event.getKey().equals("enableLiveView") && panel != null)
		{
			panel.setLiveViewEnabled(config.enableLiveView());
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
		log.debug("PLAYER DIED! Recording death trace...");

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
		int totalDamage = TickRecording.calculateTotalDamage(tickHistory);

		// Create death record
		TickRecording death = TickRecording.builder()
			.timestamp(System.currentTimeMillis())
			.killerName(killer)
			.location(locationStr)
			.ticks(new ArrayList<>(tickHistory))
			.finalHp(0)
			.totalDamageReceived(totalDamage)
			.build();

		// Save to storage
		recordingStorage.addDeath(death);

		// Refresh the panel to show the new death
		if (panel != null)
		{
			panel.refresh();
		}

		log.debug("Death recorded: Killed by {} at {}. {} ticks captured, {} total damage received.",
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

		// Damage received by the player - check for player-targeted hitsplat types
		// isMine() alone doesn't catch environmental damage (acid pools, poison, etc.)
		boolean isPlayerDamageType = hitsplatType == HitsplatID.DAMAGE_ME ||
			hitsplatType == HitsplatID.DAMAGE_ME_CYAN ||
			hitsplatType == HitsplatID.DAMAGE_ME_ORANGE ||
			hitsplatType == HitsplatID.DAMAGE_ME_YELLOW ||
			hitsplatType == HitsplatID.DAMAGE_ME_WHITE ||
			hitsplatType == HitsplatID.BLOCK_ME ||
			hitsplatType == HitsplatID.POISON ||
			hitsplatType == HitsplatID.VENOM ||
			hitsplatType == HitsplatID.DISEASE;

		if (actor == localPlayer && isPlayerDamageType)
		{
			if (!config.trackDamageReceived())
			{
				return;
			}

			// Check if this is a pending self-damage hitsplat (from divine/overload potions)
			// We already recorded this damage on the consume tick, so skip the duplicate
			if (pendingSelfDamage > 0 && damage == pendingSelfDamage)
			{
				log.debug("SKIP DUPLICATE: Self-damage {} already recorded on consume tick", damage);
				pendingSelfDamage = 0;
				return;
			}

			DamageEvent.DamageType damageType = determineDamageType(hitsplat);
			String source = determineDamageSource();

			// For poison/venom/disease, set appropriate source
			if (hitsplatType == HitsplatID.POISON)
			{
				source = "Poison";
			}
			else if (hitsplatType == HitsplatID.VENOM)
			{
				source = "Venom";
			}
			else if (hitsplatType == HitsplatID.DISEASE)
			{
				source = "Disease";
			}

			DamageEvent damageEvent = DamageEvent.received(damage, damageType, source);
			tickRecorder.addDamageEvent(damageEvent);

			log.debug("DAMAGE: Received {} {} from {}", damage, damageType, source);
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

			log.debug("DAMAGE: Dealt {} to {}", damage, targetName);
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
					// Check if this prayer was switched to another in the same mutually exclusive group
					// (e.g., switching from Protect from Melee to Protect from Missiles)
					boolean wasSwitched = isPrayerSwitch(prayer, activePrayers);

					// Skip logging if it was just a prayer switch (not a drain/disable)
					if (wasSwitched)
					{
						log.debug("Prayer switch detected: {} -> conflicting prayer", prayer);
						continue;
					}

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
					log.debug("PRAYER DRAINED: {} ({})", prayer, reason);
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
			log.debug("Tick {}: HP {}/{}, Prayer {}/{}, Active prayers: {}",
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
			log.debug("ACTION: Ate {}", cleanTarget);
		}
		// Drinking potions
		else if (option.equals("Drink") && config.trackFood())
		{
			action = PlayerAction.drank(cleanTarget);
			log.debug("ACTION: Drank {}", cleanTarget);

			// Handle self-damage potions - damage is immediate but hitsplat fires next tick
			// We capture it here so the damage appears on the same tick as the consume action
			if (config.trackDamageReceived())
			{
				int selfDamage = getSelfDamagePotionAmount(cleanTarget);
				if (selfDamage > 0)
				{
					DamageEvent selfDmgEvent = DamageEvent.received(selfDamage, DamageEvent.DamageType.TYPELESS, cleanTarget);
					tickRecorder.addDamageEvent(selfDmgEvent);
					// Mark this damage as pending so we skip the hitsplat when it fires
					pendingSelfDamage = selfDamage;
					log.debug("SELF-DAMAGE: {} from {}", selfDamage, cleanTarget);
				}
			}
		}
		// Activating prayers
		else if (option.equals("Activate") && config.trackPrayers())
		{
			action = PlayerAction.prayerOn(cleanTarget);
			log.debug("ACTION: Activated {}", cleanTarget);
		}
		// Deactivating prayers
		else if (option.equals("Deactivate"))
		{
			manuallyDeactivatedPrayers.add(cleanTarget); // Always track for drain detection
			if (config.trackPrayers())
			{
				action = PlayerAction.prayerOff(cleanTarget);
				log.debug("ACTION: Deactivated {}", cleanTarget);
			}
		}
		// Equipping gear
		else if ((option.equals("Wear") || option.equals("Wield") || option.equals("Equip")) && config.trackGear())
		{
			action = PlayerAction.equipped(cleanTarget);
			log.debug("ACTION: Equipped {}", cleanTarget);
		}
		// Unequipping gear
		else if (option.equals("Remove") && config.trackGear())
		{
			action = PlayerAction.unequipped(cleanTarget);
			log.debug("ACTION: Unequipped {}", cleanTarget);
		}
		// Attacking
		else if (option.equals("Attack") && config.trackAttacks())
		{
			// Get current weapon name
			String weapon = getCurrentWeaponName();
			action = PlayerAction.attacked(cleanTarget, weapon);
			log.debug("ACTION: Attacked {} with {}", cleanTarget, weapon);
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
			log.debug("ACTION: Special attack with {}", weaponName);
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
	 * Check if a prayer was switched to another in a conflicting group.
	 * E.g., switching from Protect from Melee to Protect from Missiles.
	 *
	 * @param oldPrayer The prayer that was deactivated
	 * @param activePrayers The currently active prayers
	 * @return true if the prayer was switched (not drained/disabled)
	 */
	private boolean isPrayerSwitch(String oldPrayer, List<String> activePrayers)
	{
		// Check each conflict group
		for (java.util.Set<String> group : PRAYER_CONFLICT_GROUPS)
		{
			if (group.contains(oldPrayer))
			{
				// Old prayer is in this group - check if another prayer from the same group is now active
				for (String activePrayer : activePrayers)
				{
					if (group.contains(activePrayer) && !activePrayer.equals(oldPrayer))
					{
						return true; // Found a conflicting prayer that's now active
					}
				}
			}
		}
		return false;
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
	 * Get the self-damage amount for potions that hurt the player when consumed.
	 * Returns 0 if the potion doesn't cause self-damage.
	 *
	 * @param potionName The name of the potion (cleaned of color tags)
	 * @return The damage amount, or 0 if no self-damage
	 */
	private int getSelfDamagePotionAmount(String potionName)
	{
		String lower = potionName.toLowerCase();

		// Divine potions - all deal 10 damage on consumption
		// Includes: Divine super combat, Divine bastion, Divine battlemage,
		// Divine magic, Divine ranging, Divine super attack/defence/strength
		if (lower.startsWith("divine"))
		{
			return 10;
		}

		// Overload variants - deal damage based on current HP (10% per hit, 5 hits)
		// First hit is immediate, we capture just that first hit here
		// The subsequent hits will be captured via normal hitsplat tracking
		if (lower.contains("overload"))
		{
			// Standard overload, Overload (+), etc.
			// First hit is 10% of current HP, minimum 1
			int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
			return Math.max(1, currentHp / 10);
		}

		// Smelling salts (ToA) - similar to overload, deals damage on use
		if (lower.contains("smelling salts"))
		{
			int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
			return Math.max(1, currentHp / 10);
		}

		// Liquid adrenaline - drains 5 prayer and 10% of current HP
		if (lower.contains("liquid adrenaline"))
		{
			int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
			return Math.max(1, currentHp / 10);
		}

		// No self-damage
		return 0;
	}

	/**
	 * Get the recording storage for UI access
	 */
	public RecordingStorage getRecordingStorage()
	{
		return recordingStorage;
	}

	@Provides
	TickReplayLoggerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TickReplayLoggerConfig.class);
	}
}
