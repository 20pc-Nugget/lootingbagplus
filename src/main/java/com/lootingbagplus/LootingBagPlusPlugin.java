/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus;

import com.google.inject.Provides;
import com.lootingbagplus.model.BagSnapshot;
import com.lootingbagplus.ui.BagDragOverlay;
import com.lootingbagplus.ui.BagInputListener;
import com.lootingbagplus.ui.BagWindowController;
import com.lootingbagplus.ui.LootingBagOverlay;
import com.lootingbagplus.ui.LootingBagPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

/**
 * Looting Bag Plus: drag items around in the looting bag's Check window and have them stay where
 * you put them (client side only), see the bag in a side panel, and pin a movable floating viewer
 * over the game like RuneLite's Inventory Viewer.
 *
 * <p>This class is wiring only: registration and event fan-out. The bag itself lives in
 * {@link BagState}, the in-game window in {@link BagWindowController} and
 * {@link BagInputListener}, and the two viewers in {@link LootingBagPanel} and
 * {@link LootingBagOverlay}. Nothing here changes what the bag contains or sends a game action.
 */
@Slf4j
@PluginDescriptor(
	name = "Looting Bag Plus",
	description = "Arrange your looting bag by dragging items, and view it in a side panel or a floating overlay",
	tags = {"looting bag", "uim", "ultimate ironman", "inventory", "organize", "layout", "overlay", "panel", "viewer"}
)
public class LootingBagPlusPlugin extends Plugin
{
	/**
	 * The clientscript that rebuilds the bag's item grid, resetting every position we set. There is
	 * no ScriptID constant for it, so the raw id is used.
	 */
	private static final int SCRIPT_LOOTING_BAG_BUILD_ITEMS = 497;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private LootingBagPlusConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private BagState state;

	@Inject
	private BagWindowController windowController;

	@Inject
	private BagInputListener inputListener;

	@Inject
	private BagDragOverlay dragOverlay;

	@Inject
	private LootingBagOverlay overlay;

	private LootingBagPanel panel;
	private NavigationButton navButton;

	/** {@code MouseManager.registerMouseListener} does not de-duplicate, so this guards it. */
	private boolean inputRegistered;

