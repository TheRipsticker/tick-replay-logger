package com.deathtrace.ui.playback;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Reusable stat box showing a label, progress bar, and value.
 * Used for HP and Prayer displays in the playback view.
 */
public class StatBoxPanel extends JPanel
{
	private static final Color HP_HIGH_COLOR = new Color(80, 200, 80);
	private static final Color HP_MED_COLOR = new Color(255, 180, 80);
	private static final Color HP_LOW_COLOR = new Color(255, 80, 80);
	private static final Color PRAYER_COLOR = new Color(150, 150, 255);

	private final JProgressBar progressBar;
	private final JLabel valueLabel;
	private final boolean isHpBar;

	private int currentValue = 0;
	private int maxValue = 1;

	public StatBoxPanel(String label, boolean isHpBar)
	{
		this.isHpBar = isHpBar;

		setLayout(new BorderLayout(5, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(2, 0, 2, 0));

		// Label on left
		JLabel nameLabel = new JLabel(label);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));
		nameLabel.setPreferredSize(new Dimension(30, 20));
		add(nameLabel, BorderLayout.WEST);

		// Progress bar in center
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(false);
		progressBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		progressBar.setBorderPainted(false);
		progressBar.setPreferredSize(new Dimension(100, 16));
		add(progressBar, BorderLayout.CENTER);

		// Value label on right
		valueLabel = new JLabel("0/0");
		valueLabel.setForeground(Color.WHITE);
		valueLabel.setFont(valueLabel.getFont().deriveFont(11f));
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		valueLabel.setPreferredSize(new Dimension(55, 20));
		add(valueLabel, BorderLayout.EAST);

		updateColors();
	}

	/**
	 * Update the displayed values.
	 */
	public void setValue(int current, int max)
	{
		this.currentValue = current;
		this.maxValue = Math.max(max, 1);

		int percent = (int) ((float) current / maxValue * 100);
		progressBar.setValue(percent);
		valueLabel.setText(current + "/" + max);

		updateColors();
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
