# Looting Bag Plus

A RuneLite plugin that combines three things:

- **Organizer**: drag items around inside the looting bag's Check window and they stay where you
  put them (client side only, saved per character).
- **Side panel**: the bag in your arrangement, with slot count and total value.
- **Floating overlay**: a movable viewer like Inventory Viewer (Alt-drag to move), toggled from the
  config, the side panel or an optional hotkey.

Right-click your looting bag and choose **Check** once to sync it. The last known contents are
remembered between sessions.

Do not run this alongside the separate Looting Bag Organizer plugin: both re-position the same
widgets and consume the same mouse input.

The slot-pinning model and the widget re-positioning technique follow Looting Bag Organizer by
robrichardson13 (BSD 2-Clause). See LICENSE.
