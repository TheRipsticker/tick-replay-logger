package com.tickreplaylogger.ui.playback;

import com.tickreplaylogger.model.DamageEvent;
import com.tickreplaylogger.model.TickRecording;
import com.tickreplaylogger.model.MovementData;
import com.tickreplaylogger.model.PlayerAction;
import com.tickreplaylogger.model.TickData;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Main playback panel for replaying tick recordings tick-by-tick.
 */
public class PlaybackPanel extends JPanel
{
	private static final int TICK_MS = 600; // OSRS tick duration

	// Colors - consistent with LiveTickPanel and RecordingEntryPanel
	private static final Color DAMAGE_RECEIVED_COLOR = new Color(255, 100, 100);
	private static final Color DAMAGE_DEALT_COLOR = new Color(100, 255, 100);
	private static final Color ACTION_COLOR = new Color(255, 200, 100);
	private static final Color PRAYER_COLOR = new Color(150, 150, 255);
	private static final Color MOVEMENT_COLOR = new Color(100, 200, 255);
	private static final Color ROW_NORMAL_1 = ColorScheme.DARK_GRAY_COLOR;
	private static final Color ROW_NORMAL_2 = new Color(45, 45, 45);
	private static final Color ROW_SIGNIFICANT_DAMAGE = new Color(80, 30, 30);   // Red - damage taken
	private static final Color ROW_SIGNIFICANT_DEALT = new Color(30, 50, 80);   // Blue - damage dealt
	private static final Color ROW_SIGNIFICANT_HEAL = new Color(30, 60, 30);    // Green - healing
	private static final Color ROW_SIGNIFICANT_PRAYER = new Color(50, 30, 70);  // Purple - prayer actions
	private static final Color ROW_DEATH = new Color(100, 0, 0);
	private static final Color ROW_SELECTED = new Color(60, 60, 100);

	private static final int ICON_SIZE = 18;

	private final Runnable onBackCallback;
	private final SpriteManager spriteManager;
	private TickRecording recording;
	private List<TickData> ticks;

	// Cached icons
	private ImageIcon iconHitpoints;
	private ImageIcon iconPrayer;
	private ImageIcon iconAttack;
	private ImageIcon iconAgility;

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
	private JLabel statusLabel;
	private JComboBox<String> speedCombo;
	private StatBoxPanel hpBar;
	private StatBoxPanel prayerBar;
	private JLabel damageTakenLabel;
	private JLabel damageDealtLabel;
	private JLabel prayersLabel;
	private JPanel currentTickPanel;
	private JList<TickData> eventLogList;
	private DefaultListModel<TickData> eventLogModel;

	// Unicode icons for playback controls
	private static final String ICON_FIRST = "\u23EE";  // ⏮
	private static final String ICON_PREV = "\u25C0";   // ◀
	private static final String ICON_PLAY = "\u25B6";   // ▶
	private static final String ICON_PAUSE = "\u23F8";  // ⏸
	private static final String ICON_NEXT = "\u25B6";   // ▶
	private static final String ICON_LAST = "\u23ED";   // ⏭

	// Status colors
	private static final Color STATUS_ATTACKING = new Color(255, 150, 100);
	private static final Color STATUS_IN_COMBAT = new Color(255, 200, 100);
	private static final Color STATUS_MOVING = new Color(100, 200, 255);
	private static final Color STATUS_IDLE = ColorScheme.LIGHT_GRAY_COLOR;

	// Combat detection - ticks to look back for "In Combat" status
	private static final int COMBAT_LOOKBACK_TICKS = 5;

	public PlaybackPanel(Runnable onBackCallback, SpriteManager spriteManager)
	{
		this.onBackCallback = onBackCallback;
		this.spriteManager = spriteManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		loadSprites();
		buildUI();
		setupKeyboardShortcuts();
	}

