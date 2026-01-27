package com.tickreplaylogger.ui;

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
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel displaying a single recording entry with expandable tick timeline.
 */
public class RecordingEntryPanel extends JPanel
{
	private static final Color DAMAGE_RECEIVED_COLOR = new Color(255, 100, 100);
	private static final Color DAMAGE_DEALT_COLOR = new Color(100, 255, 100);
	private static final Color ACTION_COLOR = new Color(255, 200, 100);
	private static final Color PRAYER_COLOR = new Color(150, 150, 255);
	private static final Color MOVEMENT_COLOR = new Color(100, 200, 255);
	private static final Color HP_CHANGE_COLOR = new Color(200, 100, 200);
	private static final Color HP_LOW_COLOR = new Color(255, 80, 80);
	private static final Color HP_MED_COLOR = new Color(255, 180, 80);
	private static final Color HP_HIGH_COLOR = new Color(80, 200, 80);

	// Alternating row colors
	private static final Color ROW_COLOR_1 = ColorScheme.DARK_GRAY_COLOR;
	private static final Color ROW_COLOR_2 = new Color(45, 45, 45);
	private static final Color DEATH_ROW_COLOR = new Color(60, 30, 30);

	private static final int ICON_SIZE = 18;

	private final TickRecording recording;
	private final JPanel ticksPanel;
	private final SpriteManager spriteManager;
	private final Consumer<TickRecording> saveCallback;
	private final boolean isSaved;
	private boolean expanded = false;

	// Cached icons
	private ImageIcon iconHitpoints;
	private ImageIcon iconPrayer;
	private ImageIcon iconAttack;
	private ImageIcon iconSkull;
	private ImageIcon iconHeart;
	private ImageIcon iconAgility;

	/**
	 * Constructor for recordings (without save callback).
	 */
	public RecordingEntryPanel(TickRecording recording, int recordingNumber, Consumer<TickRecording> exportCallback, Consumer<TickRecording> deleteCallback, Consumer<TickRecording> playbackCallback, SpriteManager spriteManager)
	{
		this(recording, recordingNumber, exportCallback, deleteCallback, playbackCallback, null, spriteManager, false);
	}

	/**
	 * Constructor with save callback and saved flag.
	 */
	public RecordingEntryPanel(TickRecording recording, int recordingNumber, Consumer<TickRecording> exportCallback, Consumer<TickRecording> deleteCallback, Consumer<TickRecording> playbackCallback, Consumer<TickRecording> saveCallback, SpriteManager spriteManager, boolean isSaved)
	{
		this.recording = recording;
		this.spriteManager = spriteManager;
		this.saveCallback = saveCallback;
		this.isSaved = isSaved;

		loadSprites();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		// Header panel (always visible)
		JPanel headerPanel = createHeaderPanel(recordingNumber, exportCallback, deleteCallback, playbackCallback);
		add(headerPanel, BorderLayout.NORTH);

		// Ticks panel (expandable) - use GridBagLayout to respect width
		ticksPanel = new JPanel(new GridBagLayout());
		ticksPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		ticksPanel.setVisible(false);
		add(ticksPanel, BorderLayout.CENTER);
	}

