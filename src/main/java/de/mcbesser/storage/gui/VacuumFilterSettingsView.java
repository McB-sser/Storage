package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
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

public class VacuumFilterSettingsView extends AbstractMenu {
    private static final int FILTER_SLOT_COUNT = 45;
    private final UUID shulkerId;

    public VacuumFilterSettingsView(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Filtereinstellungen"), 6);
        this.shulkerId = shulkerId;
    }

    @Override
    public UUID getShulkerId() {
        return shulkerId;
    }

    @Override
    public void setMenuItems(Player player) {
        inventory.clear();
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);

        for (int slot = 0; slot < FILTER_SLOT_COUNT; slot++) {
            String materialName = settings.getVacuumFilterSlots().get(slot);
            Material material = materialName != null ? Material.matchMaterial(materialName) : null;
            if (material != null) {
                inventory.setItem(slot, createItemWithLore(material, "Filter " + (slot + 1) + ": " + material.name(),
                        "Ducken + Rechtsklick: entfernen"));
            } else {
                inventory.setItem(slot, createItemWithLore(Material.LIME_STAINED_GLASS_PANE,
                        "Filter " + (slot + 1) + " leer",
                        "Klick mit Item am Cursor: setzen"));
            }
        }

        inventory.setItem(45, createSimpleItem(Material.ARROW, "Zur\u00fcck"));
        for (int slot = 46; slot < 53; slot++) {
            inventory.setItem(slot, createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, ""));
        }
        inventory.setItem(53, createItemWithLore(Material.HOPPER, "Filtermodus: "
                + (settings.isVacuumFilterEnabled() ? "NUR FILTER" : "ALLE ITEMS"),
                "Klick: umschalten"));
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);

        if (slot >= 0 && slot < FILTER_SLOT_COUNT) {
            if (clickType == ClickType.SHIFT_RIGHT) {
                settings.getVacuumFilterSlots().remove(slot);
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
                return;
            }

            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                settings.getVacuumFilterSlots().put(slot, cursor.getType().name());
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            return;
        }

        switch (slot) {
            case 45 -> new VacuumSettingsView(plugin, shulkerId).open(player);
            case 53 -> {
                settings.setVacuumFilterEnabled(!settings.isVacuumFilterEnabled());
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(Component.text("Einsaug-Filtermodus: "
                        + (settings.isVacuumFilterEnabled() ? "NUR FILTER" : "ALLE ITEMS"), NamedTextColor.YELLOW));
                setMenuItems(player);
            }
            default -> {
            }
        }
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

    private ItemStack createItemWithLore(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.YELLOW));
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
