package de.mcbesser.storage.managers;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.gui.ColorMenu;
import de.mcbesser.storage.models.ItemCategory;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import de.mcbesser.storage.gui.LagerView;
import de.mcbesser.storage.gui.QuickSlotsView;
import de.mcbesser.storage.gui.RefillSettingsView;
import de.mcbesser.storage.managers.TranslationManager;
import de.mcbesser.storage.gui.VacuumFilterSettingsView;
import de.mcbesser.storage.gui.VacuumSettingsView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StorageDisplayManager {
    private static final int DISPLAY_COLUMNS = 9;
    private static final int DISPLAY_ROWS = 6;
    private static final int DISPLAY_SLOT_COUNT = DISPLAY_COLUMNS * DISPLAY_ROWS;
    private static final int QUICK_SLOT_COLUMNS = 9;
    private static final int QUICK_SLOT_ROWS = 4;
    private static final int QUICK_SLOT_COUNT = QUICK_SLOT_COLUMNS * QUICK_SLOT_ROWS;
    private static final double DEFAULT_ENTITY_INTERACTION_RANGE = 3.0;

    private final Storage plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey displayYawKey;
    private final NamespacedKey shulkerKey;
    private final NamespacedKey slotKey;
    private final Map<UUID, Location> trackedLocations = new HashMap<>();
    private final Map<UUID, DisplayCluster> activeDisplays = new HashMap<>();
    private BukkitTask refreshTask;
    private int passiveRescanCounter;

    public StorageDisplayManager(Storage plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.displayYawKey = new NamespacedKey(plugin, "display_yaw");
        this.shulkerKey = new NamespacedKey(plugin, "display_shulker_id");
        this.slotKey = new NamespacedKey(plugin, "display_slot");
    }

    public void start() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        passiveRescanCounter = 0;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 10L);
        Bukkit.getScheduler().runTask(plugin, this::scanLoadedChunks);
        Bukkit.getScheduler().runTaskLater(plugin, this::scanLoadedChunks, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::scanLoadedChunks, 60L);
        Bukkit.getScheduler().runTaskLater(plugin, this::scanLoadedChunks, 120L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        clearAll();
    }

    public void trackPlacedShulker(UUID shulkerId, Location location) {
        if (shulkerId == null || location == null || location.getWorld() == null) {
            return;
        }
        trackedLocations.put(shulkerId, location.clone());
        refreshShulker(shulkerId);
    }

    public void untrackShulker(UUID shulkerId) {
        trackedLocations.remove(shulkerId);
        removeDisplay(shulkerId);
    }

    public void scanChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof ShulkerBox shulker)) {
                continue;
            }
            String rawId = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            try {
                trackPlacedShulker(UUID.fromString(rawId), shulker.getLocation());
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed ids.
            }
        }
    }

    public void refreshShulker(UUID shulkerId) {
        Location location = trackedLocations.get(shulkerId);
        if (location == null || location.getWorld() == null) {
            removeDisplay(shulkerId);
            return;
        }

        Block block = location.getBlock();
        if (!(block.getState() instanceof ShulkerBox shulker)) {
            trackedLocations.remove(shulkerId);
            removeDisplay(shulkerId);
            return;
        }

        String rawId = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
        if (!shulkerId.toString().equals(rawId)) {
            trackedLocations.remove(shulkerId);
            removeDisplay(shulkerId);
            return;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        if (!settings.isDisplayEnabled()) {
            removeDisplay(shulkerId);
            return;
        }

        UUID storageOwner = resolveStorageOwner(settings, shulker);
        if (storageOwner == null) {
            removeDisplay(shulkerId);
            return;
        }
        if (!hasNearbyViewer(location)) {
            removeDisplay(shulkerId);
            return;
        }
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
        float displayYaw = resolveDisplayYaw(shulker);
        DisplayCluster cluster = activeDisplays.get(shulkerId);
        if (cluster == null || !cluster.isValid()) {
            removeDisplay(shulkerId);
            cluster = spawnCluster(location, shulkerId, displayYaw);
            activeDisplays.put(shulkerId, cluster);
        }
        updateCluster(cluster, settings, lager, displayYaw);
    }

    public boolean handleDisplayUse(Player player, UUID shulkerId, int displaySlot, boolean rightClick) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        UUID storageOwner = resolveStorageOwner(settings, null);
        if (storageOwner == null) {
            return true;
        }
        if (!canAccessStorage(player, shulkerId, storageOwner)) {
            return false;
        }

        int row = displaySlot / DISPLAY_COLUMNS;
        int column = displaySlot % DISPLAY_COLUMNS;

        if (row == 0) {
            new LagerView(plugin, shulkerId, column + 1).open(player);
            return true;
        }

        if (row == 5) {
            return handleMenuAction(player, shulkerId, storageOwner, displaySlot, rightClick);
        }

        if (row < 1 || row > 4) {
            return true;
        }

        int quickSlotIndex = ((row - 1) * QUICK_SLOT_COLUMNS) + column;
        String materialName = settings.getQuickSlots().get(9 + quickSlotIndex);
        if (materialName == null || materialName.isBlank()) {
            return true;
        }

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return true;
        }

        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
        int available = lager.getAmountByMaterial(material);
        if (available <= 0) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Keine Items diesen Typs im Lager!",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }

        int requested = rightClick ? material.getMaxStackSize() : 1;
        int amount = Math.min(available, requested);
        int removed = lager.removeByMaterial(material, amount);
        if (removed <= 0) {
            return true;
        }

        plugin.getLagerManager().saveLager(storageOwner);
        ItemStack give = new ItemStack(material, removed);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(give);
        for (ItemStack rest : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        refreshShulker(shulkerId);
        return true;
    }

    public boolean isDisplayEntity(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(shulkerKey, PersistentDataType.STRING);
    }

    public UUID getDisplayShulkerId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String rawId = entity.getPersistentDataContainer().get(shulkerKey, PersistentDataType.STRING);
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public int getDisplaySlot(Entity entity) {
        if (entity == null) {
            return -1;
        }
        Integer slot = entity.getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
        return slot == null ? -1 : slot;
    }

    public HoveredDisplayInfo getHoveredDisplayInfo(Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }

        double interactionRange = getEntityInteractionRange(player);
        RayTraceResult rayTrace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                interactionRange,
                entity -> isDisplayEntity(entity) && getDisplaySlot(entity) >= 0
        );
        if (rayTrace == null || rayTrace.getHitEntity() == null) {
            return null;
        }

        Entity entity = rayTrace.getHitEntity();
        UUID shulkerId = getDisplayShulkerId(entity);
        int displaySlot = getDisplaySlot(entity);
        if (shulkerId == null || displaySlot < 0) {
            return null;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        UUID storageOwner = resolveStorageOwner(settings, null);
        if (storageOwner == null) {
            return null;
        }

        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
        SlotVisual visual = createSlotVisual(displaySlot, settings, lager);
        ItemMeta meta = visual.itemStack().getItemMeta();
        Component title = meta != null && meta.displayName() != null
                ? meta.displayName()
                : Component.text(TranslationManager.toGermanMaterialName(visual.itemStack().getType()), NamedTextColor.YELLOW);
        List<Component> lore = meta != null && meta.hasLore() && meta.lore() != null
                ? new ArrayList<>(meta.lore())
                : new ArrayList<>();

        return new HoveredDisplayInfo(shulkerId, displaySlot, title, lore);
    }

    public double getEntityInteractionRange(Player player) {
        return getPlayerAttributeValue(player, Attribute.ENTITY_INTERACTION_RANGE, DEFAULT_ENTITY_INTERACTION_RANGE);
    }

    private double getPlayerAttributeValue(Player player, Attribute attribute, double fallback) {
        if (player == null || attribute == null) {
            return fallback;
        }

        AttributeInstance attributeInstance = player.getAttribute(attribute);
        if (attributeInstance == null) {
            return fallback;
        }

        double value = attributeInstance.getValue();
        return value > 0.0 ? value : fallback;
    }

    private void refreshAll() {
        passiveRescanCounter++;
        if (passiveRescanCounter >= 12) {
            passiveRescanCounter = 0;
            scanLoadedChunks();
        }

        scanNearbyPlayerChunks();

        if (trackedLocations.isEmpty()) {
            return;
        }

        List<UUID> trackedIds = new ArrayList<>(trackedLocations.keySet());
        for (UUID shulkerId : trackedIds) {
            refreshShulker(shulkerId);
        }
    }

    private void scanLoadedChunks() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    private void scanNearbyPlayerChunks() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == null) {
                continue;
            }

            Chunk centerChunk = player.getLocation().getChunk();
            for (int chunkX = centerChunk.getX() - 2; chunkX <= centerChunk.getX() + 2; chunkX++) {
                for (int chunkZ = centerChunk.getZ() - 2; chunkZ <= centerChunk.getZ() + 2; chunkZ++) {
                    if (!player.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                        continue;
                    }
                    scanChunk(player.getWorld().getChunkAt(chunkX, chunkZ));
                }
            }
        }
    }

    private DisplayCluster spawnCluster(Location location, UUID shulkerId, float displayYaw) {
        Location titleLocation = offsetLocation(location, 0.0, 4.45, -0.03, displayYaw);
        TextDisplay title = titleLocation.getWorld().spawn(titleLocation, TextDisplay.class, display -> {
            prepareDisplayEntity(display, shulkerId, -1);
            display.setBillboard(Display.Billboard.FIXED);
            display.text(net.kyori.adventure.text.Component.text("Lageranzeige"));
            display.setRotation(displayYaw, 0f);
        });

        List<ItemDisplay> backgroundDisplays = new ArrayList<>(DISPLAY_SLOT_COUNT);
        List<ItemDisplay> itemDisplays = new ArrayList<>(DISPLAY_SLOT_COUNT);
        List<TextDisplay> amountDisplays = new ArrayList<>(DISPLAY_SLOT_COUNT);
        List<Interaction> interactions = new ArrayList<>(DISPLAY_SLOT_COUNT);

        for (int index = 0; index < DISPLAY_SLOT_COUNT; index++) {
            int row = index / DISPLAY_COLUMNS;
            int column = index % DISPLAY_COLUMNS;
            double xOffset = -2.0 + (column * 0.50);
            double yOffset = 4.00 - (row * 0.50);

            Location backgroundLocation = offsetLocation(location, xOffset, yOffset, 0.0, displayYaw);
            ItemDisplay backgroundDisplay = backgroundLocation.getWorld().spawn(backgroundLocation, ItemDisplay.class, display -> {
                prepareDisplayEntity(display, shulkerId, -1);
                display.setBillboard(Display.Billboard.FIXED);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setTransformation(
                        new org.bukkit.util.Transformation(new Vector3f(), new Quaternionf(),
                                new Vector3f(0.50f, 0.50f, 0.50f), new Quaternionf()));
                display.setRotation(displayYaw, 0f);
            });

            Location itemLocation = offsetLocation(location, xOffset, yOffset + 0.06, 0.02, displayYaw);
            ItemDisplay itemDisplay = itemLocation.getWorld().spawn(itemLocation, ItemDisplay.class, display -> {
                prepareDisplayEntity(display, shulkerId, -1);
                display.setBillboard(Display.Billboard.FIXED);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setTransformation(
                        new org.bukkit.util.Transformation(new Vector3f(), new Quaternionf(),
                                new Vector3f(0.28f, 0.28f, 0.28f), new Quaternionf()));
                display.setRotation(displayYaw, 0f);
            });

            Location amountLocation = offsetLocation(location, xOffset, yOffset - 0.22, 0.02, displayYaw);
            TextDisplay amountDisplay = amountLocation.getWorld().spawn(amountLocation, TextDisplay.class, display -> {
                prepareDisplayEntity(display, shulkerId, -1);
                display.setBillboard(Display.Billboard.FIXED);
                display.setTransformation(new org.bukkit.util.Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.60f, 0.60f, 0.60f), new Quaternionf()));
                display.setRotation(displayYaw, 0f);
            });

            backgroundDisplays.add(backgroundDisplay);
            itemDisplays.add(itemDisplay);
            amountDisplays.add(amountDisplay);

            final int displaySlot = index;
            Location interactionLocation = offsetLocation(location, xOffset, yOffset - 0.255, 0.0, displayYaw);
            Interaction interaction = interactionLocation.getWorld().spawn(interactionLocation, Interaction.class, hitbox -> {
                prepareDisplayEntity(hitbox, shulkerId, displaySlot);
                hitbox.setInteractionWidth(0.50f);
                hitbox.setInteractionHeight(0.50f);
                hitbox.setResponsive(true);
                hitbox.setRotation(displayYaw, 0f);
            });
            interactions.add(interaction);
        }

        return new DisplayCluster(location.clone(), title, backgroundDisplays, itemDisplays, amountDisplays, interactions);
    }

    private void updateCluster(DisplayCluster cluster, ShulkerSettings settings, PlayerLager lager, float displayYaw) {
        String ownerName = settings.getOwnerName();
        if (ownerName == null || ownerName.isBlank()) {
            ownerName = "Unbekannt";
        }
        cluster.title().text(net.kyori.adventure.text.Component.text("Lager von: " + ownerName));
        cluster.title().setRotation(displayYaw, 0f);

        for (int index = 0; index < DISPLAY_SLOT_COUNT; index++) {
            cluster.backgroundDisplays().get(index).setItemStack(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
            cluster.backgroundDisplays().get(index).setRotation(displayYaw, 0f);

            SlotVisual slotVisual = createSlotVisual(index, settings, lager);
            cluster.itemDisplays().get(index).setItemStack(slotVisual.itemStack());
            cluster.itemDisplays().get(index).setRotation(displayYaw, 0f);
            cluster.amountDisplays().get(index).text(net.kyori.adventure.text.Component.text(slotVisual.amountText()));
            cluster.amountDisplays().get(index).setRotation(displayYaw, 0f);
        }
    }

    private SlotVisual createSlotVisual(int displaySlot, ShulkerSettings settings, PlayerLager lager) {
        int row = displaySlot / DISPLAY_COLUMNS;
        int column = displaySlot % DISPLAY_COLUMNS;

        if (row == 0) {
            int categoryNumber = column + 1;
            Material icon = Material.matchMaterial(settings.getCategoryIconMaterial(categoryNumber));
            if (icon == null) {
                ItemCategory fallback = ItemCategory.fromNumber(categoryNumber);
                icon = fallback != null ? fallback.getIcon() : Material.CHEST;
            }
            ItemStack stack = new ItemStack(icon);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(settings.getCategoryName(categoryNumber), NamedTextColor.AQUA));
                meta.lore(List.of(
                        Component.text("Funktion: Kategorie in der Lageransicht \u00f6ffnen", NamedTextColor.GRAY),
                        Component.text("Linksklick: Kategorie \u00f6ffnen", NamedTextColor.GRAY)
                ));
                stack.setItemMeta(meta);
            }
            return new SlotVisual(stack, "");
        }

        if (row >= 1 && row <= 4) {
            int quickSlot = 9 + ((row - 1) * QUICK_SLOT_COLUMNS) + column;
            String materialName = settings.getQuickSlots().get(quickSlot);
            Material material = materialName != null ? Material.matchMaterial(materialName) : null;
            if (material == null) {
                Material placeholder = quickSlot < 18 ? Material.GREEN_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
                ItemStack stack = new ItemStack(placeholder);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("Leerer Schnellzugriffslot " + (quickSlot - 8), NamedTextColor.YELLOW));
                    meta.lore(List.of(
                            Component.text("Status: Nicht belegt", NamedTextColor.GRAY),
                            Component.text("Funktion: Keine Entnahme m\u00f6glich", NamedTextColor.GRAY)
                    ));
                    stack.setItemMeta(meta);
                }
                return new SlotVisual(stack, "");
            }
            int amount = lager.getAmountByMaterial(material);
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String germanName = TranslationManager.toGermanMaterialName(material);
                meta.displayName(Component.text(germanName, NamedTextColor.YELLOW));
                meta.lore(List.of(
                        Component.text("Name: " + germanName, NamedTextColor.GRAY),
                        Component.text("Menge: " + amount, NamedTextColor.GRAY),
                        Component.text("Linksklick: 1 Item nehmen", NamedTextColor.GRAY),
                        Component.text("Rechtsklick: 1 Stack nehmen", NamedTextColor.GRAY)
                ));
                stack.setItemMeta(meta);
            }
            return new SlotVisual(stack, amount > 0 ? abbreviateAmount(amount) : "-");
        }

        return new SlotVisual(createMenuSlotItem(displaySlot, settings, lager), "");
    }

    private ItemStack named(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(TranslationManager.toGermanMaterialName(material), NamedTextColor.YELLOW));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createMenuSlotItem(int displaySlot, ShulkerSettings settings, PlayerLager lager) {
        return switch (displaySlot) {
            case 45 -> itemWithLore(Material.HOPPER_MINECART, "Filtereinstellungen",
                    "Funktion: Einstellungen f\u00fcr den Einsaugfilter \u00f6ffnen",
                    "Linksklick: Filtereinstellungen \u00f6ffnen");
            case 46 -> itemWithLore(Material.BLAZE_POWDER, "Einsaugen: " + (settings.isVacuumEnabled() ? "AN" : "AUS"),
                    "Funktion: Einsaugen ein oder ausschalten",
                    "Linksklick: Einsaugen umschalten",
                    "Rechtsklick: Einsaug-Einstellungen \u00f6ffnen");
            case 47 -> itemWithLore(Material.GOLDEN_PICKAXE, "Automatische Einlagerung: " + (settings.isAutoStore() ? "AN" : "AUS"),
                    "Funktion: Abgebaute Items direkt einlagern",
                    "Linksklick: Automatische Einlagerung umschalten");
            case 48 -> itemWithLore(Material.SHULKER_BOX, "Shulker nachf\u00fcllen und einlagern: "
                            + (settings.isShulkerRefillEnabled() ? "AN" : "AUS"),
                    "Funktion: Nachf\u00fcllen und Einlagern umschalten",
                    "Linksklick: Funktion umschalten",
                    "Rechtsklick: Einstellungen \u00f6ffnen");
            case 49 -> itemWithLore(Material.HOPPER, "Items ins Lager legen",
                    "Funktion: Items aus dem Inventar einlagern",
                    "Mit Cursor-Item: Dieses Item einlagern",
                    "Ohne Cursor-Item: Hauptinventar einlagern");
            case 50 -> itemWithLore(settings.isDisplayEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                    "Display anzeigen: " + (settings.isDisplayEnabled() ? "AN" : "AUS"),
                    "Funktion: Display oder Bereich-Partikel steuern",
                    "Linksklick: Anzeige an oder aus",
                    "Rechtsklick: Bereich-Partikel " + (settings.isVacuumRangeParticlesEnabled() ? "ausschalten" : "einschalten"));
            case 51 -> itemWithLore(Material.PINK_DYE, "Farbe w\u00e4hlen",
                    "Funktion: Shulker-Farbe konfigurieren",
                    "Linksklick: Farbmen\u00fc \u00f6ffnen");
            case 52 -> itemWithLore(Material.EXPERIENCE_BOTTLE, "Erfahrungsspeicher",
                    "Funktion: Erfahrung einlagern oder entnehmen",
                    "Gespeichert: " + lager.getStoredExp() + " XP",
                    "Linksklick: gesamte Spieler-XP einlagern",
                    "Rechtsklick: 100 XP als Orbs ausgeben");
            case 53 -> itemWithLore(Material.SPYGLASS, "Lager durchsuchen",
                    "Funktion: Lageransicht mit Kategorien und Suche \u00f6ffnen",
                    "Linksklick: Lageransicht \u00f6ffnen");
            default -> named(Material.BLACK_STAINED_GLASS_PANE);
        };
    }

    private ItemStack itemWithLore(Material material, String name, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW));
            List<Component> lore = new ArrayList<>();
            for (String loreLine : loreLines) {
                if (loreLine == null || loreLine.isBlank()) {
                    continue;
                }
                lore.add(Component.text(loreLine, NamedTextColor.GRAY));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String abbreviateAmount(int amount) {
        if (amount >= 1_000_000) {
            double value = amount / 1_000_000.0;
            return formatCompact(value) + "kk";
        }
        if (amount >= 100_000) {
            double value = amount / 1_000.0;
            return formatCompact(value) + "k";
        }
        return String.valueOf(amount);
    }

    private String formatCompact(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.valueOf((int) Math.rint(rounded));
        }
        return String.valueOf(rounded).replace(".0", "");
    }

    private boolean handleMenuAction(Player player, UUID shulkerId, UUID storageOwner, int displaySlot, boolean rightClick) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);

        switch (displaySlot) {
            case 45 -> new VacuumFilterSettingsView(plugin, shulkerId).open(player);
            case 46 -> {
                if (rightClick) {
                    new VacuumSettingsView(plugin, shulkerId).open(player);
                } else {
                    settings.setVacuumEnabled(!settings.isVacuumEnabled());
                    plugin.getLagerManager().saveShulkerSettings(shulkerId);
                    player.sendMessage(Component.text("Einsaugen: " + (settings.isVacuumEnabled() ? "AN" : "AUS"),
                            NamedTextColor.YELLOW));
                }
            }
            case 47 -> {
                settings.setAutoStore(!settings.isAutoStore());
                plugin.getLagerManager().saveShulkerSettings(shulkerId);
                player.sendMessage(Component.text("Auto-Store: " + (settings.isAutoStore() ? "AN" : "AUS"),
                        NamedTextColor.YELLOW));
            }
            case 48 -> {
                if (rightClick) {
                    new RefillSettingsView(plugin, shulkerId).open(player);
                } else {
                    settings.setShulkerRefillEnabled(!settings.isShulkerRefillEnabled());
                    plugin.getLagerManager().saveShulkerSettings(shulkerId);
                    player.sendMessage(Component.text("Shulker nachf\u00fcllen und einlagern: "
                            + (settings.isShulkerRefillEnabled() ? "AN" : "AUS"), NamedTextColor.YELLOW));
                }
            }
            case 49 -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
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
            }
            case 50 -> {
                if (rightClick) {
                    settings.setVacuumRangeParticlesEnabled(!settings.isVacuumRangeParticlesEnabled());
                    plugin.getLagerManager().saveShulkerSettings(shulkerId);
                    player.sendMessage(Component.text("Bereich-Partikel: "
                            + (settings.isVacuumRangeParticlesEnabled() ? "AN" : "AUS"), NamedTextColor.YELLOW));
                } else {
                    settings.setDisplayEnabled(!settings.isDisplayEnabled());
                    plugin.getLagerManager().saveShulkerSettings(shulkerId);
                    player.sendMessage(Component.text("Display anzeigen: " + (settings.isDisplayEnabled() ? "AN" : "AUS"),
                            NamedTextColor.YELLOW));
                    if (!settings.isDisplayEnabled()) {
                        removeDisplay(shulkerId);
                    }
                }
            }
            case 51 -> new ColorMenu(plugin, shulkerId).open(player);
            case 52 -> {
                if (rightClick) {
                    int taken = lager.takeStoredExp(100);
                    if (taken <= 0) {
                        player.sendMessage(Component.text("Kein XP im Speicher.", NamedTextColor.YELLOW));
                    } else {
                        plugin.getLagerManager().saveLager(storageOwner);
                        dropExperience(player, taken);
                        player.sendMessage(Component.text(taken + " XP als Orbs ausgegeben.", NamedTextColor.GREEN));
                    }
                } else {
                    int current = getPlayerTotalExperience(player);
                    if (current <= 0) {
                        player.sendMessage(Component.text("Du hast keine XP zum Einlagern.", NamedTextColor.YELLOW));
                    } else {
                        lager.addStoredExp(current);
                        plugin.getLagerManager().saveLager(storageOwner);
                        setPlayerTotalExperience(player, 0);
                        player.sendMessage(Component.text(current + " XP eingelagert.", NamedTextColor.GREEN));
                    }
                }
            }
            case 53 -> new LagerView(plugin, shulkerId).open(player);
            default -> {
                return true;
            }
        }

        refreshShulker(shulkerId);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        return true;
    }

    private void dropExperience(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int orbValue = Math.min(remaining, 100);
            org.bukkit.entity.ExperienceOrb orb = player.getWorld().spawn(
                    player.getLocation().add(0, 0.5, 0), org.bukkit.entity.ExperienceOrb.class);
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
        }

        player.setLevel(level);
        player.setExp(level > 0 ? (float) exp / getExpAtLevel(level) : 0f);
    }

    private int getTotalExperienceForLevel(int level) {
        int total = 0;
        for (int current = 0; current < level; current++) {
            total += getExpAtLevel(current);
        }
        return total;
    }

    private int getExpAtLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }

    private boolean hasNearbyViewer(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        double maxDistance = plugin.getConfig().getDouble("storage.display.max-view-distance", 64.0D);
        double maxDistanceSquared = maxDistance * maxDistance;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead() || player.getWorld() != location.getWorld()) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private void prepareDisplayEntity(Entity entity, UUID shulkerId, int slotIndex) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGravity(false);
        entity.getPersistentDataContainer().set(shulkerKey, PersistentDataType.STRING, shulkerId.toString());
        if (slotIndex >= 0) {
            entity.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, slotIndex);
        }
    }

    private Location centered(Location base) {
        return base.clone().add(0.5, 0.0, 0.5);
    }

    private Location offsetLocation(Location base, double localX, double localY, double localZ, float yaw) {
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double worldX = (localX * cos) - (localZ * sin);
        double worldZ = (localX * sin) + (localZ * cos);
        return centered(base).add(worldX, localY, worldZ);
    }

    private UUID resolveStorageOwner(ShulkerSettings settings, ShulkerBox shulker) {
        String ownerUuid = settings.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            try {
                return UUID.fromString(ownerUuid);
            } catch (IllegalArgumentException ignored) {
                // Fallback below.
            }
        }

        if (shulker != null) {
            String blockOwnerUuid = shulker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (blockOwnerUuid != null && !blockOwnerUuid.isBlank()) {
                try {
                    return UUID.fromString(blockOwnerUuid);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed owner UUID on block.
                }
            }
        }
        return null;
    }

    private float resolveDisplayYaw(ShulkerBox shulker) {
        Float storedYaw = shulker.getPersistentDataContainer().get(displayYawKey, PersistentDataType.FLOAT);
        if (storedYaw != null) {
            return storedYaw;
        }
        return 0f;
    }

    private boolean canAccessStorage(Player player, UUID shulkerId, UUID owner) {
        if (owner.equals(player.getUniqueId())) {
            return true;
        }

        PlayerLager ownerLager = plugin.getLagerManager().getLager(owner);
        if (ownerLager.getTrustedPlayers().contains(player.getUniqueId())) {
            return true;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        return settings.getTrustedPlayers().contains(player.getUniqueId());
    }

    private void removeDisplay(UUID shulkerId) {
        DisplayCluster cluster = activeDisplays.remove(shulkerId);
        if (cluster == null) {
            return;
        }
        cluster.remove();
    }

    private void clearAll() {
        for (DisplayCluster cluster : activeDisplays.values()) {
            cluster.remove();
        }
        activeDisplays.clear();
        trackedLocations.clear();
    }

    private record DisplayCluster(Location blockLocation, TextDisplay title, List<ItemDisplay> backgroundDisplays,
                                  List<ItemDisplay> itemDisplays,
                                  List<TextDisplay> amountDisplays, List<Interaction> interactions) {
        private boolean isValid() {
            if (title == null || !title.isValid()) {
                return false;
            }
            for (ItemDisplay backgroundDisplay : backgroundDisplays) {
                if (backgroundDisplay == null || !backgroundDisplay.isValid()) {
                    return false;
                }
            }
            for (ItemDisplay itemDisplay : itemDisplays) {
                if (itemDisplay == null || !itemDisplay.isValid()) {
                    return false;
                }
            }
            for (TextDisplay amountDisplay : amountDisplays) {
                if (amountDisplay == null || !amountDisplay.isValid()) {
                    return false;
                }
            }
            for (Interaction interaction : interactions) {
                if (interaction == null || !interaction.isValid()) {
                    return false;
                }
            }
            return true;
        }

        private void remove() {
            if (title != null && title.isValid()) {
                title.remove();
            }
            for (ItemDisplay backgroundDisplay : backgroundDisplays) {
                if (backgroundDisplay != null && backgroundDisplay.isValid()) {
                    backgroundDisplay.remove();
                }
            }
            for (ItemDisplay itemDisplay : itemDisplays) {
                if (itemDisplay != null && itemDisplay.isValid()) {
                    itemDisplay.remove();
                }
            }
            for (TextDisplay amountDisplay : amountDisplays) {
                if (amountDisplay != null && amountDisplay.isValid()) {
                    amountDisplay.remove();
                }
            }
            for (Interaction interaction : interactions) {
                if (interaction != null && interaction.isValid()) {
                    interaction.remove();
                }
            }
        }
    }

    private record SlotVisual(ItemStack itemStack, String amountText) {
    }

    public record HoveredDisplayInfo(UUID shulkerId, int displaySlot, Component title, List<Component> lore) {
        public List<String> plainLoreLines(int limit) {
            List<String> lines = new ArrayList<>();
            PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
            for (Component line : lore) {
                if (line == null) {
                    continue;
                }
                String plain = serializer.serialize(line).trim();
                if (plain.isEmpty()) {
                    continue;
                }
                lines.add(plain);
                if (lines.size() >= limit) {
                    break;
                }
            }
            return lines;
        }

        public String plainTitle() {
            return PlainTextComponentSerializer.plainText().serialize(title);
        }
    }
}
