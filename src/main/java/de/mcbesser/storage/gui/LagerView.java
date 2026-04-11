package de.mcbesser.storage.gui;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.managers.TranslationManager;
import de.mcbesser.storage.models.ItemCategory;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import de.mcbesser.storage.models.StorageItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LagerView extends AbstractMenu {
    private final UUID shulkerId;
    private int page = 0;
    private String searchQuery = "";
    private SortMode sortMode = SortMode.NAME_ASC;
    private int categoryFilter = 0; // 0 = all, 1-9 = category

    private enum SortMode {
        NAME_ASC("Name A-Z"),
        NAME_DESC("Name Z-A"),
        AMOUNT_DESC("Menge (viel -> wenig)"),
        CATEGORY("Kategorie");

        private final String displayName;

        SortMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public LagerView(Storage plugin, UUID shulkerId) {
        super(plugin, Component.text("Lager Inhalt"), 6);
        this.shulkerId = shulkerId;
        this.sortMode = loadSortMode();
    }

    public LagerView(Storage plugin, UUID shulkerId, int initialCategoryFilter) {
        this(plugin, shulkerId);
        if (initialCategoryFilter >= 0 && initialCategoryFilter <= 9) {
            this.categoryFilter = initialCategoryFilter;
        }
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
        PlayerLager lager = plugin.getLagerManager().getLager(resolveStorageOwner(player));
        List<StorageItem> filteredItems = getFilteredItems(lager);
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);

        int start = page * 45;
        int unlockedOnPage = getUnlockedSlotsOnPage(lager);
        int end = Math.min(start + unlockedOnPage, filteredItems.size());
        for (int i = start; i < end; i++) {
            StorageItem storageItem = filteredItems.get(i);
            ItemStack item = storageItem.toItemStack();
            if (item == null) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                lore.add(Component.text("---", NamedTextColor.DARK_GRAY));
                lore.add(Component.text("Menge im Lager: " + storageItem.getAmount(), NamedTextColor.GRAY));

                int categoryNum = getEffectiveCategory(settings, item.getType());
                lore.add(Component.text("Kategorie: " + categoryNum + " (" + settings.getCategoryName(categoryNum) + ")",
                        NamedTextColor.AQUA));

                lore.add(Component.text("---", NamedTextColor.DARK_GRAY));
                lore.add(Component.text("Linksklick: 1 entnehmen", NamedTextColor.YELLOW));
                lore.add(Component.text("Rechtsklick: 1 Stack entnehmen", NamedTextColor.YELLOW));
                lore.add(Component.text("Q (Drop): Inventar f\u00fcllen", NamedTextColor.YELLOW));
                lore.add(Component.text("---", NamedTextColor.DARK_GRAY));
                lore.add(Component.text("Kategorie per Zahlentaste setzen:", NamedTextColor.AQUA));
                lore.add(Component.text("1: " + settings.getCategoryName(1) + "  2: " + settings.getCategoryName(2)
                        + "  3: " + settings.getCategoryName(3), NamedTextColor.GRAY));
                lore.add(Component.text("4: " + settings.getCategoryName(4) + "  5: " + settings.getCategoryName(5)
                        + "  6: " + settings.getCategoryName(6), NamedTextColor.GRAY));
                lore.add(Component.text("7: " + settings.getCategoryName(7) + "  8: " + settings.getCategoryName(8)
                        + "  9: " + settings.getCategoryName(9), NamedTextColor.GRAY));
                lore.add(Component.text("0/F: Aus Kategorie entfernen", NamedTextColor.RED));

                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(i - start, item);
        }

        for (int slot = unlockedOnPage; slot < 45; slot++) {
            inventory.setItem(slot, createItem(Material.RED_STAINED_GLASS_PANE, "Gesperrt"));
        }

        addNavigationItems(player);
    }

    protected void addNavigationItems(Player player) {
        PlayerLager lager = plugin.getLagerManager().getLager(resolveStorageOwner(player));
        List<StorageItem> filteredItems = getFilteredItems(lager);
        int totalPages = Math.max(1, (int) Math.ceil(filteredItems.size() / 45.0));

        inventory.setItem(45, createItem(Material.ARROW, "Zur\u00fcck zum Hauptmen\u00fc"));
        inventory.setItem(46, createItem(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "Upgrades"));
        inventory.setItem(47, createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
        inventory.setItem(48, createItemWithLore(Material.COMPARATOR, "Sortierung",
                "Aktuell: " + sortMode.getDisplayName(),
                "Linksklick: n\u00e4chste Sortierung",
                "Rechtsklick: auf Standard zur\u00fccksetzen"));
        inventory.setItem(49, createItem(Material.HOPPER, "Items ins Lager legen"));
        inventory.setItem(50, createItemWithLore(Material.CHEST, "Kategorien",
                "Filter: " + getCategoryFilterText(),
                "Linksklick: n\u00e4chste Kategorie",
                "Rechtsklick: alle anzeigen"));
        inventory.setItem(51, createItemWithLore(Material.SPYGLASS, "Suche",
                "Linksklick: Suche \u00f6ffnen",
                "Rechtsklick: Suche l\u00f6schen"));

        if (page > 0) {
            inventory.setItem(52, createItemWithLore(Material.ARROW, "Vorherige Seite",
                    "Seite " + page + " von " + totalPages));
        } else {
            inventory.setItem(52, createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
        }

        if (page < totalPages - 1) {
            inventory.setItem(53, createItemWithLore(Material.ARROW, "N\u00e4chste Seite",
                    "Seite " + (page + 2) + " von " + totalPages));
        } else {
            inventory.setItem(53, createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
        }
    }

    private List<StorageItem> getFilteredItems(PlayerLager lager) {
        return getFilteredItems(lager, searchQuery);
    }

    private List<StorageItem> getFilteredItems(PlayerLager lager, String queryText) {
        List<StorageItem> filtered = new ArrayList<>();
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);

        String effectiveQuery = queryText == null ? "" : queryText.trim();
        String query = "";
        String translatedQuery = "";
        String normalizedQuery = "";
        boolean useSearch = !effectiveQuery.isEmpty();
        if (useSearch) {
            query = effectiveQuery.toLowerCase().replace(" ", "_");
            translatedQuery = TranslationManager.translatePartial(query).toLowerCase();
            normalizedQuery = TranslationManager.normalize(effectiveQuery);
        }

        for (StorageItem item : lager.getItems()) {
            ItemStack stack = item.toItemStack();
            if (stack == null) {
                continue;
            }

            int effectiveCategory = getEffectiveCategory(settings, stack.getType());
            if (categoryFilter > 0 && effectiveCategory != categoryFilter) {
                continue;
            }

            if (useSearch) {
                final String loweredQuery = query;
                final String loweredTranslatedQuery = translatedQuery;
                final String loweredEffectiveQuery = effectiveQuery.toLowerCase();
                final String loweredNormalizedQuery = normalizedQuery;
                String matName = stack.getType().name().toLowerCase();
                String normalizedMatName = TranslationManager.normalize(matName);
                String displayName = "";
                Set<String> aliases = TranslationManager.getSearchAliases(stack.getType());
                ItemMeta meta = stack.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    try {
                        Component dn = meta.displayName();
                        if (dn != null) {
                            displayName = PlainTextComponentSerializer.plainText().serialize(dn).toLowerCase();
                        }
                    } catch (Exception e) {
                        // ignore malformed display names
                    }
                }
                String normalizedDisplayName = TranslationManager.normalize(displayName);

                boolean aliasMatch = aliases.stream().anyMatch(alias ->
                        alias.contains(loweredQuery)
                                || alias.contains(loweredTranslatedQuery)
                                || alias.contains(loweredEffectiveQuery)
                                || alias.contains(loweredNormalizedQuery));

                if (!(matName.contains(loweredQuery)
                        || matName.contains(loweredTranslatedQuery)
                        || normalizedMatName.contains(loweredNormalizedQuery)
                        || displayName.contains(loweredEffectiveQuery)
                        || normalizedDisplayName.contains(loweredNormalizedQuery)
                        || aliasMatch)) {
                    continue;
                }
            }

            filtered.add(item);
        }

        Comparator<StorageItem> comparator = switch (sortMode) {
            case NAME_DESC -> Comparator.comparing(StorageItem::getMaterial).reversed();
            case AMOUNT_DESC -> Comparator.comparingInt(StorageItem::getAmount).reversed()
                    .thenComparing(StorageItem::getMaterial);
            case CATEGORY -> Comparator
                    .comparingInt((StorageItem item) -> {
                        ItemStack stack = item.toItemStack();
                        return stack == null ? 10 : getEffectiveCategory(settings, stack.getType());
                    })
                    .thenComparing(StorageItem::getMaterial);
            case NAME_ASC -> Comparator.comparing(StorageItem::getMaterial);
        };

        filtered.sort(comparator);
        return filtered;
    }

    private int countFilteredItemsForQuery(Player player, String queryText) {
        PlayerLager lager = plugin.getLagerManager().getLager(resolveStorageOwner(player));
        return getFilteredItems(lager, queryText).size();
    }

    private int getEffectiveCategory(ShulkerSettings settings, Material material) {
        int assigned = settings.getCategoryForMaterial(material.name());
        if (assigned > 0) {
            return assigned;
        }
        return ItemCategory.getCategoryForMaterial(material).getNumber();
    }

    private String getCategoryFilterText() {
        if (categoryFilter == 0) {
            return "Alle";
        }
        ItemCategory category = ItemCategory.fromNumber(categoryFilter);
        if (category == null) {
            return "Alle";
        }
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        return categoryFilter + " (" + settings.getCategoryName(categoryFilter) + ")";
    }

    private SortMode loadSortMode() {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        String stored = settings.getLagerSortMode();
        if (stored == null || stored.isBlank()) {
            return SortMode.NAME_ASC;
        }
        try {
            return SortMode.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return SortMode.NAME_ASC;
        }
    }

    private void saveSortMode() {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        settings.setLagerSortMode(sortMode.name());
        plugin.getLagerManager().saveShulkerSettings(shulkerId);
    }

    @Override
    public void handleInteraction(Player player, int slot, ItemStack clickedItem, ClickType clickType, int hotbarButton) {
        if (slot < 45) {
            UUID storageOwner = resolveStorageOwner(player);
            PlayerLager currentLager = plugin.getLagerManager().getLager(storageOwner);
            if (slot >= getUnlockedSlotsOnPage(currentLager)) {
                player.sendMessage(Component.text("Dieser Lager-Slot ist gesperrt. Upgrade n\u00f6tig.", NamedTextColor.RED));
                return;
            }

            if (clickedItem == null || clickedItem.getType() == Material.AIR
                    || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                return;
            }

            PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
            List<StorageItem> filtered = getFilteredItems(lager);
            int index = page * 45 + slot;
            if (index >= filtered.size()) {
                return;
            }

            StorageItem storageItem = filtered.get(index);
            ItemStack result = storageItem.toItemStack();
            if (result == null) {
                player.sendMessage(Component.text("Dieses Item konnte nicht geladen werden.", NamedTextColor.RED));
                return;
            }
            int amountToTake = 0;

            if (clickType == ClickType.NUMBER_KEY) {
                if (hotbarButton >= 0 && hotbarButton <= 8) {
                    int category = hotbarButton + 1;
                    assignCategory(player, result.getType().name(), category);
                    return;
                }
            } else if (clickType == ClickType.SWAP_OFFHAND) {
                assignCategory(player, result.getType().name(), 0);
                return;
            } else if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
                String materialName = result.getType().name();
                int currentCategory = settings.getCategoryForMaterial(materialName);

                int next = clickType == ClickType.SHIFT_LEFT ? (currentCategory + 1) % 10 : currentCategory - 1;
                if (next < 0) {
                    next = 9;
                }

                settings.removeFromAllCategories(materialName);
                if (next > 0) {
                    settings.addToCategory(next, materialName);
                    player.sendMessage(Component.text(
                            result.getType().name() + " -> Kategorie " + next + " (" + settings.getCategoryName(next) + ")",
                            NamedTextColor.AQUA));
                } else {
                    player.sendMessage(Component.text(result.getType().name() + " aus Kategorien entfernt",
                            NamedTextColor.YELLOW));
                }

                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                setMenuItems(player);
                return;
            } else if (clickType == ClickType.LEFT) {
                amountToTake = 1;
            } else if (clickType == ClickType.RIGHT) {
                amountToTake = Math.min(storageItem.getAmount(), result.getMaxStackSize());
            } else if (clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP) {
                amountToTake = Math.min(storageItem.getAmount(), getInsertableAmount(player.getInventory(), result));
            }

            if (amountToTake > 0) {
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
            }
        } else {
            handleNavigation(player, slot, clickType);
        }
    }

    private void assignCategory(Player player, String materialName, int category) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        settings.removeFromAllCategories(materialName);
        if (category > 0) {
            settings.addToCategory(category, materialName);
            player.sendMessage(Component.text(
                    materialName + " -> Kategorie " + category + " (" + settings.getCategoryName(category) + ")",
                    NamedTextColor.AQUA));
        } else {
            player.sendMessage(Component.text(materialName + " aus Kategorien entfernt", NamedTextColor.YELLOW));
        }
        plugin.getLagerManager().saveShulkerSettings(shulkerId);
        setMenuItems(player);
    }

    private int getUnlockedSlotsOnPage(PlayerLager lager) {
        int unlocked = lager.getUnlockedSlots();
        int pageStart = page * 45;
        int remaining = unlocked - pageStart;
        if (remaining <= 0) {
            return 0;
        }
        return Math.min(45, remaining);
    }

    private void handleNavigation(Player player, int slot, ClickType clickType) {
        UUID storageOwner = resolveStorageOwner(player);
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
        List<StorageItem> filteredItems = getFilteredItems(lager);
        int totalPages = Math.max(1, (int) Math.ceil(filteredItems.size() / 45.0));

        switch (slot) {
            case 52 -> {
                if (page > 0) {
                    page--;
                    setMenuItems(player);
                }
            }
            case 53 -> {
                if (page < totalPages - 1) {
                    page++;
                    setMenuItems(player);
                }
            }
            case 45 -> new QuickSlotsView(plugin, shulkerId).open(player);
            case 51 -> {
                if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
                    searchQuery = "";
                    page = 0;
                    player.sendMessage(Component.text("Suche zur\u00fcckgesetzt.", NamedTextColor.YELLOW));
                    setMenuItems(player);
                } else {
                    openSearch(player);
                }
            }
            case 46 -> new UpgradeMenu(plugin, shulkerId).open(player);
            case 48 -> {
                if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
                    sortMode = SortMode.NAME_ASC;
                    player.sendMessage(Component.text("Sortierung zur\u00fcckgesetzt.", NamedTextColor.YELLOW));
                } else {
                    sortMode = sortMode.next();
                    player.sendMessage(Component.text("Sortierung: " + sortMode.getDisplayName(), NamedTextColor.YELLOW));
                }
                saveSortMode();
                page = 0;
                setMenuItems(player);
            }
            case 49 -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor.getType() != Material.AIR) {
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
            case 50 -> {
                if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
                    categoryFilter = 0;
                    player.sendMessage(Component.text("Kategorien-Filter: Alle", NamedTextColor.YELLOW));
                } else {
                    categoryFilter = (categoryFilter + 1) % 10;
                    player.sendMessage(
                            Component.text("Kategorien-Filter: " + getCategoryFilterText(), NamedTextColor.YELLOW));
                }
                page = 0;
                setMenuItems(player);
            }
        }
    }

    private void openSearch(Player player) {
        plugin.getChatPromptManager().requestText(
                player,
                "Suche",
                searchQuery == null || searchQuery.isBlank() ? "" : searchQuery,
                input -> {
                    this.searchQuery = input == null ? "" : input.trim();
                    this.page = 0;
                    this.open(player);
                },
                () -> this.open(player)
        );
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
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line).color(NamedTextColor.GRAY));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}


