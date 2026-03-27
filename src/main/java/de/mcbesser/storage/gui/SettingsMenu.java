package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class SettingsMenu extends AbstractMenu {
    private final UUID shulkerId;

    public SettingsMenu(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Lager Shulker - Einstellungen"), 3);
        this.shulkerId = shulkerId;
    }

    @Override
    public UUID getShulkerId() {
        return shulkerId;
    }

    @Override
    public void setMenuItems(Player player) {
        inventory.clear();

        inventory.setItem(11, createItem(Material.PAPER, "Berechtigungen verwalten"));
        inventory.setItem(15, createItem(Material.PINK_DYE, "Farbe w\u00e4hlen"));

        inventory.setItem(26, createItem(Material.ARROW, "Zur\u00fcck zum Hauptmen\u00fc"));
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType,
            int hotbarButton) {
        if (slot == 26) {
            new QuickSlotsView(plugin, shulkerId).open(player);
            return;
        }

        switch (slot) {
            case 11 -> new PermissionsMenu(plugin, shulkerId).open(player);
            case 15 -> new ColorMenu(plugin, shulkerId).open(player);
        }
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
