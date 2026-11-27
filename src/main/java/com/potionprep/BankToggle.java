package com.potionprep;

import java.awt.Color;
import java.awt.event.ItemEvent;
import javax.swing.JToggleButton;
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
		b.setFont(FontManager.getRunescapeBoldFont());
		// Fires on clicks and on setSelected, so the label follows the setting either way.
		b.addItemListener(e -> paint(b, e.getStateChange() == ItemEvent.SELECTED));
		paint(b, b.isSelected());
	}

	private static void paint(JToggleButton b, boolean on)
	{
		b.setText("Show in bank: " + (on ? "On" : "Off"));
		b.setBackground(on ? ON : ColorScheme.DARK_GRAY_COLOR);
		b.setForeground(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
	}
}
