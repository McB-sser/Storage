package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public class PermissionsMenu extends AbstractMenu {
    private final UUID shulkerId;

    public PermissionsMenu(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Berechtigungen verwalten"), 6);
        this.shulkerId = shulkerId;
    }

    @Override
    public UUID getShulkerId() {
        return shulkerId;
    }

    private UUID resolveStorageOwner(Player viewer) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        String ownerUuid = settings.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            try {
                return UUID.fromString(ownerUuid);
            } catch (IllegalArgumentException ignored) {
                // Fallback below.
            }
        }
        return viewer.getUniqueId();
    }

    @Override
    public void setMenuItems(Player player) {
        inventory.clear();
        UUID ownerUuid = resolveStorageOwner(player);
        PlayerLager ownerLager = plugin.getLagerManager().getLager(ownerUuid);
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);

        // Legacy migration: merge old per-shulker trusts into the new global player-lager trust list.
        boolean changed = false;
        for (UUID trusted : settings.getTrustedPlayers()) {
            if (!ownerLager.getTrustedPlayers().contains(trusted)) {
                ownerLager.getTrustedPlayers().add(trusted);
                changed = true;
            }
        }
        if (changed) {
            plugin.getLagerManager().saveLager(ownerUuid);
        }

        int slot = 0;
        for (UUID uuid : ownerLager.getTrustedPlayers()) {
            if (slot >= 45) {
                break;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            inventory.setItem(slot++, createPlayerHead(op));
        }

        for (int bottomSlot = 45; bottomSlot <= 53; bottomSlot++) {
            inventory.setItem(bottomSlot, createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
        }
        inventory.setItem(45, createItem(Material.ARROW, "Zur\u00fcck zum Hauptmen\u00fc"));
        inventory.setItem(53, createItem(Material.WRITABLE_BOOK, "Spieler hinzuf\u00fcgen"));
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        if (slot == 45) {
            new QuickSlotsView(plugin, shulkerId).open(player);
            return;
        }

        if (slot == 53) {
            openAddPlayerAnvil(player);
            return;
        }

        if (slot < 45 && clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
            UUID ownerUuid = resolveStorageOwner(player);
            PlayerLager ownerLager = plugin.getLagerManager().getLager(ownerUuid);
            if (slot < ownerLager.getTrustedPlayers().size()) {
                ownerLager.getTrustedPlayers().remove(slot);
                plugin.getLagerManager().saveLager(ownerUuid);
                player.sendMessage(Component.text("Spieler entfernt.", NamedTextColor.YELLOW));
                setMenuItems(player);
            }
        }
    }

    private void openAddPlayerAnvil(Player player) {
        plugin.getChatPromptManager().requestText(
                player,
                "Spieler hinzufuegen",
                "Spielername",
                input -> {
                    String name = input == null ? "" : input.trim();
                    if (name.isEmpty()) {
                        player.sendMessage(Component.text("Bitte gib einen Spielernamen ein.", NamedTextColor.RED));
                        this.open(player);
                        return;
                    }

                    OfflinePlayer target = resolveKnownPlayerByName(name);
                    if (target == null || target.getUniqueId() == null) {
                        player.sendMessage(Component.text(
                                "Spieler nicht gefunden. Der Spieler muss mindestens einmal auf dem Server gewesen sein.",
                                NamedTextColor.RED));
                        this.open(player);
                        return;
                    }

                    UUID ownerUuid = resolveStorageOwner(player);
                    PlayerLager ownerLager = plugin.getLagerManager().getLager(ownerUuid);
                    if (!ownerLager.getTrustedPlayers().contains(target.getUniqueId())) {
                        ownerLager.getTrustedPlayers().add(target.getUniqueId());
                        plugin.getLagerManager().saveLager(ownerUuid);
                        player.sendMessage(Component.text("Spieler " + name + " hinzugefuegt.", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Spieler " + name + " ist bereits berechtigt.", NamedTextColor.YELLOW));
                    }

                    this.open(player);
                },
                () -> this.open(player)
        );
    }

    private OfflinePlayer resolveKnownPlayerByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String knownName = offline.getName();
            if (knownName != null && knownName.equalsIgnoreCase(name)) {
                return offline;
            }
        }
        return null;
    }

    private ItemStack createPlayerHead(OfflinePlayer op) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            plugin.getPlayerHeadCache().applyCachedProfile(meta, op);
            meta.displayName(Component.text(op.getName() != null ? op.getName() : "Unbekannt").color(NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("Klicken zum Entfernen", NamedTextColor.RED)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.YELLOW));
            item.setItemMeta(meta);
        }
        return item;
    }
}
