package com.tickreplaylogger.ui;

import com.tickreplaylogger.model.TickRecording;
import com.tickreplaylogger.model.TickData;
import com.tickreplaylogger.ui.playback.PlaybackPanel;
import com.tickreplaylogger.util.RecordingStorage;
import com.tickreplaylogger.util.TickRecorder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main panel for Tick Replay Logger plugin.
 * Shows list of recent deaths and saved recordings with expandable tick timelines,
 * or a live timeline of the current tick buffer.
 */
public class TickReplayLoggerPanel extends PluginPanel
{
	private static final String RECORDINGS_VIEW = "recordings";
	private static final String LIVE_VIEW = "live";
	private static final String PLAYBACK_VIEW = "playback";

	private final RecordingStorage recordingStorage;
	private final SpriteManager spriteManager;
	private TickRecorder tickRecorder;

	// View switching
	private final CardLayout cardLayout;
	private final JPanel contentPanel;
	private final JButton toggleViewBtn;
	private String currentView = RECORDINGS_VIEW;
	private String previousView = RECORDINGS_VIEW; // For returning from playback

	// Recordings view components (deaths + saved)
	private final JPanel recordingsListContainer;
	private final JLabel noRecordingsLabel;

	// Live view components
	private final JPanel liveTimelineContainer;
	private final JLabel liveStatusLabel;
	private final JButton liveStartStopBtn;
	private final JButton liveSaveBtn;
	private boolean liveRecording = true; // Controls display updates
	private List<TickData> pausedSnapshot;

	// Playback view components
	private final PlaybackPanel playbackPanel;

	// Gson for JSON export
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public TickReplayLoggerPanel(RecordingStorage recordingStorage, SpriteManager spriteManager)
	{
		super(false);
		this.recordingStorage = recordingStorage;
		this.spriteManager = spriteManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Header with title and view toggle
		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel titleLabel = new JLabel("Tick Replay Logger");
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
		headerPanel.add(titleLabel, BorderLayout.WEST);

		// Toggle view button
		toggleViewBtn = new JButton("Live View");
		toggleViewBtn.setToolTipText("Switch to live timeline view");
		toggleViewBtn.addActionListener(e -> toggleView());
		headerPanel.add(toggleViewBtn, BorderLayout.EAST);

		add(headerPanel, BorderLayout.NORTH);

		// Content panel with CardLayout for view switching
		cardLayout = new CardLayout();
		contentPanel = new JPanel(cardLayout);
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// === Recordings View (Deaths + Saved) ===
		JPanel recordingsViewPanel = new JPanel(new BorderLayout());
		recordingsViewPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Recordings list container
		recordingsListContainer = new JPanel(new GridBagLayout())
		{
			@Override
			public Dimension getPreferredSize()
			{
				Dimension d = super.getPreferredSize();
				Container parent = getParent();
				if (parent instanceof JViewport)
				{
					d.width = parent.getWidth();
				}
				return d;
			}
		};
		recordingsListContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		noRecordingsLabel = new JLabel("No recordings yet");
		noRecordingsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		noRecordingsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		noRecordingsLabel.setBorder(new EmptyBorder(20, 10, 20, 10));

		JScrollPane recordingsScrollPane = new JScrollPane(recordingsListContainer);
		recordingsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		recordingsScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		recordingsScrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		recordingsScrollPane.setBorder(null);

		recordingsViewPanel.add(recordingsScrollPane, BorderLayout.CENTER);

		// === Live View ===
		JPanel liveViewPanel = new JPanel(new BorderLayout());
		liveViewPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Live header with status and buttons on separate rows
		JPanel liveHeader = new JPanel();
		liveHeader.setLayout(new BoxLayout(liveHeader, BoxLayout.Y_AXIS));
		liveHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		liveHeader.setBorder(new EmptyBorder(6, 10, 6, 10));

		// Row 1: Buttons
		JPanel liveButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		liveButtonsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		liveButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		liveStartStopBtn = new JButton("Pause");
		liveStartStopBtn.setMargin(new Insets(2, 6, 2, 6));
		liveStartStopBtn.setToolTipText("Pause/resume live updates");
		liveStartStopBtn.addActionListener(e -> toggleLiveUpdates());
		liveButtonsPanel.add(liveStartStopBtn);

		JButton liveExportBtn = new JButton("Export");
		liveExportBtn.setMargin(new Insets(2, 6, 2, 6));
		liveExportBtn.setToolTipText("Copy current tick buffer to clipboard");
		liveExportBtn.addActionListener(e -> exportLiveBuffer());
		liveButtonsPanel.add(liveExportBtn);

		liveSaveBtn = new JButton("★ Save");
		liveSaveBtn.setMargin(new Insets(2, 6, 2, 6));
		liveSaveBtn.setToolTipText("Save current tick buffer as a recording");
		liveSaveBtn.setBackground(new Color(50, 80, 50));
		liveSaveBtn.setForeground(new Color(150, 200, 150));
		liveSaveBtn.setEnabled(false);
		liveSaveBtn.addActionListener(e -> saveLiveBuffer());
		liveButtonsPanel.add(liveSaveBtn);

		liveHeader.add(liveButtonsPanel);

		// Row 2: Status
		liveStatusLabel = new JLabel("Recording: Active");
		liveStatusLabel.setForeground(new Color(80, 200, 80));
		liveStatusLabel.setFont(liveStatusLabel.getFont().deriveFont(Font.BOLD, 11f));
		liveStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		liveHeader.add(liveStatusLabel);

		liveViewPanel.add(liveHeader, BorderLayout.NORTH);

		// Live timeline container
		liveTimelineContainer = new JPanel(new GridBagLayout())
		{
			@Override
			public Dimension getPreferredSize()
			{
				Dimension d = super.getPreferredSize();
				Container parent = getParent();
				if (parent instanceof JViewport)
				{
					d.width = parent.getWidth();
				}
				return d;
			}
		};
		liveTimelineContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane liveScrollPane = new JScrollPane(liveTimelineContainer);
		liveScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		liveScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		liveScrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		liveScrollPane.setBorder(null);

		liveViewPanel.add(liveScrollPane, BorderLayout.CENTER);

		// === Playback View ===
		playbackPanel = new PlaybackPanel(this::returnFromPlayback, spriteManager);

		// Add views to card layout
		contentPanel.add(recordingsViewPanel, RECORDINGS_VIEW);
		contentPanel.add(liveViewPanel, LIVE_VIEW);
		contentPanel.add(playbackPanel, PLAYBACK_VIEW);

		add(contentPanel, BorderLayout.CENTER);

		// Initial refresh
		refreshRecordingsList();
	}

