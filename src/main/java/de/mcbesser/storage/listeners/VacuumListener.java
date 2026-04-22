package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VacuumListener implements org.bukkit.event.Listener {
    private static final int XZ_SUM_LIMIT = 48;
    private static final int Y_SUM_LIMIT = 320;
    private final Storage plugin;
    private BukkitTask task;
    private int particleTick = 0;

    public VacuumListener(Storage plugin) {
        this.plugin = plugin;
        startTask();
    }

    private void startTask() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickVacuum, 14L, 10L);
    }

    private void tickVacuum() {
        particleTick++;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ActiveVacuum active = findCarriedVacuum(player);
            if (active != null) {
                processActiveVacuum(active);
            }
        }

        Set<String> processedChunks = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk loadedChunk : world.getLoadedChunks()) {
                processPlacedVacuumsInChunk(loadedChunk, processedChunks);
            }
        }
    }

    private ActiveVacuum findCarriedVacuum(Player player) {
        PlayerLager lager = plugin.getLagerManager().getLager(player.getUniqueId());
        if (lager.getVacuumCharge() <= 0) {
            return null;
        }

        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !stack.getType().name().contains("SHULKER_BOX") || !stack.hasItemMeta()) {
                continue;
            }

            String idStr = stack.getItemMeta().getPersistentDataContainer().get(RecipeManager.SHULKER_KEY,
                    PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }

            UUID shulkerId;
            try {
                shulkerId = UUID.fromString(idStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
            if (settings.isVacuumEnabled()) {
                Color rangeColor = resolveCarriedRangeColor(stack.getType(), settings);
                return new ActiveVacuum(player.getUniqueId(), shulkerId, player.getLocation().add(0, 1.0, 0), settings,
                        rangeColor, true);
            }
        }
        return null;
    }

    private void processPlacedVacuumsInChunk(Chunk chunk, Set<String> processedChunks) {
        String key = chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
        if (!processedChunks.add(key)) {
            return;
        }

        List<ActiveVacuum> placed = new ArrayList<>();
        NamespacedKey ownerKey = new NamespacedKey(plugin, "owner");

        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof ShulkerBox shulker)) {
                continue;
            }

            String idStr = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
            String ownerStr = shulker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (idStr == null || ownerStr == null) {
                continue;
            }

            UUID shulkerId;
            UUID owner;
            try {
                shulkerId = UUID.fromString(idStr);
                owner = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
            if (!settings.isVacuumEnabled()) {
                continue;
            }

            PlayerLager lager = plugin.getLagerManager().getLager(owner);
            if (lager.getVacuumCharge() <= 0) {
                continue;
            }

            Color rangeColor = resolvePlacedRangeColor(shulker, settings);
            placed.add(new ActiveVacuum(owner, shulkerId, shulker.getLocation().add(0.5, 0.5, 0.5), settings, rangeColor,
                    false));
        }

        for (ActiveVacuum vacuum : placed) {
            processActiveVacuum(vacuum);
        }
    }

    private void processActiveVacuum(ActiveVacuum active) {
        if (active.carried()) {
            double carriedRadius = 4.0;
            absorbNearbyItems(active.target(), carriedRadius, active);
            if (particleTick % 2 == 0) {
                spawnRangeRing(active.target(), carriedRadius, true, active.rangeColor());
            }
            return;
        }

        String mode = normalizeRangeMode(active.settings().getVacuumRangeMode());
        if ("BOX".equals(mode)) {
            absorbInRelativeBox(active);
        } else {
            int chunkRadius = "CHUNK_3X3".equals(mode) ? 1 : 0;
            absorbNearbyChunks(active.target().getChunk(), active, chunkRadius);
        }

        if (active.settings().isVacuumRangeParticlesEnabled() && particleTick % 2 == 0) {
            if ("BOX".equals(mode)) {
                spawnRelativeBoxOutline(active.target(), active.settings(), active.rangeColor());
            } else {
                int chunkRadius = "CHUNK_3X3".equals(mode) ? 1 : 0;
                spawnChunkAreaOutline(active.target().getChunk(), chunkRadius, active.target().getY(), active.rangeColor());
            }
        }
    }

    private void absorbNearbyItems(Location center, double radius, ActiveVacuum active) {
        if (center == null || center.getWorld() == null || radius <= 0) {
            return;
        }
        PlayerLager lager = plugin.getLagerManager().getLager(active.ownerUuid());
        double radiusSq = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Item itemEntity)) {
                continue;
            }
            if (itemEntity.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            absorbItemEntity(itemEntity, active, lager);
        }
    }

    private void spawnRangeRing(Location center, double radius, boolean carried, Color baseColor) {
        if (center == null || center.getWorld() == null || radius <= 0) {
            return;
        }
        int points = carried ? 22 : 32;
        float size = carried ? 0.65f : 0.55f;
        Color tone = carried ? brighten(baseColor, 45) : baseColor;
        Particle.DustOptions dust = new Particle.DustOptions(tone, size);
        double y = center.getY() + 0.05;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            center.getWorld().spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust, true);
        }
    }

    private void absorbNearbyChunks(Chunk center, ActiveVacuum active, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                if (!center.getWorld().isChunkLoaded(cx, cz)) {
                    continue;
                }
                absorbChunkDrops(center.getWorld().getChunkAt(cx, cz), active);
            }
        }
    }

    private void absorbInRelativeBox(ActiveVacuum active) {
        Location center = active.target();
        if (center == null || center.getWorld() == null) {
            return;
        }
        RangeBounds bounds = computeRelativeBounds(center, active.settings());

        PlayerLager lager = plugin.getLagerManager().getLager(active.ownerUuid());
        double epsilon = 0.25;
        for (Entity entity : center.getWorld().getNearbyEntities(center,
                bounds.queryHalfX(), bounds.queryHalfY(), bounds.queryHalfZ())) {
            if (!(entity instanceof Item itemEntity)) {
                continue;
            }
            Location itemLoc = itemEntity.getLocation();
            if (itemLoc.getX() < bounds.minX() - epsilon || itemLoc.getX() > bounds.maxX() + epsilon) {
                continue;
            }
            if (itemLoc.getY() < bounds.minY() - epsilon || itemLoc.getY() > bounds.maxY() + epsilon) {
                continue;
            }
            if (itemLoc.getZ() < bounds.minZ() - epsilon || itemLoc.getZ() > bounds.maxZ() + epsilon) {
                continue;
            }
            absorbItemEntity(itemEntity, active, lager);
        }
    }

    private void absorbChunkDrops(Chunk chunk, ActiveVacuum active) {
        PlayerLager lager = plugin.getLagerManager().getLager(active.ownerUuid());
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Item itemEntity)) {
                continue;
            }
            absorbItemEntity(itemEntity, active, lager);
        }
    }

    private void absorbItemEntity(Item itemEntity, ActiveVacuum active, PlayerLager lager) {
        ItemStack drop = itemEntity.getItemStack();
        if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) {
            return;
        }
        if (!active.settings().isVacuumItemAllowed(drop.getType())) {
            return;
        }

        int charge = lager.getVacuumCharge();
        if (charge <= 0) {
            return;
        }

        ItemStack toStore = drop.clone();
        toStore.setAmount(Math.min(drop.getAmount(), charge));
        int added = plugin.getLagerManager().addItemToLager(active.ownerUuid(), active.shulkerId(), toStore);
        if (added <= 0) {
            return;
        }

        lager.takeVacuumCharge(added);
        plugin.getLagerManager().saveLager(active.ownerUuid());

        int remaining = drop.getAmount() - added;
        if (remaining <= 0) {
            itemEntity.remove();
        } else {
            drop.setAmount(remaining);
            itemEntity.setItemStack(drop);
        }

        spawnBeam(itemEntity.getLocation(), active.target());
    }

    private void spawnChunkAreaOutline(Chunk centerChunk, int radiusChunks, double centerY, Color baseColor) {
        World world = centerChunk.getWorld();
        double minEdgeX = ((centerChunk.getX() - radiusChunks) << 4);
        double maxEdgeX = ((centerChunk.getX() + radiusChunks + 1) << 4);
        double minEdgeZ = ((centerChunk.getZ() - radiusChunks) << 4);
        double maxEdgeZ = ((centerChunk.getZ() + radiusChunks + 1) << 4);

        Color bright = brighten(baseColor, 65);
        Particle.DustOptions dustMain = new Particle.DustOptions(bright, 0.8f);
        Particle.DustOptions dustSoft = new Particle.DustOptions(brighten(bright, 35), 0.55f);
        int step = 2;
        double yMin = centerY - 0.8;
        double yMax = centerY + 2.6;
        double yStep = 0.8;

        for (double y = yMin; y <= yMax; y += yStep) {
            for (double x = minEdgeX; x <= maxEdgeX; x += step) {
                world.spawnParticle(Particle.DUST, x, y, minEdgeZ, 1, 0, 0, 0, 0, dustMain, true);
                world.spawnParticle(Particle.DUST, x, y, maxEdgeZ, 1, 0, 0, 0, 0, dustMain, true);
            }
            for (double z = minEdgeZ; z <= maxEdgeZ; z += step) {
                world.spawnParticle(Particle.DUST, minEdgeX, y, z, 1, 0, 0, 0, 0, dustMain, true);
                world.spawnParticle(Particle.DUST, maxEdgeX, y, z, 1, 0, 0, 0, 0, dustMain, true);
            }
        }

        int pillarStep = 4;
        for (double x = minEdgeX; x <= maxEdgeX; x += pillarStep) {
            for (double y = yMin; y <= yMax; y += 0.55) {
                world.spawnParticle(Particle.DUST, x, y, minEdgeZ, 1, 0, 0, 0, 0, dustSoft, true);
                world.spawnParticle(Particle.DUST, x, y, maxEdgeZ, 1, 0, 0, 0, 0, dustSoft, true);
            }
        }
        for (double z = minEdgeZ; z <= maxEdgeZ; z += pillarStep) {
            for (double y = yMin; y <= yMax; y += 0.55) {
                world.spawnParticle(Particle.DUST, minEdgeX, y, z, 1, 0, 0, 0, 0, dustSoft, true);
                world.spawnParticle(Particle.DUST, maxEdgeX, y, z, 1, 0, 0, 0, 0, dustSoft, true);
            }
        }

        spawnEndRodRectangle(world, minEdgeX, maxEdgeX, minEdgeZ, maxEdgeZ, yMin, yMax);
    }

    private void spawnRelativeBoxOutline(Location center, ShulkerSettings settings, Color baseColor) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        RangeBounds bounds = computeRelativeBounds(center, settings);
        double minX = bounds.minX();
        double maxX = bounds.maxX();
        double minY = bounds.minY();
        double maxY = bounds.maxY();
        double minZ = bounds.minZ();
        double maxZ = bounds.maxZ();

        Color bright = brighten(baseColor, 50);
        Particle.DustOptions dust = new Particle.DustOptions(bright, 0.75f);
        int step = 2;

        for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX); x += step) {
            world.spawnParticle(Particle.DUST, x + 0.5, minY, minZ, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, x + 0.5, minY, maxZ, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, x + 0.5, maxY, minZ, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, x + 0.5, maxY, maxZ, 1, 0, 0, 0, 0, dust, true);
        }
        for (int z = (int) Math.floor(minZ); z <= (int) Math.ceil(maxZ); z += step) {
            world.spawnParticle(Particle.DUST, minX, minY, z + 0.5, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, maxX, minY, z + 0.5, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, minX, maxY, z + 0.5, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, maxX, maxY, z + 0.5, 1, 0, 0, 0, 0, dust, true);
        }
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y += 2) {
            world.spawnParticle(Particle.DUST, minX, y + 0.5, minZ, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, maxX, y + 0.5, minZ, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, minX, y + 0.5, maxZ, 1, 0, 0, 0, 0, dust, true);
            world.spawnParticle(Particle.DUST, maxX, y + 0.5, maxZ, 1, 0, 0, 0, 0, dust, true);
        }

        spawnEndRodRectangle(world, minX, maxX, minZ, maxZ, minY, maxY);
    }

    private void spawnEndRodRectangle(World world, double minX, double maxX, double minZ, double maxZ, double minY, double maxY) {
        for (double x = minX; x <= maxX; x += 3.0) {
            world.spawnParticle(Particle.END_ROD, x, minY, minZ, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, x, minY, maxZ, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, x, maxY, minZ, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, x, maxY, maxZ, 1, 0, 0, 0, 0.0);
        }
        for (double z = minZ; z <= maxZ; z += 3.0) {
            world.spawnParticle(Particle.END_ROD, minX, minY, z, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, maxX, minY, z, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, minX, maxY, z, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, maxX, maxY, z, 1, 0, 0, 0, 0.0);
        }
        for (double y = minY; y <= maxY; y += 2.0) {
            world.spawnParticle(Particle.END_ROD, minX, y, minZ, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, minX, y, maxZ, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, maxX, y, minZ, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, maxX, y, maxZ, 1, 0, 0, 0, 0.0);
        }

        // Ensure all 8 corners are always present, regardless of step alignment.
        world.spawnParticle(Particle.END_ROD, minX, minY, minZ, 1, 0, 0, 0, 0.0);
        world.spawnParticle(Particle.END_ROD, minX, minY, maxZ, 1, 0, 0, 0, 0.0);
        world.spawnParticle(Particle.END_ROD, maxX, minY, minZ, 1, 0, 0, 0, 0.0);
        world.spawnParticle(Particle.END_ROD, maxX, minY, maxZ, 1, 0, 0, 0, 0.0);
        world.spawnParticle(Particle.END_ROD, minX, maxY, minZ, 1, 0, 0, 0, 0.0);
        world.spawnParticle(Particle.END_ROD, minX, maxY, maxZ, 1, 0, 0, 0, 0.0);
        world.spawnParticle(Particle.END_ROD, maxX, maxY, minZ, 1, 0, 0, 0, 0.0);
        world.spawnParticle(Particle.END_ROD, maxX, maxY, maxZ, 1, 0, 0, 0, 0.0);
    }

    private RangeBounds computeRelativeBounds(Location center, ShulkerSettings settings) {
        int negX = clamp(settings.getVacuumRangeNegX(), 0, XZ_SUM_LIMIT);
        int posX = clamp(settings.getVacuumRangePosX(), 0, XZ_SUM_LIMIT - negX);
        int negZ = clamp(settings.getVacuumRangeNegZ(), 0, XZ_SUM_LIMIT);
        int posZ = clamp(settings.getVacuumRangePosZ(), 0, XZ_SUM_LIMIT - negZ);
        int negY = clamp(settings.getVacuumRangeNegY(), 0, Y_SUM_LIMIT);
        int posY = clamp(settings.getVacuumRangePosY(), 0, Y_SUM_LIMIT - negY);

        // Use block-edge bounds so particles and absorption align visually and functionally.
        double minX = center.getX() - negX - 0.5;
        double maxX = center.getX() + posX + 0.5;
        double minY = center.getY() - negY - 0.5;
        double maxY = center.getY() + posY + 0.5;
        double minZ = center.getZ() - negZ - 0.5;
        double maxZ = center.getZ() + posZ + 0.5;

        double queryHalfX = Math.max(center.getX() - minX, maxX - center.getX()) + 0.25;
        double queryHalfY = Math.max(center.getY() - minY, maxY - center.getY()) + 0.25;
        double queryHalfZ = Math.max(center.getZ() - minZ, maxZ - center.getZ()) + 0.25;

        return new RangeBounds(minX, maxX, minY, maxY, minZ, maxZ, queryHalfX, queryHalfY, queryHalfZ);
    }

    private Color resolvePlacedRangeColor(ShulkerBox shulker, ShulkerSettings settings) {
        DyeColor dye = shulker.getColor();
        if (dye != null && dye.getColor() != null) {
            return dye.getColor();
        }
        String configured = settings.getColor();
        if (configured != null && !configured.isBlank()) {
            try {
                DyeColor mapped = DyeColor.valueOf(configured.toUpperCase());
                if (mapped.getColor() != null) {
                    return mapped.getColor();
                }
            } catch (IllegalArgumentException ignored) {
                // fallback below
            }
        }
        return Color.fromRGB(255, 170, 70);
    }

    private Color resolveCarriedRangeColor(Material shulkerType, ShulkerSettings settings) {
        if (shulkerType != null) {
            String matName = shulkerType.name();
            if (matName.endsWith("_SHULKER_BOX")) {
                String prefix = matName.substring(0, matName.length() - "_SHULKER_BOX".length());
                if (!prefix.isBlank()) {
                    try {
                        DyeColor mapped = DyeColor.valueOf(prefix);
                        if (mapped.getColor() != null) {
                            return mapped.getColor();
                        }
                    } catch (IllegalArgumentException ignored) {
                        // fallback below
                    }
                } else {
                    return DyeColor.PURPLE.getColor();
                }
            }
        }

        String configured = settings.getColor();
        if (configured != null && !configured.isBlank()) {
            try {
                DyeColor mapped = DyeColor.valueOf(configured.toUpperCase());
                if (mapped.getColor() != null) {
                    return mapped.getColor();
                }
            } catch (IllegalArgumentException ignored) {
                // fallback below
            }
        }
        return Color.fromRGB(120, 210, 255);
    }

    private Color brighten(Color color, int extra) {
        int r = Math.min(255, color.getRed() + extra);
        int g = Math.min(255, color.getGreen() + extra);
        int b = Math.min(255, color.getBlue() + extra);
        return Color.fromRGB(r, g, b);
    }

    private void spawnBeam(Location from, Location to) {
        Vector diff = to.toVector().subtract(from.toVector());
        double length = diff.length();
        if (length <= 0.01) {
            return;
        }

        Vector dir = diff.normalize();
        int steps = Math.max(6, (int) (length * 5));
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 70, 70), 1.2f);

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location point = from.clone().add(dir.clone().multiply(length * t));
            from.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust, true);
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ActiveVacuum(UUID ownerUuid, UUID shulkerId, Location target, ShulkerSettings settings,
                                Color rangeColor, boolean carried) {
    }

    private record RangeBounds(double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
                               double queryHalfX, double queryHalfY, double queryHalfZ) {
    }
}

