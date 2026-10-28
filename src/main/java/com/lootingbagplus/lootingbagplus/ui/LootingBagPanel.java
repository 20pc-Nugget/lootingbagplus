/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.ui;

import com.lootingbagplus.PriceType;
import com.lootingbagplus.model.BagGeometry;
import com.lootingbagplus.model.BagSnapshot;
import com.lootingbagplus.model.BagSnapshot.SlotView;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * The looting bag side panel: the bag drawn in the player's own arrangement, its slot count and
 * total value, a switch for the floating overlay, and a way to forget the saved arrangement.
 *
 * <p>Swing thread only. The plugin hands it immutable {@link BagSnapshot}s built on the client
 * thread, so this class never touches the game client.
 */
public class LootingBagPanel extends PluginPanel
{
	private static final Dimension CELL_SIZE = new Dimension(BagGeometry.CELL_W + 10, BagGeometry.CELL_H + 6);
	private static final String HINT = "<html><div style='width: 190px'>"
		+ "Right-click your looting bag and choose <b>Check</b> to sync it. "
		+ "Drag items around in the Check window to arrange them; the arrangement shows here too."
		+ "</div></html>";

	private final ItemManager itemManager;
	private final Runnable onResetRequested;

	private final JLabel slotsLabel = new JLabel();
	private final JLabel valueLabel = new JLabel();
	private final JPanel grid = new JPanel(new GridLayout(BagGeometry.ROWS, BagGeometry.COLS, 4, 4));
	private final JCheckBox overlayToggle = new JCheckBox("Show floating overlay");

	/**
	 * @param onOverlayToggled called with the new state when the player ticks or unticks the
	 *                         overlay checkbox
	 * @param onResetRequested called after the player confirms forgetting their arrangement
	 */
	public LootingBagPanel(ItemManager itemManager, Consumer<Boolean> onOverlayToggled, Runnable onResetRequested)
	{
		super();
		this.itemManager = itemManager;
		this.onResetRequested = onResetRequested;

		JLabel title = new JLabel("Looting Bag");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		slotsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		valueLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);

		overlayToggle.setBackground(ColorScheme.DARK_GRAY_COLOR);
		overlayToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		overlayToggle.setFocusPainted(false);
		overlayToggle.addActionListener(e -> onOverlayToggled.accept(overlayToggle.isSelected()));

		JButton resetButton = new JButton("Reset arrangement");
		resetButton.setFocusPainted(false);
		resetButton.addActionListener(e -> confirmReset());

		JLabel hint = new JLabel(HINT);
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		add(title);
		add(slotsLabel);
		add(valueLabel);
		add(grid);
		add(overlayToggle);
		add(resetButton);
		add(hint);

		update(BagSnapshot.UNKNOWN, true, PriceType.GRAND_EXCHANGE);
	}

	/** Reflects the overlay setting without firing the toggle callback. */
	public void setOverlayChecked(boolean checked)
	{
		overlayToggle.setSelected(checked);
	}

	/**
	 * Redraws the bag.
	 *
	 * @param showValue whether to show the total and per-stack values
	 * @param valueType which price the values use
	 */
	public void update(BagSnapshot snapshot, boolean showValue, PriceType valueType)
	{
		if (snapshot.isKnown())
		{
			slotsLabel.setText(snapshot.getUsedSlots() + " / " + BagGeometry.SLOTS + " slots used");
		}
		else
		{
			slotsLabel.setText("Not synced yet - check your bag");
		}

		valueLabel.setVisible(showValue && snapshot.isKnown());
		if (showValue && snapshot.isKnown())
		{
			boolean alch = valueType == PriceType.HIGH_ALCHEMY;
			long total = alch ? snapshot.getHaValue() : snapshot.getGeValue();
			valueLabel.setText("Value: " + QuantityFormatter.formatNumber(total) + " gp (" + (alch ? "HA" : "GE") + ")");
		}

		grid.removeAll();
		for (int slot = 0; slot < BagGeometry.SLOTS; slot++)
		{
			grid.add(createCell(snapshot.slot(slot), showValue, valueType));
		}
		grid.revalidate();
		grid.repaint();
	}

	private JLabel createCell(SlotView view, boolean showValue, PriceType valueType)
	{
		JLabel cell = new JLabel();
		cell.setOpaque(true);
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setHorizontalAlignment(SwingConstants.CENTER);
		cell.setPreferredSize(CELL_SIZE);

		if (view != null)
		{
			AsyncBufferedImage image = itemManager.getImage(view.getItemId(), view.getQuantity(), view.isStackable());
			if (image != null)
			{
				image.addTo(cell);
			}
			cell.setToolTipText(tooltip(view, showValue, valueType));
		}

		return cell;
	}

	private static String tooltip(SlotView view, boolean showValue, PriceType valueType)
	{
		StringBuilder sb = new StringBuilder("<html>").append(escape(view.getName()));
		if (view.getQuantity() > 1)
		{
			sb.append(" x ").append(QuantityFormatter.formatNumber(view.getQuantity()));
		}
		if (showValue)
		{
			long unit = valueType == PriceType.HIGH_ALCHEMY ? view.getHaPrice() : view.getGePrice();
			sb.append("<br>").append(QuantityFormatter.formatNumber(unit * view.getQuantity())).append(" gp");
		}
		return sb.append("</html>").toString();
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void confirmReset()
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Forget your saved looting bag arrangement and return to the game's own order?",
			"Reset arrangement", JOptionPane.YES_NO_OPTION);
		if (choice == JOptionPane.YES_OPTION)
		{
			onResetRequested.run();
		}
	}
}
