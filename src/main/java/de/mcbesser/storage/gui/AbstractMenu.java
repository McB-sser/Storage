package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public abstract class AbstractMenu implements InventoryHolder {
    protected final Inventory inventory;
    protected final Storage plugin;

    public AbstractMenu(Storage plugin, Component title, int rows) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public abstract void setMenuItems(Player player);

    public void open(Player player) {
        setMenuItems(player);
        player.openInventory(inventory);
    }

    public abstract void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType,
            int hotbarButton);

    public UUID getShulkerId() {
        return null;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