	private void loadSprites()
	{
		if (spriteManager == null)
		{
			return;
		}

		spriteManager.getSpriteAsync(SpriteID.SKILL_HITPOINTS, 0, img -> {
			iconHitpoints = scaleIcon(img);
		});

		spriteManager.getSpriteAsync(SpriteID.SKILL_PRAYER, 0, img -> {
			iconPrayer = scaleIcon(img);
		});

		spriteManager.getSpriteAsync(SpriteID.SKILL_ATTACK, 0, img -> {
			iconAttack = scaleIcon(img);
		});

		spriteManager.getSpriteAsync(SpriteID.SKILL_AGILITY, 0, img -> {
			iconAgility = scaleIcon(img);
		});
	}

	private ImageIcon scaleIcon(BufferedImage img)
	{
		if (img == null)
		{
			return null;
		}
		Image scaled = img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
		return new ImageIcon(scaled);
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
		titleLabel = new JLabel("Recording Name");
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerPanel.add(titleLabel);

		// Location row
		locationLabel = new JLabel("Location");
		locationLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		locationLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
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

		// Stats section - fixed height
		JPanel statsPanel = new JPanel();
		statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
		statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		hpBar = new StatBoxPanel("HP", true);
		hpBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsPanel.add(hpBar);

		prayerBar = new StatBoxPanel("PR", false);
		prayerBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsPanel.add(prayerBar);

		// Damage counters row
		JPanel damageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		damageRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		damageRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		damageTakenLabel = new JLabel("Taken: 0");
		damageTakenLabel.setForeground(DAMAGE_RECEIVED_COLOR);
		damageTakenLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		damageRow.add(damageTakenLabel);

		damageDealtLabel = new JLabel("Dealt: 0");
		damageDealtLabel.setForeground(DAMAGE_DEALT_COLOR);
		damageDealtLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		damageRow.add(damageDealtLabel);

		statsPanel.add(damageRow);
		statsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, statsPanel.getPreferredSize().height + 10));

		mainPanel.add(statsPanel);

		// Active prayers - fixed height for ~5 entries
		JPanel prayersSection = new JPanel(new BorderLayout());
		prayersSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		prayersSection.setBorder(new EmptyBorder(4, 8, 4, 8));
		prayersSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		prayersSection.setPreferredSize(new Dimension(0, 90));
		prayersSection.setMinimumSize(new Dimension(0, 90));
		prayersSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

		JLabel prayersTitleLabel = new JLabel("Active Prayers:");
		prayersTitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		prayersTitleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		prayersSection.add(prayersTitleLabel, BorderLayout.NORTH);

		prayersLabel = new JLabel("None");
		prayersLabel.setForeground(PRAYER_COLOR);
		prayersLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		prayersLabel.setVerticalAlignment(SwingConstants.TOP);
		prayersSection.add(prayersLabel, BorderLayout.CENTER);

		mainPanel.add(prayersSection);

		// Current tick events - fixed height for ~5 entries
		JPanel currentTickSection = new JPanel(new BorderLayout());
		currentTickSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		currentTickSection.setBorder(new EmptyBorder(4, 8, 4, 8));
		currentTickSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		currentTickSection.setPreferredSize(new Dimension(0, 100));
		currentTickSection.setMinimumSize(new Dimension(0, 100));
		currentTickSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

		JLabel currentTickTitle = new JLabel("This Tick:");
		currentTickTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		currentTickTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		currentTickSection.add(currentTickTitle, BorderLayout.NORTH);

		currentTickPanel = new JPanel();
		currentTickPanel.setLayout(new BoxLayout(currentTickPanel, BoxLayout.Y_AXIS));
		currentTickPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		currentTickSection.add(currentTickPanel, BorderLayout.CENTER);

		mainPanel.add(currentTickSection);

		// Event log title
		JLabel eventLogTitle = new JLabel("Event Log (click to jump)");
		eventLogTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		eventLogTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		eventLogTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.add(eventLogTitle);

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

		// Row 1: Transport buttons with icons
		JPanel transportRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		transportRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		transportRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		transportRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

		JButton firstBtn = createTransportButton(ICON_FIRST, "First tick");
		firstBtn.addActionListener(e -> jumpToTick(0));
		transportRow.add(firstBtn);

		JButton prevBtn = createTransportButton(ICON_PREV, "Previous tick (Left)");
		prevBtn.addActionListener(e -> prevTick());
		transportRow.add(prevBtn);

		playPauseBtn = createTransportButton(ICON_PLAY, "Play/Pause (Space)");
		playPauseBtn.setPreferredSize(new Dimension(40, 26));
		playPauseBtn.addActionListener(e -> togglePlayback());
		transportRow.add(playPauseBtn);

		JButton nextBtn = createTransportButton(ICON_NEXT, "Next tick (Right)");
		nextBtn.addActionListener(e -> nextTick());
		transportRow.add(nextBtn);

		JButton lastBtn = createTransportButton(ICON_LAST, "Last tick (death)");
		lastBtn.addActionListener(e -> jumpToTick(ticks != null ? ticks.size() - 1 : 0));
		transportRow.add(lastBtn);

		controlBar.add(transportRow);
		controlBar.add(Box.createVerticalStrut(4));

		// Row 2: Speed selector + tick counter + status
		JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		infoRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		infoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		speedCombo = new JComboBox<>(new String[]{"0.5x", "1x", "2x"});
		speedCombo.setSelectedIndex(1); // Default 1x
		speedCombo.setPreferredSize(new Dimension(55, 22));
		speedCombo.addActionListener(e -> updatePlaybackSpeed());
		infoRow.add(speedCombo);

		tickCounterLabel = new JLabel("Tick 0/0");
		tickCounterLabel.setForeground(Color.WHITE);
		tickCounterLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		infoRow.add(tickCounterLabel);

		// Status indicator
		statusLabel = new JLabel("Idle");
		statusLabel.setForeground(STATUS_IDLE);
		statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		infoRow.add(statusLabel);

		controlBar.add(infoRow);

		return controlBar;
	}

	private JButton createTransportButton(String icon, String tooltip)
	{
		JButton btn = new JButton(icon);
		btn.setFont(new Font("Dialog", Font.PLAIN, 14));
		btn.setMargin(new Insets(2, 8, 2, 8));
		btn.setPreferredSize(new Dimension(32, 26));
		btn.setToolTipText(tooltip);
		btn.setFocusPainted(false);
		return btn;
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
	 * Load a tick recording for playback.
	 */
	public void loadRecording(TickRecording recording)
	{
		stopPlayback();

		this.recording = recording;
		this.ticks = recording.getTicks();
		this.currentTickIndex = 0;

		// Reset stat bars for fresh change tracking
		hpBar.reset();
		prayerBar.reset();

		// Update header
		titleLabel.setText(recording.getKillerName());
		locationLabel.setText(recording.getLocation() + " - " + recording.getTimeSinceRecording());

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
			hpBar.reset();
			prayerBar.reset();
			updateDisplay();
		}

		int delay = (int) (TICK_MS / playbackSpeed);
		playbackTimer = new Timer(delay, e -> advanceTick());
		playbackTimer.start();
		isPlaying = true;
		playPauseBtn.setText(ICON_PAUSE);
	}

	private void stopPlayback()
	{
		if (playbackTimer != null)
		{
			playbackTimer.stop();
			playbackTimer = null;
		}
		isPlaying = false;
		playPauseBtn.setText(ICON_PLAY);
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

		// Update cumulative damage counters
		int[] damageStats = calculateCumulativeDamage(currentTickIndex);
		damageTakenLabel.setText("Taken: " + damageStats[0]);
		damageDealtLabel.setText("Dealt: " + damageStats[1]);

		// Update active prayers as vertical list
		List<String> prayers = tick.getActivePrayers();
		if (prayers == null || prayers.isEmpty())
		{
			prayersLabel.setText("None");
		}
		else
		{
			// Use HTML for vertical list
			StringBuilder html = new StringBuilder("<html>");
			for (String prayer : prayers)
			{
				html.append(prayer).append("<br>");
			}
			html.append("</html>");
			prayersLabel.setText(html.toString());
		}

		// Update current tick events
		updateCurrentTickPanel(tick);

		// Update event log selection
		eventLogList.setSelectedIndex(currentTickIndex);
		eventLogList.ensureIndexIsVisible(currentTickIndex);

		// Update tick counter
		tickCounterLabel.setText("Tick " + (currentTickIndex + 1) + "/" + ticks.size());

		// Update status indicator
		updateStatus(tick);
	}

	private void updateStatus(TickData tick)
	{
		// Check if actively attacking this tick
		boolean isAttackingNow = tick.getDamageEvents().stream()
			.anyMatch(dmg -> dmg.getDirection() == DamageEvent.DamageDirection.DEALT);

		// Check if in combat (attacked or was attacked in recent ticks)
		boolean isInCombat = isInCombatRecently();

		MovementData movement = tick.getMovement();
		boolean isMoving = movement != null && movement.hasMovement();

		// Priority: Attacking > In Combat > Moving > Idle
		if (isAttackingNow)
		{
			statusLabel.setText("Attacking");
			statusLabel.setForeground(STATUS_ATTACKING);
		}
		else if (isInCombat)
		{
			statusLabel.setText("In Combat");
			statusLabel.setForeground(STATUS_IN_COMBAT);
		}
		else if (isMoving)
		{
			statusLabel.setText("Moving");
			statusLabel.setForeground(STATUS_MOVING);
		}
		else
		{
			statusLabel.setText("Idle");
			statusLabel.setForeground(STATUS_IDLE);
		}
	}

	/**
	 * Check if player has been in combat recently (dealt or received damage in last N ticks)
	 */
	private boolean isInCombatRecently()
	{
		int startIndex = Math.max(0, currentTickIndex - COMBAT_LOOKBACK_TICKS);
		for (int i = startIndex; i <= currentTickIndex; i++)
		{
			TickData t = ticks.get(i);
			// If any damage events (dealt or received), we're in combat
			if (!t.getDamageEvents().isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	private void updateCurrentTickPanel(TickData tick)
	{
		currentTickPanel.removeAll();

		// Damage events
		for (DamageEvent dmg : tick.getDamageEvents())
		{
			boolean isReceived = dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED;
			Color color = isReceived ? DAMAGE_RECEIVED_COLOR : DAMAGE_DEALT_COLOR;
			JLabel label = createIconLabel(dmg.getDescription(), iconAttack, color);
			currentTickPanel.add(label);
		}

		// Player actions (skip PRAYER_DRAINED as it's unreliable - often triggered by prayer switching)
		for (PlayerAction action : tick.getPlayerActions())
		{
			// Skip PRAYER_DRAINED since it incorrectly fires when switching overhead prayers
			if (action.getType() == PlayerAction.ActionType.PRAYER_DRAINED)
			{
				continue;
			}
			ImageIcon icon = getActionIcon(action);
			Color color = getActionColor(action);
			JLabel label = createIconLabel(action.getDescription(), icon, color);
			currentTickPanel.add(label);
		}

		// Movement
		MovementData movement = tick.getMovement();
		if (movement != null && movement.hasMovement())
		{
			JLabel label = createIconLabel(movement.getDescription(), iconAgility, MOVEMENT_COLOR);
			currentTickPanel.add(label);
		}

		// Empty state
		if (currentTickPanel.getComponentCount() == 0)
		{
			JLabel emptyLabel = new JLabel("(no events)");
			emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 13));
			currentTickPanel.add(emptyLabel);
		}

		currentTickPanel.revalidate();
		currentTickPanel.repaint();
	}

	private JLabel createIconLabel(String text, ImageIcon icon, Color color)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		label.setToolTipText(text);
		if (icon != null)
		{
			label.setIcon(icon);
			label.setIconTextGap(6);
			label.setBorder(new EmptyBorder(1, 4, 1, 0));
		}
		else
		{
			label.setBorder(new EmptyBorder(1, 20, 1, 0));
		}
		return label;
	}

	private ImageIcon getActionIcon(PlayerAction action)
	{
		String desc = action.getDescription().toLowerCase();
		if (desc.startsWith("ate") || desc.startsWith("drank"))
		{
			return iconHitpoints;
		}
		else if (desc.contains("prayer") || desc.startsWith("activated") || desc.startsWith("deactivated") || desc.contains("disabled"))
		{
			return iconPrayer;
		}
		else if (desc.startsWith("equipped") || desc.startsWith("unequipped"))
		{
			return iconAttack;
		}
		else if (desc.startsWith("attacked") || desc.startsWith("special"))
		{
			return iconAttack;
		}
		return null;
	}

	private Color getActionColor(PlayerAction action)
	{
		PlayerAction.ActionType type = action.getType();
		if (type == PlayerAction.ActionType.PRAYER_ACTIVATED ||
			type == PlayerAction.ActionType.PRAYER_DEACTIVATED)
		{
			return PRAYER_COLOR;
		}
		return ACTION_COLOR;
	}

	/**
	 * Calculate cumulative damage taken and dealt up to the given tick index.
	 * @return int[2] where [0] = damage taken, [1] = damage dealt
	 */
	private int[] calculateCumulativeDamage(int upToIndex)
	{
		int taken = 0;
		int dealt = 0;
		for (int i = 0; i <= upToIndex && i < ticks.size(); i++)
		{
			for (DamageEvent dmg : ticks.get(i).getDamageEvents())
			{
				if (dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED)
				{
					taken += dmg.getAmount();
				}
				else if (dmg.getDirection() == DamageEvent.DamageDirection.DEALT)
				{
					dealt += dmg.getAmount();
				}
			}
		}
		return new int[]{taken, dealt};
	}

	/**
	 * Custom cell renderer for the event log list with angled multi-color backgrounds.
	 */
	private class EventLogCellRenderer implements ListCellRenderer<TickData>
	{
		@Override
		public Component getListCellRendererComponent(JList<? extends TickData> list, TickData tick, int index,
													  boolean isSelected, boolean cellHasFocus)
		{
			String summary = buildTickSummary(tick);
			boolean isDeathTick = (index == ticks.size() - 1);
			boolean isCurrent = (index == currentTickIndex);

			// Collect event colors for this tick
			java.util.List<Color> eventColors = new java.util.ArrayList<>();

			if (hasDamageReceived(tick))
			{
				eventColors.add(ROW_SIGNIFICANT_DAMAGE);
			}
			if (hasDamageDealt(tick))
			{
				eventColors.add(ROW_SIGNIFICANT_DEALT);
			}
			if (hasHealing(tick))
			{
				eventColors.add(ROW_SIGNIFICANT_HEAL);
			}
			if (hasPrayerAction(tick))
			{
				eventColors.add(ROW_SIGNIFICANT_PRAYER);
			}

			// Create custom panel with angled stripes
			AngledStripePanel panel = new AngledStripePanel(eventColors, index);
			panel.setDeathTick(isDeathTick);
			panel.setCurrentTick(isCurrent);

			// Add label for text
			JLabel label = new JLabel(summary);
			label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
			label.setForeground(isDeathTick ? DAMAGE_RECEIVED_COLOR : Color.WHITE);
			label.setBorder(new EmptyBorder(5, 6, 5, 6));

			panel.setLayout(new BorderLayout());
			panel.add(label, BorderLayout.CENTER);

			return panel;
		}

		private String buildTickSummary(TickData tick)
		{
			StringBuilder sb = new StringBuilder();
			sb.append(tick.getTickNumber()).append("  ");
			int baseLength = sb.length(); // Track length after tick number

			// Summarize damage events
			int dmgReceived = 0;
			int dmgDealt = 0;
			for (DamageEvent dmg : tick.getDamageEvents())
			{
				if (dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED)
				{
					dmgReceived += dmg.getAmount();
				}
				else if (dmg.getDirection() == DamageEvent.DamageDirection.DEALT)
				{
					dmgDealt += dmg.getAmount();
				}
			}
			if (dmgReceived > 0)
			{
				sb.append("-").append(dmgReceived);
			}
			if (dmgDealt > 0)
			{
				if (dmgReceived > 0) sb.append(" ");
				sb.append("+").append(dmgDealt);
			}

			// Player actions (food/potions only)
			for (PlayerAction action : tick.getPlayerActions())
			{
				if (action.getType() == PlayerAction.ActionType.EAT_FOOD ||
					action.getType() == PlayerAction.ActionType.DRINK_POTION)
				{
					if (sb.length() > baseLength) sb.append(", ");
					sb.append(action.getItemName());
				}
			}

			MovementData movement = tick.getMovement();
			if (movement != null && movement.hasMovement())
			{
				if (sb.length() > baseLength) sb.append(", ");
				sb.append("Moved ").append(movement.getTilesCompleted()).append("t");
			}

			// Show dash if no events
			if (sb.length() == baseLength)
			{
				sb.append("-");
			}

			return sb.toString();
		}

		private boolean hasDamageReceived(TickData tick)
		{
			for (DamageEvent dmg : tick.getDamageEvents())
			{
				if (dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED && dmg.getAmount() > 0)
				{
					return true;
				}
			}
			return false;
		}

		private boolean hasDamageDealt(TickData tick)
		{
			for (DamageEvent dmg : tick.getDamageEvents())
			{
				if (dmg.getDirection() == DamageEvent.DamageDirection.DEALT && dmg.getAmount() > 0)
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

		private boolean hasPrayerAction(TickData tick)
		{
			for (PlayerAction action : tick.getPlayerActions())
			{
				if (action.getType() == PlayerAction.ActionType.PRAYER_ACTIVATED ||
					action.getType() == PlayerAction.ActionType.PRAYER_DEACTIVATED)
				{
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Custom panel that paints angled color stripes for multiple event types.
	 */
	private class AngledStripePanel extends JPanel
	{
		private final java.util.List<Color> colors;
		private final int rowIndex;
		private boolean isDeathTick = false;
		private boolean isCurrentTick = false;

		public AngledStripePanel(java.util.List<Color> colors, int rowIndex)
		{
			this.colors = colors;
			this.rowIndex = rowIndex;
			setOpaque(true);
		}

		public void setDeathTick(boolean deathTick)
		{
			this.isDeathTick = deathTick;
		}

		public void setCurrentTick(boolean currentTick)
		{
			this.isCurrentTick = currentTick;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth();
			int h = getHeight();

			// Death tick gets solid red background
			if (isDeathTick)
			{
				g2d.setColor(ROW_DEATH);
				g2d.fillRect(0, 0, w, h);
				g2d.dispose();
				return;
			}

			// Current tick gets selection highlight
			if (isCurrentTick)
			{
				g2d.setColor(ROW_SELECTED);
				g2d.fillRect(0, 0, w, h);
				g2d.dispose();
				return;
			}

			// No events - use alternating background
			if (colors.isEmpty())
			{
				Color bgColor = (rowIndex % 2 == 0) ? ROW_NORMAL_1 : ROW_NORMAL_2;
				g2d.setColor(bgColor);
				g2d.fillRect(0, 0, w, h);
				g2d.dispose();
				return;
			}

			// Single event - solid color
			if (colors.size() == 1)
			{
				g2d.setColor(colors.get(0));
				g2d.fillRect(0, 0, w, h);
				g2d.dispose();
				return;
			}

			// Multiple events - angled stripes
			int numColors = colors.size();
			int slant = 15; // Pixels to slant the dividers

			for (int i = 0; i < numColors; i++)
			{
				g2d.setColor(colors.get(i));

				// Calculate the x positions for this stripe
				int leftStart = (w * i) / numColors;
				int rightStart = (w * (i + 1)) / numColors;

				// Create angled polygon
				int[] xPoints, yPoints;
				if (i == 0)
				{
					// First stripe - left edge is straight
					xPoints = new int[]{0, rightStart + slant, rightStart, 0};
					yPoints = new int[]{0, 0, h, h};
				}
				else if (i == numColors - 1)
				{
					// Last stripe - right edge is straight
					xPoints = new int[]{leftStart + slant, w, w, leftStart};
					yPoints = new int[]{0, 0, h, h};
				}
				else
				{
					// Middle stripes - both edges angled
					xPoints = new int[]{leftStart + slant, rightStart + slant, rightStart, leftStart};
					yPoints = new int[]{0, 0, h, h};
				}

				g2d.fillPolygon(xPoints, yPoints, 4);
			}

			g2d.dispose();
		}
	}
}
