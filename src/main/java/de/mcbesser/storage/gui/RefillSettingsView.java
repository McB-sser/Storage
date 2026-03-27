package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.models.ShulkerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RefillSettingsView extends AbstractMenu {
    private final UUID shulkerId;

    public RefillSettingsView(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Auto-Nachf\u00fcll Einstellungen"), 3);
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

        Material mat = settings.getFillItemMaterial() != null ? Material.matchMaterial(settings.getFillItemMaterial())
                : null;

        ItemStack refillItem = new ItemStack(mat != null ? mat : Material.BARRIER);
        ItemMeta meta = refillItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Nachf\u00fcll-Item: " + (mat != null ? mat.name() : "Keines"))
                    .color(NamedTextColor.YELLOW));
            meta.lore(List.of(
                    Component.text("Klicken mit Item am Mauszeiger,", NamedTextColor.GRAY),
                    Component.text("um das Nachf\u00fcll-Item festzulegen.", NamedTextColor.GRAY),
                    Component.text("Rechtsklick: Zur\u00fccksetzen", NamedTextColor.GRAY)));
            refillItem.setItemMeta(meta);
        }
        inventory.setItem(11, refillItem);

        inventory.setItem(13, createItem(Material.IRON_INGOT, "Mindestbestand: " + settings.getMinStock()));
        inventory.setItem(15, createItem(Material.HOPPER, "Entnahmemenge: " + settings.getWithdrawAmount()));
        inventory.setItem(26, createItem(Material.ARROW, "Zur\u00fcck zum Hauptmen\u00fc"));
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        if (slot == 26) {
            new QuickSlotsView(plugin, shulkerId).open(player);
            return;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);

        if (slot == 11) {
            if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
                settings.setFillItemMaterial(null);
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(Component.text("Nachf\u00fcll-Item zur\u00fcckgesetzt.", NamedTextColor.YELLOW));
                setMenuItems(player);
                return;
            }
            ItemStack cursor = player.getItemOnCursor();
            if (cursor.getType() != Material.AIR) {
                settings.setFillItemMaterial(cursor.getType().name());
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(
                        Component.text("Nachf\u00fcll-Item gesetzt auf: " + cursor.getType().name(), NamedTextColor.GREEN));
                setMenuItems(player);
            } else {
                player.sendMessage(Component.text("Bitte nimm ein Item in den Mauszeiger!", NamedTextColor.RED));
            }
        } else if (slot == 13) {
            openAnvilNumber(player, "Mindestbestand", settings.getMinStock(), (val) -> {
                settings.setMinStock(val);
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
            });
        } else if (slot == 15) {
            openAnvilNumber(player, "Entnahmemenge", settings.getWithdrawAmount(), (val) -> {
                settings.setWithdrawAmount(val);
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
            });
        }
    }

    private void openAnvilNumber(Player player, String title, int current, java.util.function.Consumer<Integer> callback) {
        new AnvilGUI.Builder()
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }
                    try {
                        int val = Integer.parseInt(snapshot.getText());
                        callback.accept(val);
                        plugin.getServer().getScheduler().runTask(plugin, () -> this.open(player));
                        return List.of(AnvilGUI.ResponseAction.close());
                    } catch (NumberFormatException e) {
                        return Collections.emptyList();
                    }
                })
                .text(String.valueOf(current))
                .itemLeft(new ItemStack(Material.PAPER))
                .title(title)
                .plugin(plugin)
                .open(player);
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
