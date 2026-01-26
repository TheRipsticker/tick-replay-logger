package com.deathtrace.ui;

import com.deathtrace.model.DeathRecord;
import com.deathtrace.model.TickData;
import com.deathtrace.ui.playback.PlaybackPanel;
import com.deathtrace.util.DeathStorage;
import com.deathtrace.util.TickRecorder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * Main panel for Death Trace plugin.
 * Shows list of recent deaths with expandable tick timelines,
 * or a live timeline of the current tick buffer.
 */
public class DeathTracePanel extends PluginPanel
{
	private static final String DEATHS_VIEW = "deaths";
	private static final String LIVE_VIEW = "live";
	private static final String PLAYBACK_VIEW = "playback";

	private final DeathStorage deathStorage;
	private final SpriteManager spriteManager;
	private TickRecorder tickRecorder;

	// View switching
	private final CardLayout cardLayout;
	private final JPanel contentPanel;
	private final JButton toggleViewBtn;
	private String currentView = DEATHS_VIEW;
	private String previousView = DEATHS_VIEW; // For returning from playback

	// Deaths view components
	private final JPanel deathListContainer;
	private final JLabel noDeathsLabel;

	// Live view components
	private final JPanel liveTimelineContainer;
	private final JLabel liveStatusLabel;
	private final JButton liveStartStopBtn;
	private boolean liveRecording = true; // Controls display updates

	// Playback view components
	private final PlaybackPanel playbackPanel;

	// Gson for JSON export
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public DeathTracePanel(DeathStorage deathStorage, SpriteManager spriteManager)
	{
		super(false);
		this.deathStorage = deathStorage;
		this.spriteManager = spriteManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Header with title and view toggle
		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel titleLabel = new JLabel("Death Trace");
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

		// === Deaths View ===
		JPanel deathsViewPanel = new JPanel(new BorderLayout());
		deathsViewPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Deaths list container
		deathListContainer = new JPanel(new GridBagLayout())
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
		deathListContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		noDeathsLabel = new JLabel("No deaths recorded yet");
		noDeathsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		noDeathsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		noDeathsLabel.setBorder(new EmptyBorder(20, 10, 20, 10));

		JScrollPane deathsScrollPane = new JScrollPane(deathListContainer);
		deathsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		deathsScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		deathsScrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		deathsScrollPane.setBorder(null);

		deathsViewPanel.add(deathsScrollPane, BorderLayout.CENTER);

		// Deaths footer
		JPanel deathsFooter = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		deathsFooter.setBackground(ColorScheme.DARK_GRAY_COLOR);
		deathsFooter.setBorder(new EmptyBorder(5, 10, 10, 10));

		JButton exportAllBtn = new JButton("Export All");
		exportAllBtn.setToolTipText("Copy all deaths to clipboard as JSON");
		exportAllBtn.addActionListener(e -> exportAllDeaths());
		deathsFooter.add(exportAllBtn);

		JButton clearBtn = new JButton("Clear All");
		clearBtn.setToolTipText("Delete all recorded deaths");
		clearBtn.addActionListener(e -> {
			int result = JOptionPane.showConfirmDialog(
				this,
				"Are you sure you want to clear all death records?",
				"Confirm Clear",
				JOptionPane.YES_NO_OPTION
			);
			if (result == JOptionPane.YES_OPTION)
			{
				deathStorage.clearDeaths();
				refreshDeathsList();
			}
		});
		deathsFooter.add(clearBtn);

		deathsViewPanel.add(deathsFooter, BorderLayout.SOUTH);

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
		playbackPanel = new PlaybackPanel(this::returnFromPlayback);

		// Add views to card layout
		contentPanel.add(deathsViewPanel, DEATHS_VIEW);
		contentPanel.add(liveViewPanel, LIVE_VIEW);
		contentPanel.add(playbackPanel, PLAYBACK_VIEW);

		add(contentPanel, BorderLayout.CENTER);

		// Initial refresh
		refreshDeathsList();
	}

	/**
	 * Set the tick recorder for live view updates.
	 */
	public void setTickRecorder(TickRecorder tickRecorder)
	{
		this.tickRecorder = tickRecorder;
	}

