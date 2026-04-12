package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.managers.StorageDisplayManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.UUID;

public final class StorageDisplayListener implements Listener {
    private final Storage plugin;
    private final StorageDisplayManager displayManager;

    public StorageDisplayListener(Storage plugin, StorageDisplayManager displayManager) {
        this.plugin = plugin;
        this.displayManager = displayManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDisplayInteract(PlayerInteractAtEntityEvent event) {
        handleRightClick(event.getPlayer(), event.getRightClicked());
        if (displayManager.isDisplayEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDisplayInteract(PlayerInteractEntityEvent event) {
        handleRightClick(event.getPlayer(), event.getRightClicked());
        if (displayManager.isDisplayEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private void handleRightClick(Player player, Entity entity) {
        if (!displayManager.isDisplayEntity(entity)) {
            return;
        }

        UUID shulkerId = displayManager.getDisplayShulkerId(entity);
        int slot = displayManager.getDisplaySlot(entity);
        if (shulkerId == null || slot < 0) {
            return;
        }

        displayManager.handleDisplayUse(player, shulkerId, slot, true);
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDisplayDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!displayManager.isDisplayEntity(event.getEntity())) {
            return;
        }

        event.setCancelled(true);
        UUID shulkerId = displayManager.getDisplayShulkerId(event.getEntity());
        int slot = displayManager.getDisplaySlot(event.getEntity());
        if (shulkerId == null || slot < 0) {
            return;
        }

        displayManager.handleDisplayUse(player, shulkerId, slot, false);
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
    }
}
