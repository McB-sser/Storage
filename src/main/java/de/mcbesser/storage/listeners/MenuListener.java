package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.gui.AbstractMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

public class MenuListener implements Listener {
    private final Storage plugin;

    public MenuListener(Storage plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AbstractMenu menu) {
            // Cancel only clicks within the custom menu inventory
            if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() == menu) {
                event.setCancelled(true);
                menu.handleInteraction(player, event.getSlot(), event.getCurrentItem(), event.getClick(),
                        event.getHotbarButton());
            } else if (event.getClick().isShiftClick()) {
                // Prevent shift-click from moving items to the menu slots
                event.setCancelled(true);
                if (event.getCurrentItem() != null && event.getCurrentItem().getType() != org.bukkit.Material.AIR) {
                    int amount = event.getCurrentItem().getAmount();
                    int added = plugin.getLagerManager().addItemToLager(player.getUniqueId(), menu.getShulkerId(),
                            event.getCurrentItem());
                    if (added > 0) {
                        if (added >= amount) {
                            event.getCurrentItem().setAmount(0);
                        } else {
                            event.getCurrentItem().setAmount(amount - added);
                        }
                    }
                    // Refresh menu items to show updated counts if applicable
                    menu.setMenuItems(player);
                }
            }
        }
    }
}

