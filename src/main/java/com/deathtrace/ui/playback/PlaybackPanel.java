package com.deathtrace.ui.playback;

import com.deathtrace.model.DamageEvent;
import com.deathtrace.model.DeathRecord;
import com.deathtrace.model.MovementData;
import com.deathtrace.model.PlayerAction;
import com.deathtrace.model.TickData;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Main playback panel for replaying death recordings tick-by-tick.
 */
public class PlaybackPanel extends JPanel
{
	private static final int TICK_MS = 600; // OSRS tick duration

	// Colors
	private static final Color DAMAGE_COLOR = new Color(255, 100, 100);
	private static final Color HEAL_COLOR = new Color(100, 255, 100);
	private static final Color ACTION_COLOR = new Color(255, 200, 100);
	private static final Color PRAYER_COLOR = new Color(150, 150, 255);
	private static final Color MOVEMENT_COLOR = new Color(100, 200, 255);
	private static final Color ROW_NORMAL_1 = ColorScheme.DARK_GRAY_COLOR;
	private static final Color ROW_NORMAL_2 = new Color(45, 45, 45);
	private static final Color ROW_SIGNIFICANT_DAMAGE = new Color(80, 30, 30);
	private static final Color ROW_SIGNIFICANT_HEAL = new Color(30, 60, 30);
	private static final Color ROW_DEATH = new Color(100, 0, 0);
	private static final Color ROW_SELECTED = new Color(60, 60, 100);

	private final Runnable onBackCallback;
	private DeathRecord death;
	private List<TickData> ticks;

	// Playback state
	private int currentTickIndex = 0;
	private boolean isPlaying = false;
	private float playbackSpeed = 1.0f;
	private Timer playbackTimer;

	// UI Components
	private JLabel titleLabel;
	private JLabel locationLabel;
	private JButton playPauseBtn;
	private JLabel tickCounterLabel;
	private JComboBox<String> speedCombo;
	private StatBoxPanel hpBar;
	private StatBoxPanel prayerBar;
	private JLabel damageLabel;
	private JLabel prayersLabel;
	private JPanel currentTickPanel;
	private JList<TickData> eventLogList;
	private DefaultListModel<TickData> eventLogModel;

	public PlaybackPanel(Runnable onBackCallback)
	{
		this.onBackCallback = onBackCallback;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		buildUI();
		setupKeyboardShortcuts();
	}

