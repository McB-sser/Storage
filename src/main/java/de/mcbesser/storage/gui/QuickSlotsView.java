package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.models.ItemCategory;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import de.mcbesser.storage.models.StorageItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class QuickSlotsView extends AbstractMenu {
    private final UUID shulkerId;

    public QuickSlotsView(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Lager von: " + resolveOwnerName(plugin, shulkerId)), 6);
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
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        if (settings.getOwnerUuid() == null || settings.getOwnerUuid().isBlank()) {
            settings.setOwnerUuid(player.getUniqueId().toString());
        }
        if (settings.getOwnerName() == null || settings.getOwnerName().isBlank()) {
            settings.setOwnerName(player.getName());
        }
        plugin.getLagerManager().saveShulkerSettings(shulkerId);
        UUID storageOwner = resolveStorageOwner(player);
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);

        for (int categoryNumber = 1; categoryNumber <= 9; categoryNumber++) {
            int slot = categoryNumber - 1;
            inventory.setItem(slot, createCategoryItem(settings, categoryNumber));
        }

        // Slots 9-44 are for learning/assigned quick slots
        for (int i = 9; i < 45; i++) {
            String materialName = settings.getQuickSlots().get(i);
            if (materialName != null) {
                Material mat = Material.matchMaterial(materialName);
                if (mat != null) {
                    int count = 0;
                    for (StorageItem si : lager.getItems()) {
                        ItemStack stack = si.toItemStack();
                        if (stack != null && stack.getType() == mat) {
                            count = si.getAmount();
                            break;
                        }
                    }
                    inventory.setItem(i, createQuickSlotItem(mat, count,
                            "Linksklick: 1 Item nehmen",
                            "Rechtsklick: 1 Stack nehmen",
                            "Q/Drop: Inventar mit diesem Block f\u00fcllen",
                            "Shift-Rechtsklick: Slot l\u00f6schen"));
                    continue;
                }
            }
            inventory.setItem(i, createItemWithLore(Material.LIME_STAINED_GLASS_PANE,
                    "Slot " + (i + 1) + " (Klicken zum Belegen)",
                    "Klick mit Item am Cursor: Slot belegen",
                    "Mittelklick mit Cursor-Item: Slot belegen"));
        }

        addNavigationItems(player);
    }

    private ItemStack createCategoryItem(ShulkerSettings settings, int categoryNumber) {
        Material icon = Material.matchMaterial(settings.getCategoryIconMaterial(categoryNumber));
        if (icon == null) {
            ItemCategory fallback = ItemCategory.fromNumber(categoryNumber);
            icon = fallback != null ? fallback.getIcon() : Material.CHEST;
        }
        String categoryName = settings.getCategoryName(categoryNumber);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(categoryNumber + ": " + categoryName)
                    .color(NamedTextColor.AQUA));
            meta.lore(List.of(
                    Component.text("Klicken: Kategorie \u00f6ffnen", NamedTextColor.GRAY),
                    Component.text("Mittelklick + Cursor-Item: Icon tauschen", NamedTextColor.GRAY),
                    Component.text("Rechtsklick: Begriff umbenennen", NamedTextColor.GRAY),
                    Component.text("Filter in der Lageransicht", NamedTextColor.DARK_GRAY)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addNavigationItems(Player player) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        PlayerLager lager = plugin.getLagerManager().getLager(resolveStorageOwner(player));
        inventory.setItem(45, createItemWithLore(Material.HOPPER_MINECART, "Filtereinstellungen",
                "Filterseite f\u00fcr Einsaugen \u00f6ffnen"));
        inventory.setItem(46, createItemWithLore(Material.BLAZE_POWDER, "Einsaugen: "
                + (settings.isVacuumEnabled() ? "AN" : "AUS"),
                "Linksklick: An/Aus",
                "Rechtsklick: Einstellungen"));
        inventory.setItem(47, createItemWithLore(Material.GOLDEN_PICKAXE, "Autom. Einlagerung: "
                + (settings.isAutoStore() ? "AN" : "AUS"),
                "Wenn du Bl\u00f6cke abbaust, werden Drops",
                "automatisch ins Lager transferiert."));
        inventory.setItem(48, createItemWithLore(Material.SHULKER_BOX, "Shulker nachf\u00fcllen und einlagern: "
                + (settings.isShulkerRefillEnabled() ? "AN" : "AUS"),
                "Linksklick: An/Aus",
                "Rechtsklick: Einstellungen"));
        inventory.setItem(49, createItemWithLore(Material.HOPPER, "Items ins Lager legen",
                "Mit Cursor-Item: nur dieses Item einlagern",
                "Ohne Cursor-Item: Hauptinventar einlagern"));
        inventory.setItem(50, createItemWithLore(Material.PLAYER_HEAD, "Spieler hinzuf\u00fcgen",
                "Vertrauensliste f\u00fcr dieses Lager \u00f6ffnen"));
        inventory.setItem(51, createItemWithLore(Material.PINK_DYE, "Farbe w\u00e4hlen",
                "Shulker-Farbe konfigurieren"));
        inventory.setItem(52, createItemWithLore(Material.EXPERIENCE_BOTTLE, "EXP-Speicher",
                "Gespeichert: " + lager.getStoredExp() + " XP",
                "Linksklick: gesamte Spieler-XP einlagern",
                "Rechtsklick: 100 XP als Orbs ausgeben",
                "Shift-Rechtsklick: alles ausgeben"));
        inventory.setItem(53, createItemWithLore(Material.SPYGLASS, "Lager durchsuchen",
                "Klick: Vollansicht mit Kategorien, Suche und Sortierung",
                "Ducken + Rechtsklick: Schnellzugriffbelegung leeren"));
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        if (slot < 9) {
            int category = slot + 1;
            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);

            if (clickType == ClickType.MIDDLE) {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    settings.setCategoryIconMaterial(category, cursor.getType().name());
                    plugin.getLagerManager().saveShulkerSettings(shulkerId);
                    player.sendMessage(Component.text("Kategorie " + category + " Icon auf " + cursor.getType().name()
                            + " gesetzt.", NamedTextColor.GREEN));
                    setMenuItems(player);
                } else {
                    player.sendMessage(Component.text("Nimm ein Item in den Mauszeiger, um das Icon zu setzen.",
                            NamedTextColor.YELLOW));
                }
                return;
            }

            if (clickType == ClickType.RIGHT) {
                openCategoryRenameAnvil(player, category, settings.getCategoryName(category));
                return;
            }

            new LagerView(plugin, shulkerId, category).open(player);
            return;
        }

        if (slot >= 45) {
            handleNavigation(player, slot, clickType);
            return;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        UUID storageOwner = resolveStorageOwner(player);
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);

        if (clickType == ClickType.MIDDLE
                || (clickedItem != null && (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE
                        || clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE))) {
            ItemStack cursor = player.getItemOnCursor();
            if (cursor.getType() != Material.AIR) {
                settings.getQuickSlots().put(slot, cursor.getType().name());
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(Component.text("Slot " + (slot + 1) + " belegt mit ", NamedTextColor.GREEN)
                        .append(Component.translatable(cursor.getType().translationKey()).color(NamedTextColor.GREEN)));
                setMenuItems(player);
                return;
            }
            player.sendMessage(Component.text("Nimm ein Item in den Mauszeiger, um den Slot zu belegen!",
                    NamedTextColor.YELLOW));
            return;
        }

        if (clickType == ClickType.SHIFT_RIGHT && clickedItem != null
                && clickedItem.getType() != Material.GRAY_STAINED_GLASS_PANE
                && clickedItem.getType() != Material.BLACK_STAINED_GLASS_PANE) {
            settings.getQuickSlots().remove(slot);
            plugin.getLagerManager().saveShulkerSettings(shulkerId);
            player.sendMessage(Component.text("Slot " + (slot + 1) + " geleert.", NamedTextColor.YELLOW));
            setMenuItems(player);
            return;
        }

        String materialName = settings.getQuickSlots().get(slot);
        if (materialName != null) {
            Material mat = Material.matchMaterial(materialName);
            if (mat != null) {
                StorageItem si = null;
                for (StorageItem item : lager.getItems()) {
                    ItemStack stack = item.toItemStack();
                    if (stack != null && stack.getType() == mat) {
                        si = item;
                        break;
                    }
                }

                if (si != null && si.getAmount() > 0) {
                    int amountToTake;
                    if (clickType == ClickType.LEFT) {
                        amountToTake = 1;
                    } else if (clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP) {
                        ItemStack probe = si.toItemStack();
                        if (probe == null) {
                            player.sendMessage(Component.text("Dieses Item konnte nicht geladen werden.", NamedTextColor.RED));
                            return;
                        }
                        amountToTake = Math.min(si.getAmount(), getInsertableAmount(player.getInventory(), probe));
                    } else {
                        amountToTake = Math.min(si.getAmount(), mat.getMaxStackSize());
                    }

                    if (amountToTake <= 0) {
                        player.sendMessage(Component.text("Inventar ist voll!", NamedTextColor.RED));
                        return;
                    }

                    ItemStack result = si.toItemStack();
                    if (result == null) {
                        player.sendMessage(Component.text("Dieses Item konnte nicht geladen werden.", NamedTextColor.RED));
                        return;
                    }
                    result.setAmount(amountToTake);

                    java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(result);
                    int notInserted = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int inserted = amountToTake - notInserted;
                    if (inserted > 0) {
                        lager.removeItem(result, inserted);
                        plugin.getLagerManager().saveLager(storageOwner);
                        setMenuItems(player);
                    } else {
                        player.sendMessage(Component.text("Inventar ist voll!", NamedTextColor.RED));
                    }
                } else {
                    player.sendMessage(Component.text("Keine Items diesen Typs im Lager!", NamedTextColor.RED));
                }
            }
        }
    }

    private void handleNavigation(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case 45 -> new VacuumFilterSettingsView(plugin, shulkerId).open(player);
            case 46 -> handleVacuumButton(player, clickType);
            case 47 -> {
                ShulkerSettings s = plugin.getLagerManager().getShulkerSettings(shulkerId);
                s.setAutoStore(!s.isAutoStore());
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(Component.text("Auto-Store: " + (s.isAutoStore() ? "AN" : "AUS"), NamedTextColor.YELLOW));
                setMenuItems(player);
            }
            case 48 -> handleRefillButton(player, clickType);
            case 49 -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor.getType() != Material.AIR) {
                    UUID storageOwner = resolveStorageOwner(player);
                    int added = plugin.getLagerManager().addItemToLager(storageOwner, shulkerId, cursor);
                    if (added > 0) {
                        if (added >= cursor.getAmount()) {
                            player.setItemOnCursor(null);
                        } else {
                            cursor.setAmount(cursor.getAmount() - added);
                        }
                        player.sendMessage(Component.text(added + " Item(s) ins Lager gelegt!", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Kein Platz im Lager!", NamedTextColor.RED));
                    }
                } else {
                    UUID storageOwner = resolveStorageOwner(player);
                    int moved = 0;
                    for (ItemStack item : player.getInventory().getStorageContents()) {
                        if (item != null && item.getType() != Material.AIR && !item.hasItemMeta()) {
                            int added = plugin.getLagerManager().addItemToLager(storageOwner, shulkerId, item);
                            if (added > 0) {
                                moved += added;
                                if (added >= item.getAmount()) {
                                    item.setAmount(0);
                                } else {
                                    item.setAmount(item.getAmount() - added);
                                }
                            }
                        }
                    }
                    if (moved > 0) {
                        player.sendMessage(Component.text(moved + " Item(s) ins Lager verschoben!", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Kein Platz im Lager!", NamedTextColor.RED));
                    }
                }
                setMenuItems(player);
            }
            case 50 -> new PermissionsMenu(plugin, shulkerId).open(player);
            case 51 -> new ColorMenu(plugin, shulkerId).open(player);
            case 52 -> {
                UUID storageOwner = resolveStorageOwner(player);
                PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
                if (clickType == ClickType.LEFT) {
                    int current = getPlayerTotalExperience(player);
                    if (current <= 0) {
                        player.sendMessage(Component.text("Du hast keine XP zum Einlagern.", NamedTextColor.YELLOW));
                        return;
                    }
                    lager.addStoredExp(current);
                    plugin.getLagerManager().saveLager(storageOwner);
                    setPlayerTotalExperience(player, 0);
                    player.sendMessage(Component.text(current + " XP eingelagert.", NamedTextColor.GREEN));
                    setMenuItems(player);
                    return;
                }

                if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
                    int requested = clickType == ClickType.SHIFT_RIGHT ? lager.getStoredExp() : 100;
                    int taken = lager.takeStoredExp(requested);
                    if (taken <= 0) {
                        player.sendMessage(Component.text("Kein XP im Speicher.", NamedTextColor.YELLOW));
                        return;
                    }
                    plugin.getLagerManager().saveLager(storageOwner);
                    dropExperience(player, taken);
                    player.sendMessage(Component.text(taken + " XP als Orbs ausgegeben.", NamedTextColor.GREEN));
                    setMenuItems(player);
                }
            }
            case 53 -> {
                if (player.isSneaking() && (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT)) {
                    clearQuickSlots(player);
                } else {
                    new LagerView(plugin, shulkerId).open(player);
                }
            }
        }
    }

    private void clearQuickSlots(Player player) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        settings.getQuickSlots().clear();
        plugin.getLagerManager().saveShulkerSettings(shulkerId);
        player.sendMessage(Component.text("Schnellzugriffbelegung geleert.", NamedTextColor.GREEN));
        setMenuItems(player);
    }

    private ItemStack createQuickSlotItem(Material material, int count, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.translatable(material.translationKey())
                    .append(Component.text(" (Lager: " + count + ")", NamedTextColor.YELLOW))
                    .color(NamedTextColor.YELLOW));
            java.util.List<Component> lore = new java.util.ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line).color(NamedTextColor.GRAY));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleVacuumButton(Player player, ClickType clickType) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            new VacuumSettingsView(plugin, shulkerId).open(player);
            return;
        }
        settings.setVacuumEnabled(!settings.isVacuumEnabled());
        plugin.getLagerManager().saveShulkerSettings(shulkerId);
        player.sendMessage(Component.text("Einsaugen: " + (settings.isVacuumEnabled() ? "AN" : "AUS"),
                NamedTextColor.YELLOW));
        setMenuItems(player);
    }

    private void handleRefillButton(Player player, ClickType clickType) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            new RefillSettingsView(plugin, shulkerId).open(player);
            return;
        }
        settings.setShulkerRefillEnabled(!settings.isShulkerRefillEnabled());
        plugin.getLagerManager().saveShulkerSettings(shulkerId);
        player.sendMessage(Component.text("Shulker nachf\u00fcllen und einlagern: "
                + (settings.isShulkerRefillEnabled() ? "AN" : "AUS"), NamedTextColor.YELLOW));
        setMenuItems(player);
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

    private ItemStack createItemWithLore(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.YELLOW));
            java.util.List<Component> lore = new java.util.ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line).color(NamedTextColor.GRAY));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openCategoryRenameAnvil(Player player, int category, String currentName) {
        new AnvilGUI.Builder()
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }

                    String text = snapshot.getText() != null ? snapshot.getText().trim() : "";
                    if (text.isEmpty() || text.equals("Begriff...")) {
                        return Collections.emptyList();
                    }

                    ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
                    settings.setCategoryName(category, text);
                    plugin.getLagerManager().saveShulkerSettings(shulkerId);
                    player.sendMessage(Component.text("Kategorie " + category + " umbenannt zu: " + text,
                            NamedTextColor.GREEN));

                    plugin.getServer().getScheduler().runTask(plugin, () -> this.open(player));
                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .text(currentName == null || currentName.isEmpty() ? "Begriff..." : currentName)
                .itemLeft(new ItemStack(Material.NAME_TAG))
                .title("Kategorie " + category + " Begriff")
                .plugin(plugin)
                .open(player);
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

    private int getInsertableAmount(PlayerInventory inventory, ItemStack target) {
        if (target == null || target.getType() == Material.AIR) {
            return 0;
        }

        int maxStack = target.getMaxStackSize();
        int free = 0;
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                free += maxStack;
                continue;
            }
            if (slot.isSimilar(target) && slot.getAmount() < maxStack) {
                free += (maxStack - slot.getAmount());
            }
        }
        return Math.max(0, free);
    }
}







