package com.tickreplaylogger.ui.playback;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Reusable stat box showing a label, progress bar, and value.
 * Used for HP and Prayer displays in the playback view.
 * Highlights when values change to show damage/healing.
 */
public class StatBoxPanel extends JPanel
{
	private static final Color HP_HIGH_COLOR = new Color(80, 200, 80);
	private static final Color HP_MED_COLOR = new Color(255, 180, 80);
	private static final Color HP_LOW_COLOR = new Color(255, 80, 80);
	private static final Color PRAYER_COLOR = new Color(150, 150, 255);

	// Highlight colors for changes
	private static final Color HIGHLIGHT_DAMAGE = new Color(255, 60, 60, 180);
	private static final Color HIGHLIGHT_HEAL = new Color(60, 255, 60, 180);
	private static final Color HIGHLIGHT_DRAIN = new Color(100, 100, 255, 180);

	private static final Border NORMAL_BORDER = new EmptyBorder(2, 0, 2, 0);

	private final JProgressBar progressBar;
	private final JLabel valueLabel;
	private final JLabel changeLabel;
	private final boolean isHpBar;

	private int currentValue = 0;
	private int maxValue = 1;
	private int previousValue = -1;

	public StatBoxPanel(String label, boolean isHpBar)
	{
		this.isHpBar = isHpBar;

		setLayout(new BorderLayout(5, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(NORMAL_BORDER);

		// Label on left
		JLabel nameLabel = new JLabel(label);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		nameLabel.setPreferredSize(new Dimension(30, 22));
		add(nameLabel, BorderLayout.WEST);

		// Progress bar in center
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(false);
		progressBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		progressBar.setBorderPainted(false);
		progressBar.setPreferredSize(new Dimension(100, 18));
		add(progressBar, BorderLayout.CENTER);

		// Right panel with value and change indicator
		JPanel rightPanel = new JPanel(new BorderLayout(2, 0));
		rightPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Value label
		valueLabel = new JLabel("0/0");
		valueLabel.setForeground(Color.WHITE);
		valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		rightPanel.add(valueLabel, BorderLayout.CENTER);

		// Change indicator (shows +X or -X)
		changeLabel = new JLabel("");
		changeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		changeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		changeLabel.setPreferredSize(new Dimension(38, 22));
		rightPanel.add(changeLabel, BorderLayout.EAST);

		rightPanel.setPreferredSize(new Dimension(85, 22));
		add(rightPanel, BorderLayout.EAST);

		updateColors();
	}

	/**
	 * Update the displayed values.
	 */
	public void setValue(int current, int max)
	{
		int oldValue = this.currentValue;
		this.currentValue = current;
		this.maxValue = Math.max(max, 1);

		int percent = (int) ((float) current / maxValue * 100);
		progressBar.setValue(percent);
		valueLabel.setText(current + "/" + max);

		// Show change indicator if value changed (and not first load)
		if (previousValue >= 0 && current != oldValue)
		{
			int delta = current - oldValue;
			updateChangeIndicator(delta);
		}
		else
		{
			clearChangeIndicator();
		}

		previousValue = oldValue;
		updateColors();
	}

	/**
	 * Reset tracking for a new recording playback.
	 */
	public void reset()
	{
		previousValue = -1;
		clearChangeIndicator();
	}

	private void updateChangeIndicator(int delta)
	{
		if (delta > 0)
		{
			// Healing/restore
			changeLabel.setText("+" + delta);
			changeLabel.setForeground(HIGHLIGHT_HEAL);
			setBorder(new CompoundBorder(
				new LineBorder(HIGHLIGHT_HEAL, 1),
				new EmptyBorder(1, 0, 1, 0)
			));
		}
		else if (delta < 0)
		{
			// Damage/drain
			changeLabel.setText(String.valueOf(delta));
			Color highlightColor = isHpBar ? HIGHLIGHT_DAMAGE : HIGHLIGHT_DRAIN;
			changeLabel.setForeground(highlightColor);
			setBorder(new CompoundBorder(
				new LineBorder(highlightColor, 1),
				new EmptyBorder(1, 0, 1, 0)
			));
		}
	}

	private void clearChangeIndicator()
	{
		changeLabel.setText("");
		setBorder(NORMAL_BORDER);
	}

	private void updateColors()
	{
		Color barColor;
		if (isHpBar)
		{
			float percent = (float) currentValue / maxValue;
			if (percent > 0.5f)
			{
				barColor = HP_HIGH_COLOR;
			}
			else if (percent > 0.25f)
			{
				barColor = HP_MED_COLOR;
			}
			else
			{
				barColor = HP_LOW_COLOR;
			}
		}
		else
		{
			barColor = PRAYER_COLOR;
		}

		progressBar.setForeground(barColor);
		valueLabel.setForeground(barColor);
	}
}