	/** A 16x16 sack, drawn in code so the plugin needs no image resource. */
	private static final String[] ICON_ART = {
		"................",
		"......OOOO......",
		".....ORRRRO.....",
		"......ORRO......",
		".....OBBBBO.....",
		"....OBLLBBBO....",
		"...OBLLBBBBBO...",
		"..OBLLBBBBBBBO..",
		"..OBLBBBBBBBBO..",
		"..OBBBBBBBBBBO..",
		"..OBBBBBBBBBBO..",
		"..OBBBBBBBBBBO..",
		"...OBBBBBBBBO...",
		"....OOBBBBOO....",
		".....OOOOOO.....",
		"................"
	};

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.toggleKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			configManager.setConfiguration(LootingBagPlusConfig.GROUP, LootingBagPlusConfig.KEY_SHOW_OVERLAY, !config.showOverlay());
		}
	};

	@Provides
	LootingBagPlusConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootingBagPlusConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new LootingBagPanel(itemManager, this::onPanelOverlayToggled, this::onPanelResetRequested);
		panel.setOverlayChecked(config.showOverlay());

		BufferedImage icon = createIcon();
		navButton = NavigationButton.builder()
			.tooltip("Looting Bag")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		state.setListener(this::onSnapshotChanged);
		overlayManager.add(overlay);
		overlayManager.add(dragOverlay);
		keyManager.registerKeyListener(hotkeyListener);

		if (!inputRegistered)
		{
			mouseManager.registerMouseListener(0, inputListener);
			keyManager.registerKeyListener(inputListener);
			inputRegistered = true;
		}

		clientThread.invoke(() ->
		{
			windowController.startUp();
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				state.onProfileChanged(configManager.getRSProfileKey());
			}
			refreshPanel(state.getSnapshot());
		});
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		overlayManager.remove(dragOverlay);
		keyManager.unregisterKeyListener(hotkeyListener);

		if (inputRegistered)
		{
			mouseManager.unregisterMouseListener(inputListener);
			keyManager.unregisterKeyListener(inputListener);
			inputRegistered = false;
		}

		state.setListener(null);
		panel = null;
		navButton = null;

		// Save anything pending, then put the game's own cell order and title back.
		clientThread.invoke(() ->
		{
			state.flushNow();
			windowController.shutDown();
		});
	}

	// ---- game events --------------------------------------------------------------------------

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		windowController.tick();
		state.tick();
	}

	/** The precise "the grid was just rebuilt" signal: every firing wipes the positions we set. */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == SCRIPT_LOOTING_BAG_BUILD_ITEMS)
		{
			windowController.markDirty();
		}
	}

	/**
	 * The game transmitting the bag's contents. This is also what keeps the side panel and overlay
	 * live while the Check window is closed (checking the bag, using items on it, picking items up
	 * into it).
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.LOOTING_BAG)
		{
			state.updateContents(BagState.contentsOf(event.getItemContainer()));
			windowController.markDirty();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.WILDERNESS_LOOTINGBAG)
		{
			windowController.markDirty();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.WILDERNESS_LOOTINGBAG)
		{
			// The widgets are going away, so any in-flight drag is meaningless.
			windowController.cancelDrag();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadProfile();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			state.flushNow();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadProfile();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LootingBagPlusConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		String key = event.getKey();
		if (LootingBagPlusConfig.KEY_SHOW_OVERLAY.equals(key))
		{
			LootingBagPanel current = panel;
			if (current != null)
			{
				boolean show = config.showOverlay();
				SwingUtilities.invokeLater(() -> current.setOverlayChecked(show));
			}
		}
		else if (LootingBagPlusConfig.KEY_SHOW_VALUE.equals(key) || LootingBagPlusConfig.KEY_VALUE_TYPE.equals(key))
		{
			refreshPanel(state.getSnapshot());
		}
	}

	// ---- helpers ------------------------------------------------------------------------------

	/**
	 * The RS profile key is only stable once logged in. Loading on both the game state change and
	 * the profile change is safe because {@link BagState} ignores a repeat of the key it has, and
	 * routing through the client thread covers the profile event being posted from another thread.
	 */
	private void loadProfile()
	{
		String profileKey = configManager.getRSProfileKey();
		clientThread.invoke(() -> state.onProfileChanged(profileKey));
	}

	/** Client thread: the bag or its arrangement changed. */
	private void onSnapshotChanged(BagSnapshot snapshot)
	{
		windowController.markDirty();
		refreshPanel(snapshot);
	}

	private void refreshPanel(BagSnapshot snapshot)
	{
		LootingBagPanel current = panel;
		if (current == null)
		{
			return;
		}

		boolean showValue = config.showValue();
		PriceType valueType = config.valueType();
		SwingUtilities.invokeLater(() -> current.update(snapshot, showValue, valueType));
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++)
		{
			for (int x = 0; x < 16; x++)
			{
				switch (ICON_ART[y].charAt(x))
				{
					case 'O':
						image.setRGB(x, y, 0xFF28190A);
						break;
					case 'B':
						image.setRGB(x, y, 0xFF96642D);
						break;
					case 'L':
						image.setRGB(x, y, 0xFFBE8C46);
						break;
					case 'R':
						image.setRGB(x, y, 0xFF781E1E);
						break;
					default:
						break;
				}
			}
		}
		return image;
	}

	private void onPanelOverlayToggled(boolean show)
	{
		configManager.setConfiguration(LootingBagPlusConfig.GROUP, LootingBagPlusConfig.KEY_SHOW_OVERLAY, show);
	}

	private void onPanelResetRequested()
	{
		clientThread.invoke(() -> state.resetLayout());
	}
}
