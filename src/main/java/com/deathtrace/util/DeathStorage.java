package com.deathtrace.util;

import com.deathtrace.model.DeathRecord;
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
 * Handles persistence of death records to JSON file.
 * Deaths are stored in ~/.runelite/death-trace/deaths.json
 */
@Slf4j
public class DeathStorage
{
	private static final String FOLDER_NAME = "death-trace";
	private static final String FILE_NAME = "deaths.json";

	private final Gson gson;
	private final File storageFile;
	private int maxDeaths;

	private List<DeathRecord> deaths;

	public DeathStorage(int maxDeaths)
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

		this.storageFile = new File(folder, FILE_NAME);
		this.deaths = new ArrayList<>();

		// Load existing deaths
		loadDeaths();
	}

	/**
	 * Load deaths from JSON file
	 */
	public void loadDeaths()
	{
		if (!storageFile.exists())
		{
			log.debug("No existing deaths file found");
			deaths = new ArrayList<>();
			return;
		}

		try (FileReader reader = new FileReader(storageFile))
		{
			Type listType = new TypeToken<ArrayList<DeathRecord>>(){}.getType();
			List<DeathRecord> loaded = gson.fromJson(reader, listType);

			if (loaded != null)
			{
				deaths = loaded;
				log.info("Loaded {} death records from file", deaths.size());
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
	 * Save deaths to JSON file
	 */
	public void saveDeaths()
	{
		try (FileWriter writer = new FileWriter(storageFile))
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
	 * Add a new death record.
	 * Prunes old deaths if we exceed maxDeaths.
	 */
	public void addDeath(DeathRecord death)
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

		log.info("Death recorded: killed by {} ({} total deaths stored)",
			death.getKillerName(), deaths.size());
	}

	/**
	 * Get all stored death records (newest first)
	 */
	public List<DeathRecord> getDeaths()
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
			log.info("Death storage max changed to {}, pruned to {} deaths", maxDeaths, deaths.size());
		}
		else
		{
			log.info("Death storage max changed to {}", maxDeaths);
		}
	}

	/**
	 * Clear all stored deaths
	 */
	public void clearDeaths()
	{
		deaths.clear();
		saveDeaths();
		log.info("All death records cleared");
	}

	/**
	 * Delete a specific death record
	 */
	public void deleteDeath(DeathRecord death)
	{
		if (deaths.remove(death))
		{
			saveDeaths();
			log.info("Death record deleted: killed by {}", death.getKillerName());
		}
	}

	/**
	 * Delete a death by index
	 */
	public void deleteDeath(int index)
	{
		if (index >= 0 && index < deaths.size())
		{
			DeathRecord removed = deaths.remove(index);
			saveDeaths();
			log.info("Death record deleted: killed by {}", removed.getKillerName());
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
	public String exportDeathAsJson(DeathRecord death)
	{
		return gson.toJson(death);
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
}