	private void loadSprites()
	{
		// Load sprites from the game using SpriteManager
		// These are the native OSRS skill icons
		spriteManager.getSpriteAsync(SpriteID.SKILL_HITPOINTS, 0, img -> {
			iconHitpoints = scaleIcon(img);
			iconHeart = iconHitpoints; // Reuse for heart
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

		// Use the TAB_COMBAT for skull (combat-related deaths)
		spriteManager.getSpriteAsync(SpriteID.TAB_COMBAT, 0, img -> {
			iconSkull = scaleIcon(img);
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

	@Override
	public Dimension getMaximumSize()
	{
		// Constrain width to preferred width, allow any height
		Dimension pref = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, pref.height);
	}

	private JPanel createHeaderPanel(int recordingNumber, Consumer<TickRecording> exportCallback, Consumer<TickRecording> deleteCallback, Consumer<TickRecording> playbackCallback)
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(0, 0, 5, 0));

		// Row 1: Title and utility buttons
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Title with skull icon
		JLabel titleLabel = new JLabel("#" + recordingNumber + ": " + recording.getKillerName());
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		if (iconSkull != null)
		{
			titleLabel.setIcon(iconSkull);
			titleLabel.setIconTextGap(6);
		}
		titleRow.add(titleLabel, BorderLayout.WEST);

		// Small utility buttons on right
		JPanel utilityPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		utilityPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Save button (only for recordings that can be saved)
		if (saveCallback != null && !isSaved)
		{
			JButton saveBtn = new JButton("★");
			saveBtn.setMargin(new Insets(1, 3, 1, 3));
			saveBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
			saveBtn.setBackground(new Color(50, 80, 50));
			saveBtn.setForeground(new Color(150, 200, 150));
			saveBtn.setToolTipText("Save this recording (won't auto-delete)");
			saveBtn.addActionListener(e -> saveCallback.accept(recording));
			utilityPanel.add(saveBtn);
		}

		JButton exportBtn = new JButton("Copy");
		exportBtn.setMargin(new Insets(1, 3, 1, 3));
		exportBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
		exportBtn.setToolTipText("Copy recording data as JSON");
		exportBtn.addActionListener(e -> exportCallback.accept(recording));
		utilityPanel.add(exportBtn);

		JButton deleteBtn = new JButton("X");
		deleteBtn.setMargin(new Insets(1, 3, 1, 3));
		deleteBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
		deleteBtn.setToolTipText("Delete this recording");
		deleteBtn.addActionListener(e -> deleteCallback.accept(recording));
		utilityPanel.add(deleteBtn);

		titleRow.add(utilityPanel, BorderLayout.EAST);
		header.add(titleRow);

		// Row 2: Details
		JLabel detailLabel = new JLabel(recording.getTimeSinceRecording() + " - " + recording.getLocation());
		detailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(detailLabel);

		// Row 3: Summary
		JLabel summaryLabel = new JLabel(recording.getTicks().size() + " ticks, " + recording.getTotalDamageReceived() + " dmg taken");
		summaryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		summaryLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(summaryLabel);

		header.add(Box.createVerticalStrut(6));

		// Row 4: Action buttons - Play and Expand on their own row
		JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		actionRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Prominent playback button
		JButton playbackBtn = new JButton("▶ Replay");
		playbackBtn.setMargin(new Insets(4, 12, 4, 12));
		playbackBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		playbackBtn.setBackground(new Color(50, 100, 50));
		playbackBtn.setForeground(new Color(150, 255, 150));
		playbackBtn.setToolTipText("Replay this recording tick-by-tick");
		playbackBtn.addActionListener(e -> playbackCallback.accept(recording));
		playbackBtn.setFocusPainted(false);
		actionRow.add(playbackBtn);

		JButton expandBtn = new JButton("▼ Timeline");
		expandBtn.setMargin(new Insets(4, 8, 4, 8));
		expandBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		expandBtn.setToolTipText("Expand tick timeline");
		expandBtn.addActionListener(e -> toggleExpanded(expandBtn));
		actionRow.add(expandBtn);

		header.add(actionRow);

		// Click non-button areas to expand
		titleRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		titleRow.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				toggleExpanded(expandBtn);
			}
		});