	/**
	 * Set the tick recorder for live view updates.
	 */
	public void setTickRecorder(TickRecorder tickRecorder)
	{
		this.tickRecorder = tickRecorder;
	}

	/**
	 * Toggle between recordings view and live view.
	 */
	private void toggleView()
	{
		if (currentView.equals(RECORDINGS_VIEW))
		{
			currentView = LIVE_VIEW;
			toggleViewBtn.setText("Recordings");
			toggleViewBtn.setToolTipText("Switch to recordings list view");
			cardLayout.show(contentPanel, LIVE_VIEW);
			refreshLiveTimeline();
		}
		else if (currentView.equals(LIVE_VIEW))
		{
			currentView = RECORDINGS_VIEW;
			toggleViewBtn.setText("Live View");
			toggleViewBtn.setToolTipText("Switch to live timeline view");
			cardLayout.show(contentPanel, RECORDINGS_VIEW);
		}
		// If in playback view, toggle goes to recordings view
		else if (currentView.equals(PLAYBACK_VIEW))
		{
			returnFromPlayback();
		}
	}

	/**
	 * Show the playback view for a recording.
	 */
	private void showPlayback(TickRecording recording)
	{
		previousView = currentView;
		currentView = PLAYBACK_VIEW;
		toggleViewBtn.setText("Recordings");
		toggleViewBtn.setToolTipText("Return to recordings list");
		playbackPanel.loadRecording(recording);
		cardLayout.show(contentPanel, PLAYBACK_VIEW);
	}

	/**
	 * Return from playback view to previous view.
	 */
	private void returnFromPlayback()
	{
		currentView = RECORDINGS_VIEW; // Always return to recordings view
		toggleViewBtn.setText("Live View");
		toggleViewBtn.setToolTipText("Switch to live timeline view");
		cardLayout.show(contentPanel, RECORDINGS_VIEW);
	}

