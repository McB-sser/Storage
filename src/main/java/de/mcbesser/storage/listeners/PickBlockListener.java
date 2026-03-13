package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

public class PickBlockListener implements Listener {
    private final Storage plugin;

    public PickBlockListener(Storage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickItem(PlayerPickItemEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.getType().name().contains("SHULKER_BOX") || !hand.hasItemMeta()) {
            return;
        }

        String shulkerIdRaw = hand.getItemMeta().getPersistentDataContainer()
                .get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
        if (shulkerIdRaw == null || shulkerIdRaw.isBlank()) {
            return;
        }

        UUID shulkerId;
        try {
            shulkerId = UUID.fromString(shulkerIdRaw);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            return;
        }

        Material targetMaterial = target.getType();
        if (targetMaterial == Material.AIR || !targetMaterial.isItem()) {
            return;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        UUID storageOwner = resolveStorageOwner(player, settings);
        if (!canAccessStorage(player, shulkerId, storageOwner)) {
            return;
        }

        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
        int available = lager.getAmountByMaterial(targetMaterial);
        if (available <= 0) {
            return;
        }

        int amount = Math.min(available, targetMaterial.getMaxStackSize());
        int removed = lager.removeByMaterial(targetMaterial, amount);
        if (removed <= 0) {
            return;
        }

        plugin.getLagerManager().saveLager(storageOwner);

        ItemStack give = new ItemStack(targetMaterial, removed);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(give);
        for (ItemStack rest : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }

        event.setCancelled(true);
    }

    private UUID resolveStorageOwner(Player player, ShulkerSettings settings) {
        String ownerUuidStr = settings.getOwnerUuid();
        if (ownerUuidStr == null || ownerUuidStr.isBlank()) {
            return player.getUniqueId();
        }
        try {
            return UUID.fromString(ownerUuidStr);
        } catch (IllegalArgumentException ignored) {
            return player.getUniqueId();
        }
    }

    private boolean canAccessStorage(Player player, UUID shulkerId, UUID owner) {
        if (owner.equals(player.getUniqueId())) {
            return true;
        }

        PlayerLager ownerLager = plugin.getLagerManager().getLager(owner);
        if (ownerLager.getTrustedPlayers().contains(player.getUniqueId())) {
            return true;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        return settings.getTrustedPlayers().contains(player.getUniqueId());
    }
}