		return header;
	}

	private void toggleExpanded(JButton expandBtn)
	{
		expanded = !expanded;
		expandBtn.setText(expanded ? "▲ Timeline" : "▼ Timeline");

		if (expanded && ticksPanel.getComponentCount() == 0)
		{
			buildTicksPanel();
		}

		ticksPanel.setVisible(expanded);
		revalidate();
		repaint();
	}

	private void buildTicksPanel()
	{
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(1, 0, 1, 0);

		List<TickData> ticks = recording.getTicks();
		int totalTicks = ticks.size();
		int rowIndex = 0;

		// Show ALL ticks in reverse order (most recent first, closest to death)
		for (int i = ticks.size() - 1; i >= 0; i--)
		{
			TickData tick = ticks.get(i);
			int ticksBeforeDeath = totalTicks - 1 - i;
			boolean isDeathTick = (i == ticks.size() - 1);

			// Get next tick's HP (previous in our reverse iteration) for delta
			int nextTickHp = (i < ticks.size() - 1) ? ticks.get(i + 1).getCurrentHp() : 0;

			JPanel tickPanel = createTickPanel(tick, ticksBeforeDeath, isDeathTick, nextTickHp, rowIndex);
			ticksPanel.add(tickPanel, gbc);
			gbc.gridy++;
			rowIndex++;
		}

		// Add filler at bottom to push content up
		gbc.weighty = 1.0;
		ticksPanel.add(Box.createVerticalGlue(), gbc);
	}

	private JPanel createTickPanel(TickData tick, int ticksBeforeDeath, boolean isDeathTick, int nextTickHp, int rowIndex)
	{
		JPanel panel = new JPanel(new GridBagLayout());

		// Alternating row colors
		Color bgColor;
		if (isDeathTick)
		{
			bgColor = DEATH_ROW_COLOR;
		}
		else
		{
			bgColor = (rowIndex % 2 == 0) ? ROW_COLOR_1 : ROW_COLOR_2;
		}
		panel.setBackground(bgColor);
		panel.setBorder(new EmptyBorder(4, 6, 4, 6));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;

		// Calculate HP color
		float hpPercent = tick.getMaxHp() > 0 ? (float) tick.getCurrentHp() / tick.getMaxHp() : 0;
		Color hpColor = hpPercent > 0.5 ? HP_HIGH_COLOR : (hpPercent > 0.25 ? HP_MED_COLOR : HP_LOW_COLOR);

		// Header: tick label, prayer, and HP on same line
		// Show tick number without the '-' prefix
		String tickLabel = isDeathTick ? "DEATH" : String.valueOf(ticksBeforeDeath);
		String hpText = tick.getCurrentHp() + "/" + tick.getMaxHp();
		String prayerText = tick.getCurrentPrayer() + "/" + tick.getMaxPrayer();

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(bgColor);

		JLabel tickLbl = new JLabel(tickLabel);
		tickLbl.setForeground(isDeathTick ? HP_LOW_COLOR : Color.WHITE);
		tickLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		headerPanel.add(tickLbl, BorderLayout.WEST);

		// Right side panel for prayer and HP
		JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		statsPanel.setBackground(bgColor);

		// Prayer points label
		JLabel prayerLbl = new JLabel(prayerText);
		prayerLbl.setForeground(PRAYER_COLOR);
		prayerLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		if (iconPrayer != null)
		{
			prayerLbl.setIcon(iconPrayer);
			prayerLbl.setHorizontalTextPosition(SwingConstants.LEFT);
		}
		statsPanel.add(prayerLbl);

		// HP label
		JLabel hpLbl = new JLabel(hpText);
		hpLbl.setForeground(hpColor);
		hpLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		if (iconHitpoints != null)
		{
			hpLbl.setIcon(iconHitpoints);
			hpLbl.setHorizontalTextPosition(SwingConstants.LEFT);
		}
		statsPanel.add(hpLbl);

		headerPanel.add(statsPanel, BorderLayout.EAST);

		panel.add(headerPanel, gbc);
		gbc.gridy++;

		// Content
		boolean hasContent = false;

		// Show HP change from previous tick to this tick
		// Get previous tick's HP (i-1 in array = earlier in game time)
		int prevTickIndex = recording.getTicks().size() - 2 - ticksBeforeDeath;
		int prevTickHp = (prevTickIndex >= 0 && prevTickIndex < recording.getTicks().size()) ?
			recording.getTicks().get(prevTickIndex).getCurrentHp() :
			tick.getCurrentHp();

		if (!isDeathTick)
		{
			int hpChange = tick.getCurrentHp() - prevTickHp;

			// Check if this HP change is already explained by damage events
			// HP is recorded at START of tick, damage happens DURING the tick
			// So damage events on the PREVIOUS tick (in game time) cause HP changes on THIS tick
			int trackedDamageReceived = 0;
			if (prevTickIndex >= 0 && prevTickIndex < recording.getTicks().size())
			{
				TickData prevTick = recording.getTicks().get(prevTickIndex);
				for (DamageEvent dmg : prevTick.getDamageEvents())
				{
					if (dmg.getDirection() == DamageEvent.DamageDirection.RECEIVED)
					{
						trackedDamageReceived += dmg.getAmount();
					}
				}
			}

			if (hpChange > 0)
			{
				// HP increased = healing (from food eaten previous tick, or passive healing)
				JLabel healLabel = createIconLabel("+" + hpChange + " HP", iconHeart, HP_HIGH_COLOR);
				panel.add(healLabel, gbc);
				gbc.gridy++;
				hasContent = true;
			}
			else if (hpChange < 0)
			{
				// HP decreased = damage taken
				int actualDamage = -hpChange; // Make positive
				int untrackedDamage = actualDamage - trackedDamageReceived;
				if (untrackedDamage > 0)
				{
					JLabel dmgLabel = createIconLabel("-" + untrackedDamage + " HP (untracked)", iconHeart, HP_CHANGE_COLOR);
					panel.add(dmgLabel, gbc);
					gbc.gridy++;
					hasContent = true;
				}
			}
		}

		// Active prayers - each on its own line
		for (String prayer : tick.getActivePrayers())
		{
			JLabel label = createIconLabel(prayer, iconPrayer, PRAYER_COLOR);
			panel.add(label, gbc);
			gbc.gridy++;
			hasContent = true;
		}

		// Movement
		MovementData movement = tick.getMovement();
		if (movement != null && movement.hasMovement())
		{
			String moveDesc = movement.getDescription();
			if (moveDesc != null)
			{
				JLabel label = createIconLabel(moveDesc, iconAgility, MOVEMENT_COLOR);
				panel.add(label, gbc);
				gbc.gridy++;
				hasContent = true;
			}
		}

		// Player actions
		for (PlayerAction action : tick.getPlayerActions())
		{
			ImageIcon icon = getActionIcon(action);
			JLabel label = createIconLabel(action.getDescription(), icon, ACTION_COLOR);
			panel.add(label, gbc);
			gbc.gridy++;
			hasContent = true;
		}

		// Damage events
		for (DamageEvent damage : tick.getDamageEvents())
		{
			boolean isReceived = damage.getDirection() == DamageEvent.DamageDirection.RECEIVED;
			Color color = isReceived ? DAMAGE_RECEIVED_COLOR : DAMAGE_DEALT_COLOR;
			JLabel label = createIconLabel(damage.getDescription(), iconAttack, color);
			panel.add(label, gbc);
			gbc.gridy++;
			hasContent = true;
		}

		// If no content, show dash
		if (!hasContent)
		{
			JLabel emptyLabel = new JLabel("-");
			emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			emptyLabel.setBorder(new EmptyBorder(1, 20, 1, 0));
			panel.add(emptyLabel, gbc);
		}

		return panel;
	}

	private ImageIcon getActionIcon(PlayerAction action)
	{
		String desc = action.getDescription().toLowerCase();
		if (desc.startsWith("ate") || desc.startsWith("drank"))
		{
			return iconHitpoints; // Use HP icon for consumables
		}
		else if (desc.contains("prayer") || desc.startsWith("activated") || desc.startsWith("deactivated"))
		{
			return iconPrayer;
		}
		else if (desc.startsWith("equipped") || desc.startsWith("unequipped"))
		{
			return iconAttack; // Use attack icon for gear
		}
		else if (desc.startsWith("attacked"))
		{
			return iconAttack;
		}
		return null;
	}

	private JLabel createIconLabel(String text, ImageIcon icon, Color color)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		label.setToolTipText(text); // Show full text on hover
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
}
