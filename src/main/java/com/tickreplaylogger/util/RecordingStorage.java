package com.tickreplaylogger.util;

import com.tickreplaylogger.model.TickRecording;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence of tick recordings to JSON files.
 * Deaths are stored in ~/.runelite/tick-replay-logger/deaths.json
 * Saved recordings are stored in ~/.runelite/tick-replay-logger/saved.json
 */
@Slf4j
public class RecordingStorage
{
	private static final String FOLDER_NAME = "tick-replay-logger";
	private static final String DEATHS_FILE_NAME = "deaths.json";
	private static final String SAVED_FILE_NAME = "saved.json";

	private final Gson gson;
	private final File deathsFile;
	private final File savedFile;
	private int maxDeaths;

	private List<TickRecording> deaths;
	private List<TickRecording> savedRecordings;

	public RecordingStorage(int maxDeaths)
	{
		this.maxDeaths = maxDeaths;
		this.gson = new GsonBuilder()
			.setPrettyPrinting()
			.create();

		// Create storage directory
		File folder = new File(RuneLite.RUNELITE_DIR, FOLDER_NAME);
		if (!folder.exists())
		{
			folder.mkdirs();
		}

		this.deathsFile = new File(folder, DEATHS_FILE_NAME);
		this.savedFile = new File(folder, SAVED_FILE_NAME);
		this.deaths = new ArrayList<>();
		this.savedRecordings = new ArrayList<>();

		// Load existing data
		loadDeaths();
		loadSavedRecordings();
	}

	/**
	 * Load deaths from JSON file
	 */
	public void loadDeaths()
	{
		if (!deathsFile.exists())
		{
			log.debug("No existing deaths file found");
			deaths = new ArrayList<>();
			return;
		}

		try (FileReader reader = new FileReader(deathsFile))
		{
			Type listType = new TypeToken<ArrayList<TickRecording>>(){}.getType();
			List<TickRecording> loaded = gson.fromJson(reader, listType);

			if (loaded != null)
			{
				deaths = loaded;
				log.debug("Loaded {} death records from file", deaths.size());
			}
			else
			{
				deaths = new ArrayList<>();
			}
		}
		catch (IOException e)
		{
			log.error("Failed to load deaths from file", e);
			deaths = new ArrayList<>();
		}
		catch (Exception e)
		{
			log.error("Failed to parse deaths file", e);
			deaths = new ArrayList<>();
		}
	}

	/**
	 * Load saved recordings from JSON file
	 */
	public void loadSavedRecordings()
	{
		if (!savedFile.exists())
		{
			log.debug("No existing saved recordings file found");
			savedRecordings = new ArrayList<>();
			return;
		}

		try (FileReader reader = new FileReader(savedFile))
		{
			Type listType = new TypeToken<ArrayList<TickRecording>>(){}.getType();
			List<TickRecording> loaded = gson.fromJson(reader, listType);

			if (loaded != null)
			{
				savedRecordings = loaded;
				log.debug("Loaded {} saved recordings from file", savedRecordings.size());
			}
			else
			{
				savedRecordings = new ArrayList<>();
			}
		}
		catch (IOException e)
		{
			log.error("Failed to load saved recordings from file", e);
			savedRecordings = new ArrayList<>();
		}
		catch (Exception e)
		{
			log.error("Failed to parse saved recordings file", e);
			savedRecordings = new ArrayList<>();
		}
	}

	/**
	 * Save deaths to JSON file
	 */
	public void saveDeaths()
	{
		try (FileWriter writer = new FileWriter(deathsFile))
		{
			gson.toJson(deaths, writer);
			log.debug("Saved {} death records to file", deaths.size());
		}
		catch (IOException e)
		{
			log.error("Failed to save deaths to file", e);
		}
	}

	/**
	 * Save saved recordings to JSON file
	 */
	public void saveSavedRecordings()
	{
		try (FileWriter writer = new FileWriter(savedFile))
		{
			gson.toJson(savedRecordings, writer);
			log.debug("Saved {} saved recordings to file", savedRecordings.size());
		}
		catch (IOException e)
		{
			log.error("Failed to save saved recordings to file", e);
		}
	}

	/**
	 * Add a new death record.
	 * Prunes old deaths if we exceed maxDeaths.
	 */
	public void addDeath(TickRecording death)
	{
		// Add to beginning of list (newest first)
		deaths.add(0, death);

		// Prune if we exceed max
		while (deaths.size() > maxDeaths)
		{
			deaths.remove(deaths.size() - 1);
		}

		// Save to file
		saveDeaths();

		log.debug("Death recorded: killed by {} ({} total deaths stored)",
			death.getKillerName(), deaths.size());
	}

