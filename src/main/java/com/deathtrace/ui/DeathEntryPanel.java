package com.deathtrace.ui;

import com.deathtrace.model.DamageEvent;
import com.deathtrace.model.DeathRecord;
import com.deathtrace.model.MovementData;
import com.deathtrace.model.PlayerAction;
import com.deathtrace.model.TickData;
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
 * Panel displaying a single death entry with expandable tick timeline.
 */
public class DeathEntryPanel extends JPanel
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

	private final DeathRecord death;
	private final JPanel ticksPanel;
	private final SpriteManager spriteManager;
	private boolean expanded = false;

	// Cached icons
	private ImageIcon iconHitpoints;
	private ImageIcon iconPrayer;
	private ImageIcon iconAttack;
	private ImageIcon iconSkull;
	private ImageIcon iconHeart;
	private ImageIcon iconAgility;

	public DeathEntryPanel(DeathRecord death, int deathNumber, Consumer<DeathRecord> exportCallback, Consumer<DeathRecord> deleteCallback, Consumer<DeathRecord> playbackCallback, SpriteManager spriteManager)
	{
		this.death = death;
		this.spriteManager = spriteManager;

		loadSprites();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		// Header panel (always visible)
		JPanel headerPanel = createHeaderPanel(deathNumber, exportCallback, deleteCallback, playbackCallback);
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

	private JPanel createHeaderPanel(int deathNumber, Consumer<DeathRecord> exportCallback, Consumer<DeathRecord> deleteCallback, Consumer<DeathRecord> playbackCallback)
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setBorder(new EmptyBorder(0, 0, 5, 0));

		// Row 1: Title and buttons
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Title with skull icon
		JLabel titleLabel = new JLabel("#" + deathNumber + ": " + death.getKillerName());
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		if (iconSkull != null)
		{
			titleLabel.setIcon(iconSkull);
			titleLabel.setIconTextGap(6);
		}
		titleRow.add(titleLabel, BorderLayout.WEST);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JButton playbackBtn = new JButton("▶");
		playbackBtn.setMargin(new Insets(1, 4, 1, 4));
		playbackBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		playbackBtn.setToolTipText("Playback this death");
		playbackBtn.addActionListener(e -> playbackCallback.accept(death));
		buttonPanel.add(playbackBtn);

		JButton exportBtn = new JButton("Copy");
		exportBtn.setMargin(new Insets(1, 4, 1, 4));
		exportBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		exportBtn.addActionListener(e -> exportCallback.accept(death));
		buttonPanel.add(exportBtn);

		JButton deleteBtn = new JButton("X");
		deleteBtn.setMargin(new Insets(1, 4, 1, 4));
		deleteBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		deleteBtn.setToolTipText("Delete this death record");
		deleteBtn.addActionListener(e -> deleteCallback.accept(death));
		buttonPanel.add(deleteBtn);

		JButton expandBtn = new JButton("▼");
		expandBtn.setMargin(new Insets(1, 4, 1, 4));
		expandBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		expandBtn.addActionListener(e -> toggleExpanded(expandBtn));
		buttonPanel.add(expandBtn);

		titleRow.add(buttonPanel, BorderLayout.EAST);
		header.add(titleRow);

		// Row 2: Details
		JLabel detailLabel = new JLabel(death.getTimeSinceDeath() + " - " + death.getLocation());
		detailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		header.add(detailLabel);

		// Row 3: Summary
		JLabel summaryLabel = new JLabel(death.getTicks().size() + " ticks, " + death.getTotalDamageReceived() + " dmg");
		summaryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		summaryLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		header.add(summaryLabel);

		// Click header to expand
		header.addMouseListener(new java.awt.event.MouseAdapter()
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
		expandBtn.setText(expanded ? "▲" : "▼");

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

		List<TickData> ticks = death.getTicks();
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
		int prevTickIndex = death.getTicks().size() - 2 - ticksBeforeDeath;
		int prevTickHp = (prevTickIndex >= 0 && prevTickIndex < death.getTicks().size()) ?
			death.getTicks().get(prevTickIndex).getCurrentHp() :
			tick.getCurrentHp();

		if (!isDeathTick)
		{
			int hpChange = tick.getCurrentHp() - prevTickHp;

			// Check if this HP change is already explained by damage events
			// HP is recorded at START of tick, damage happens DURING the tick
			// So damage events on the PREVIOUS tick (in game time) cause HP changes on THIS tick
			int trackedDamageReceived = 0;
			if (prevTickIndex >= 0 && prevTickIndex < death.getTicks().size())
			{
				TickData prevTick = death.getTicks().get(prevTickIndex);
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
