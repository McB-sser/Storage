package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.gui.QuickSlotsView;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlockListener implements Listener {
    private final Storage plugin;
    private final NamespacedKey ownerKey;
    private final Map<UUID, TrackedShulker> trackedShulkers = new HashMap<>();
    private final BukkitTask automationTask;

    public BlockListener(Storage plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.automationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrackedShulkers, 10L, 10L);
        Bukkit.getScheduler().runTask(plugin, (Runnable) this::scanLoadedChunks);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> scanLoadedChunks(event.getWorld().getLoadedChunks()));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        scanChunk(event.getChunk());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.getType().name().contains("SHULKER_BOX")) {
            return;
        }
        if (!item.hasItemMeta()) {
            return;
        }

        String id = item.getItemMeta().getPersistentDataContainer().get(RecipeManager.SHULKER_KEY,
                PersistentDataType.STRING);
        if (id == null) {
            return;
        }

        if (!event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlock();
        if (!(block.getState() instanceof ShulkerBox shulker)) {
            return;
        }

        UUID shulkerUuid = UUID.fromString(id);
        ShulkerSettings placedSettings = plugin.getLagerManager().getShulkerSettings(shulkerUuid);
        String effectiveOwnerUuid = placedSettings.getOwnerUuid();
        String effectiveOwnerName = placedSettings.getOwnerName();
        if (effectiveOwnerUuid == null || effectiveOwnerUuid.isBlank()) {
            effectiveOwnerUuid = event.getPlayer().getUniqueId().toString();
            placedSettings.setOwnerUuid(effectiveOwnerUuid);
        }
        if (effectiveOwnerName == null || effectiveOwnerName.isBlank()) {
            if (effectiveOwnerUuid.equals(event.getPlayer().getUniqueId().toString())) {
                effectiveOwnerName = event.getPlayer().getName();
            } else {
                try {
                    effectiveOwnerName = Bukkit.getOfflinePlayer(UUID.fromString(effectiveOwnerUuid)).getName();
                } catch (IllegalArgumentException ignored) {
                    effectiveOwnerName = event.getPlayer().getName();
                }
            }
            placedSettings.setOwnerName(effectiveOwnerName);
        }
        plugin.getLagerManager().saveShulkerSettings(shulkerUuid);

        shulker.getPersistentDataContainer().set(RecipeManager.SHULKER_KEY, PersistentDataType.STRING, id);
        shulker.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING,
                effectiveOwnerUuid);
        shulker.customName(Component.text("Lager von: " + effectiveOwnerName, NamedTextColor.GOLD));
        shulker.update();

        UUID ownerFromUuid;
        try {
            ownerFromUuid = UUID.fromString(effectiveOwnerUuid);
        } catch (IllegalArgumentException ignored) {
            ownerFromUuid = event.getPlayer().getUniqueId();
        }
        final UUID ownerForAutomation = ownerFromUuid;
        Location placedLocation = shulker.getLocation();
        trackedShulkers.put(shulkerUuid, new TrackedShulker(placedLocation, ownerForAutomation));
        Bukkit.getScheduler().runTaskLater(plugin, () -> processShulkerAutomation(placedLocation, ownerForAutomation, shulkerUuid),
                1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType().name().contains("SHULKER_BOX") && block.getState() instanceof ShulkerBox shulker) {
            String id = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
            if (id != null) {
                event.setDropItems(false);

                String ownerUuid = shulker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                if (ownerUuid != null) {
                    UUID ownerFromUuid = UUID.fromString(ownerUuid);
                    UUID shulkerUuid = UUID.fromString(id);
                    Inventory shulkerInv = shulker.getInventory();
                    int transferred = 0;

                    for (ItemStack stack : shulkerInv.getContents()) {
                        if (stack == null || stack.getType() == Material.AIR) {
                            continue;
                        }
                        int added = plugin.getLagerManager().addItemToLager(ownerFromUuid, shulkerUuid, stack);
                        transferred += Math.max(0, added);

                        int remaining = stack.getAmount() - added;
                        if (remaining > 0) {
                            ItemStack rest = stack.clone();
                            rest.setAmount(remaining);
                            block.getWorld().dropItemNaturally(block.getLocation(), rest);
                        }
                    }

                    if (transferred > 0) {
                        player.sendMessage(Component.text(transferred + " Items wurden ins Lager transferiert.",
                                NamedTextColor.GREEN));
                    }
                    shulkerInv.clear();
                }

                ItemStack drop = new ItemStack(block.getType());
                org.bukkit.inventory.meta.ItemMeta meta = drop.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(RecipeManager.SHULKER_KEY, PersistentDataType.STRING, id);
                    String ownerName = null;
                    if (ownerUuid != null) {
                        try {
                            UUID ownerFromUuid = UUID.fromString(ownerUuid);
                            ownerName = Bukkit.getOfflinePlayer(ownerFromUuid).getName();
                        } catch (IllegalArgumentException ignored) {
                            // fallback below
                        }
                    }
                    if (ownerName == null || ownerName.isBlank()) {
                        ownerName = player.getName();
                    }
                    meta.displayName(Component.text("Lager von: " + ownerName, NamedTextColor.GOLD));
                    drop.setItemMeta(meta);
                }
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
                trackedShulkers.remove(UUID.fromString(id));
                return;
            }
        }

        UUID ownShulkerId = null;
        UUID ownStorageOwner = null;
        UUID otherShulkerId = null;
        UUID otherStorageOwner = null;

        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem == null || !invItem.getType().name().contains("SHULKER_BOX") || !invItem.hasItemMeta()) {
                continue;
            }
            String id = invItem.getItemMeta().getPersistentDataContainer().get(RecipeManager.SHULKER_KEY,
                    PersistentDataType.STRING);
            if (id == null) {
                continue;
            }

            UUID shulkerUuid;
            try {
                shulkerUuid = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerUuid);
            if (!settings.isAutoStore()) {
                continue;
            }

            UUID storageOwner = player.getUniqueId();
            String ownerUuidStr = settings.getOwnerUuid();
            if (ownerUuidStr != null && !ownerUuidStr.isBlank()) {
                try {
                    storageOwner = UUID.fromString(ownerUuidStr);
                } catch (IllegalArgumentException ignored) {
                    storageOwner = player.getUniqueId();
                }
            }

            if (storageOwner.equals(player.getUniqueId())) {
                if (ownShulkerId == null) {
                    ownShulkerId = shulkerUuid;
                    ownStorageOwner = storageOwner;
                }
            } else if (otherShulkerId == null) {
                otherShulkerId = shulkerUuid;
                otherStorageOwner = storageOwner;
            }
        }

        UUID selectedShulkerId = ownShulkerId != null ? ownShulkerId : otherShulkerId;
        UUID selectedStorageOwner = ownShulkerId != null ? ownStorageOwner : otherStorageOwner;

        if (selectedShulkerId != null && selectedStorageOwner != null) {
            boolean stored = false;
            int transferred = 0;
            List<ItemStack> leftovers = new ArrayList<>();
            List<ItemStack> drops = new ArrayList<>(
                    event.getBlock().getDrops(player.getInventory().getItemInMainHand(), player));
            if (drops.isEmpty()) {
                drops.addAll(event.getBlock().getDrops());
            }
            boolean containerHadItems = false;
            // Shulker drops already keep their inventory in block-item NBT.
            // Adding inventory contents here would duplicate items.
            if (block.getState() instanceof Container container && !(container instanceof ShulkerBox)) {
                for (ItemStack content : container.getInventory().getContents()) {
                    if (content == null || content.getType() == Material.AIR) {
                        continue;
                    }
                    containerHadItems = true;
                    drops.add(content.clone());
                }
            }

            for (ItemStack drop : drops) {
                if (drop == null || drop.getType() == Material.AIR) {
                    continue;
                }
                int added = plugin.getLagerManager().addItemToLager(selectedStorageOwner, selectedShulkerId, drop);
                if (added > 0) {
                    stored = true;
                    transferred += added;
                }
                int remaining = drop.getAmount() - added;
                if (remaining > 0) {
                    ItemStack rest = drop.clone();
                    rest.setAmount(remaining);
                    leftovers.add(rest);
                }
            }

            if (stored) {
                event.setDropItems(false);
                for (ItemStack rest : leftovers) {
                    block.getWorld().dropItemNaturally(block.getLocation(), rest);
                }
                if (containerHadItems) {
                    player.sendMessage(Component.text(transferred + " Items wurden ins Lager transferiert.",
                            NamedTextColor.GREEN));
                }
                player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5),
                        10, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !block.getType().name().contains("SHULKER_BOX")) {
            return;
        }
        if (!(block.getState() instanceof ShulkerBox shulker)) {
            return;
        }

        String id = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
        if (id == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        String ownerUuid = shulker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        UUID shulkerUuid = UUID.fromString(id);

        boolean allowed = false;
        UUID owner = null;
        if (ownerUuid != null) {
            try {
                owner = UUID.fromString(ownerUuid);
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
        if (ownerUuid != null && ownerUuid.equals(player.getUniqueId().toString())) {
            allowed = true;
        } else {
            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerUuid);
            if (owner != null) {
                PlayerLager ownerLager = plugin.getLagerManager().getLager(owner);
                if (ownerLager.getTrustedPlayers().contains(player.getUniqueId())) {
                    allowed = true;
                }
            }
            if (!allowed && settings.getTrustedPlayers().contains(player.getUniqueId())) {
                allowed = true;
                if (owner != null) {
                    PlayerLager ownerLager = plugin.getLagerManager().getLager(owner);
                    if (!ownerLager.getTrustedPlayers().contains(player.getUniqueId())) {
                        ownerLager.getTrustedPlayers().add(player.getUniqueId());
                        plugin.getLagerManager().saveLager(owner);
                    }
                }
            }
        }

        if (allowed) {
            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerUuid);
            if (settings.getOwnerUuid() == null || settings.getOwnerUuid().isBlank()) {
                if (ownerUuid != null) {
                    settings.setOwnerUuid(ownerUuid);
                }
            }
            if (settings.getOwnerName() == null || settings.getOwnerName().isBlank()) {
                if (ownerUuid != null) {
                    UUID ownerFromUuid = UUID.fromString(ownerUuid);
                    String ownerName = Bukkit.getOfflinePlayer(ownerFromUuid).getName();
                    settings.setOwnerName(ownerName != null ? ownerName : player.getName());
                }
                plugin.getLagerManager().saveShulkerSettings(shulkerUuid);
            }
            UUID automationOwner = owner;
            if (automationOwner == null) {
                String settingsOwner = settings.getOwnerUuid();
                if (settingsOwner != null && !settingsOwner.isBlank()) {
                    try {
                        automationOwner = UUID.fromString(settingsOwner);
                    } catch (IllegalArgumentException ignored) {
                        automationOwner = player.getUniqueId();
                    }
                } else {
                    automationOwner = player.getUniqueId();
                }
            }
            processShulkerAutomation(block.getLocation(), automationOwner, shulkerUuid);
            new QuickSlotsView(plugin, shulkerUuid).open(player);
        } else {
            player.sendMessage(Component.text("Du hast keine Berechtigung f\u00fcr diesen Lager-Shulker!",
                    NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.SHULKER_BOX) {
            return;
        }
        if (event.getInventory().getHolder() instanceof ShulkerBox shulker) {
            scheduleAutomation(shulker, 1L);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.SHULKER_BOX) {
            return;
        }
        if (event.getInventory().getHolder() instanceof ShulkerBox shulker) {
            scheduleAutomation(shulker, 1L);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getType() != InventoryType.SHULKER_BOX) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ShulkerBox shulker)) {
            return;
        }

        int topSize = event.getInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                scheduleAutomation(shulker, 1L);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.SHULKER_BOX) {
            return;
        }
        if (event.getInventory().getHolder() instanceof ShulkerBox shulker) {
            scheduleAutomation(shulker, 1L);
        }
    }

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder() instanceof ShulkerBox shulker) {
            scheduleAutomation(shulker, 1L);
        }
        if (event.getSource().getHolder() instanceof ShulkerBox shulker) {
            scheduleAutomation(shulker, 2L);
        }
    }

    @EventHandler
    public void onBlockDispense(BlockDispenseEvent event) {
        if (!(event.getBlock().getBlockData() instanceof Directional directional)) {
            return;
        }

        Block targetBlock = event.getBlock().getRelative(directional.getFacing());
        if (targetBlock.getState() instanceof ShulkerBox shulker) {
            scheduleAutomation(shulker, 2L);
        }
    }

    private void scheduleAutomation(ShulkerBox shulker, long delay) {
        String id = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
        String ownerUuid = shulker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (id == null || ownerUuid == null) {
            return;
        }

        UUID ownerFromUuid = UUID.fromString(ownerUuid);
        UUID shulkerId = UUID.fromString(id);
        Location location = shulker.getLocation();
        trackedShulkers.put(shulkerId, new TrackedShulker(location, ownerFromUuid));
        Bukkit.getScheduler().runTaskLater(plugin, () -> processShulkerAutomation(location, ownerFromUuid, shulkerId), delay);
    }

    private void scanLoadedChunks() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            scanLoadedChunks(world.getLoadedChunks());
        }
    }

    private void scanLoadedChunks(Chunk[] chunks) {
        for (Chunk chunk : chunks) {
            scanChunk(chunk);
        }
    }

    private void scanChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof ShulkerBox shulker)) {
                continue;
            }

            String id = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
            String ownerUuid = shulker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (id == null || ownerUuid == null || ownerUuid.isBlank()) {
                continue;
            }

            UUID shulkerId;
            UUID owner;
            try {
                shulkerId = UUID.fromString(id);
                owner = UUID.fromString(ownerUuid);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            trackedShulkers.put(shulkerId, new TrackedShulker(shulker.getLocation(), owner));
        }
    }

    private void tickTrackedShulkers() {
        if (trackedShulkers.isEmpty()) {
            return;
        }

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, TrackedShulker> entry : trackedShulkers.entrySet()) {
            UUID shulkerId = entry.getKey();
            TrackedShulker tracked = entry.getValue();

            if (tracked.location() == null || tracked.location().getWorld() == null) {
                toRemove.add(shulkerId);
                continue;
            }

            Block block = tracked.location().getBlock();
            if (!(block.getState() instanceof ShulkerBox shulker)) {
                toRemove.add(shulkerId);
                continue;
            }

            String currentId = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
            if (currentId == null || !shulkerId.toString().equals(currentId)) {
                toRemove.add(shulkerId);
                continue;
            }

            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
            if (!settings.isShulkerRefillEnabled()) {
                continue;
            }

            processShulkerAutomation(tracked.location(), tracked.owner(), shulkerId);
        }

        for (UUID shulkerId : toRemove) {
            trackedShulkers.remove(shulkerId);
        }
    }

    private void processShulkerAutomation(Location location, UUID owner, UUID shulkerId) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Block block = location.getBlock();
        if (!(block.getState() instanceof ShulkerBox shulker)) {
            return;
        }

        String currentId = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
        if (currentId == null || !shulkerId.toString().equals(currentId)) {
            return;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        if (!settings.isShulkerRefillEnabled()) {
            return;
        }

        PlayerLager lager = plugin.getLagerManager().getLager(owner);
        Material refillMat = settings.getFillItemMaterial() != null
                ? Material.matchMaterial(settings.getFillItemMaterial())
                : null;

        Inventory inv = shulker.getInventory();
        boolean changed = false;
        int reserveSlots = 5;
        int refillLimit = Math.max(0, inv.getSize() - reserveSlots);

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            boolean refillSlot = i < refillLimit;
            if (refillSlot && refillMat != null && item.getType() == refillMat) {
                continue;
            }

            int added = plugin.getLagerManager().addItemToLager(owner, shulkerId, item, false);
            if (added > 0) {
                int remaining = item.getAmount() - added;
                if (remaining <= 0) {
                    inv.setItem(i, null);
                } else {
                    item.setAmount(remaining);
                }
                changed = true;
            }
        }

        if (refillMat != null) {
            int perAction = settings.getWithdrawAmount() > 0 ? settings.getWithdrawAmount() : refillMat.getMaxStackSize();
            for (int i = 0; i < refillLimit; i++) {
                ItemStack slotItem = inv.getItem(i);
                if (slotItem != null && slotItem.getType() != Material.AIR && slotItem.getType() != refillMat) {
                    continue;
                }

                int current = (slotItem != null && slotItem.getType() == refillMat) ? slotItem.getAmount() : 0;
                if (current >= refillMat.getMaxStackSize()) {
                    continue;
                }

                int needed = refillMat.getMaxStackSize() - current;
                int available = Math.max(0, lager.getAmountByMaterial(refillMat) - Math.max(0, settings.getMinStock()));
                if (available <= 0) {
                    break;
                }
                int toInsert = Math.min(Math.min(needed, perAction), available);
                if (toInsert > 0) {
                    if (slotItem == null || slotItem.getType() == Material.AIR) {
                        inv.setItem(i, new ItemStack(refillMat, toInsert));
                    } else {
                        slotItem.setAmount(current + toInsert);
                    }
                    lager.removeByMaterial(refillMat, toInsert);
                    changed = true;
                }
            }
        }

        for (int i = refillLimit; i < inv.getSize(); i++) {
            ItemStack slotItem = inv.getItem(i);
            if (slotItem == null || slotItem.getType() == Material.AIR) {
                continue;
            }

            if (refillMat != null && slotItem.getType() == refillMat) {
                int remaining = moveRefillItemIntoManagedArea(inv, slotItem, refillMat, refillLimit);
                if (remaining != slotItem.getAmount()) {
                    changed = true;
                    if (remaining <= 0) {
                        inv.setItem(i, null);
                        continue;
                    }
                    slotItem.setAmount(remaining);
                }
            }

            int added = plugin.getLagerManager().addItemToLager(owner, shulkerId, slotItem, false);
            if (added > 0) {
                int remaining = slotItem.getAmount() - added;
                changed = true;
                if (remaining <= 0) {
                    inv.setItem(i, null);
                    continue;
                }
                slotItem.setAmount(remaining);
            }

            if (slotItem.getType() == Material.AIR || slotItem.getAmount() <= 0) {
                inv.setItem(i, null);
            }
        }

        if (changed) {
            for (HumanEntity viewer : inv.getViewers()) {
                if (viewer instanceof Player player) {
                    player.updateInventory();
                }
            }
            plugin.getLagerManager().saveLager(owner);
        }
    }

    private int moveRefillItemIntoManagedArea(Inventory inv, ItemStack source, Material refillMat, int refillLimit) {
        int remaining = source.getAmount();
        if (remaining <= 0 || refillLimit <= 0) {
            return remaining;
        }

        for (int i = 0; i < refillLimit && remaining > 0; i++) {
            ItemStack target = inv.getItem(i);
            if (target == null || target.getType() == Material.AIR || target.getType() != refillMat) {
                continue;
            }

            int free = refillMat.getMaxStackSize() - target.getAmount();
            if (free <= 0) {
                continue;
            }

            int moved = Math.min(free, remaining);
            target.setAmount(target.getAmount() + moved);
            remaining -= moved;
        }

        for (int i = 0; i < refillLimit && remaining > 0; i++) {
            ItemStack target = inv.getItem(i);
            if (target != null && target.getType() != Material.AIR) {
                continue;
            }

            int moved = Math.min(refillMat.getMaxStackSize(), remaining);
            inv.setItem(i, new ItemStack(refillMat, moved));
            remaining -= moved;
        }

        return remaining;
    }

    private record TrackedShulker(Location location, UUID owner) {
    }
}
