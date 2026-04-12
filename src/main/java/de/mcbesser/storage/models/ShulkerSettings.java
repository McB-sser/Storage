package de.mcbesser.storage.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.mcbesser.storage.models.ItemCategory;
import org.bukkit.Material;

public class ShulkerSettings {
    private final UUID shulkerId;
    private String ownerUuid;
    private String ownerName;
    private String color; // Shulker color
    private String fillItemMaterial; // Material for auto-fill shulker inv
    private boolean autoStore;
    private int minStock;
    private int withdrawAmount;
    private java.util.List<UUID> trustedPlayers;
    private Map<Integer, String> quickSlots; // Slot 9-44 for individual items
    private Map<Integer, List<String>> categorySlots; // Category (1-9) -> List of Materials
    private Map<Integer, String> categoryNames; // Category (1-9) -> Display name
    private Map<Integer, String> categoryIcons; // Category (1-9) -> Material name
    private int unlockedSlots;
    private int capacity;
    private int storedExp;
    private boolean vacuumEnabled;
    private String vacuumFuelMaterial;
    private int vacuumCharge;
    private Boolean shulkerRefillEnabled;
    private Boolean vacuumFilterEnabled;
    private Map<Integer, String> vacuumFilterSlots; // 0-8 -> material name
    private Boolean vacuumRangeParticlesEnabled;
    private String vacuumRangeMode;
    private int vacuumRangeNegX;
    private int vacuumRangePosX;
    private int vacuumRangeNegY;
    private int vacuumRangePosY;
    private int vacuumRangeNegZ;
    private int vacuumRangePosZ;
    private String lagerSortMode;
    private Boolean displayEnabled;

    public ShulkerSettings(UUID shulkerId) {
        this.shulkerId = shulkerId;
        this.ownerUuid = null;
        this.ownerName = null;
        this.color = "PURPLE";
        this.autoStore = false;
        this.minStock = 0;
        this.withdrawAmount = 64;
        this.trustedPlayers = new java.util.ArrayList<>();
        this.quickSlots = new HashMap<>();
        this.categorySlots = new HashMap<>();
        this.categoryNames = new HashMap<>();
        this.categoryIcons = new HashMap<>();
        this.unlockedSlots = 27;
        this.capacity = 6 * 9 * 64;
        this.storedExp = 0;
        this.vacuumEnabled = false;
        this.vacuumFuelMaterial = null;
        this.vacuumCharge = 0;
        this.shulkerRefillEnabled = Boolean.TRUE;
        this.vacuumFilterEnabled = Boolean.FALSE;
        this.vacuumFilterSlots = new HashMap<>();
        this.vacuumRangeParticlesEnabled = Boolean.FALSE;
        this.vacuumRangeMode = "CHUNK_1X1";
        this.vacuumRangeNegX = 0;
        this.vacuumRangePosX = 0;
        this.vacuumRangeNegY = 0;
        this.vacuumRangePosY = 0;
        this.vacuumRangeNegZ = 0;
        this.vacuumRangePosZ = 0;
        this.lagerSortMode = "NAME_ASC";
        this.displayEnabled = Boolean.FALSE;

        // Initialize empty lists for each category
        ensureDefaults();
    }

    public UUID getShulkerId() {
        return shulkerId;
    }

    public String getOwnerName() {
        ensureDefaults();
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        ensureDefaults();
        this.ownerName = ownerName;
    }

