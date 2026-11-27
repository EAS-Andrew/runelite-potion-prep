package com.potionprep;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.ItemEvent;
import javax.swing.BorderFactory;
import javax.swing.JToggleButton;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** The Show in bank toggle, saying whether it's on: green "On", grey "Off". */
final class BankToggle
{
	private static final Color ON = new Color(0x2F7D32);

	private BankToggle()
	{
	}

	static void style(JToggleButton b)
	{
		// RuneLite's look draws a pressed toggle in its own grey text: paint it plainly
		// so the colours below are what you see.
		b.setUI(new BasicToggleButtonUI());
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setFont(FontManager.getRunescapeBoldFont());
		// Fires on clicks and on setSelected, so the label follows the setting either way.
		b.addItemListener(e -> paint(b, e.getStateChange() == ItemEvent.SELECTED));
		paint(b, b.isSelected());
	}

	private static void paint(JToggleButton b, boolean on)
	{
		b.setText("Show in bank: " + (on ? "On" : "Off"));
		b.setBackground(on ? ON : ColorScheme.DARKER_GRAY_COLOR);
		b.setForeground(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
	}
}
