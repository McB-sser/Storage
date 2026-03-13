package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.gui.AbstractMenu;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class MiddleClickStoreListener implements Listener {
    private final Storage plugin;

    public MiddleClickStoreListener(Storage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMiddleClickStore(InventoryClickEvent event) {
        if (event.getClick() != ClickType.SHIFT_RIGHT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof AbstractMenu) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        boolean inPlayerInventory = event.getClickedInventory() instanceof PlayerInventory
                || event.getClickedInventory().equals(event.getView().getBottomInventory());
        if (!inPlayerInventory) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        ActiveStorage targetStorage = findAccessibleStorage(player);
        if (targetStorage == null) {
            return;
        }

        int added = plugin.getLagerManager().addItemToLager(
                targetStorage.ownerUuid(),
                targetStorage.shulkerId(),
                clicked);
        if (added <= 0) {
            return;
        }

        event.setCancelled(true);
        if (added >= clicked.getAmount()) {
            event.setCurrentItem(null);
        } else {
            clicked.setAmount(clicked.getAmount() - added);
        }
        player.sendMessage(Component.text(added + "x ", NamedTextColor.GREEN)
                .append(Component.translatable(clicked.getType().translationKey()).color(NamedTextColor.GREEN))
                .append(Component.text(" ins Lager eingelagert.", NamedTextColor.GREEN)));
    }

    private ActiveStorage findAccessibleStorage(Player player) {
        ActiveStorage own = null;
        ActiveStorage trusted = null;

        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem == null || !invItem.hasItemMeta() || !invItem.getType().name().contains("SHULKER_BOX")) {
                continue;
            }

            String id = invItem.getItemMeta().getPersistentDataContainer().get(RecipeManager.SHULKER_KEY,
                    PersistentDataType.STRING);
            if (id == null || id.isBlank()) {
                continue;
            }

            UUID shulkerId;
            try {
                shulkerId = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
            UUID owner = resolveOwner(player, settings);

            if (owner.equals(player.getUniqueId())) {
                if (own == null) {
                    own = new ActiveStorage(owner, shulkerId);
                }
                continue;
            }

            if (trusted == null && canAccessStorage(player, shulkerId, owner)) {
                trusted = new ActiveStorage(owner, shulkerId);
            }
        }

        return own != null ? own : trusted;
    }

    private UUID resolveOwner(Player player, ShulkerSettings settings) {
        String ownerUuid = settings.getOwnerUuid();
        if (ownerUuid == null || ownerUuid.isBlank()) {
            return player.getUniqueId();
        }
        try {
            return UUID.fromString(ownerUuid);
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

    private record ActiveStorage(UUID ownerUuid, UUID shulkerId) {
    }
}

