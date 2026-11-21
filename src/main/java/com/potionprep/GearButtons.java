package com.potionprep;

import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

/** "Save worn gear" / "Clear": remember what you're wearing as this mode's gear. */
class GearButtons extends JPanel
{
	private final JButton save = new JButton("Save worn gear");
	private final JButton clear = new JButton("Clear");

	GearButtons(PotionPrepPlugin plugin, boolean farm)
	{
		super(new BorderLayout(4, 0));
		setOpaque(false);
		save.setFocusable(false);
		clear.setFocusable(false);
		save.setToolTipText("<html>Lay out what you're wearing now on the left of the bank view,<br>"
			+ "like the equipment tab. Pieces you're wearing show faded.</html>");
		clear.setToolTipText("Forget the saved gear");
		save.addActionListener(e -> plugin.saveWornGear(farm));
		clear.addActionListener(e -> plugin.clearGear(farm));
		add(save, BorderLayout.CENTER);
		add(clear, BorderLayout.EAST);
		setSaved(false);
	}

	void setSaved(boolean saved)
	{
		save.setText(saved ? "Update worn gear" : "Save worn gear");
		clear.setVisible(saved);
	}
}
