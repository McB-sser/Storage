package de.mcbesser.storage.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.mcbesser.storage.Storage;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import de.mcbesser.storage.models.StorageItem;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.filters.FluentFilter;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class LagerManager {
    private final Storage plugin;
    private final Gson gson;
    private final File dataFolder;
    private final File shulkerFolder;

    private final Map<UUID, PlayerLager> playerLagers = new HashMap<>();
    private final Map<UUID, ShulkerSettings> shulkerSettings = new HashMap<>();

    private boolean mysqlEnabled;
    private boolean nitriteEnabled;
    private Connection mysqlConnection;
    private Nitrite nitriteDb;
    private NitriteCollection nitritePlayers;
    private NitriteCollection nitriteShulkers;

    public LagerManager(Storage plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFolder = new File(plugin.getDataFolder(), "players");
        this.shulkerFolder = new File(plugin.getDataFolder(), "shulkers");

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        if (!shulkerFolder.exists()) {
            shulkerFolder.mkdirs();
        }

        initStorage();
    }

    private void initStorage() {
        FileConfiguration cfg = plugin.getConfig();
        boolean enabled = cfg.getBoolean("storage.mysql.enabled", false);
        if (enabled && connectMySql()) {
            mysqlEnabled = true;
            nitriteEnabled = false;
            plugin.getLogger().info("Storage: MySQL");
            return;
        }

        mysqlEnabled = false;
        if (enabled) {
            plugin.getLogger().warning("MySQL konnte nicht initialisiert werden, fallback auf Nitrite.");
        }

        if (connectNitrite()) {
            nitriteEnabled = true;
            plugin.getLogger().info("Storage: Nitrite");
            return;
        }

        nitriteEnabled = false;
        plugin.getLogger().warning("Nitrite konnte nicht initialisiert werden, fallback auf JSON files.");
    }

    private boolean connectMySql() {
        FileConfiguration cfg = plugin.getConfig();
        String host = cfg.getString("storage.mysql.host", "127.0.0.1");
        int port = cfg.getInt("storage.mysql.port", 3306);
        String database = cfg.getString("storage.mysql.database", "storage");
        String user = cfg.getString("storage.mysql.username", "root");
        String pass = cfg.getString("storage.mysql.password", "");
        boolean useSsl = cfg.getBoolean("storage.mysql.use_ssl", false);
        int connectTimeoutMs = cfg.getInt("storage.mysql.connect_timeout_ms", 5000);
        int socketTimeoutMs = cfg.getInt("storage.mysql.socket_timeout_ms", 15000);
        boolean tcpKeepAlive = cfg.getBoolean("storage.mysql.tcp_keep_alive", true);

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=utf8"
                + "&useUnicode=true"
                + "&connectTimeout=" + connectTimeoutMs
                + "&socketTimeout=" + socketTimeoutMs
                + "&tcpKeepAlive=" + tcpKeepAlive;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            mysqlConnection = DriverManager.getConnection(jdbcUrl, user, pass);
            createTablesIfMissing();
            return true;
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL Verbindungsfehler", e);
            return false;
        }
    }

    private boolean connectNitrite() {
        try {
            File nitriteFile = new File(plugin.getDataFolder(), "storage.db");
            MVStoreModule storeModule = MVStoreModule.withConfig()
                    .filePath(nitriteFile.getAbsolutePath())
                    .build();

            nitriteDb = Nitrite.builder()
                    .loadModule(storeModule)
                    .openOrCreate();
            nitritePlayers = nitriteDb.getCollection("lager_players");
            nitriteShulkers = nitriteDb.getCollection("lager_shulkers");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Nitrite Initialisierungsfehler", e);
            closeNitrite();
            return false;
        }
    }

    private void createTablesIfMissing() throws SQLException {
        if (!ensureMySqlConnection()) {
            throw new SQLException("Keine MySQL Verbindung");
        }

        try (Statement st = mysqlConnection.createStatement()) {
            // Legacy table (kept for migration/backward compatibility)
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS lager_players ("
                            + "player_uuid VARCHAR(36) PRIMARY KEY,"
                            + "json_data LONGTEXT NOT NULL,"
                            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                            + ")");

            // New normalized player meta table
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS lager_players_meta ("
                            + "player_uuid VARCHAR(36) PRIMARY KEY,"
                            + "unlocked_slots INT NOT NULL,"
                            + "capacity INT NOT NULL,"
                            + "vacuum_fuel_material VARCHAR(64) NULL,"
                            + "vacuum_charge INT NOT NULL,"
                            + "stored_exp INT NOT NULL,"
                            + "trusted_players_json LONGTEXT NULL,"
                            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                            + ")");
            st.executeUpdate("ALTER TABLE lager_players_meta ADD COLUMN IF NOT EXISTS trusted_players_json LONGTEXT NULL");

            // New normalized player items table (one row per item type)
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS lager_player_items ("
                            + "player_uuid VARCHAR(36) NOT NULL,"
                            + "slot_index INT NOT NULL,"
                            + "base64_data LONGTEXT NOT NULL,"
                            + "amount INT NOT NULL,"
                            + "PRIMARY KEY (player_uuid, slot_index),"
                            + "INDEX idx_lager_player_items_player (player_uuid)"
                            + ")");

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS lager_shulkers ("
                            + "shulker_uuid VARCHAR(36) PRIMARY KEY,"
                            + "json_data LONGTEXT NOT NULL,"
                            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                            + ")");
        }
    }

    private boolean ensureMySqlConnection() {
        try {
            // During initial setup mysqlEnabled can still be false, so only reconnect when enabled.
            if (mysqlConnection == null) {
                return mysqlEnabled && connectMySql();
            }

            if (mysqlConnection.isClosed() || !mysqlConnection.isValid(2)) {
                if (!mysqlEnabled) {
                    return false;
                }
                return connectMySql();
            }
            return true;
        } catch (SQLException e) {
            if (!mysqlEnabled) {
                plugin.getLogger().log(Level.SEVERE, "MySQL Verbindung nicht verfuegbar", e);
                return false;
            }
            plugin.getLogger().log(Level.WARNING, "MySQL Verbindung ungueltig, reconnect wird versucht", e);
            return connectMySql();
        }
    }

    private boolean ensureNitriteConnection() {
        if (nitriteDb == null) {
            return nitriteEnabled && connectNitrite();
        }

        try {
            if (nitriteDb.isClosed()) {
                if (!nitriteEnabled) {
                    return false;
                }
                return connectNitrite();
            }
            return nitritePlayers != null && nitriteShulkers != null;
        } catch (Exception e) {
            if (!nitriteEnabled) {
                plugin.getLogger().log(Level.SEVERE, "Nitrite nicht verfuegbar", e);
                return false;
            }
            plugin.getLogger().log(Level.WARNING, "Nitrite ungueltig, Reconnect wird versucht", e);
            closeNitrite();
            return connectNitrite();
        }
    }

    public PlayerLager getLager(UUID playerUuid) {
        if (playerLagers.containsKey(playerUuid)) {
            return playerLagers.get(playerUuid);
        }

        PlayerLager loaded = mysqlEnabled ? loadLagerMySql(playerUuid) : loadLagerNitrite(playerUuid);
        if (loaded == null) {
            loaded = new PlayerLager(playerUuid);
        }

        playerLagers.put(playerUuid, loaded);
        return loaded;
    }

    private PlayerLager loadLagerJson(UUID playerUuid) {
        File file = new File(dataFolder, playerUuid + ".json");
        if (!file.exists()) {
            return null;
        }

        try (Reader reader = new FileReader(file)) {
            return gson.fromJson(reader, PlayerLager.class);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load lager for " + playerUuid, e);
            return null;
        }
    }

    private PlayerLager loadLagerMySql(UUID playerUuid) {
        if (!ensureMySqlConnection()) {
            return null;
        }

        PlayerLager structured = loadLagerMySqlStructured(playerUuid);
        if (structured != null) {
            return structured;
        }

        String sql = "SELECT json_data FROM lager_players WHERE player_uuid = ?";
        try (PreparedStatement ps = mysqlConnection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String legacyJson = rs.getString("json_data");
                PlayerLager legacy = gson.fromJson(legacyJson, PlayerLager.class);
                if (legacy == null) {
                    legacy = new PlayerLager(playerUuid);
                }

                if (saveLagerMySqlStructured(playerUuid, legacy)) {
                    plugin.getLogger().info("MySQL Migration: Spieler-Lager migriert -> " + playerUuid);
                    PlayerLager migrated = loadLagerMySqlStructured(playerUuid);
                    return migrated != null ? migrated : legacy;
                }

                plugin.getLogger().warning("MySQL Migration fehlgeschlagen, nutze Legacy-JSON fÃƒÂ¼r " + playerUuid);
                return legacy;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load lager from mysql for " + playerUuid, e);
            return null;
        }
    }

    private PlayerLager loadLagerNitrite(UUID playerUuid) {
        if (!ensureNitriteConnection()) {
            return loadLagerJson(playerUuid);
        }

        try {
            Document document = nitritePlayers.find(FluentFilter.where("player_uuid").eq(playerUuid.toString()))
                    .firstOrNull();
            if (document != null) {
                String json = document.get("json_data", String.class);
                if (json != null && !json.isBlank()) {
                    return gson.fromJson(json, PlayerLager.class);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load lager from nitrite for " + playerUuid, e);
        }

        PlayerLager legacy = loadLagerJson(playerUuid);
        if (legacy != null) {
            saveLagerNitrite(playerUuid, legacy);
        }
        return legacy;
    }

    public void saveLager(UUID playerUuid) {
        PlayerLager lager = playerLagers.get(playerUuid);
        if (lager == null) {
            return;
        }

        if (mysqlEnabled) {
            saveLagerMySql(playerUuid, lager);
        } else if (nitriteEnabled) {
            saveLagerNitrite(playerUuid, lager);
        } else {
            saveLagerJson(playerUuid, lager);
        }
    }

    private void saveLagerJson(UUID playerUuid, PlayerLager lager) {
        File file = new File(dataFolder, playerUuid + ".json");
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(lager, writer);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save lager for " + playerUuid, e);
        }
    }

    private void saveLagerMySql(UUID playerUuid, PlayerLager lager) {
        if (!ensureMySqlConnection()) {
            return;
        }

        if (!saveLagerMySqlStructured(playerUuid, lager)) {
            plugin.getLogger().severe("Could not save lager to mysql (structured) for " + playerUuid);
        }
    }

    private void saveLagerNitrite(UUID playerUuid, PlayerLager lager) {
        if (!ensureNitriteConnection()) {
            saveLagerJson(playerUuid, lager);
            return;
        }

        try {
            nitritePlayers.remove(FluentFilter.where("player_uuid").eq(playerUuid.toString()));
            Document document = Document.createDocument("player_uuid", playerUuid.toString())
                    .put("json_data", gson.toJson(lager));
            nitritePlayers.insert(document);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save lager to nitrite for " + playerUuid, e);
            saveLagerJson(playerUuid, lager);
        }
    }

    private PlayerLager loadLagerMySqlStructured(UUID playerUuid) {
        String metaSql = "SELECT unlocked_slots, capacity, vacuum_fuel_material, vacuum_charge, stored_exp, trusted_players_json "
                + "FROM lager_players_meta WHERE player_uuid = ?";

        try (PreparedStatement metaPs = mysqlConnection.prepareStatement(metaSql)) {
            metaPs.setString(1, playerUuid.toString());
            try (ResultSet metaRs = metaPs.executeQuery()) {
                if (!metaRs.next()) {
                    return null;
                }

                PlayerLager lager = new PlayerLager(playerUuid);
                lager.setUnlockedSlots(metaRs.getInt("unlocked_slots"));
                lager.setCapacity(metaRs.getInt("capacity"));
                lager.setVacuumFuelMaterial(metaRs.getString("vacuum_fuel_material"));
                lager.setVacuumCharge(metaRs.getInt("vacuum_charge"));
                lager.setStoredExp(metaRs.getInt("stored_exp"));
                String trustedJson = metaRs.getString("trusted_players_json");
                if (trustedJson != null && !trustedJson.isBlank()) {
                    try {
                        Type listType = new TypeToken<List<UUID>>() {
                        }.getType();
                        List<UUID> trusted = gson.fromJson(trustedJson, listType);
                        lager.setTrustedPlayers(trusted != null ? trusted : new ArrayList<>());
                    } catch (Exception ignored) {
                        lager.setTrustedPlayers(new ArrayList<>());
                    }
                }

                List<StorageItem> items = new ArrayList<>();
                String itemSql = "SELECT base64_data, amount FROM lager_player_items WHERE player_uuid = ? ORDER BY slot_index ASC";
                try (PreparedStatement itemPs = mysqlConnection.prepareStatement(itemSql)) {
                    itemPs.setString(1, playerUuid.toString());
                    try (ResultSet itemRs = itemPs.executeQuery()) {
                        while (itemRs.next()) {
                            String base64 = itemRs.getString("base64_data");
                            int amount = itemRs.getInt("amount");
                            if (base64 != null && !base64.isEmpty() && amount > 0) {
                                items.add(new StorageItem(base64, amount));
                            }
                        }
                    }
                }
                lager.setItems(items);
                return lager;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load structured lager from mysql for " + playerUuid, e);
            return null;
        }
    }

    private boolean saveLagerMySqlStructured(UUID playerUuid, PlayerLager lager) {
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = mysqlConnection.getAutoCommit();
            mysqlConnection.setAutoCommit(false);

            String upsertMeta = "INSERT INTO lager_players_meta "
                    + "(player_uuid, unlocked_slots, capacity, vacuum_fuel_material, vacuum_charge, stored_exp, trusted_players_json) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "unlocked_slots = VALUES(unlocked_slots), "
                    + "capacity = VALUES(capacity), "
                    + "vacuum_fuel_material = VALUES(vacuum_fuel_material), "
                    + "vacuum_charge = VALUES(vacuum_charge), "
                    + "stored_exp = VALUES(stored_exp), "
                    + "trusted_players_json = VALUES(trusted_players_json)";

            try (PreparedStatement metaPs = mysqlConnection.prepareStatement(upsertMeta)) {
                metaPs.setString(1, playerUuid.toString());
                metaPs.setInt(2, lager.getUnlockedSlots());
                metaPs.setInt(3, lager.getCapacity());
                metaPs.setString(4, lager.getVacuumFuelMaterial());
                metaPs.setInt(5, lager.getVacuumCharge());
                metaPs.setInt(6, lager.getStoredExp());
                metaPs.setString(7, gson.toJson(lager.getTrustedPlayers()));
                metaPs.executeUpdate();
            }

            try (PreparedStatement deleteItems = mysqlConnection
                    .prepareStatement("DELETE FROM lager_player_items WHERE player_uuid = ?")) {
                deleteItems.setString(1, playerUuid.toString());
                deleteItems.executeUpdate();
            }

            String insertItem = "INSERT INTO lager_player_items (player_uuid, slot_index, base64_data, amount) "
                    + "VALUES (?, ?, ?, ?)";
            try (PreparedStatement itemPs = mysqlConnection.prepareStatement(insertItem)) {
                int idx = 0;
                for (StorageItem item : lager.getItems()) {
                    if (item == null || item.getBase64Data() == null || item.getBase64Data().isEmpty()
                            || item.getAmount() <= 0) {
                        continue;
                    }
                    itemPs.setString(1, playerUuid.toString());
                    itemPs.setInt(2, idx++);
                    itemPs.setString(3, item.getBase64Data());
                    itemPs.setInt(4, item.getAmount());
                    itemPs.addBatch();
                }
                itemPs.executeBatch();
            }

            mysqlConnection.commit();
            return true;
        } catch (SQLException e) {
            try {
                mysqlConnection.rollback();
            } catch (SQLException rollbackError) {
                plugin.getLogger().log(Level.SEVERE, "Rollback failed while saving structured lager for " + playerUuid,
                        rollbackError);
            }
            plugin.getLogger().log(Level.SEVERE, "Could not save structured lager to mysql for " + playerUuid, e);
            return false;
        } finally {
            try {
                mysqlConnection.setAutoCommit(previousAutoCommit);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not restore auto-commit state", e);
            }
        }
    }

    public int addItemToLager(UUID playerUuid, UUID shulkerId, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(RecipeManager.SHULKER_KEY, PersistentDataType.STRING)) {
            return 0; // Never store/suck Lager-Shulker items
        }

        PlayerLager lager = getLager(playerUuid);
        int added = lager.addItemWithLimits(item, lager.getUnlockedSlots(), lager.getCapacity());

        if (added > 0) {
            saveLager(playerUuid);
        }
        return added;
    }

    public ShulkerSettings getShulkerSettings(UUID shulkerId) {
        if (shulkerSettings.containsKey(shulkerId)) {
            return shulkerSettings.get(shulkerId);
        }

        ShulkerSettings loaded = mysqlEnabled
                ? loadShulkerSettingsMySql(shulkerId)
                : loadShulkerSettingsNitrite(shulkerId);
        if (loaded == null) {
            loaded = new ShulkerSettings(shulkerId);
        }

        shulkerSettings.put(shulkerId, loaded);
        return loaded;
    }

    private ShulkerSettings loadShulkerSettingsJson(UUID shulkerId) {
        File file = new File(shulkerFolder, shulkerId + ".json");
        if (!file.exists()) {
            return null;
        }

        try (Reader reader = new FileReader(file)) {
            return gson.fromJson(reader, ShulkerSettings.class);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load shulker settings " + shulkerId, e);
            return null;
        }
    }

    private ShulkerSettings loadShulkerSettingsMySql(UUID shulkerId) {
        if (!ensureMySqlConnection()) {
            return null;
        }

        String sql = "SELECT json_data FROM lager_shulkers WHERE shulker_uuid = ?";
        try (PreparedStatement ps = mysqlConnection.prepareStatement(sql)) {
            ps.setString(1, shulkerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String json = rs.getString("json_data");
                return gson.fromJson(json, ShulkerSettings.class);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load shulker settings from mysql " + shulkerId, e);
            return null;
        }
    }

    private ShulkerSettings loadShulkerSettingsNitrite(UUID shulkerId) {
        if (!ensureNitriteConnection()) {
            return loadShulkerSettingsJson(shulkerId);
        }

        try {
            Document document = nitriteShulkers.find(FluentFilter.where("shulker_uuid").eq(shulkerId.toString()))
                    .firstOrNull();
            if (document != null) {
                String json = document.get("json_data", String.class);
                if (json != null && !json.isBlank()) {
                    return gson.fromJson(json, ShulkerSettings.class);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load shulker settings from nitrite " + shulkerId, e);
        }

        ShulkerSettings legacy = loadShulkerSettingsJson(shulkerId);
        if (legacy != null) {
            saveShulkerSettingsNitrite(shulkerId, legacy);
        }
        return legacy;
    }

    public void saveShulkerSettings(UUID shulkerId) {
        ShulkerSettings settings = shulkerSettings.get(shulkerId);
        if (settings == null) {
            return;
        }

        if (mysqlEnabled) {
            saveShulkerSettingsMySql(shulkerId, settings);
        } else if (nitriteEnabled) {
            saveShulkerSettingsNitrite(shulkerId, settings);
        } else {
            saveShulkerSettingsJson(shulkerId, settings);
        }
    }

    private void saveShulkerSettingsJson(UUID shulkerId, ShulkerSettings settings) {
        File file = new File(shulkerFolder, shulkerId + ".json");
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(settings, writer);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shulker settings " + shulkerId, e);
        }
    }

    private void saveShulkerSettingsMySql(UUID shulkerId, ShulkerSettings settings) {
        if (!ensureMySqlConnection()) {
            return;
        }

        String sql = "INSERT INTO lager_shulkers (shulker_uuid, json_data) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE json_data = VALUES(json_data)";
        try (PreparedStatement ps = mysqlConnection.prepareStatement(sql)) {
            ps.setString(1, shulkerId.toString());
            ps.setString(2, gson.toJson(settings));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shulker settings to mysql " + shulkerId, e);
        }
    }

    private void saveShulkerSettingsNitrite(UUID shulkerId, ShulkerSettings settings) {
        if (!ensureNitriteConnection()) {
            saveShulkerSettingsJson(shulkerId, settings);
            return;
        }

        try {
            nitriteShulkers.remove(FluentFilter.where("shulker_uuid").eq(shulkerId.toString()));
            Document document = Document.createDocument("shulker_uuid", shulkerId.toString())
                    .put("json_data", gson.toJson(settings));
            nitriteShulkers.insert(document);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shulker settings to nitrite " + shulkerId, e);
            saveShulkerSettingsJson(shulkerId, settings);
        }
    }

    public void saveAllData() {
        for (UUID playerUuid : playerLagers.keySet()) {
            saveLager(playerUuid);
        }
        for (UUID shulkerId : shulkerSettings.keySet()) {
            saveShulkerSettings(shulkerId);
        }
    }

    public void shutdown() {
        if (mysqlConnection != null) {
            try {
                mysqlConnection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not close mysql connection", e);
            }
        }
        closeNitrite();
    }

    private void closeNitrite() {
        if (nitriteDb != null) {
            try {
                nitriteDb.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not close nitrite database", e);
            } finally {
                nitriteDb = null;
                nitritePlayers = null;
                nitriteShulkers = null;
            }
        }
    }
}