	private void buildUI()
	{
		// === Header ===
		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

		// Back button row
		JPanel backRow = new JPanel(new BorderLayout());
		backRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		backRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton backBtn = new JButton("< Back");
		backBtn.setMargin(new Insets(2, 6, 2, 6));
		backBtn.addActionListener(e -> {
			stopPlayback();
			onBackCallback.run();
		});
		backRow.add(backBtn, BorderLayout.WEST);
		headerPanel.add(backRow);

		// Title row
		titleLabel = new JLabel("Killer Name");
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerPanel.add(titleLabel);

		// Location row
		locationLabel = new JLabel("Location");
		locationLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		locationLabel.setFont(locationLabel.getFont().deriveFont(11f));
		locationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerPanel.add(locationLabel);

		add(headerPanel, BorderLayout.NORTH);

		// === Main Content ===
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		mainPanel.setBorder(new EmptyBorder(5, 10, 5, 10));

		// Control bar
		JPanel controlBar = createControlBar();
		controlBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.add(controlBar);
		mainPanel.add(Box.createVerticalStrut(8));

		// Stats section
		JPanel statsPanel = new JPanel();
		statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
		statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		hpBar = new StatBoxPanel("HP", true);
		hpBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		hpBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		statsPanel.add(hpBar);

		prayerBar = new StatBoxPanel("PR", false);
		prayerBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		prayerBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		statsPanel.add(prayerBar);

		damageLabel = new JLabel("DMG TAKEN: 0");
		damageLabel.setForeground(DAMAGE_COLOR);
		damageLabel.setFont(damageLabel.getFont().deriveFont(Font.BOLD, 11f));
		damageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		damageLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
		statsPanel.add(damageLabel);

		mainPanel.add(statsPanel);
		mainPanel.add(Box.createVerticalStrut(8));

		// Active prayers
		JPanel prayersPanel = new JPanel(new BorderLayout());
		prayersPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		prayersPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
		prayersPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel prayersTitleLabel = new JLabel("PRAYERS: ");
		prayersTitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		prayersTitleLabel.setFont(prayersTitleLabel.getFont().deriveFont(10f));
		prayersPanel.add(prayersTitleLabel, BorderLayout.WEST);

		prayersLabel = new JLabel("None");
		prayersLabel.setForeground(PRAYER_COLOR);
		prayersLabel.setFont(prayersLabel.getFont().deriveFont(11f));
		prayersPanel.add(prayersLabel, BorderLayout.CENTER);

		mainPanel.add(prayersPanel);
		mainPanel.add(Box.createVerticalStrut(8));

		// Current tick events
		JPanel currentTickSection = new JPanel(new BorderLayout());
		currentTickSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		currentTickSection.setBorder(new EmptyBorder(4, 8, 4, 8));
		currentTickSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		currentTickSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

		JLabel currentTickTitle = new JLabel("THIS TICK:");
		currentTickTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		currentTickTitle.setFont(currentTickTitle.getFont().deriveFont(Font.BOLD, 10f));
		currentTickSection.add(currentTickTitle, BorderLayout.NORTH);

		currentTickPanel = new JPanel();
		currentTickPanel.setLayout(new BoxLayout(currentTickPanel, BoxLayout.Y_AXIS));
		currentTickPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		currentTickSection.add(currentTickPanel, BorderLayout.CENTER);

		mainPanel.add(currentTickSection);
		mainPanel.add(Box.createVerticalStrut(8));

		// Event log title
		JLabel eventLogTitle = new JLabel("EVENT LOG (click to jump)");
		eventLogTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		eventLogTitle.setFont(eventLogTitle.getFont().deriveFont(Font.BOLD, 10f));
		eventLogTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.add(eventLogTitle);
		mainPanel.add(Box.createVerticalStrut(2));

		// Event log list
		eventLogModel = new DefaultListModel<>();
		eventLogList = new JList<>(eventLogModel);
		eventLogList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		eventLogList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		eventLogList.setCellRenderer(new EventLogCellRenderer());
		eventLogList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				int index = eventLogList.getSelectedIndex();
				if (index >= 0 && index != currentTickIndex)
				{
					jumpToTick(index);
				}
			}
		});

		JScrollPane eventLogScroll = new JScrollPane(eventLogList);
		eventLogScroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		eventLogScroll.setBorder(null);
		eventLogScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

		mainPanel.add(eventLogScroll);

		add(mainPanel, BorderLayout.CENTER);
	}

	private JPanel createControlBar()
	{
		JPanel controlBar = new JPanel();
		controlBar.setLayout(new BoxLayout(controlBar, BoxLayout.Y_AXIS));
		controlBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		controlBar.setBorder(new EmptyBorder(6, 8, 6, 8));

		// Transport buttons row
		JPanel transportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		transportRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		transportRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton firstBtn = new JButton("|<");
		firstBtn.setMargin(new Insets(2, 4, 2, 4));
		firstBtn.setToolTipText("First tick");
		firstBtn.addActionListener(e -> jumpToTick(0));
		transportRow.add(firstBtn);

		JButton prevBtn = new JButton("<");
		prevBtn.setMargin(new Insets(2, 4, 2, 4));
		prevBtn.setToolTipText("Previous tick");
		prevBtn.addActionListener(e -> prevTick());
		transportRow.add(prevBtn);

		playPauseBtn = new JButton(">");
		playPauseBtn.setMargin(new Insets(2, 6, 2, 6));
		playPauseBtn.setToolTipText("Play/Pause");
		playPauseBtn.addActionListener(e -> togglePlayback());
		transportRow.add(playPauseBtn);

		JButton nextBtn = new JButton(">");
		nextBtn.setMargin(new Insets(2, 4, 2, 4));
		nextBtn.setToolTipText("Next tick");
		nextBtn.addActionListener(e -> nextTick());
		transportRow.add(nextBtn);

		JButton lastBtn = new JButton(">|");
		lastBtn.setMargin(new Insets(2, 4, 2, 4));
		lastBtn.setToolTipText("Last tick (death)");
		lastBtn.addActionListener(e -> jumpToTick(ticks != null ? ticks.size() - 1 : 0));
		transportRow.add(lastBtn);

		transportRow.add(Box.createHorizontalStrut(10));

		speedCombo = new JComboBox<>(new String[]{"0.5x", "1x", "2x"});
		speedCombo.setSelectedIndex(1); // Default 1x
		speedCombo.setMaximumSize(new Dimension(60, 24));
		speedCombo.addActionListener(e -> updatePlaybackSpeed());
		transportRow.add(speedCombo);

		controlBar.add(transportRow);

		// Tick counter row
		tickCounterLabel = new JLabel("Tick 0/0");
		tickCounterLabel.setForeground(Color.WHITE);
		tickCounterLabel.setFont(tickCounterLabel.getFont().deriveFont(Font.BOLD, 12f));
		tickCounterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		controlBar.add(tickCounterLabel);

		return controlBar;
	}

	private void setupKeyboardShortcuts()
	{
		// Space = play/pause
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "playPause");
		getActionMap().put("playPause", new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				togglePlayback();
			}
		});

		// Left arrow = prev tick
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prevTick");
		getActionMap().put("prevTick", new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				prevTick();
			}
		});

		// Right arrow = next tick
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "nextTick");
		getActionMap().put("nextTick", new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				nextTick();
			}
		});
	}

	/**
	 * Load a death record for playback.
	 */
	public void loadDeath(DeathRecord death)
	{
		stopPlayback();

		this.death = death;
		this.ticks = death.getTicks();
		this.currentTickIndex = 0;

		// Update header
		titleLabel.setText(death.getKillerName());
		locationLabel.setText(death.getLocation() + " - " + death.getTimeSinceDeath());

		// Populate event log
		eventLogModel.clear();
		for (TickData tick : ticks)
		{
			eventLogModel.addElement(tick);
		}

		// Update display
		updateDisplay();
	}

	private void togglePlayback()
	{
		if (isPlaying)
		{
			stopPlayback();
		}
		else
		{
			startPlayback();
		}
	}

	private void startPlayback()
	{
		if (ticks == null || ticks.isEmpty())
		{
			return;
		}

		// If at end, restart from beginning
		if (currentTickIndex >= ticks.size() - 1)
		{
			currentTickIndex = 0;
			updateDisplay();
		}

		int delay = (int) (TICK_MS / playbackSpeed);
		playbackTimer = new Timer(delay, e -> advanceTick());
		playbackTimer.start();
		isPlaying = true;
		playPauseBtn.setText("||");
	}

	private void stopPlayback()
	{
		if (playbackTimer != null)
		{
			playbackTimer.stop();
			playbackTimer = null;
		}
		isPlaying = false;
		playPauseBtn.setText(">");
	}

	private void advanceTick()
	{
		if (currentTickIndex < ticks.size() - 1)
		{
			currentTickIndex++;
			updateDisplay();
		}
		else
		{
			stopPlayback();
		}
	}

	private void prevTick()
	{
		if (currentTickIndex > 0)
		{
			currentTickIndex--;
			updateDisplay();
		}
	}

	private void nextTick()
	{
		if (ticks != null && currentTickIndex < ticks.size() - 1)
		{
			currentTickIndex++;
			updateDisplay();
		}
	}

	private void jumpToTick(int index)
	{
		if (ticks == null || index < 0 || index >= ticks.size())
		{
			return;
		}
		currentTickIndex = index;
		updateDisplay();
	}

	private void updatePlaybackSpeed()
	{
		String selected = (String) speedCombo.getSelectedItem();
		if (selected != null)
		{
			switch (selected)
			{
				case "0.5x":
					playbackSpeed = 0.5f;
					break;
				case "2x":
					playbackSpeed = 2.0f;
					break;
				default:
					playbackSpeed = 1.0f;
			}

			// Update timer if playing
			if (isPlaying && playbackTimer != null)
			{
				playbackTimer.setDelay((int) (TICK_MS / playbackSpeed));
			}
		}
	}

	private void updateDisplay()
	{
		if (ticks == null || ticks.isEmpty())
		{
			return;
		}

		TickData tick = ticks.get(currentTickIndex);

		// Update stat bars
		hpBar.setValue(tick.getCurrentHp(), tick.getMaxHp());
		prayerBar.setValue(tick.getCurrentPrayer(), tick.getMaxPrayer());

		// Update cumulative damage
		int cumDamage = calculateCumulativeDamage(currentTickIndex);
		damageLabel.setText("DMG TAKEN: " + cumDamage);

		// Update active prayers
		List<String> prayers = tick.getActivePrayers();
		if (prayers == null || prayers.isEmpty())
		{
			prayersLabel.setText("None");
		}
		else
		{
			prayersLabel.setText(String.join(", ", prayers));
		}

		// Update current tick events
		updateCurrentTickPanel(tick);

		// Update event log selection
		eventLogList.setSelectedIndex(currentTickIndex);
		eventLogList.ensureIndexIsVisible(currentTickIndex);

		// Update tick counter
		tickCounterLabel.setText("Tick " + (currentTickIndex + 1) + "/" + ticks.size());
	}

	private void updateCurrentTickPanel(TickData tick)
	{
		currentTickPanel.removeAll();

		// Damage events
		for (DamageEvent dmg : tick.getDamageEvents())
		{
			boolean isReceived = dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED;
			JLabel label = new JLabel("> " + dmg.getDescription());
			label.setForeground(isReceived ? DAMAGE_COLOR : HEAL_COLOR);
			label.setFont(label.getFont().deriveFont(11f));
			currentTickPanel.add(label);
		}

		// Player actions
		for (PlayerAction action : tick.getPlayerActions())
		{
			JLabel label = new JLabel("> " + action.getDescription());
			label.setForeground(ACTION_COLOR);
			label.setFont(label.getFont().deriveFont(11f));
			currentTickPanel.add(label);
		}

		// Movement
		MovementData movement = tick.getMovement();
		if (movement != null && movement.hasMovement())
		{
			JLabel label = new JLabel("> " + movement.getDescription());
			label.setForeground(MOVEMENT_COLOR);
			label.setFont(label.getFont().deriveFont(11f));
			currentTickPanel.add(label);
		}

		// Empty state
		if (currentTickPanel.getComponentCount() == 0)
		{
			JLabel emptyLabel = new JLabel("(no events)");
			emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 11f));
			currentTickPanel.add(emptyLabel);
		}

		currentTickPanel.revalidate();
		currentTickPanel.repaint();
	}

	private int calculateCumulativeDamage(int upToIndex)
	{
		int total = 0;
		for (int i = 0; i <= upToIndex && i < ticks.size(); i++)
		{
			for (DamageEvent dmg : ticks.get(i).getDamageEvents())
			{
				if (dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED)
				{
					total += dmg.getAmount();
				}
			}
		}
		return total;
	}

	/**
	 * Custom cell renderer for the event log list.
	 */
	private class EventLogCellRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index,
													  boolean isSelected, boolean cellHasFocus)
		{
			TickData tick = (TickData) value;
			String summary = buildTickSummary(tick);

			JLabel label = (JLabel) super.getListCellRendererComponent(list, summary, index, isSelected, cellHasFocus);
			label.setFont(label.getFont().deriveFont(11f));
			label.setBorder(new EmptyBorder(3, 6, 3, 6));

			// Determine background color
			boolean isDeathTick = (index == ticks.size() - 1);
			boolean isCurrent = (index == currentTickIndex);

			Color bgColor;
			if (isDeathTick)
			{
				bgColor = ROW_DEATH;
			}
			else if (isCurrent)
			{
				bgColor = ROW_SELECTED;
			}
			else if (hasSignificantDamage(tick))
			{
				bgColor = ROW_SIGNIFICANT_DAMAGE;
			}
			else if (hasHealing(tick))
			{
				bgColor = ROW_SIGNIFICANT_HEAL;
			}
			else
			{
				bgColor = (index % 2 == 0) ? ROW_NORMAL_1 : ROW_NORMAL_2;
			}

			label.setBackground(bgColor);
			label.setForeground(isDeathTick ? DAMAGE_COLOR : Color.WHITE);
			label.setOpaque(true);

			return label;
		}

		private String buildTickSummary(TickData tick)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("#").append(tick.getTickNumber()).append(" ");

			// Summarize key events
			int dmgReceived = 0;
			for (DamageEvent dmg : tick.getDamageEvents())
			{
				if (dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED)
				{
					dmgReceived += dmg.getAmount();
				}
			}
			if (dmgReceived > 0)
			{
				sb.append(dmgReceived).append(" dmg");
			}

			for (PlayerAction action : tick.getPlayerActions())
			{
				if (action.getType() == PlayerAction.ActionType.EAT_FOOD ||
					action.getType() == PlayerAction.ActionType.DRINK_POTION)
				{
					if (sb.length() > 5) sb.append(", ");
					sb.append(action.getItemName());
				}
				else if (action.getType() == PlayerAction.ActionType.PRAYER_DRAINED)
				{
					if (sb.length() > 5) sb.append(", ");
					sb.append("Prayer drained");
				}
			}

			MovementData movement = tick.getMovement();
			if (movement != null && movement.hasMovement())
			{
				if (sb.length() > 5) sb.append(", ");
				sb.append("Moved ").append(movement.getTilesCompleted()).append("t");
			}

			if (sb.length() <= 5)
			{
				sb.append("-");
			}

			return sb.toString();
		}

		private boolean hasSignificantDamage(TickData tick)
		{
			for (DamageEvent dmg : tick.getDamageEvents())
			{
				if (dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED && dmg.getAmount() > 15)
				{
					return true;
				}
			}
			for (PlayerAction action : tick.getPlayerActions())
			{
				if (action.getType() == PlayerAction.ActionType.PRAYER_DRAINED)
				{
					return true;
				}
			}
			return false;
		}

		private boolean hasHealing(TickData tick)
		{
			for (PlayerAction action : tick.getPlayerActions())
			{
				if (action.getType() == PlayerAction.ActionType.EAT_FOOD ||
					action.getType() == PlayerAction.ActionType.DRINK_POTION)
				{
					return true;
				}
			}
			return false;
		}
	}
}
