package de.mcbesser.storage.profile;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
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
import org.bukkit.profile.PlayerTextures;

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
            PlayerProfile profile = createProfile(uniqueId, coalesce(name, entry.name()));
            applySkin(profile, entry.skinUrl());
            meta.setPlayerProfile(profile);
        } catch (MalformedURLException | IllegalArgumentException exception) {
            plugin.getLogger().log(Level.WARNING, "Ungueltige Skin-URL im Kopf-Cache fuer " + uniqueId, exception);
        }
    }

    private void refreshAsync(UUID uniqueId, String name) {
        if (refreshInFlight.putIfAbsent(uniqueId, Boolean.TRUE) != null) {
            return;
        }

        PlayerProfile profile = createProfile(uniqueId, name);
        CompletionStage<? extends PlayerProfile> updateStage = profile.update();

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

    private PlayerProfile createProfile(UUID uniqueId, String name) {
        return name != null && !name.isBlank()
                ? Bukkit.createProfile(uniqueId, name)
                : Bukkit.createProfile(uniqueId);
    }

    private void applySkin(PlayerProfile profile, String skinUrl) throws MalformedURLException {
        PlayerTextures textures = profile.getTextures();
        textures.setSkin(URI.create(skinUrl).toURL());
        profile.setTextures(textures);
    }

    private URL extractSkinUrl(PlayerProfile profile) {
        return profile.getTextures().getSkin();
    }

    private String extractName(PlayerProfile profile) {
        return profile.getName();
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
