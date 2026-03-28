package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.sidebar.StorageSidebar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class StorageSidebarListener implements Listener {
    private final Storage plugin;
    private final StorageSidebar sidebar;

    public StorageSidebarListener(Storage plugin, StorageSidebar sidebar) {
        this.plugin = plugin;
        this.sidebar = sidebar;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sidebar.scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sidebar.clear(event.getPlayer());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        sidebar.scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        sidebar.scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> sidebar.refresh(player));
    }
}
