package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.models.PlayerLager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExpStorageView extends AbstractMenu {
    private final UUID shulkerId;

    public ExpStorageView(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("EXP-Speicher"), 3);
        this.shulkerId = shulkerId;
    }

    @Override
    public UUID getShulkerId() {
        return shulkerId;
    }

    @Override
    public void setMenuItems(Player player) {
        inventory.clear();
        PlayerLager lager = plugin.getLagerManager().getLager(player.getUniqueId());

        inventory.setItem(13, createItem(Material.EXPERIENCE_BOTTLE, "EXP-Speicher",
                List.of(
                        "Gespeichert (global): " + lager.getStoredExp() + " XP",
                        "Linksklick: gesamte Spieler-XP einlagern",
                        "Rechtsklick: 100 XP als Orbs ausgeben",
                        "Shift-Rechtsklick: alles ausgeben")));
        inventory.setItem(26, createSimpleItem(Material.ARROW, "ZurÃƒÂ¼ck"));
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        PlayerLager lager = plugin.getLagerManager().getLager(player.getUniqueId());

        switch (slot) {
            case 13 -> {
                if (clickType == ClickType.LEFT) {
                    int current = getPlayerTotalExperience(player);
                    if (current <= 0) {
                        player.sendMessage(Component.text("Du hast keine XP zum Einlagern.", NamedTextColor.YELLOW));
                        return;
                    }
                    lager.addStoredExp(current);
                    plugin.getLagerManager().saveLager(player.getUniqueId());
                    setPlayerTotalExperience(player, 0);
                    player.sendMessage(Component.text(current + " XP global eingelagert.", NamedTextColor.GREEN));
                    setMenuItems(player);
                } else if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
                    int requested = clickType == ClickType.SHIFT_RIGHT ? lager.getStoredExp() : 100;
                    int taken = lager.takeStoredExp(requested);
                    if (taken <= 0) {
                        player.sendMessage(Component.text("Kein XP im Speicher.", NamedTextColor.YELLOW));
                        return;
                    }
                    plugin.getLagerManager().saveLager(player.getUniqueId());
                    dropExperience(player, taken);
                    player.sendMessage(Component.text(taken + " XP (global) als Orbs ausgegeben.", NamedTextColor.GREEN));
                    setMenuItems(player);
                }
            }
            case 26 -> new QuickSlotsView(plugin, shulkerId).open(player);
        }
    }

    private void dropExperience(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int orbValue = Math.min(remaining, 100);
            ExperienceOrb orb = player.getWorld().spawn(player.getLocation().add(0, 0.5, 0), ExperienceOrb.class);
            orb.setExperience(orbValue);
            remaining -= orbValue;
        }
    }

    private int getPlayerTotalExperience(Player player) {
        int level = player.getLevel();
        float progress = player.getExp();
        return getTotalExperienceForLevel(level) + Math.round(progress * player.getExpToLevel());
    }

    private void setPlayerTotalExperience(Player player, int exp) {
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);

        int level = 0;
        while (exp >= getExpAtLevel(level)) {
            exp -= getExpAtLevel(level);
            level++;
            if (level > 10000) {
                break;
            }
        }
        player.setLevel(level);
        int toNext = getExpAtLevel(level);
        if (toNext > 0) {
            player.setExp(Math.min(1.0f, exp / (float) toNext));
        }
    }

    private int getExpAtLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    private int getTotalExperienceForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
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

    private ItemStack createItem(Material material, String name, List<String> loreLines) {
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


