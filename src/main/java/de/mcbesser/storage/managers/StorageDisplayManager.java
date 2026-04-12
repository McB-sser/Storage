package de.mcbesser.storage.managers;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.models.ItemCategory;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

    private final Storage plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey displayYawKey;
    private final NamespacedKey shulkerKey;
    private final NamespacedKey slotKey;
    private final Map<UUID, Location> trackedLocations = new HashMap<>();
    private final Map<UUID, DisplayCluster> activeDisplays = new HashMap<>();
    private BukkitTask refreshTask;

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
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 10L);
        Bukkit.getScheduler().runTask(plugin, this::scanLoadedChunks);
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
        PlayerLager lager = plugin.getLagerManager().getLager(storageOwner);
        float displayYaw = resolveDisplayYaw(shulker);
        DisplayCluster cluster = activeDisplays.computeIfAbsent(shulkerId,
                ignored -> spawnCluster(location, shulkerId, displayYaw));
        updateCluster(cluster, settings, lager, displayYaw);
    }

    public boolean handleDisplayUse(Player player, UUID shulkerId, int slotIndex, boolean stackRequest) {
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        UUID storageOwner = resolveStorageOwner(settings, null);
        if (storageOwner == null) {
            return true;
        }
        if (!canAccessStorage(player, shulkerId, storageOwner)) {
            return false;
        }

        String materialName = settings.getQuickSlots().get(9 + slotIndex);
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

        int requested = stackRequest ? material.getMaxStackSize() : 1;
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

    private void refreshAll() {
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
        List<Interaction> interactions = new ArrayList<>(QUICK_SLOT_COUNT);

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

            if (row >= 1 && row <= 4) {
                final int quickSlotIndex = (row - 1) * QUICK_SLOT_COLUMNS + column;
                Location interactionLocation = offsetLocation(location, xOffset, yOffset - 0.255, 0.0, displayYaw);
                Interaction interaction = interactionLocation.getWorld().spawn(interactionLocation, Interaction.class, hitbox -> {
                    prepareDisplayEntity(hitbox, shulkerId, quickSlotIndex);
                    hitbox.setInteractionWidth(0.50f);
                    hitbox.setInteractionHeight(0.50f);
                    hitbox.setResponsive(true);
                    hitbox.setRotation(displayYaw, 0f);
                });
                interactions.add(interaction);
            }
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
            return new SlotVisual(named(icon), "");
        }

        if (row >= 1 && row <= 4) {
            int quickSlot = 9 + ((row - 1) * QUICK_SLOT_COLUMNS) + column;
            String materialName = settings.getQuickSlots().get(quickSlot);
            Material material = materialName != null ? Material.matchMaterial(materialName) : null;
            if (material == null) {
                Material placeholder = quickSlot < 18 ? Material.GREEN_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
                return new SlotVisual(named(placeholder), "");
            }
            int amount = lager.getAmountByMaterial(material);
            return new SlotVisual(named(material), amount > 0 ? String.valueOf(amount) : "-");
        }

        Material material = switch (displaySlot) {
            case 45 -> Material.HOPPER_MINECART;
            case 46 -> Material.BLAZE_POWDER;
            case 47 -> Material.GOLDEN_PICKAXE;
            case 48 -> Material.SHULKER_BOX;
            case 49 -> Material.HOPPER;
            case 50 -> settings.isDisplayEnabled() ? Material.LIME_DYE : Material.GRAY_DYE;
            case 51 -> Material.PINK_DYE;
            case 52 -> Material.EXPERIENCE_BOTTLE;
            case 53 -> Material.SPYGLASS;
            default -> Material.BLACK_STAINED_GLASS_PANE;
        };
        return new SlotVisual(named(material), "");
    }

    private ItemStack named(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.translatable(material.translationKey()));
            stack.setItemMeta(meta);
        }
        return stack;
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
        double radians = Math.toRadians(-yaw);
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
        private void remove() {
            title.remove();
            for (ItemDisplay backgroundDisplay : backgroundDisplays) {
                backgroundDisplay.remove();
            }
            for (ItemDisplay itemDisplay : itemDisplays) {
                itemDisplay.remove();
            }
            for (TextDisplay amountDisplay : amountDisplays) {
                amountDisplay.remove();
            }
            for (Interaction interaction : interactions) {
                interaction.remove();
            }
        }
    }

    private record SlotVisual(ItemStack itemStack, String amountText) {
    }
}
