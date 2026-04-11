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
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VacuumSettingsView extends AbstractMenu {
    private static final int XZ_SUM_LIMIT = 48;
    private static final int Y_SUM_LIMIT = 320;
    private static final int SMALL_STEP = 1;
    private static final int BIG_STEP = 8;
    private final UUID shulkerId;

    public VacuumSettingsView(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Einsaug Einstellungen"), 5);
        this.shulkerId = shulkerId;
    }

    @Override
    public UUID getShulkerId() {
        return shulkerId;
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
    public void setMenuItems(Player player) {
        inventory.clear();
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        PlayerLager lager = plugin.getLagerManager().getLager(resolveStorageOwner(player));

        inventory.setItem(0, createItemWithLore(Material.REPEATER, "Einsaugen: " + (settings.isVacuumEnabled() ? "AN" : "AUS"),
                "Klick zum Umschalten"));

        Material fuelMat = lager.getVacuumFuelMaterial() != null
                ? Material.matchMaterial(lager.getVacuumFuelMaterial())
                : null;
        inventory.setItem(2, createItemWithLore(fuelMat != null ? fuelMat : Material.BARRIER,
                "Brennstoff (global): " + (fuelMat != null ? fuelMat.name() : "nicht gesetzt"),
                "Item am Cursor + Klick: Brennstoff setzen",
                "Nur echte Ofen-Brennstoffe sind erlaubt"));

        int chargePerFuel = getChargeForFuel(fuelMat);
        inventory.setItem(3, createItemWithLore(Material.EXPERIENCE_BOTTLE, "Ladung (global): " + lager.getVacuumCharge(),
                "Linksklick: 1 Brennstoff verbrauchen",
                "Shift-Links: alle passenden verbrauchen",
                chargePerFuel > 0
                        ? "+" + chargePerFuel + " Ladung pro Brennstoff"
                        : "Minecraft-Brennda\u00fcr /20 wird verwendet"));
        inventory.setItem(8, createItemWithLore(Material.HOPPER_MINECART, "Filtereinstellungen",
                "Klick: Filterseite \u00f6ffnen"));

        String rangeMode = normalizeRangeMode(settings.getVacuumRangeMode());
        inventory.setItem(19, createItemWithLore(Material.COMPARATOR, "Bereichsmodus: " + toModeDisplay(rangeMode),
                "Klick: n\u00e4chster Modus",
                "1x1 = nur eigener Chunk",
                "3x3 = ein Chunk Umkreis"));
        inventory.setItem(20, createItemWithLore(Material.MAP, "Preset 1x1 Chunk",
                "Setzt Modus auf 1x1"));
        inventory.setItem(21, createItemWithLore(Material.FILLED_MAP, "Preset 3x3 Chunk",
                "Setzt Modus auf 3x3"));
        inventory.setItem(22, createItemWithLore(Material.ENDER_EYE, "Bereich-Partikel: "
                + (settings.isVacuumRangeParticlesEnabled() ? "AN" : "AUS"),
                "Rand wird mit Dust + EndRod gezeigt",
                "Klick: umschalten"));
        inventory.setItem(23, createItemWithLore(Material.STRUCTURE_VOID, "Preset Einzelbereich",
                "Setzt Modus auf Einzelbereich"));
        inventory.setItem(24, createItemWithLore(Material.BARRIER, "Einzelbereich reset",
                "Setzt alle X/Y/Z Werte auf 0"));

        inventory.setItem(27, createItemWithLore(Material.PAPER, "Einzelbereich relativ zur Shulker",
                "Links/Rechts, Oben/Unten, Vor/Hinten",
                "X-Summe max: " + XZ_SUM_LIMIT,
                "Z-Summe max: " + XZ_SUM_LIMIT,
                "Y-Summe max: " + Y_SUM_LIMIT));
        inventory.setItem(28, createRangeItem(Material.RED_DYE, "X- (links)", settings.getVacuumRangeNegX(),
                settings.getVacuumRangePosX(), XZ_SUM_LIMIT));
        inventory.setItem(29, createRangeItem(Material.LIME_DYE, "X+ (rechts)", settings.getVacuumRangePosX(),
                settings.getVacuumRangeNegX(), XZ_SUM_LIMIT));
        inventory.setItem(30, createRangeItem(Material.RED_DYE, "Z- (vorne)", settings.getVacuumRangeNegZ(),
                settings.getVacuumRangePosZ(), XZ_SUM_LIMIT));
        inventory.setItem(31, createRangeItem(Material.LIME_DYE, "Z+ (hinten)", settings.getVacuumRangePosZ(),
                settings.getVacuumRangeNegZ(), XZ_SUM_LIMIT));
        inventory.setItem(32, createRangeItem(Material.RED_CANDLE, "Y- (unten)", settings.getVacuumRangeNegY(),
                settings.getVacuumRangePosY(), Y_SUM_LIMIT));
        inventory.setItem(33, createRangeItem(Material.LIME_CANDLE, "Y+ (oben)", settings.getVacuumRangePosY(),
                settings.getVacuumRangeNegY(), Y_SUM_LIMIT));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, ""));
            }
        }
        for (int slot = 36; slot <= 44; slot++) {
            inventory.setItem(slot, createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, ""));
        }
        inventory.setItem(36, createSimpleItem(Material.ARROW, "Zur\u00fcck"));
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        UUID storageOwner = resolveStorageOwner(player);
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);

        switch (slot) {
            case 0 -> {
                settings.setVacuumEnabled(!settings.isVacuumEnabled());
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(Component.text("Einsaugen: " + (settings.isVacuumEnabled() ? "AN" : "AUS"),
                        NamedTextColor.YELLOW));
                setMenuItems(player);
            }
            case 2 -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    if (!cursor.getType().isFuel() || getChargeForFuel(cursor.getType()) <= 0) {
                        player.sendMessage(Component.text("Dieses Item ist kein g\u00fcltiger Brennstoff.", NamedTextColor.RED));
                        return;
                    }
                    lager.setVacuumFuelMaterial(cursor.getType().name());
                    plugin.getLagerManager().saveLager(storageOwner);
                    player.sendMessage(Component.text("Brennstoff (global) gesetzt: " + cursor.getType().name(),
                            NamedTextColor.GREEN));
                    setMenuItems(player);
                } else {
                    player.sendMessage(Component.text("Nimm ein Item am Cursor, um den Brennstoff zu setzen.",
                            NamedTextColor.RED));
                }
            }
            case 3 -> {
                String fuelName = lager.getVacuumFuelMaterial();
                Material fuelMat = fuelName != null ? Material.matchMaterial(fuelName) : null;
                int chargePerFuel = getChargeForFuel(fuelMat);
                if (fuelMat == null || chargePerFuel <= 0) {
                    player.sendMessage(Component.text("Kein g\u00fcltiger Brennstoff gesetzt.", NamedTextColor.RED));
                    return;
                }

                FuelConsumption consumption = consumeFuel(player, fuelMat, clickType.isShiftClick(), chargePerFuel);
                if (consumption.consumed() <= 0) {
                    player.sendMessage(Component.text("Kein passender Brennstoff gefunden.", NamedTextColor.RED));
                    return;
                }

                lager.addVacuumCharge(consumption.chargeAdded());
                plugin.getLagerManager().saveLager(storageOwner);
                addRemainderItems(player, consumption.remainders());
                player.sendMessage(Component.text(
                        consumption.consumed() + "x " + fuelMat.name() + " verbraucht, +" + consumption.chargeAdded()
                                + " Ladung (global).",
                        NamedTextColor.GREEN));
                setMenuItems(player);
            }
            case 8 -> new VacuumFilterSettingsView(plugin, shulkerId).open(player);
            case 19 -> {
                settings.setVacuumRangeMode(nextMode(normalizeRangeMode(settings.getVacuumRangeMode())));
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(Component.text("Bereichsmodus: " + toModeDisplay(settings.getVacuumRangeMode()),
                        NamedTextColor.YELLOW));
                setMenuItems(player);
            }
            case 20 -> {
                settings.setVacuumRangeMode("CHUNK_1X1");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 21 -> {
                settings.setVacuumRangeMode("CHUNK_3X3");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 22 -> {
                settings.setVacuumRangeParticlesEnabled(!settings.isVacuumRangeParticlesEnabled());
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(Component.text("Bereich-Partikel: "
                        + (settings.isVacuumRangeParticlesEnabled() ? "AN" : "AUS"), NamedTextColor.YELLOW));
                setMenuItems(player);
            }
            case 23 -> {
                settings.setVacuumRangeMode("BOX");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 24 -> {
                settings.setVacuumRangeNegX(0);
                settings.setVacuumRangePosX(0);
                settings.setVacuumRangeNegY(0);
                settings.setVacuumRangePosY(0);
                settings.setVacuumRangeNegZ(0);
                settings.setVacuumRangePosZ(0);
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 28 -> {
                adjustRange(settings, Axis.X, false, deltaFromClick(clickType));
                settings.setVacuumRangeMode("BOX");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 29 -> {
                adjustRange(settings, Axis.X, true, deltaFromClick(clickType));
                settings.setVacuumRangeMode("BOX");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 30 -> {
                adjustRange(settings, Axis.Z, false, deltaFromClick(clickType));
                settings.setVacuumRangeMode("BOX");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 31 -> {
                adjustRange(settings, Axis.Z, true, deltaFromClick(clickType));
                settings.setVacuumRangeMode("BOX");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 32 -> {
                adjustRange(settings, Axis.Y, false, deltaFromClick(clickType));
                settings.setVacuumRangeMode("BOX");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 33 -> {
                adjustRange(settings, Axis.Y, true, deltaFromClick(clickType));
                settings.setVacuumRangeMode("BOX");
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
            }
            case 36 -> new QuickSlotsView(plugin, shulkerId).open(player);
        }
    }

    private int deltaFromClick(ClickType clickType) {
        if (clickType == ClickType.LEFT) {
            return SMALL_STEP;
        }
        if (clickType == ClickType.RIGHT) {
            return -SMALL_STEP;
        }
        if (clickType == ClickType.SHIFT_LEFT) {
            return BIG_STEP;
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            return -BIG_STEP;
        }
        return 0;
    }

    private void adjustRange(ShulkerSettings settings, Axis axis, boolean positiveSide, int delta) {
        if (delta == 0) {
            return;
        }

        int current;
        int other;
        int axisLimit = axis == Axis.Y ? Y_SUM_LIMIT : XZ_SUM_LIMIT;

        if (axis == Axis.X) {
            current = positiveSide ? settings.getVacuumRangePosX() : settings.getVacuumRangeNegX();
            other = positiveSide ? settings.getVacuumRangeNegX() : settings.getVacuumRangePosX();
        } else if (axis == Axis.Y) {
            current = positiveSide ? settings.getVacuumRangePosY() : settings.getVacuumRangeNegY();
            other = positiveSide ? settings.getVacuumRangeNegY() : settings.getVacuumRangePosY();
        } else {
            current = positiveSide ? settings.getVacuumRangePosZ() : settings.getVacuumRangeNegZ();
            other = positiveSide ? settings.getVacuumRangeNegZ() : settings.getVacuumRangePosZ();
        }

        int target = current + delta;
        target = Math.max(0, target);
        target = Math.min(target, axisLimit - other);

        if (axis == Axis.X) {
            if (positiveSide) {
                settings.setVacuumRangePosX(target);
            } else {
                settings.setVacuumRangeNegX(target);
            }
        } else if (axis == Axis.Y) {
            if (positiveSide) {
                settings.setVacuumRangePosY(target);
            } else {
                settings.setVacuumRangeNegY(target);
            }
        } else {
            if (positiveSide) {
                settings.setVacuumRangePosZ(target);
            } else {
                settings.setVacuumRangeNegZ(target);
            }
        }
    }

    private String normalizeRangeMode(String mode) {
        if ("CHUNK_3X3".equalsIgnoreCase(mode)) {
            return "CHUNK_3X3";
        }
        if ("BOX".equalsIgnoreCase(mode) || "RELATIVE".equalsIgnoreCase(mode)) {
            return "BOX";
        }
        return "CHUNK_1X1";
    }

    private String nextMode(String current) {
        return switch (current) {
            case "CHUNK_1X1" -> "CHUNK_3X3";
            case "CHUNK_3X3" -> "BOX";
            default -> "CHUNK_1X1";
        };
    }

    private String toModeDisplay(String mode) {
        String normalized = normalizeRangeMode(mode);
        return switch (normalized) {
            case "CHUNK_3X3" -> "3x3 Chunks";
            case "BOX" -> "Einzelbereich (X/Y/Z)";
            default -> "1x1 Chunk";
        };
    }

    private FuelConsumption consumeFuel(Player player, Material fuelMat, boolean consumeAll, int chargePerFuel) {
        ItemStack cursor = player.getItemOnCursor();
        List<ItemStack> remainders = new ArrayList<>();
        if (cursor != null && cursor.getType() == fuelMat && cursor.getAmount() > 0) {
            int taken = consumeAll ? cursor.getAmount() : 1;
            cursor.setAmount(cursor.getAmount() - taken);
            if (cursor.getAmount() <= 0) {
                player.setItemOnCursor(null);
            }
            addRemainders(remainders, fuelMat, taken);
            return new FuelConsumption(taken, taken * chargePerFuel, remainders);
        }

        int remaining = consumeAll ? Integer.MAX_VALUE : 1;
        int consumed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != fuelMat) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            consumed += take;
            remaining -= take;
            addRemainders(remainders, fuelMat, take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
        return new FuelConsumption(consumed, consumed * chargePerFuel, remainders);
    }

    private void addRemainders(List<ItemStack> remainders, Material fuelMat, int consumed) {
        Material remainderType = fuelMat.getCraftingRemainingItem();
        if (remainderType == null || remainderType == Material.AIR || consumed <= 0) {
            return;
        }
        remainders.add(new ItemStack(remainderType, consumed));
    }

    private void addRemainderItems(Player player, List<ItemStack> remainders) {
        for (ItemStack remainder : remainders) {
            if (remainder == null || remainder.getType() == Material.AIR || remainder.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(remainder);
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
            }
        }
    }

    private int getChargeForFuel(Material material) {
        if (material == null) {
            return 0;
        }
        ItemType itemType = material.asItemType();
        if (itemType == null || !itemType.isFuel()) {
            return 0;
        }
        int burnDuration = itemType.getBurnDuration();
        return burnDuration <= 0 ? 0 : burnDuration / 20;
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

    private ItemStack createRangeItem(Material material, String name, int own, int other, int axisLimit) {
        int remaining = Math.max(0, axisLimit - own - other);
        return createItemWithLore(material, name + ": " + own,
                "Linksklick: +" + SMALL_STEP,
                "Rechtsklick: -" + SMALL_STEP,
                "Shift-Links: +" + BIG_STEP,
                "Shift-Rechts: -" + BIG_STEP,
                "Gegenrichtung: " + other,
                "Rest bis Limit: " + remaining);
    }

    private enum Axis {
        X, Y, Z
    }

    private record FuelConsumption(int consumed, int chargeAdded, List<ItemStack> remainders) {
    }
}

