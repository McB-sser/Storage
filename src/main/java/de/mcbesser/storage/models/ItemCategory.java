package de.mcbesser.storage.models;

import org.bukkit.Material;

public enum ItemCategory {
    BLOCKS(1, "Bl\u00f6cke", Material.STONE),
    TOOLS(2, "Werkzeuge", Material.DIAMOND_PICKAXE),
    WEAPONS(3, "Waffen", Material.DIAMOND_SWORD),
    ARMOR(4, "R\u00fcstungen", Material.DIAMOND_CHESTPLATE),
    FOOD(5, "Nahrung", Material.COOKED_BEEF),
    ORES(6, "Erze", Material.DIAMOND),
    REDSTONE(7, "Redstone", Material.REDSTONE),
    DECORATIONS(8, "Dekorationen", Material.FLOWER_POT),
    MISC(9, "Sonstiges", Material.STICK);

    private final int number;
    private final String displayName;
    private final Material icon;

    ItemCategory(int number, String displayName, Material icon) {
        this.number = number;
        this.displayName = displayName;
        this.icon = icon;
    }

    public int getNumber() {
        return number;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    /**
     * Get category by number (1-9)
     */
    public static ItemCategory fromNumber(int number) {
        for (ItemCategory category : values()) {
            if (category.number == number) {
                return category;
            }
        }
        return null;
    }

    /**
     * Get the default category for a material
     */
    public static ItemCategory getCategoryForMaterial(Material material) {
        if (material == null)
            return MISC;

        String name = material.name();

        // Tools
        if (name.contains("PICKAXE") || name.contains("AXE") || name.contains("SHOVEL")
                || name.contains("HOE") || name.contains("SHEARS")) {
            return TOOLS;
        }

        // Weapons
        if (name.contains("SWORD") || name.contains("BOW") || name.contains("CROSSBOW")
                || name.contains("TRIDENT")) {
            return WEAPONS;
        }

        // Armor
        if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS")
                || name.contains("BOOTS") || name.contains("ELYTRA")) {
            return ARMOR;
        }

        // Food
        if (material.isEdible() || name.contains("POTION") || name.contains("STEW")
                || name.contains("SOUP")) {
            return FOOD;
        }

        // Ores
        if (name.contains("ORE") || name.equals("DIAMOND") || name.equals("EMERALD")
                || name.equals("GOLD_INGOT") || name.equals("IRON_INGOT")
                || name.equals("COAL") || name.equals("LAPIS_LAZULI")) {
            return ORES;
        }

        // Redstone
        if (name.contains("REDSTONE") || name.contains("PISTON") || name.contains("REPEATER")
                || name.contains("COMPARATOR") || name.contains("OBSERVER")
                || name.contains("HOPPER") || name.contains("DISPENSER")
                || name.contains("DROPPER")) {
            return REDSTONE;
        }

        // Decorations
        if (name.contains("FLOWER") || name.contains("CARPET") || name.contains("BANNER")
                || name.contains("PAINTING") || name.contains("ITEM_FRAME")
                || name.contains("CANDLE") || name.contains("LANTERN")) {
            return DECORATIONS;
        }

        // Blocks (default for solid blocks)
        if (material.isBlock() && material.isSolid()) {
            return BLOCKS;
        }

        return MISC;
    }
}
