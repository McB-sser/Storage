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
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.filters.FluentFilter;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class LagerManager {
    private static final long SAVE_DEBOUNCE_MILLIS = 150L;

    private final Storage plugin;
    private final Gson gson;
    private final File dataFolder;
    private final File shulkerFolder;

    private final Map<UUID, PlayerLager> playerLagers = new HashMap<>();
    private final Map<UUID, ShulkerSettings> shulkerSettings = new HashMap<>();
    private final Map<UUID, List<PersistedItem>> persistedPlayerItems = new HashMap<>();
    private final Map<UUID, PersistedMeta> persistedPlayerMeta = new HashMap<>();
    private final Map<UUID, ScheduledFuture<?>> pendingShulkerSaves = new ConcurrentHashMap<>();
    private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Storage-Database-Writer");
        thread.setDaemon(true);
        return thread;
    });

    private boolean mysqlEnabled;
    private boolean nitriteEnabled;
    private Connection mysqlConnection;
    private long lastMySqlValidationNanos;
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
            lastMySqlValidationNanos = System.nanoTime();
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

            if (mysqlConnection.isClosed()) {
                if (!mysqlEnabled) {
                    return false;
                }
                return connectMySql();
            }

            // A validation ping is network I/O. Doing it for every inventory click was
            // one of the main sources of the visible delay on remote MySQL servers.
            long validationAge = System.nanoTime() - lastMySqlValidationNanos;
            if (validationAge < TimeUnit.SECONDS.toNanos(30)) {
                return true;
            }
            if (!mysqlConnection.isValid(2)) {
                if (!mysqlEnabled) {
                    return false;
                }
                return connectMySql();
            }
            lastMySqlValidationNanos = System.nanoTime();
            return true;
        } catch (SQLException e) {
            if (!mysqlEnabled) {
                plugin.getLogger().log(Level.SEVERE, "MySQL Verbindung nicht verf\u00fcgbar", e);
                return false;
            }
            plugin.getLogger().log(Level.WARNING, "MySQL Verbindung ung\u00fcltig, reconnect wird versucht", e);
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
                plugin.getLogger().log(Level.SEVERE, "Nitrite nicht verf\u00fcgbar", e);
                return false;
            }
            plugin.getLogger().log(Level.WARNING, "Nitrite ung\u00fcltig, Reconnect wird versucht", e);
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

    private synchronized PlayerLager loadLagerJson(UUID playerUuid) {
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

    private synchronized PlayerLager loadLagerMySql(UUID playerUuid) {
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

                plugin.getLogger().warning("MySQL Migration fehlgeschlagen, nutze Legacy-JSON f\u00fcr " + playerUuid);
                return legacy;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load lager from mysql for " + playerUuid, e);
            return null;
        }
    }

    private synchronized PlayerLager loadLagerNitrite(UUID playerUuid) {
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

    public boolean saveLager(UUID playerUuid) {
        PlayerLager lager = playerLagers.get(playerUuid);
        if (lager == null) {
            return false;
        }

        // Item quantities are write-through: the call only succeeds once the storage
        // backend committed the new state. GUI-only shulker settings remain debounced.
        return persistLager(playerUuid, lager);
    }

    private boolean persistLager(UUID playerUuid, PlayerLager lager) {
        if (mysqlEnabled) {
            return saveLagerMySql(playerUuid, lager);
        } else if (nitriteEnabled) {
            return saveLagerNitrite(playerUuid, lager);
        } else {
            return saveLagerJson(playerUuid, lager);
        }
    }

    private synchronized boolean saveLagerJson(UUID playerUuid, PlayerLager lager) {
        File file = new File(dataFolder, playerUuid + ".json");
        if (!writeJsonAtomically(file, lager)) {
            plugin.getLogger().severe("Could not save lager for " + playerUuid);
            return false;
        }
        return true;
    }

    private synchronized boolean saveLagerMySql(UUID playerUuid, PlayerLager lager) {
        if (!ensureMySqlConnection()) {
            return false;
        }

        if (!saveLagerMySqlStructured(playerUuid, lager)) {
            plugin.getLogger().severe("Could not save lager to mysql (structured) for " + playerUuid);
            return false;
        }
        return true;
    }

    private synchronized boolean saveLagerNitrite(UUID playerUuid, PlayerLager lager) {
        if (!ensureNitriteConnection()) {
            return saveLagerJson(playerUuid, lager);
        }

        try {
            Document document = Document.createDocument("player_uuid", playerUuid.toString())
                    .put("json_data", gson.toJson(lager));
            nitritePlayers.update(
                    FluentFilter.where("player_uuid").eq(playerUuid.toString()),
                    document,
                    UpdateOptions.updateOptions(true, true));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save lager to nitrite for " + playerUuid, e);
            return saveLagerJson(playerUuid, lager);
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
                persistedPlayerItems.put(playerUuid, toPersistedItems(items));
                persistedPlayerMeta.put(playerUuid, toPersistedMeta(lager));
                return lager;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load structured lager from mysql for " + playerUuid, e);
            return null;
        }
    }

    private boolean saveLagerMySqlStructured(UUID playerUuid, PlayerLager lager) {
        boolean previousAutoCommit = true;
        List<PersistedItem> currentItems = toPersistedItems(lager.getItems());
        List<PersistedItem> previousItems = persistedPlayerItems.get(playerUuid);
        PersistedMeta currentMeta = toPersistedMeta(lager);
        PersistedMeta previousMeta = persistedPlayerMeta.get(playerUuid);
        try {
            previousAutoCommit = mysqlConnection.getAutoCommit();
            mysqlConnection.setAutoCommit(false);

            if (!currentMeta.equals(previousMeta)) {
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
                    metaPs.setInt(2, currentMeta.unlockedSlots());
                    metaPs.setInt(3, currentMeta.capacity());
                    metaPs.setString(4, currentMeta.vacuumFuelMaterial());
                    metaPs.setInt(5, currentMeta.vacuumCharge());
                    metaPs.setInt(6, currentMeta.storedExp());
                    metaPs.setString(7, currentMeta.trustedPlayersJson());
                    metaPs.executeUpdate();
                }
            }

            String insertItem = "INSERT INTO lager_player_items (player_uuid, slot_index, base64_data, amount) "
                    + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                    + "base64_data = VALUES(base64_data), amount = VALUES(amount)";
            try (PreparedStatement itemPs = mysqlConnection.prepareStatement(insertItem)) {
                int changedRows = 0;
                for (int idx = 0; idx < currentItems.size(); idx++) {
                    PersistedItem item = currentItems.get(idx);
                    if (previousItems != null && idx < previousItems.size()
                            && item.equals(previousItems.get(idx))) {
                        continue;
                    }
                    itemPs.setString(1, playerUuid.toString());
                    itemPs.setInt(2, idx);
                    itemPs.setString(3, item.base64Data());
                    itemPs.setInt(4, item.amount());
                    itemPs.addBatch();
                    changedRows++;
                }
                if (changedRows > 0) {
                    itemPs.executeBatch();
                }
            }

            if (previousItems == null || previousItems.size() > currentItems.size()) {
                try (PreparedStatement deleteItems = mysqlConnection.prepareStatement(
                        "DELETE FROM lager_player_items WHERE player_uuid = ? AND slot_index >= ?")) {
                    deleteItems.setString(1, playerUuid.toString());
                    deleteItems.setInt(2, currentItems.size());
                    deleteItems.executeUpdate();
                }
            }

            mysqlConnection.commit();
            persistedPlayerItems.put(playerUuid, currentItems);
            persistedPlayerMeta.put(playerUuid, currentMeta);
            return true;
        } catch (SQLException e) {
            lastMySqlValidationNanos = 0L;
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
        return addItemToLager(playerUuid, shulkerId, item, true);
    }

    public int addItemToLager(UUID playerUuid, UUID shulkerId, ItemStack item, boolean saveImmediately) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(RecipeManager.SHULKER_KEY, PersistentDataType.STRING)) {
            return 0; // Never store/suck Lager-Shulker items
        }

        PlayerLager lager = getLager(playerUuid);
        int added = lager.addItemWithLimits(item, lager.getUnlockedSlots(), lager.getCapacity());

        if (added > 0 && saveImmediately) {
            if (!saveLager(playerUuid)) {
                lager.removeItem(item, added);
                return 0;
            }
        }
        return added;
    }

    public int takeItemFromLager(UUID playerUuid, ItemStack item, int amount) {
        PlayerLager lager = getLager(playerUuid);
        int removed = lager.removeItem(item, amount);
        if (removed <= 0) {
            return 0;
        }
        if (!saveLager(playerUuid)) {
            ItemStack rollback = item.clone();
            rollback.setAmount(removed);
            lager.addItem(rollback);
            return 0;
        }
        return removed;
    }

    public int addVacuumItemToLager(UUID playerUuid, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        PlayerLager lager = getLager(playerUuid);
        int chargeBefore = lager.getVacuumCharge();
        int allowed = Math.min(item.getAmount(), chargeBefore);
        if (allowed <= 0) {
            return 0;
        }
        ItemStack limited = item.clone();
        limited.setAmount(allowed);
        int added = lager.addItemWithLimits(limited, lager.getUnlockedSlots(), lager.getCapacity());
        if (added <= 0) {
            return 0;
        }
        lager.takeVacuumCharge(added);
        if (!saveLager(playerUuid)) {
            lager.removeItem(limited, added);
            lager.setVacuumCharge(chargeBefore);
            return 0;
        }
        return added;
    }

    public int takeMaterialFromLager(UUID playerUuid, org.bukkit.Material material, int amount) {
        PlayerLager lager = getLager(playerUuid);
        int removed = lager.removeByMaterial(material, amount);
        if (removed <= 0) {
            return 0;
        }
        if (!saveLager(playerUuid)) {
            lager.addItem(new ItemStack(material, removed));
            return 0;
        }
        return removed;
    }

    public boolean addStoredExperience(UUID playerUuid, int amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerLager lager = getLager(playerUuid);
        lager.addStoredExp(amount);
        if (saveLager(playerUuid)) {
            return true;
        }
        lager.takeStoredExp(amount);
        return false;
    }

    public int takeStoredExperience(UUID playerUuid, int amount) {
        PlayerLager lager = getLager(playerUuid);
        int taken = lager.takeStoredExp(amount);
        if (taken <= 0) {
            return 0;
        }
        if (saveLager(playerUuid)) {
            return taken;
        }
        lager.addStoredExp(taken);
        return 0;
    }

    public boolean addVacuumCharge(UUID playerUuid, int amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerLager lager = getLager(playerUuid);
        int previous = lager.getVacuumCharge();
        lager.addVacuumCharge(amount);
        if (saveLager(playerUuid)) {
            return true;
        }
        lager.setVacuumCharge(previous);
        return false;
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

    private synchronized ShulkerSettings loadShulkerSettingsJson(UUID shulkerId) {
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

    private synchronized ShulkerSettings loadShulkerSettingsMySql(UUID shulkerId) {
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

    private synchronized ShulkerSettings loadShulkerSettingsNitrite(UUID shulkerId) {
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

        ShulkerSettings snapshot = gson.fromJson(gson.toJson(settings), ShulkerSettings.class);
        scheduleShulkerSave(shulkerId, snapshot);
    }

    private void scheduleShulkerSave(UUID shulkerId, ShulkerSettings snapshot) {
        pendingShulkerSaves.compute(shulkerId, (uuid, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return saveExecutor.schedule(() -> {
                persistShulkerSettings(uuid, snapshot);
            }, SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        });
    }

    private void persistShulkerSettings(UUID shulkerId, ShulkerSettings settings) {
        if (mysqlEnabled) {
            saveShulkerSettingsMySql(shulkerId, settings);
        } else if (nitriteEnabled) {
            saveShulkerSettingsNitrite(shulkerId, settings);
        } else {
            saveShulkerSettingsJson(shulkerId, settings);
        }
    }

    private List<PersistedItem> toPersistedItems(List<StorageItem> items) {
        List<PersistedItem> result = new ArrayList<>();
        for (StorageItem item : items) {
            if (item == null || item.getBase64Data() == null || item.getBase64Data().isEmpty()
                    || item.getAmount() <= 0) {
                continue;
            }
            result.add(new PersistedItem(item.getBase64Data(), item.getAmount()));
        }
        return result;
    }

    private PersistedMeta toPersistedMeta(PlayerLager lager) {
        return new PersistedMeta(
                lager.getUnlockedSlots(),
                lager.getCapacity(),
                lager.getVacuumFuelMaterial(),
                lager.getVacuumCharge(),
                lager.getStoredExp(),
                gson.toJson(lager.getTrustedPlayers()));
    }

    private synchronized void saveShulkerSettingsJson(UUID shulkerId, ShulkerSettings settings) {
        File file = new File(shulkerFolder, shulkerId + ".json");
        if (!writeJsonAtomically(file, settings)) {
            plugin.getLogger().severe("Could not save shulker settings " + shulkerId);
        }
    }

    private boolean writeJsonAtomically(File file, Object value) {
        Path target = file.toPath();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, gson.toJson(value), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Atomic JSON write failed for " + file.getName(), e);
            return false;
        }
    }

    private synchronized void saveShulkerSettingsMySql(UUID shulkerId, ShulkerSettings settings) {
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

    private synchronized void saveShulkerSettingsNitrite(UUID shulkerId, ShulkerSettings settings) {
        if (!ensureNitriteConnection()) {
            saveShulkerSettingsJson(shulkerId, settings);
            return;
        }

        try {
            Document document = Document.createDocument("shulker_uuid", shulkerId.toString())
                    .put("json_data", gson.toJson(settings));
            nitriteShulkers.update(
                    FluentFilter.where("shulker_uuid").eq(shulkerId.toString()),
                    document,
                    UpdateOptions.updateOptions(true, true));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shulker settings to nitrite " + shulkerId, e);
            saveShulkerSettingsJson(shulkerId, settings);
        }
    }

    public void saveAllData() {
        pendingShulkerSaves.values().forEach(future -> future.cancel(false));
        pendingShulkerSaves.clear();

        for (Map.Entry<UUID, PlayerLager> entry : playerLagers.entrySet()) {
            persistLager(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, ShulkerSettings> entry : shulkerSettings.entrySet()) {
            persistShulkerSettings(entry.getKey(), entry.getValue());
        }
    }

    public void shutdown() {
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Storage writer did not stop within 10 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private record PersistedItem(String base64Data, int amount) {
    }

    private record PersistedMeta(int unlockedSlots, int capacity, String vacuumFuelMaterial, int vacuumCharge,
            int storedExp, String trustedPlayersJson) {
    }
}


