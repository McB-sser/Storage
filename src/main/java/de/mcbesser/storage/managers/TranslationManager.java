package de.mcbesser.storage.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.mcbesser.storage.Storage;
import org.bukkit.Material;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TranslationManager {
    private static final Gson GSON = new Gson();
    private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final Map<String, String> GERMAN_TO_MATERIAL = new HashMap<>();
    private static final Map<Material, String> MATERIAL_TO_GERMAN = new HashMap<>();
    private static final Map<String, String> TOKEN_TO_GERMAN = new HashMap<>();
    private static volatile boolean initialized;

    static {
        addStaticAlias("erde", "DIRT");
        addStaticAlias("stein", "STONE");
        addStaticAlias("holz", "LOG");
        addStaticAlias("brett", "PLANKS");
        addStaticAlias("eiche", "OAK");
        addStaticAlias("fichte", "SPRUCE");
        addStaticAlias("birke", "BIRCH");
        addStaticAlias("schwarzeiche", "DARK_OAK");
        addStaticAlias("tropenholz", "JUNGLE");
        addStaticAlias("akazie", "ACACIA");
        addStaticAlias("pflasterstein", "COBBLESTONE");
        addStaticAlias("bruchstein", "COBBLESTONE");
        addStaticAlias("diamant", "DIAMOND");
        addStaticAlias("gold", "GOLD");
        addStaticAlias("eisen", "IRON");
        addStaticAlias("kohle", "COAL");
        addStaticAlias("glas", "GLASS");
        addStaticAlias("sand", "SAND");
        addStaticAlias("kies", "GRAVEL");
        addStaticAlias("wolle", "WOOL");
        addStaticAlias("ziegel", "BRICK");
        addStaticAlias("quarz", "QUARTZ");
        addStaticAlias("gras", "GRASS");
        addStaticAlias("laub", "LEAVES");
        addStaticAlias("setzling", "SAPLING");
        addStaticAlias("schwert", "SWORD");
        addStaticAlias("spitzhacke", "PICKAXE");
        addStaticAlias("axt", "AXE");
        addStaticAlias("schaufel", "SHOVEL");
        addStaticAlias("hacke", "HOE");
        addStaticAlias("truhe", "CHEST");
        addStaticAlias("kiste", "CHEST");
        addStaticAlias("ofen", "FURNACE");
        addStaticAlias("fackel", "TORCH");
        addStaticAlias("bett", "BED");
        addStaticAlias("eimer", "BUCKET");
        addStaticAlias("apfel", "APPLE");
        addStaticAlias("brot", "BREAD");
        addStaticAlias("fleisch", "BEEF");
        addStaticAlias("haehnchen", "CHICKEN");
        addStaticAlias("hähnchen", "CHICKEN");
        addStaticAlias("schwein", "PORKCHOP");
        addStaticAlias("fisch", "FISH");
        addStaticAlias("buch", "BOOK");
        addStaticAlias("papier", "PAPER");
        addStaticAlias("pfeil", "ARROW");
        addStaticAlias("bogen", "BOW");
        addStaticAlias("trank", "POTION");
        addStaticAlias("zauber", "ENCHANT");

        TOKEN_TO_GERMAN.put("grass", "gras");
        TOKEN_TO_GERMAN.put("block", "block");
        TOKEN_TO_GERMAN.put("dirt", "erde");
        TOKEN_TO_GERMAN.put("coarse", "grob");
        TOKEN_TO_GERMAN.put("rooted", "bewurzelt");
        TOKEN_TO_GERMAN.put("stone", "stein");
        TOKEN_TO_GERMAN.put("cobblestone", "bruchstein");
        TOKEN_TO_GERMAN.put("deepslate", "tiefenschiefer");
        TOKEN_TO_GERMAN.put("oak", "eichen");
        TOKEN_TO_GERMAN.put("spruce", "fichten");
        TOKEN_TO_GERMAN.put("birch", "birken");
        TOKEN_TO_GERMAN.put("jungle", "tropenholz");
        TOKEN_TO_GERMAN.put("acacia", "akazien");
        TOKEN_TO_GERMAN.put("dark", "schwarz");
        TOKEN_TO_GERMAN.put("mangrove", "mangroven");
        TOKEN_TO_GERMAN.put("cherry", "kirsch");
        TOKEN_TO_GERMAN.put("log", "stamm");
        TOKEN_TO_GERMAN.put("planks", "bretter");
        TOKEN_TO_GERMAN.put("chest", "truhe");
        TOKEN_TO_GERMAN.put("barrel", "fass");
        TOKEN_TO_GERMAN.put("furnace", "ofen");
        TOKEN_TO_GERMAN.put("crafting", "werk");
        TOKEN_TO_GERMAN.put("table", "bank");
        TOKEN_TO_GERMAN.put("glass", "glas");
        TOKEN_TO_GERMAN.put("pane", "scheibe");
        TOKEN_TO_GERMAN.put("sand", "sand");
        TOKEN_TO_GERMAN.put("red", "rot");
        TOKEN_TO_GERMAN.put("gravel", "kies");
        TOKEN_TO_GERMAN.put("iron", "eisen");
        TOKEN_TO_GERMAN.put("gold", "gold");
        TOKEN_TO_GERMAN.put("diamond", "diamant");
        TOKEN_TO_GERMAN.put("emerald", "smaragd");
        TOKEN_TO_GERMAN.put("coal", "kohle");
        TOKEN_TO_GERMAN.put("paper", "papier");
        TOKEN_TO_GERMAN.put("book", "buch");
        TOKEN_TO_GERMAN.put("name", "namen");
        TOKEN_TO_GERMAN.put("tag", "schild");
        TOKEN_TO_GERMAN.put("shulker", "shulker");
        TOKEN_TO_GERMAN.put("box", "kiste");
    }

    private TranslationManager() {
    }

    public static synchronized void initialize(Storage plugin) {
        if (initialized) {
            return;
        }

        try (InputStream input = plugin.getResource("lang/de_de.json")) {
            if (input == null) {
                plugin.getLogger().warning("de_de.json konnte nicht geladen werden. Fallback-Suche wird verwendet.");
                initialized = true;
                return;
            }

            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                Map<String, String> translations = GSON.fromJson(reader, STRING_MAP_TYPE);
                if (translations != null) {
                    loadMaterialTranslations(translations);
                    plugin.getLogger().info("Deutsche Minecraft-Übersetzungen für die Suche geladen: "
                            + MATERIAL_TO_GERMAN.size() + " Materialien.");
                }
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("de_de.json konnte nicht verarbeitet werden: " + exception.getMessage());
        }

        initialized = true;
    }

    public static String translate(String query) {
        String normalizedQuery = normalize(query);
        for (Map.Entry<String, String> entry : GERMAN_TO_MATERIAL.entrySet()) {
            if (normalizedQuery.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return query;
    }

    public static String translatePartial(String query) {
        String normalizedQuery = normalize(query);

        for (Map.Entry<String, String> entry : GERMAN_TO_MATERIAL.entrySet()) {
            String german = entry.getKey();
            if (normalizedQuery.equals(german)
                    || german.startsWith(normalizedQuery)
                    || german.contains(normalizedQuery)) {
                return entry.getValue();
            }
        }

        return query;
    }

    public static Set<String> getSearchAliases(Material material) {
        Set<String> aliases = new LinkedHashSet<>();
        if (material == null) {
            return aliases;
        }

        String materialName = material.name().toLowerCase(Locale.ROOT);
        addAlias(aliases, materialName);
        addAlias(aliases, materialName.replace('_', ' '));

        String germanName = toGermanMaterialName(material);
        addAlias(aliases, germanName);

        for (Map.Entry<String, String> entry : GERMAN_TO_MATERIAL.entrySet()) {
            if (material.name().contains(entry.getValue())) {
                addAlias(aliases, entry.getKey());
            }
        }

        return aliases;
    }

    public static String toGermanMaterialName(Material material) {
        if (material == null) {
            return "Unbekannt";
        }

        String translated = MATERIAL_TO_GERMAN.get(material);
        if (translated != null && !translated.isBlank()) {
            return translated;
        }

        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String token = TOKEN_TO_GERMAN.getOrDefault(part, part);
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return builder.toString();
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");

        StringBuilder builder = new StringBuilder(normalized.length());
        for (char current : normalized.toCharArray()) {
            if (Character.isLetterOrDigit(current)) {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static void loadMaterialTranslations(Map<String, String> translations) {
        for (Material material : Material.values()) {
            if (material.isAir() || material.isLegacy()) {
                continue;
            }

            String key = material.translationKey();
            String german = translations.get(key);
            if (german == null || german.isBlank()) {
                continue;
            }

            MATERIAL_TO_GERMAN.put(material, german);
            addStaticAlias(german, material.name());
        }
    }

    private static void addStaticAlias(String german, String materialName) {
        String normalized = normalize(german);
        if (normalized.isBlank()) {
            return;
        }
        GERMAN_TO_MATERIAL.putIfAbsent(normalized, materialName);
    }

    private static void addAlias(Set<String> aliases, String alias) {
        if (alias == null || alias.isBlank()) {
            return;
        }

        aliases.add(alias.toLowerCase(Locale.ROOT));
        aliases.add(normalize(alias));
    }
}