	/**
	 * Toggle between deaths view and live view.
	 */
	private void toggleView()
	{
		if (currentView.equals(DEATHS_VIEW))
		{
			currentView = LIVE_VIEW;
			toggleViewBtn.setText("Deaths");
			toggleViewBtn.setToolTipText("Switch to deaths list view");
			cardLayout.show(contentPanel, LIVE_VIEW);
			refreshLiveTimeline();
		}
		else if (currentView.equals(LIVE_VIEW))
		{
			currentView = DEATHS_VIEW;
			toggleViewBtn.setText("Live View");
			toggleViewBtn.setToolTipText("Switch to live timeline view");
			cardLayout.show(contentPanel, DEATHS_VIEW);
		}
		// If in playback view, toggle goes to deaths view
		else if (currentView.equals(PLAYBACK_VIEW))
		{
			returnFromPlayback();
		}
	}

	/**
	 * Show the playback view for a death record.
	 */
	private void showPlayback(DeathRecord death)
	{
		previousView = currentView;
		currentView = PLAYBACK_VIEW;
		toggleViewBtn.setText("Deaths");
		toggleViewBtn.setToolTipText("Return to deaths list");
		playbackPanel.loadDeath(death);
		cardLayout.show(contentPanel, PLAYBACK_VIEW);
	}

	/**
	 * Return from playback view to previous view.
	 */
	private void returnFromPlayback()
	{
		currentView = DEATHS_VIEW; // Always return to deaths view
		toggleViewBtn.setText("Live View");
		toggleViewBtn.setToolTipText("Switch to live timeline view");
		cardLayout.show(contentPanel, DEATHS_VIEW);
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
			refreshLiveTimeline();
		}
		else
		{
			liveStartStopBtn.setText("Resume");
			liveStatusLabel.setText("Recording: Paused");
			liveStatusLabel.setForeground(new Color(255, 180, 80));
		}
	}

	/**
	 * Refresh the panel - called after death recording.
	 */
	public void refresh()
	{
		refreshDeathsList();
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
	 * Refresh the deaths list view.
	 */
	private void refreshDeathsList()
	{
		deathListContainer.removeAll();

		List<DeathRecord> deaths = deathStorage.getDeaths();

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(3, 0, 3, 0);

		if (deaths.isEmpty())
		{
			gbc.weighty = 1.0;
			deathListContainer.add(noDeathsLabel, gbc);
		}
		else
		{
			for (int i = 0; i < deaths.size(); i++)
			{
				DeathRecord death = deaths.get(i);
				DeathEntryPanel entryPanel = new DeathEntryPanel(death, i + 1, this::exportDeath, this::deleteDeath, this::showPlayback, spriteManager);
				deathListContainer.add(entryPanel, gbc);
				gbc.gridy++;
			}

			gbc.weighty = 1.0;
			deathListContainer.add(Box.createVerticalGlue(), gbc);
		}

		deathListContainer.revalidate();
		deathListContainer.repaint();
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
	 * Export a single death to clipboard.
	 */
	private void exportDeath(DeathRecord death)
	{
		String json = deathStorage.exportDeathAsJson(death);
		copyToClipboard(json);
		JOptionPane.showMessageDialog(
			this,
			"Death exported to clipboard!",
			"Export Successful",
			JOptionPane.INFORMATION_MESSAGE
		);
	}

	/**
	 * Delete a single death record.
	 */
	private void deleteDeath(DeathRecord death)
	{
		int result = JOptionPane.showConfirmDialog(
			this,
			"Delete this death record?",
			"Confirm Delete",
			JOptionPane.YES_NO_OPTION
		);
		if (result == JOptionPane.YES_OPTION)
		{
			deathStorage.deleteDeath(death);
			refreshDeathsList();
		}
	}

	/**
	 * Export all deaths to clipboard.
	 */
	private void exportAllDeaths()
	{
		if (deathStorage.getDeathCount() == 0)
		{
			JOptionPane.showMessageDialog(
				this,
				"No deaths to export!",
				"Export",
				JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		String json = deathStorage.exportAllAsJson();
		copyToClipboard(json);
		JOptionPane.showMessageDialog(
			this,
			"All deaths exported to clipboard!",
			"Export Successful",
			JOptionPane.INFORMATION_MESSAGE
		);
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
	 * Copy text to system clipboard.
	 */
	private void copyToClipboard(String text)
	{
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(text), null);
	}
}