	/**
	 * Toggle live updates on/off.
	 */
	private void toggleLiveUpdates()
	{
		liveRecording = !liveRecording;
		if (liveRecording)
		{
			liveStartStopBtn.setText("Pause");
			liveStatusLabel.setText("Recording: Active");
			liveStatusLabel.setForeground(new Color(80, 200, 80));
			liveSaveBtn.setEnabled(false);
			pausedSnapshot = null;
			refreshLiveTimeline();
		}
		else
		{
			liveStartStopBtn.setText("Resume");
			liveStatusLabel.setText("Recording: Paused");
			liveStatusLabel.setForeground(new Color(255, 180, 80));
			if (tickRecorder != null)
			{
				pausedSnapshot = new ArrayList<>(tickRecorder.getTickHistory());
			}
			else
			{
				pausedSnapshot = Collections.emptyList();
			}
			liveSaveBtn.setEnabled(!pausedSnapshot.isEmpty());
		}
	}

	/**
	 * Refresh the panel - called after death recording.
	 */
	public void refresh()
	{
		refreshRecordingsList();
	}

	/**
	 * Called on each game tick to update live view.
	 */
	public void onGameTick()
	{
		if (currentView.equals(LIVE_VIEW) && liveRecording)
		{
			SwingUtilities.invokeLater(this::refreshLiveTimeline);
		}
	}

	/**
	 * Refresh the recordings list view (deaths + saved).
	 */
	private void refreshRecordingsList()
	{
		recordingsListContainer.removeAll();

		List<TickRecording> deaths = recordingStorage.getDeaths();
		List<TickRecording> saved = recordingStorage.getSavedRecordings();

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(3, 0, 3, 0);

		boolean hasContent = false;

		// === Saved Recordings Section ===
		if (!saved.isEmpty())
		{
			hasContent = true;
			JPanel savedHeader = createSectionHeader("★ Saved Recordings (" + saved.size() + ")", new Color(80, 150, 80));
			recordingsListContainer.add(savedHeader, gbc);
			gbc.gridy++;

			for (int i = 0; i < saved.size(); i++)
			{
				TickRecording recording = saved.get(i);
				RecordingEntryPanel entryPanel = new RecordingEntryPanel(
					recording, i + 1,
					this::exportRecording,
					this::deleteSavedRecording,
					this::showPlayback,
					null, // No save callback for already-saved items
					spriteManager,
					true  // isSaved = true
				);
				recordingsListContainer.add(entryPanel, gbc);
				gbc.gridy++;
			}

			// Add spacing between sections
			recordingsListContainer.add(Box.createVerticalStrut(10), gbc);
			gbc.gridy++;
		}

		// === Recent Deaths Section ===
		if (!deaths.isEmpty())
		{
			hasContent = true;
			JPanel deathsHeader = createSectionHeader("☠ Recent Deaths (" + deaths.size() + ")", new Color(180, 80, 80));
			recordingsListContainer.add(deathsHeader, gbc);
			gbc.gridy++;

			for (int i = 0; i < deaths.size(); i++)
			{
				TickRecording death = deaths.get(i);
				RecordingEntryPanel entryPanel = new RecordingEntryPanel(
					death, i + 1,
					this::exportRecording,
					this::deleteDeath,
					this::showPlayback,
					this::saveRecording,  // Save callback to move to saved
					spriteManager,
					false  // isSaved = false
				);
				recordingsListContainer.add(entryPanel, gbc);
				gbc.gridy++;
			}
		}

		// Empty state
		if (!hasContent)
		{
			gbc.weighty = 1.0;
			recordingsListContainer.add(noRecordingsLabel, gbc);
		}
		else
		{
			gbc.weighty = 1.0;
			recordingsListContainer.add(Box.createVerticalGlue(), gbc);
		}

		recordingsListContainer.revalidate();
		recordingsListContainer.repaint();
	}