	/**
	 * Get all stored death records (newest first)
	 */
	public List<TickRecording> getDeaths()
	{
		return new ArrayList<>(deaths);
	}

	/**
	 * Get the number of stored deaths
	 */
	public int getDeathCount()
	{
		return deaths.size();
	}

	/**
	 * Set the maximum number of deaths to store.
	 * Prunes oldest deaths if current count exceeds new max.
	 */
	public void setMaxDeaths(int newMax)
	{
		if (newMax == maxDeaths)
		{
			return;
		}

		maxDeaths = newMax;

		// Prune if we now exceed the limit
		boolean pruned = false;
		while (deaths.size() > maxDeaths)
		{
			deaths.remove(deaths.size() - 1);
			pruned = true;
		}

		if (pruned)
		{
			saveDeaths();
			log.debug("Death storage max changed to {}, pruned to {} deaths", maxDeaths, deaths.size());
		}
		else
		{
			log.debug("Death storage max changed to {}", maxDeaths);
		}
	}

	/**
	 * Clear all stored deaths
	 */
	public void clearDeaths()
	{
		deaths.clear();
		saveDeaths();
		log.debug("All death records cleared");
	}

	/**
	 * Delete a specific death record
	 */
	public void deleteDeath(TickRecording death)
	{
		if (deaths.remove(death))
		{
			saveDeaths();
			log.debug("Death record deleted: killed by {}", death.getKillerName());
		}
	}

	/**
	 * Delete a death by index
	 */
	public void deleteDeath(int index)
	{
		if (index >= 0 && index < deaths.size())
		{
			TickRecording removed = deaths.remove(index);
			saveDeaths();
			log.debug("Death record deleted: killed by {}", removed.getKillerName());
		}
	}

	/**
	 * Export all deaths as JSON string (for clipboard)
	 */
	public String exportAllAsJson()
	{
		return gson.toJson(deaths);
	}

	/**
	 * Export a single death as JSON string (for clipboard)
	 */
	public String exportRecordingAsJson(TickRecording recording)
	{
		return gson.toJson(recording);
	}

	/**
	 * Export a single death by index as JSON string
	 */
	public String exportDeathAsJson(int index)
	{
		if (index >= 0 && index < deaths.size())
		{
			return gson.toJson(deaths.get(index));
		}
		return "{}";
	}

	// ===== SAVED RECORDINGS =====

	/**
	 * Get all saved recordings (newest first)
	 */
	public List<TickRecording> getSavedRecordings()
	{
		return new ArrayList<>(savedRecordings);
	}

	/**
	 * Get the number of saved recordings
	 */
	public int getSavedCount()
	{
		return savedRecordings.size();
	}

	/**
	 * Save a death - moves it from deaths list to saved recordings (no auto-delete)
	 */
	public void saveRecording(TickRecording death)
	{
		if (deaths.remove(death))
		{
			savedRecordings.add(0, death);
			saveDeaths();
			saveSavedRecordings();
			log.debug("Death saved to permanent recordings: {}", death.getKillerName());
		}
	}

	/**
	 * Add a new saved recording directly (e.g., from live view snapshot)
	 * No limit on saved recordings.
	 */
	public void addSavedRecording(TickRecording recording)
	{
		savedRecordings.add(0, recording);
		saveSavedRecordings();
		log.debug("Recording saved: {}", recording.getKillerName());
	}

	/**
	 * Rename a recording (applies to both recent deaths and saved recordings).
	 */
	public void renameRecording(TickRecording recording, String newName)
	{
		if (recording == null || newName == null)
		{
			return;
		}
		String trimmed = newName.trim();
		if (trimmed.isEmpty())
		{
			return;
		}

		recording.setKillerName(trimmed);
		saveDeaths();
		saveSavedRecordings();
		log.debug("Recording renamed to {}", trimmed);
	}

	/**
	 * Delete a specific saved recording
	 */
	public void deleteSavedRecording(TickRecording recording)
	{
		if (savedRecordings.remove(recording))
		{
			saveSavedRecordings();
			log.debug("Saved recording deleted: {}", recording.getKillerName());
		}
	}

	/**
	 * Clear all saved recordings
	 */
	public void clearSavedRecordings()
	{
		savedRecordings.clear();
		saveSavedRecordings();
		log.debug("All saved recordings cleared");
	}

	/**
	 * Export a saved recording as JSON string
	 */
	public String exportSavedRecordingAsJson(TickRecording recording)
	{
		return gson.toJson(recording);
	}
}
