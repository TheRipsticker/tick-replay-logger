package com.tickreplaylogger.ui;

import com.tickreplaylogger.model.DamageEvent;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for creating tick panels in the live timeline view.
 */
public class LiveTickPanel
{
	private static final Color DAMAGE_RECEIVED_COLOR = new Color(255, 100, 100);
	private static final Color DAMAGE_DEALT_COLOR = new Color(100, 255, 100);
	private static final Color ACTION_COLOR = new Color(255, 200, 100);
	private static final Color PRAYER_COLOR = new Color(150, 150, 255);
	private static final Color MOVEMENT_COLOR = new Color(100, 200, 255);
	private static final Color HP_LOW_COLOR = new Color(255, 80, 80);
	private static final Color HP_MED_COLOR = new Color(255, 180, 80);
	private static final Color HP_HIGH_COLOR = new Color(80, 200, 80);

	// Alternating row colors
	private static final Color ROW_COLOR_1 = ColorScheme.DARK_GRAY_COLOR;
	private static final Color ROW_COLOR_2 = new Color(45, 45, 45);

	private static final int ICON_SIZE = 18;

	// Cached icons - shared across all panels
	private static final AtomicReference<ImageIcon> iconHitpoints = new AtomicReference<>();
	private static final AtomicReference<ImageIcon> iconPrayer = new AtomicReference<>();
	private static final AtomicReference<ImageIcon> iconAttack = new AtomicReference<>();
	private static final AtomicReference<ImageIcon> iconAgility = new AtomicReference<>();

	private static volatile boolean iconsLoaded = false;

	/**
	 * Load sprite icons if not already loaded.
	 */
	public static void loadSprites(SpriteManager spriteManager)
	{
		if (iconsLoaded)
		{
			return;
		}

		spriteManager.getSpriteAsync(SpriteID.SKILL_HITPOINTS, 0, img -> {
			iconHitpoints.set(scaleIcon(img));
		});

		spriteManager.getSpriteAsync(SpriteID.SKILL_PRAYER, 0, img -> {
			iconPrayer.set(scaleIcon(img));
		});

		spriteManager.getSpriteAsync(SpriteID.SKILL_ATTACK, 0, img -> {
			iconAttack.set(scaleIcon(img));
		});

		spriteManager.getSpriteAsync(SpriteID.SKILL_AGILITY, 0, img -> {
			iconAgility.set(scaleIcon(img));
		});

		iconsLoaded = true;
	}

	private static ImageIcon scaleIcon(BufferedImage img)
	{
		if (img == null)
		{
			return null;
		}
		Image scaled = img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
		return new ImageIcon(scaled);
	}

	/**
	 * Create a panel displaying a single tick's data for the live view.
	 *
	 * @param tick         The tick data to display
	 * @param rowIndex     The row index for alternating colors
	 * @param spriteManager The sprite manager for loading icons
	 * @return A JPanel containing the tick display
	 */
	public static JPanel createTickPanel(TickData tick, int rowIndex, SpriteManager spriteManager)
	{
		// Ensure sprites are loaded
		loadSprites(spriteManager);

		JPanel panel = new JPanel(new GridBagLayout());

		// Alternating row colors
		Color bgColor = (rowIndex % 2 == 0) ? ROW_COLOR_1 : ROW_COLOR_2;
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

		// Header: tick number, prayer, and HP
		String tickLabel = "#" + tick.getTickNumber();
		String hpText = tick.getCurrentHp() + "/" + tick.getMaxHp();
		String prayerText = tick.getCurrentPrayer() + "/" + tick.getMaxPrayer();

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(bgColor);

		JLabel tickLbl = new JLabel(tickLabel);
		tickLbl.setForeground(Color.WHITE);
		tickLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		headerPanel.add(tickLbl, BorderLayout.WEST);

		// Right side panel for prayer and HP
		JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		statsPanel.setBackground(bgColor);

		// Prayer points label
		JLabel prayerLbl = new JLabel(prayerText);
		prayerLbl.setForeground(PRAYER_COLOR);
		prayerLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		if (iconPrayer.get() != null)
		{
			prayerLbl.setIcon(iconPrayer.get());
			prayerLbl.setHorizontalTextPosition(SwingConstants.LEFT);
		}
		statsPanel.add(prayerLbl);

		// HP label
		JLabel hpLbl = new JLabel(hpText);
		hpLbl.setForeground(hpColor);
		hpLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		if (iconHitpoints.get() != null)
		{
			hpLbl.setIcon(iconHitpoints.get());
			hpLbl.setHorizontalTextPosition(SwingConstants.LEFT);
		}
		statsPanel.add(hpLbl);

		headerPanel.add(statsPanel, BorderLayout.EAST);

		panel.add(headerPanel, gbc);
		gbc.gridy++;

		// Content
		boolean hasContent = false;

		// Active prayers - each on its own line
		for (String prayer : tick.getActivePrayers())
		{
			JLabel label = createIconLabel(prayer, iconPrayer.get(), PRAYER_COLOR);
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
				JLabel label = createIconLabel(moveDesc, iconAgility.get(), MOVEMENT_COLOR);
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
			JLabel label = createIconLabel(damage.getDescription(), iconAttack.get(), color);
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

	private static ImageIcon getActionIcon(PlayerAction action)
	{
		String desc = action.getDescription().toLowerCase();
		if (desc.startsWith("ate") || desc.startsWith("drank"))
		{
			return iconHitpoints.get();
		}
		else if (desc.contains("prayer") || desc.startsWith("activated") || desc.startsWith("deactivated") || desc.contains("disabled"))
		{
			return iconPrayer.get();
		}
		else if (desc.startsWith("equipped") || desc.startsWith("unequipped"))
		{
			return iconAttack.get();
		}
		else if (desc.startsWith("attacked") || desc.startsWith("special"))
		{
			return iconAttack.get();
		}
		return null;
	}

	private static JLabel createIconLabel(String text, ImageIcon icon, Color color)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
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
}