	/**
	 * Create a section header label.
	 */
	private JPanel createSectionHeader(String text, Color color)
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(6, 10, 6, 10));

		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		header.add(label, BorderLayout.WEST);

		return header;
	}

	/**
	 * Refresh the live timeline view.
	 */
	private void refreshLiveTimeline()
	{
		if (tickRecorder == null)
		{
			return;
		}

		liveTimelineContainer.removeAll();

		List<TickData> ticks = tickRecorder.getTickHistoryReversed();

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(1, 0, 1, 0);

		if (ticks.isEmpty())
		{
			JLabel emptyLabel = new JLabel("No ticks recorded yet");
			emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			emptyLabel.setBorder(new EmptyBorder(20, 10, 20, 10));
			gbc.weighty = 1.0;
			liveTimelineContainer.add(emptyLabel, gbc);
		}
		else
		{
			// Update status with tick count
			int tickCount = tickRecorder.getTickCount();
			int currentTick = tickRecorder.getCurrentTickNumber();
			String statusText = liveRecording ? "Recording: Active" : "Recording: Paused";
			statusText += " (" + tickCount + " ticks, #" + currentTick + ")";
			liveStatusLabel.setText(statusText);

			int rowIndex = 0;
			for (TickData tick : ticks)
			{
				JPanel tickPanel = LiveTickPanel.createTickPanel(tick, rowIndex, spriteManager);
				liveTimelineContainer.add(tickPanel, gbc);
				gbc.gridy++;
				rowIndex++;
			}

			gbc.weighty = 1.0;
			liveTimelineContainer.add(Box.createVerticalGlue(), gbc);
		}

		liveTimelineContainer.revalidate();
		liveTimelineContainer.repaint();
	}

	/**
	 * Export a recording to clipboard.
	 */
	private void exportRecording(TickRecording recording)
	{
		String json = recordingStorage.exportRecordingAsJson(recording);
		copyToClipboard(json);
		JOptionPane.showMessageDialog(
			this,
			"Recording exported to clipboard!",
			"Export Successful",
			JOptionPane.INFORMATION_MESSAGE
		);
	}

	/**
	 * Delete a death record.
	 */
	private void deleteDeath(TickRecording death)
	{
		int result = JOptionPane.showConfirmDialog(
			this,
			"Delete this death record?",
			"Confirm Delete",
			JOptionPane.YES_NO_OPTION
		);
		if (result == JOptionPane.YES_OPTION)
		{
			recordingStorage.deleteDeath(death);
			refreshRecordingsList();
		}
	}

	/**
	 * Save a recording to the saved recordings list.
	 */
	private void saveRecording(TickRecording recording)
	{
		recordingStorage.saveRecording(recording);
		refreshRecordingsList();
		JOptionPane.showMessageDialog(
			this,
			"Recording saved! It will no longer auto-delete.",
			"Saved",
			JOptionPane.INFORMATION_MESSAGE
		);
	}

	/**
	 * Delete a saved recording.
	 */
	private void deleteSavedRecording(TickRecording recording)
	{
		int result = JOptionPane.showConfirmDialog(
			this,
			"Delete this saved recording?",
			"Confirm Delete",
			JOptionPane.YES_NO_OPTION
		);
		if (result == JOptionPane.YES_OPTION)
		{
			recordingStorage.deleteSavedRecording(recording);
			refreshRecordingsList();
		}
	}

	/**
	 * Export current live buffer to clipboard.
	 */
	private void exportLiveBuffer()
	{
		if (tickRecorder == null || tickRecorder.getTickCount() == 0)
		{
			JOptionPane.showMessageDialog(
				this,
				"No ticks to export!",
				"Export",
				JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		List<TickData> ticks = tickRecorder.getTickHistory();
		String json = gson.toJson(ticks);
		copyToClipboard(json);
		JOptionPane.showMessageDialog(
			this,
			"Live buffer (" + ticks.size() + " ticks) exported to clipboard!",
			"Export Successful",
			JOptionPane.INFORMATION_MESSAGE
		);
	}

	/**
	 * Save current live buffer as a recording.
	 */
	private void saveLiveBuffer()
	{
		if (pausedSnapshot == null || pausedSnapshot.isEmpty())
		{
			JOptionPane.showMessageDialog(
				this,
				"Pause live view to capture ticks before saving.",
				"Save",
				JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		String name = JOptionPane.showInputDialog(
			this,
			"Enter a name for this recording:",
			"Save Recording",
			JOptionPane.QUESTION_MESSAGE
		);

		if (name == null || name.trim().isEmpty())
		{
			return; // Cancelled
		}

		List<TickData> ticks = new ArrayList<>(pausedSnapshot);
		int totalDamage = TickRecording.calculateTotalDamage(ticks);

		TickRecording recording = TickRecording.builder()
			.timestamp(System.currentTimeMillis())
			.killerName(name.trim())
			.location("Live Snapshot")
			.ticks(ticks)
			.finalHp(ticks.isEmpty() ? 0 : ticks.get(ticks.size() - 1).getCurrentHp())
			.totalDamageReceived(totalDamage)
			.build();

		recordingStorage.addSavedRecording(recording);
		refreshRecordingsList();

		JOptionPane.showMessageDialog(
			this,
			"Recording saved! (" + ticks.size() + " ticks)",
			"Save Successful",
			JOptionPane.INFORMATION_MESSAGE
		);

		// Switch to recordings view
		if (currentView.equals(LIVE_VIEW))
		{
			toggleView();
		}
	}

	/**
	 * Copy text to system clipboard.
	 */
	private void copyToClipboard(String text)
	{
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(text), null);
	}
}
