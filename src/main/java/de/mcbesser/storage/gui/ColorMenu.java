package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.ShulkerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ColorMenu extends AbstractMenu {
    private final UUID shulkerId;

    public ColorMenu(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Shulker Farbe wÃƒÂ¤hlen"), 3);
        this.shulkerId = shulkerId;
    }

    @Override
    public UUID getShulkerId() {
        return shulkerId;
    }

    @Override
    public void setMenuItems(Player player) {
        inventory.clear();

        Material[] boxes = {
                Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX,
                Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
                Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX,
                Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
                Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.RED_SHULKER_BOX,
                Material.BLACK_SHULKER_BOX
        };

        for (int i = 0; i < boxes.length; i++) {
            inventory.setItem(i, createItem(boxes[i], toGermanColorName(boxes[i])));
        }

        inventory.setItem(26, createItem(Material.BARRIER, "ZurÃƒÂ¼ck zum HauptmenÃƒÂ¼"));
    }
    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        if (slot == 26) {
            new QuickSlotsView(plugin, shulkerId).open(player);
            return;
        }

        if (slot < 16 && clickedItem != null && clickedItem.getType().name().contains("SHULKER_BOX")) {
            String color = clickedItem.getType().name().replace("_SHULKER_BOX", "");
            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
            settings.setColor(color);
            plugin.getLagerManager().saveShulkerSettings(shulkerId);

            Material newType = Material.matchMaterial(color + "_SHULKER_BOX");
            if (newType == null) {
                return;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().toString().contains("SHULKER_BOX")) {
                item.setType(newType);
                if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta bsm
                        && bsm.getBlockState() instanceof ShulkerBox shulkerState) {
                    shulkerState.setType(newType);
                    bsm.setBlockState(shulkerState);
                    item.setItemMeta(bsm);
                }
            }

            updatePlacedShulkerColors(shulkerId, newType, settings);
            player.sendMessage("Shulker Farbe auf " + toGermanColorName(clickedItem.getType()) + " geÃƒÂ¤ndert!");
            setMenuItems(player);
        }
    }

    private String toGermanColorName(Material box) {
        return switch (box) {
            case WHITE_SHULKER_BOX -> "WeiÃƒÅ¸";
            case ORANGE_SHULKER_BOX -> "Orange";
            case MAGENTA_SHULKER_BOX -> "Magenta";
            case LIGHT_BLUE_SHULKER_BOX -> "Hellblau";
            case YELLOW_SHULKER_BOX -> "Gelb";
            case LIME_SHULKER_BOX -> "HellgrÃƒÂ¼n";
            case PINK_SHULKER_BOX -> "Pink";
            case GRAY_SHULKER_BOX -> "Grau";
            case LIGHT_GRAY_SHULKER_BOX -> "Hellgrau";
            case CYAN_SHULKER_BOX -> "TÃƒÂ¼rkis";
            case PURPLE_SHULKER_BOX -> "Lila";
            case BLUE_SHULKER_BOX -> "Blau";
            case BROWN_SHULKER_BOX -> "Braun";
            case GREEN_SHULKER_BOX -> "GrÃƒÂ¼n";
            case RED_SHULKER_BOX -> "Rot";
            case BLACK_SHULKER_BOX -> "Schwarz";
            default -> box.name().replace("_SHULKER_BOX", "");
        };
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

    private void updatePlacedShulkerColors(UUID targetShulkerId, Material newType, ShulkerSettings settings) {
        if (newType == null || !newType.name().contains("SHULKER_BOX")) {
            return;
        }

        NamespacedKey ownerKey = new NamespacedKey(plugin, "owner");
        String ownerUuid = settings.getOwnerUuid();
        String ownerName = settings.getOwnerName();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof ShulkerBox shulker)) {
                        continue;
                    }

                    String id = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY,
                            PersistentDataType.STRING);
                    if (id == null) {
                        continue;
                    }

                    UUID foundId;
                    try {
                        foundId = UUID.fromString(id);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    if (!foundId.equals(targetShulkerId)) {
                        continue;
                    }

                    List<ItemStack> contents = new ArrayList<>();
                    for (ItemStack stack : shulker.getInventory().getContents()) {
                        contents.add(stack == null ? null : stack.clone());
                    }

                    org.bukkit.block.Block block = shulker.getBlock();
                    block.setType(newType, false);
                    if (!(block.getState() instanceof ShulkerBox recolored)) {
                        continue;
                    }

                    recolored.getPersistentDataContainer().set(RecipeManager.SHULKER_KEY, PersistentDataType.STRING,
                            targetShulkerId.toString());
                    if (ownerUuid != null && !ownerUuid.isBlank()) {
                        recolored.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerUuid);
                    }
                    if (ownerName != null && !ownerName.isBlank()) {
                        recolored.customName(Component.text("Lager von: " + ownerName, NamedTextColor.GOLD));
                    }
                    recolored.getInventory().setContents(contents.toArray(new ItemStack[0]));
                    recolored.update(true, false);
                }
            }
        }
    }
}



