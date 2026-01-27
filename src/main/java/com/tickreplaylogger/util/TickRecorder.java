package com.tickreplaylogger.util;

import com.tickreplaylogger.model.DamageEvent;
import com.tickreplaylogger.model.MovementData;
import com.tickreplaylogger.model.PlayerAction;
import com.tickreplaylogger.model.TickData;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Circular buffer that records tick data.
 * Maintains only the last N ticks (default 50) to bound memory usage.
 * When the buffer is full, oldest ticks are overwritten.
 */
@Slf4j
public class TickRecorder
{
	private TickData[] buffer;
	private int capacity;
	private int head = 0;           // Next write position
	private int count = 0;          // Number of ticks stored
	private int currentTickNumber = 0;

	// Current tick being built (actions accumulate here until tick ends)
	private TickData currentTick;

	public TickRecorder(int capacity)
	{
		this.capacity = capacity;
		this.buffer = new TickData[capacity];
		log.debug("TickRecorder initialized with capacity: {}", capacity);
	}

	/**
	 * Resize the buffer capacity. Preserves existing data up to the new capacity.
	 */
	public void resize(int newCapacity)
	{
		if (newCapacity == capacity)
		{
			return;
		}

		// Get current history in order
		List<TickData> history = getTickHistory();

		// Create new buffer
		capacity = newCapacity;
		buffer = new TickData[newCapacity];
		head = 0;
		count = 0;

		// Copy over as many ticks as will fit (most recent ones)
		int startIndex = Math.max(0, history.size() - newCapacity);
		for (int i = startIndex; i < history.size(); i++)
		{
			buffer[head] = history.get(i);
			head = (head + 1) % capacity;
			count++;
		}

		log.info("TickRecorder resized to capacity: {} ({} ticks preserved)", newCapacity, count);
	}

	/**
	 * Start a new tick. Call this at the beginning of each GameTick.
	 */
	public void startNewTick()
	{
		currentTickNumber++;
		currentTick = TickData.builder()
			.tickNumber(currentTickNumber)
			.timestamp(System.currentTimeMillis())
			.playerActions(new ArrayList<>())
			.damageEvents(new ArrayList<>())
			.activePrayers(new ArrayList<>())
			.build();
	}

	/**
	 * Finalize the current tick and add it to the buffer.
	 * Call this at the end of each GameTick after state snapshot.
	 */
	public void finalizeTick()
	{
		if (currentTick == null)
		{
			log.warn("finalizeTick called but no current tick exists");
			return;
		}

		// Add to circular buffer
		buffer[head] = currentTick;
		head = (head + 1) % capacity;
		if (count < capacity)
		{
			count++;
		}

		log.debug("Tick {} recorded. HP: {}/{}, Prayer: {}/{}",
			currentTick.getTickNumber(),
			currentTick.getCurrentHp(),
			currentTick.getMaxHp(),
			currentTick.getCurrentPrayer(),
			currentTick.getMaxPrayer());
	}

	/**
	 * Update the current tick's state snapshot.
	 */
	public void updateState(int currentHp, int maxHp, int currentPrayer, int maxPrayer, List<String> activePrayers)
	{
		if (currentTick == null)
		{
			return;
		}
		currentTick.setCurrentHp(currentHp);
		currentTick.setMaxHp(maxHp);
		currentTick.setCurrentPrayer(currentPrayer);
		currentTick.setMaxPrayer(maxPrayer);
		currentTick.setActivePrayers(activePrayers != null ? new ArrayList<>(activePrayers) : new ArrayList<>());
	}

	/**
	 * Add a player action to the current tick.
	 */
	public void addPlayerAction(PlayerAction action)
	{
		if (currentTick == null)
		{
			log.warn("Cannot add player action - no current tick");
			return;
		}
		currentTick.addPlayerAction(action);
		log.debug("Player action added: {}", action.getDescription());
	}

	/**
	 * Add a damage event to the current tick.
	 */
	public void addDamageEvent(DamageEvent event)
	{
		if (currentTick == null)
		{
			log.warn("Cannot add damage event - no current tick");
			return;
		}
		currentTick.addDamageEvent(event);
		log.debug("Damage event added: {}", event.getDescription());
	}

	/**
	 * Set movement data for the current tick.
	 */
	public void setMovement(MovementData movement)
	{
		if (currentTick == null)
		{
			return;
		}
		currentTick.setMovement(movement);
		if (movement != null && movement.hasMovement())
		{
			log.debug("Movement recorded: {}", movement.getDescription());
		}
	}

	/**
	 * Get all recorded ticks in chronological order (oldest first).
	 * Returns a copy of the data.
	 */
	public List<TickData> getTickHistory()
	{
		List<TickData> history = new ArrayList<>(count);

		if (count == 0)
		{
			return history;
		}

		// Calculate starting position (oldest tick)
		int start = (count < capacity) ? 0 : head;

		for (int i = 0; i < count; i++)
		{
			int index = (start + i) % capacity;
			if (buffer[index] != null)
			{
				history.add(buffer[index]);
			}
		}

		return history;
	}

	/**
	 * Get recorded ticks in reverse chronological order (newest first).
	 * This is the order we display in the UI.
	 */
	public List<TickData> getTickHistoryReversed()
	{
		List<TickData> history = getTickHistory();
		List<TickData> reversed = new ArrayList<>(history.size());
		for (int i = history.size() - 1; i >= 0; i--)
		{
			reversed.add(history.get(i));
		}
		return reversed;
	}

	/**
	 * Get the number of ticks currently stored.
	 */
	public int getTickCount()
	{
		return count;
	}

	/**
	 * Get the current tick number.
	 */
	public int getCurrentTickNumber()
	{
		return currentTickNumber;
	}

	/**
	 * Clear all recorded ticks.
	 */
	public void clear()
	{
		for (int i = 0; i < capacity; i++)
		{
			buffer[i] = null;
		}
		head = 0;
		count = 0;
		currentTick = null;
		log.debug("TickRecorder cleared");
	}

	/**
	 * Reset tick counter (e.g., on login).
	 */
	public void resetTickCounter()
	{
		currentTickNumber = 0;
		clear();
		log.debug("Tick counter reset");
	}
}
