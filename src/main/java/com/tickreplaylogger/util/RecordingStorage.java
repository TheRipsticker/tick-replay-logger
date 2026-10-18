package com.tickreplaylogger.util;

import com.tickreplaylogger.model.TickRecording;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles persistence of tick recordings to JSON files.
 * Deaths are stored in ~/.runelite/tick-replay-logger/deaths.json
 * Saved recordings are stored in ~/.runelite/tick-replay-logger/saved.json
 * Hotkey snapshots are stored one per file in ~/.runelite/tick-replay-logger/snapshots/
 */
@Slf4j
public class RecordingStorage
{
	private static final String FOLDER_NAME = "tick-replay-logger";
	private static final String DEATHS_FILE_NAME = "deaths.json";
	private static final String SAVED_FILE_NAME = "saved.json";
	private static final String SNAPSHOTS_FOLDER_NAME = "snapshots";
	private static final String SNAPSHOT_FILE_PREFIX = "hotkey-snapshot_";
	private static final DateTimeFormatter SNAPSHOT_FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	private final File snapshotsFolder;

	// Recordings that live in their own file (hotkey snapshots) -> that file.
	// Identity map on purpose: TickRecording uses Lombok @Data, so equals/hashCode
	// are value-based and change when a recording is renamed.
	private final Map<TickRecording, File> snapshotFiles = new IdentityHashMap<>();

	private final Gson gson;
	private final File deathsFile;
	private final File savedFile;
	private int maxDeaths;

	private List<TickRecording> deaths;
	private List<TickRecording> savedRecordings;

	public RecordingStorage(int maxDeaths, Gson gson)
	{
		this.maxDeaths = maxDeaths;
		this.gson = gson.newBuilder()
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
		this.snapshotsFolder = new File(folder, SNAPSHOTS_FOLDER_NAME);
		this.deaths = new ArrayList<>();
		this.savedRecordings = new ArrayList<>();

		// Load existing data
		loadDeaths();
		loadSavedRecordings();
		loadSnapshotFiles();
	}

	/**
	 * Load every hotkey snapshot file from the snapshots folder and merge them
	 * into the saved recordings list (newest first).
	 */
	private void loadSnapshotFiles()
	{
		snapshotFiles.clear();

		File[] files = snapshotsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
		if (files == null || files.length == 0)
		{
			return;
		}

		int loadedCount = 0;
		for (File file : files)
		{
			try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
			{
				TickRecording recording = gson.fromJson(reader, TickRecording.class);
				if (recording != null && recording.getTicks() != null)
				{
					savedRecordings.add(recording);
					snapshotFiles.put(recording, file);
					loadedCount++;
				}
			}
			catch (Exception e)
			{
				// Leave unreadable files alone on disk; never overwrite or delete them here
				log.warn("Skipping unreadable snapshot file {}", file.getName(), e);
			}
		}

		savedRecordings.sort(Comparator.comparingLong(TickRecording::getTimestamp).reversed());
		log.debug("Loaded {} hotkey snapshot files", loadedCount);
	}

	/**
	 * Save a recording to its own timestamped file in the snapshots folder
	 * and add it to the saved recordings list. Used by the snapshot hotkey.
	 *
	 * @return the file written, or null if the write failed
	 */
	public File addSnapshotFile(TickRecording recording)
	{
		if (!snapshotsFolder.exists() && !snapshotsFolder.mkdirs())
		{
			log.error("Could not create snapshots folder {}", snapshotsFolder);
			return null;
		}

		String stamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(recording.getTimestamp()), ZoneId.systemDefault())
			.format(SNAPSHOT_FILE_FORMAT);

		// Avoid collisions if the hotkey is pressed twice in the same second
		File file = new File(snapshotsFolder, SNAPSHOT_FILE_PREFIX + stamp + ".json");
		int suffix = 2;
		while (file.exists())
		{
			file = new File(snapshotsFolder, SNAPSHOT_FILE_PREFIX + stamp + "_" + suffix + ".json");
			suffix++;
		}

		if (!writeSnapshotFile(recording, file))
		{
			return null;
		}

		savedRecordings.add(0, recording);
		snapshotFiles.put(recording, file);
		log.debug("Hotkey snapshot written to {}", file.getAbsolutePath());
		return file;
	}

	/**
	 * Write one recording to a file. Writes to a temp file first, then moves it into
	 * place, so a crash mid-write can't leave a half-written snapshot.
	 */
	private boolean writeSnapshotFile(TickRecording recording, File target)
	{
		Path targetPath = target.toPath();
		Path tempPath = targetPath.resolveSibling(target.getName() + ".tmp");

		try
		{
			try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8))
			{
				gson.toJson(recording, writer);
			}

			try
			{
				Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		}
		catch (IOException e)
		{
			log.error("Failed to write snapshot file {}", target.getName(), e);
			try
			{
				Files.deleteIfExists(tempPath);
			}
			catch (IOException ignored)
			{
			}
			return false;
		}
	}

	/**
	 * Saved recordings that belong in saved.json (everything except per-file hotkey snapshots).
	 */
	private List<TickRecording> getSavedJsonRecordings()
	{
		List<TickRecording> result = new ArrayList<>();
		for (TickRecording recording : savedRecordings)
		{
			if (!snapshotFiles.containsKey(recording))
			{
				result.add(recording);
			}
		}
		return result;
	}

	/**
	 * Remove a recording from the saved list by identity (not equals()).
	 */
	private boolean removeSavedByIdentity(TickRecording recording)
	{
		for (int i = 0; i < savedRecordings.size(); i++)
		{
			if (savedRecordings.get(i) == recording)
			{
				savedRecordings.remove(i);
				return true;
			}
		}
		return false;
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
			List<TickRecording> toWrite = getSavedJsonRecordings();
			gson.toJson(toWrite, writer);
			log.debug("Saved {} saved recordings to file", toWrite.size());
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

		File snapshotFile = snapshotFiles.get(recording);
		if (snapshotFile != null)
		{
			// Hotkey snapshot: rewrite its own file (filename keeps the original timestamp)
			writeSnapshotFile(recording, snapshotFile);
		}
		else
		{
			saveDeaths();
			saveSavedRecordings();
		}
		log.debug("Recording renamed to {}", trimmed);
	}

	/**
	 * Delete a specific saved recording
	 */
	public void deleteSavedRecording(TickRecording recording)
	{
		if (!removeSavedByIdentity(recording))
		{
			return;
		}

		File snapshotFile = snapshotFiles.remove(recording);
		if (snapshotFile != null)
		{
			// Hotkey snapshot: delete its file so it doesn't come back on next launch
			if (!snapshotFile.delete() && snapshotFile.exists())
			{
				log.warn("Could not delete snapshot file {}", snapshotFile.getName());
			}
		}
		else
		{
			saveSavedRecordings();
		}
		log.debug("Saved recording deleted: {}", recording.getKillerName());
	}

	/**
	 * Clear all saved recordings in saved.json.
	 * Hotkey snapshot files are left on disk and stay in the list.
	 */
	public void clearSavedRecordings()
	{
		savedRecordings.removeIf(r -> !snapshotFiles.containsKey(r));
		saveSavedRecordings();
		log.debug("All saved.json recordings cleared");
	}

	/**
	 * Export a saved recording as JSON string
	 */
	public String exportSavedRecordingAsJson(TickRecording recording)
	{
		return gson.toJson(recording);
	}
}
