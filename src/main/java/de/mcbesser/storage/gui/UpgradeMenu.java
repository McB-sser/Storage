package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpgradeMenu extends AbstractMenu {
    private final UUID shulkerId;

    public UpgradeMenu(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Upgrades - Lager von: " + resolveOwnerName(plugin, shulkerId)), 3);
        this.shulkerId = shulkerId;
    }

    private static String resolveOwnerName(Storage plugin, UUID shulkerId) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        String ownerName = settings.getOwnerName();
        if (ownerName == null || ownerName.isBlank()) {
            return "Unbekannt";
        }
        return ownerName;
    }

    private UUID resolveStorageOwner(Player player) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        String ownerUuid = settings.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            try {
                return UUID.fromString(ownerUuid);
            } catch (IllegalArgumentException ignored) {
                // Fallback below.
            }
        }
        return player.getUniqueId();
    }

    @Override
    public UUID getShulkerId() {
        return shulkerId;
    }

    @Override
    public void setMenuItems(Player player) {
        inventory.clear();

        UUID storageOwner = resolveStorageOwner(player);
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
        String ownerName = resolveOwnerName(plugin, shulkerId);

        inventory.setItem(11, createUpgradeItem(Material.ENDER_CHEST, "Slot-Upgrade",
                List.of(
                        "Besitzer: " + ownerName,
                        "Freie Slots (global): " + lager.getUnlockedSlots(),
                        "Benutzt: " + lager.getItems().size(),
                        "Kosten: 1x Endertruhe",
                        "Klick: +27 Slots freischalten")));

        inventory.setItem(15, createUpgradeItem(Material.CHEST, "Kapazit\u00e4ts-Upgrade",
                List.of(
                        "Besitzer: " + ownerName,
                        "Kapazit\u00e4t (global): " + lager.getUsedAmount() + "/" + lager.getCapacity(),
                        "Upgrade +1728 pro Truhe",
                        "Kosten: 1x Truhe",
                        "Klick: Kapazit\u00e4t erh\u00f6hen")));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, ""));
            }
        }

        inventory.setItem(18, createSimpleItem(Material.ARROW, "Zur\u00fcck"));
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        UUID storageOwner = resolveStorageOwner(player);
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);

        switch (slot) {
            case 11 -> {
                if (!consumeMaterial(player, Material.ENDER_CHEST, 1)) {
                    player.sendMessage(Component.text("Du brauchst 1x Endertruhe.", NamedTextColor.RED));
                    return;
                }
                boolean changed = lager.upgradeSlot();
                plugin.getLagerManager().saveLager(storageOwner);
                if (changed) {
                    player.sendMessage(Component.text("Slots freigeschaltet (global). Neu: " + lager.getUnlockedSlots(),
                            NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Slots erh\u00f6ht, aber Integer-Grenze erreicht.",
                            NamedTextColor.YELLOW));
                }
                setMenuItems(player);
            }
            case 15 -> {
                if (!consumeMaterial(player, Material.CHEST, 1)) {
                    player.sendMessage(Component.text("Du brauchst 1x Truhe.", NamedTextColor.RED));
                    return;
                }
                lager.upgradeCapacity();
                plugin.getLagerManager().saveLager(storageOwner);
                player.sendMessage(Component.text("Kapazit\u00e4t (global) erh\u00f6ht auf " + lager.getCapacity(),
                        NamedTextColor.GREEN));
                setMenuItems(player);
            }
            case 18 -> new QuickSlotsView(plugin, shulkerId).open(player);
        }
    }

    private boolean consumeMaterial(Player player, Material material, int amount) {
        int available = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                available += stack.getAmount();
            }
        }
        if (available < amount) {
            return false;
        }

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            if (remaining <= 0) {
                player.getInventory().setContents(contents);
                return true;
            }
        }
        return false;
    }

    private ItemStack createSimpleItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.YELLOW));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createUpgradeItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line, NamedTextColor.GRAY));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
