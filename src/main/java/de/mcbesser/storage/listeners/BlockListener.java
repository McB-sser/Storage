package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.gui.QuickSlotsView;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlockListener implements Listener {
    private final Storage plugin;
    private final NamespacedKey ownerKey;

    public BlockListener(Storage plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "owner");
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> processShulkerAutomation(shulker, ownerForAutomation, shulkerUuid),
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
            new QuickSlotsView(plugin, shulkerUuid).open(player);
        } else {
            player.sendMessage(Component.text("Du hast keine Berechtigung fÃƒÂ¼r diesen Lager-Shulker!",
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

    private void scheduleAutomation(ShulkerBox shulker, long delay) {
        String id = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
        String ownerUuid = shulker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (id == null || ownerUuid == null) {
            return;
        }

        UUID ownerFromUuid = UUID.fromString(ownerUuid);
        UUID shulkerId = UUID.fromString(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> processShulkerAutomation(shulker, ownerFromUuid, shulkerId), delay);
    }

    private void processShulkerAutomation(ShulkerBox shulker, UUID owner, UUID shulkerId) {
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
        int refillLimit = Math.max(0, inv.getSize() - reserveSlots); // keep last 5 slots free

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (refillMat != null && item.getType() == refillMat && i < refillLimit) {
                continue;
            }

            int added = plugin.getLagerManager().addItemToLager(owner, shulkerId, item);
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

        if (changed) {
            plugin.getLagerManager().saveLager(owner);
        }
    }
}


