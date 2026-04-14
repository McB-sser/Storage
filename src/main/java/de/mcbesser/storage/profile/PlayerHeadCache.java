package de.mcbesser.storage.profile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerHeadCache {

    private static final long SUCCESS_CACHE_MILLIS = Duration.ofDays(1).toMillis();
    private static final long FAILURE_RETRY_MILLIS = Duration.ofMinutes(30).toMillis();

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> refreshInFlight = new ConcurrentHashMap<>();

    public PlayerHeadCache(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
        load();
    }

    public void applyCachedProfile(SkullMeta meta, OfflinePlayer player) {
        if (meta == null || player == null || player.getUniqueId() == null) {
            return;
        }

        UUID uniqueId = player.getUniqueId();
        long now = System.currentTimeMillis();
        CacheEntry entry = entries.get(uniqueId);
        if (entry != null) {
            applyEntry(meta, uniqueId, coalesce(player.getName(), entry.name()), entry);
            if (entry.nextRefreshAt() <= now) {
                refreshAsync(uniqueId, coalesce(player.getName(), entry.name()));
            }
            return;
        }

        refreshAsync(uniqueId, player.getName());
    }

    private void applyEntry(SkullMeta meta, UUID uniqueId, String name, CacheEntry entry) {
        if (entry.skinUrl() == null || entry.skinUrl().isBlank()) {
            return;
        }

        try {
            Object profile = createProfile(uniqueId, coalesce(name, entry.name()));
            if (profile == null || !applySkin(profile, entry.skinUrl())) {
                return;
            }
            applyProfile(meta, profile);
        } catch (MalformedURLException exception) {
            plugin.getLogger().log(Level.WARNING, "Ungueltige Skin-URL im Kopf-Cache fuer " + uniqueId, exception);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Konnte gecachtes Kopfprofil nicht anwenden fuer " + uniqueId, exception);
        }
    }

    private void refreshAsync(UUID uniqueId, String name) {
        if (refreshInFlight.putIfAbsent(uniqueId, Boolean.TRUE) != null) {
            return;
        }

        Object profile = createProfile(uniqueId, name);
        if (profile == null) {
            refreshInFlight.remove(uniqueId);
            rememberFailure(uniqueId, name);
            return;
        }

        CompletionStage<?> updateStage;
        try {
            Object updateResult = profile.getClass().getMethod("update").invoke(profile);
            if (!(updateResult instanceof CompletionStage<?> stage)) {
                refreshInFlight.remove(uniqueId);
                rememberFailure(uniqueId, name);
                return;
            }
            updateStage = stage;
        } catch (ReflectiveOperationException exception) {
            refreshInFlight.remove(uniqueId);
            rememberFailure(uniqueId, name);
            return;
        }

        updateStage.whenComplete((updatedProfile, throwable) -> {
            refreshInFlight.remove(uniqueId);

            if (throwable != null || updatedProfile == null) {
                rememberFailure(uniqueId, name);
                return;
            }

            URL skinUrl = extractSkinUrl(updatedProfile);
            String resolvedName = coalesce(extractName(updatedProfile), name);
            if (skinUrl == null) {
                rememberFailure(uniqueId, resolvedName);
                return;
            }

            entries.put(uniqueId, new CacheEntry(resolvedName, skinUrl.toExternalForm(), System.currentTimeMillis() + SUCCESS_CACHE_MILLIS));
            save();
        });
    }

    private Object createProfile(UUID uniqueId, String name) {
        try {
            if (name != null && !name.isBlank()) {
                return Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class).invoke(null, uniqueId, name);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return Bukkit.class.getMethod("createPlayerProfile", UUID.class).invoke(null, uniqueId);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            if (name != null && !name.isBlank()) {
                return Bukkit.class.getMethod("createProfile", UUID.class, String.class).invoke(null, uniqueId, name);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return Bukkit.class.getMethod("createProfile", UUID.class).invoke(null, uniqueId);
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private boolean applySkin(Object profile, String skinUrl) throws ReflectiveOperationException, MalformedURLException {
        Object textures = profile.getClass().getMethod("getTextures").invoke(profile);
        textures.getClass().getMethod("setSkin", URL.class).invoke(textures, new URL(skinUrl));
        return true;
    }

    private void applyProfile(SkullMeta meta, Object profile) throws ReflectiveOperationException {
        Method playerProfileMethod = findCompatibleMethod(meta.getClass(), "setPlayerProfile", profile.getClass());
        if (playerProfileMethod != null) {
            playerProfileMethod.setAccessible(true);
            playerProfileMethod.invoke(meta, profile);
            return;
        }

        Method ownerProfileMethod = findCompatibleMethod(meta.getClass(), "setOwnerProfile", profile.getClass());
        if (ownerProfileMethod != null) {
            ownerProfileMethod.setAccessible(true);
            ownerProfileMethod.invoke(meta, profile);
            return;
        }

        throw new NoSuchMethodException("Keine kompatible setPlayerProfile/setOwnerProfile-Methode auf " + meta.getClass().getName());
    }

    private Method findCompatibleMethod(Class<?> targetClass, String methodName, Class<?> argumentType) {
        for (Method method : targetClass.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType.isAssignableFrom(argumentType)) {
                return method;
            }
        }
        return null;
    }

    private URL extractSkinUrl(Object profile) {
        try {
            Object textures = profile.getClass().getMethod("getTextures").invoke(profile);
            Object skin = textures.getClass().getMethod("getSkin").invoke(textures);
            return skin instanceof URL url ? url : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String extractName(Object profile) {
        try {
            Object value = profile.getClass().getMethod("getName").invoke(profile);
            return value instanceof String string ? string : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private void rememberFailure(UUID uniqueId, String name) {
        CacheEntry previous = entries.get(uniqueId);
        String previousSkinUrl = previous == null ? null : previous.skinUrl();
        entries.put(uniqueId, new CacheEntry(coalesce(name, previous == null ? null : previous.name()), previousSkinUrl,
            System.currentTimeMillis() + FAILURE_RETRY_MILLIS));
        save();
    }

    private synchronized void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = configuration.getConfigurationSection("profiles");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            UUID uniqueId;
            try {
                uniqueId = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            String base = "profiles." + key + ".";
            entries.put(uniqueId, new CacheEntry(
                configuration.getString(base + "name"),
                configuration.getString(base + "skin-url"),
                configuration.getLong(base + "next-refresh-at", 0L)
            ));
        }
    }

    private synchronized void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, CacheEntry> entry : entries.entrySet()) {
            String base = "profiles." + entry.getKey() + ".";
            configuration.set(base + "name", entry.getValue().name());
            configuration.set(base + "skin-url", entry.getValue().skinUrl());
            configuration.set(base + "next-refresh-at", entry.getValue().nextRefreshAt());
        }

        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Konnte Kopf-Cache nicht speichern: " + file.getName(), exception);
        }
    }

    private String coalesce(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private record CacheEntry(String name, String skinUrl, long nextRefreshAt) {
    }
}