    public String getOwnerUuid() {
        ensureDefaults();
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        ensureDefaults();
        this.ownerUuid = ownerUuid;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getFillItemMaterial() {
        return fillItemMaterial;
    }

    public void setFillItemMaterial(String fillItemMaterial) {
        this.fillItemMaterial = fillItemMaterial;
    }

    public boolean isAutoStore() {
        return autoStore;
    }

    public void setAutoStore(boolean autoStore) {
        this.autoStore = autoStore;
    }

    public int getMinStock() {
        return minStock;
    }

    public void setMinStock(int minStock) {
        this.minStock = minStock;
    }

    public int getWithdrawAmount() {
        return withdrawAmount;
    }

    public void setWithdrawAmount(int withdrawAmount) {
        this.withdrawAmount = withdrawAmount;
    }

    public java.util.List<UUID> getTrustedPlayers() {
        if (trustedPlayers == null) {
            trustedPlayers = new java.util.ArrayList<>();
        }
        return trustedPlayers;
    }

    public Map<Integer, String> getQuickSlots() {
        if (quickSlots == null) {
            quickSlots = new HashMap<>();
        }
        return quickSlots;
    }

    public Map<Integer, List<String>> getCategorySlots() {
        ensureDefaults();
        return categorySlots;
    }

    public int getUnlockedSlots() {
        ensureDefaults();
        return unlockedSlots;
    }

    public boolean upgradeSlot() {
        ensureDefaults();
        if (unlockedSlots >= 54) {
            return false;
        }
        unlockedSlots++;
        return true;
    }

    public int getCapacity() {
        ensureDefaults();
        return capacity;
    }

    public void upgradeCapacity() {
        ensureDefaults();
        capacity += 9 * 3 * 64;
    }

    public int getStoredExp() {
        ensureDefaults();
        return storedExp;
    }

    public void addStoredExp(int points) {
        ensureDefaults();
        if (points > 0) {
            storedExp += points;
        }
    }

    public int takeStoredExp(int points) {
        ensureDefaults();
        int taken = Math.max(0, Math.min(points, storedExp));
        storedExp -= taken;
        return taken;
    }

    public boolean isVacuumEnabled() {
        ensureDefaults();
        return vacuumEnabled;
    }

    public void setVacuumEnabled(boolean vacuumEnabled) {
        ensureDefaults();
        this.vacuumEnabled = vacuumEnabled;
    }

    public String getVacuumFuelMaterial() {
        ensureDefaults();
        return vacuumFuelMaterial;
    }

    public void setVacuumFuelMaterial(String vacuumFuelMaterial) {
        ensureDefaults();
        this.vacuumFuelMaterial = vacuumFuelMaterial;
    }

    public int getVacuumCharge() {
        ensureDefaults();
        return vacuumCharge;
    }

    public void addVacuumCharge(int points) {
        ensureDefaults();
        if (points > 0) {
            vacuumCharge += points;
        }
    }

    public int takeVacuumCharge(int points) {
        ensureDefaults();
        int taken = Math.max(0, Math.min(points, vacuumCharge));
        vacuumCharge -= taken;
        return taken;
    }

    public boolean isShulkerRefillEnabled() {
        ensureDefaults();
        return Boolean.TRUE.equals(shulkerRefillEnabled);
    }

    public void setShulkerRefillEnabled(boolean shulkerRefillEnabled) {
        ensureDefaults();
        this.shulkerRefillEnabled = shulkerRefillEnabled;
    }

    public boolean isVacuumFilterEnabled() {
        ensureDefaults();
        return Boolean.TRUE.equals(vacuumFilterEnabled);
    }

    public void setVacuumFilterEnabled(boolean vacuumFilterEnabled) {
        ensureDefaults();
        this.vacuumFilterEnabled = vacuumFilterEnabled;
    }

    public Map<Integer, String> getVacuumFilterSlots() {
        ensureDefaults();
        return vacuumFilterSlots;
    }

    public boolean isVacuumRangeParticlesEnabled() {
        ensureDefaults();
        return Boolean.TRUE.equals(vacuumRangeParticlesEnabled);
    }

    public void setVacuumRangeParticlesEnabled(boolean vacuumRangeParticlesEnabled) {
        ensureDefaults();
        this.vacuumRangeParticlesEnabled = vacuumRangeParticlesEnabled;
    }

    public String getVacuumRangeMode() {
        ensureDefaults();
        return vacuumRangeMode;
    }

    public void setVacuumRangeMode(String vacuumRangeMode) {
        ensureDefaults();
        this.vacuumRangeMode = vacuumRangeMode;
    }

    public int getVacuumRangeNegX() {
        ensureDefaults();
        return vacuumRangeNegX;
    }

    public void setVacuumRangeNegX(int vacuumRangeNegX) {
        ensureDefaults();
        this.vacuumRangeNegX = vacuumRangeNegX;
    }

    public int getVacuumRangePosX() {
        ensureDefaults();
        return vacuumRangePosX;
    }

    public void setVacuumRangePosX(int vacuumRangePosX) {
        ensureDefaults();
        this.vacuumRangePosX = vacuumRangePosX;
    }

    public int getVacuumRangeNegY() {
        ensureDefaults();
        return vacuumRangeNegY;
    }

    public void setVacuumRangeNegY(int vacuumRangeNegY) {
        ensureDefaults();
        this.vacuumRangeNegY = vacuumRangeNegY;
    }

    public int getVacuumRangePosY() {
        ensureDefaults();
        return vacuumRangePosY;
    }

    public void setVacuumRangePosY(int vacuumRangePosY) {
        ensureDefaults();
        this.vacuumRangePosY = vacuumRangePosY;
    }

    public int getVacuumRangeNegZ() {
        ensureDefaults();
        return vacuumRangeNegZ;
    }

    public void setVacuumRangeNegZ(int vacuumRangeNegZ) {
        ensureDefaults();
        this.vacuumRangeNegZ = vacuumRangeNegZ;
    }

    public int getVacuumRangePosZ() {
        ensureDefaults();
        return vacuumRangePosZ;
    }

    public void setVacuumRangePosZ(int vacuumRangePosZ) {
        ensureDefaults();
        this.vacuumRangePosZ = vacuumRangePosZ;
    }

    public String getLagerSortMode() {
        ensureDefaults();
        return lagerSortMode;
    }

    public void setLagerSortMode(String lagerSortMode) {
        ensureDefaults();
        this.lagerSortMode = lagerSortMode;
    }

    public boolean isDisplayEnabled() {
        ensureDefaults();
        return Boolean.TRUE.equals(displayEnabled);
    }

    public void setDisplayEnabled(boolean displayEnabled) {
        ensureDefaults();
        this.displayEnabled = displayEnabled;
    }

    public boolean isVacuumItemAllowed(Material material) {
        ensureDefaults();
        if (material == null) {
            return false;
        }
        if (!isVacuumFilterEnabled()) {
            return true;
        }
        return vacuumFilterSlots.containsValue(material.name());
    }

    public String getCategoryName(int category) {
        ensureDefaults();
        return categoryNames.getOrDefault(category, "Kategorie " + category);
    }

    public void setCategoryName(int category, String name) {
        ensureDefaults();
        if (category >= 1 && category <= 9 && name != null && !name.trim().isEmpty()) {
            categoryNames.put(category, name.trim());
        }
    }

    public String getCategoryIconMaterial(int category) {
        ensureDefaults();
        return categoryIcons.get(category);
    }

    public void setCategoryIconMaterial(int category, String materialName) {
        ensureDefaults();
        if (category >= 1 && category <= 9 && materialName != null && !materialName.trim().isEmpty()) {
            categoryIcons.put(category, materialName.trim());
        }
    }

    /**
     * Add a material to a category
     */
    public void addToCategory(int category, String materialName) {
        ensureDefaults();
        if (category >= 1 && category <= 9) {
            List<String> materials = categorySlots.get(category);
            if (!materials.contains(materialName)) {
                materials.add(materialName);
            }
        }
    }

    /**
     * Remove a material from all categories
     */
    public void removeFromAllCategories(String materialName) {
        ensureDefaults();
        for (List<String> materials : categorySlots.values()) {
            materials.remove(materialName);
        }
    }

    /**
     * Get the category number for a material (1-9), or 0 if not assigned
     */
    public int getCategoryForMaterial(String materialName) {
        ensureDefaults();
        for (Map.Entry<Integer, List<String>> entry : categorySlots.entrySet()) {
            if (entry.getValue().contains(materialName)) {
                return entry.getKey();
            }
        }
        return 0;
    }

    private void ensureDefaults() {
        if (categorySlots == null) {
            categorySlots = new HashMap<>();
        }
        for (int i = 1; i <= 9; i++) {
            categorySlots.computeIfAbsent(i, key -> new ArrayList<>());
        }
        if (categoryNames == null) {
            categoryNames = new HashMap<>();
        }
        if (categoryIcons == null) {
            categoryIcons = new HashMap<>();
        }
        for (ItemCategory category : ItemCategory.values()) {
            categoryNames.putIfAbsent(category.getNumber(), category.getDisplayName());
            categoryIcons.putIfAbsent(category.getNumber(), category.getIcon().name());
        }
        if (quickSlots == null) {
            quickSlots = new HashMap<>();
        }
        if (trustedPlayers == null) {
            trustedPlayers = new java.util.ArrayList<>();
        }
        if (unlockedSlots <= 0) {
            unlockedSlots = 27;
        }
        if (capacity <= 0) {
            capacity = 6 * 9 * 64;
        }
        if (storedExp < 0) {
            storedExp = 0;
        }
        if (vacuumCharge < 0) {
            vacuumCharge = 0;
        }
        if (shulkerRefillEnabled == null) {
            shulkerRefillEnabled = Boolean.TRUE;
        }
        if (vacuumFilterEnabled == null) {
            vacuumFilterEnabled = Boolean.FALSE;
        }
        if (vacuumFilterSlots == null) {
            vacuumFilterSlots = new HashMap<>();
        }
        if (vacuumRangeParticlesEnabled == null) {
            vacuumRangeParticlesEnabled = Boolean.FALSE;
        }
        if (vacuumRangeMode == null || vacuumRangeMode.isBlank()) {
            vacuumRangeMode = "CHUNK_1X1";
        }
        vacuumRangeNegX = clamp(vacuumRangeNegX, 0, 48);
        vacuumRangePosX = clamp(vacuumRangePosX, 0, 48);
        if (vacuumRangeNegX + vacuumRangePosX > 48) {
            int overflow = (vacuumRangeNegX + vacuumRangePosX) - 48;
            vacuumRangePosX = Math.max(0, vacuumRangePosX - overflow);
        }
        vacuumRangeNegZ = clamp(vacuumRangeNegZ, 0, 48);
        vacuumRangePosZ = clamp(vacuumRangePosZ, 0, 48);
        if (vacuumRangeNegZ + vacuumRangePosZ > 48) {
            int overflow = (vacuumRangeNegZ + vacuumRangePosZ) - 48;
            vacuumRangePosZ = Math.max(0, vacuumRangePosZ - overflow);
        }
        vacuumRangeNegY = clamp(vacuumRangeNegY, 0, 320);
        vacuumRangePosY = clamp(vacuumRangePosY, 0, 320);
        if (vacuumRangeNegY + vacuumRangePosY > 320) {
            int overflow = (vacuumRangeNegY + vacuumRangePosY) - 320;
            vacuumRangePosY = Math.max(0, vacuumRangePosY - overflow);
        }
        if (lagerSortMode == null || lagerSortMode.isBlank()) {
            lagerSortMode = "NAME_ASC";
        }
        if (displayEnabled == null) {
            displayEnabled = Boolean.FALSE;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

