package de.mcbesser.storage.managers;

import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;

public class TranslationManager {
    private static final Map<String, String> GERMAN_TO_MATERIAL = new HashMap<>();

    static {
        // Common German terms -> Internal Material Name Snippets
        GERMAN_TO_MATERIAL.put("erde", "DIRT");
        GERMAN_TO_MATERIAL.put("stein", "STONE");
        GERMAN_TO_MATERIAL.put("holz", "LOG");
        GERMAN_TO_MATERIAL.put("brett", "PLANKS");
        GERMAN_TO_MATERIAL.put("eiche", "OAK");
        GERMAN_TO_MATERIAL.put("fichte", "SPRUCE");
        GERMAN_TO_MATERIAL.put("birke", "BIRCH");
        GERMAN_TO_MATERIAL.put("schwarzeiche", "DARK_OAK");
        GERMAN_TO_MATERIAL.put("tropenholz", "JUNGLE");
        GERMAN_TO_MATERIAL.put("akazie", "ACACIA");
        GERMAN_TO_MATERIAL.put("pflasterstein", "COBBLESTONE");
        GERMAN_TO_MATERIAL.put("bruchstein", "COBBLESTONE");
        GERMAN_TO_MATERIAL.put("diamant", "DIAMOND");
        GERMAN_TO_MATERIAL.put("gold", "GOLD");
        GERMAN_TO_MATERIAL.put("eisen", "IRON");
        GERMAN_TO_MATERIAL.put("kohle", "COAL");
        GERMAN_TO_MATERIAL.put("glas", "GLASS");
        GERMAN_TO_MATERIAL.put("sand", "SAND");
        GERMAN_TO_MATERIAL.put("kies", "GRAVEL");
        GERMAN_TO_MATERIAL.put("wolle", "WOOL");
        GERMAN_TO_MATERIAL.put("ziegel", "BRICK");
        GERMAN_TO_MATERIAL.put("quarz", "QUARTZ");
        GERMAN_TO_MATERIAL.put("gras", "GRASS");
        GERMAN_TO_MATERIAL.put("laub", "LEAVES");
        GERMAN_TO_MATERIAL.put("setzling", "SAPLING");
        GERMAN_TO_MATERIAL.put("schwert", "SWORD");
        GERMAN_TO_MATERIAL.put("spitzhacke", "PICKAXE");
        GERMAN_TO_MATERIAL.put("axt", "AXE");
        GERMAN_TO_MATERIAL.put("schaufel", "SHOVEL");
        GERMAN_TO_MATERIAL.put("hacke", "HOE");
        GERMAN_TO_MATERIAL.put("truhe", "CHEST");
        GERMAN_TO_MATERIAL.put("kiste", "CHEST");
        GERMAN_TO_MATERIAL.put("ofen", "FURNACE");
        GERMAN_TO_MATERIAL.put("fackel", "TORCH");
        GERMAN_TO_MATERIAL.put("bett", "BED");
        GERMAN_TO_MATERIAL.put("eimer", "BUCKET");
        GERMAN_TO_MATERIAL.put("apfel", "APPLE");
        GERMAN_TO_MATERIAL.put("brot", "BREAD");
        GERMAN_TO_MATERIAL.put("fleisch", "BEEF");
        GERMAN_TO_MATERIAL.put("hÃƒÂ¤hnchen", "CHICKEN");
        GERMAN_TO_MATERIAL.put("schwein", "PORKCHOP");
        GERMAN_TO_MATERIAL.put("fisch", "FISH");
        GERMAN_TO_MATERIAL.put("buch", "BOOK");
        GERMAN_TO_MATERIAL.put("papier", "PAPER");
        GERMAN_TO_MATERIAL.put("pfeil", "ARROW");
        GERMAN_TO_MATERIAL.put("bogen", "BOW");
        GERMAN_TO_MATERIAL.put("trank", "POTION");
        GERMAN_TO_MATERIAL.put("zauber", "ENCHANT");
    }

    public static String translate(String query) {
        String lower = query.toLowerCase();
        for (Map.Entry<String, String> entry : GERMAN_TO_MATERIAL.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return query;
    }

    /**
     * Translates a partial German term to an English material name.
     * Supports fuzzy matching - e.g., "Erd" will match "Erde" -> "DIRT"
     * 
     * @param query The partial search query
     * @return The translated material name, or the original query if no match found
     */
    public static String translatePartial(String query) {
        String lower = query.toLowerCase();

        // First try exact match
        for (Map.Entry<String, String> entry : GERMAN_TO_MATERIAL.entrySet()) {
            if (lower.equals(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Then try partial match (query is substring of German term)
        for (Map.Entry<String, String> entry : GERMAN_TO_MATERIAL.entrySet()) {
            if (entry.getKey().startsWith(lower)) {
                return entry.getValue();
            }
        }

        // Finally try contains match
        for (Map.Entry<String, String> entry : GERMAN_TO_MATERIAL.entrySet()) {
            if (entry.getKey().contains(lower)) {
                return entry.getValue();
            }
        }

        return query;
    }
}


