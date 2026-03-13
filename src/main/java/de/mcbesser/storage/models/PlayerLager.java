package de.mcbesser.storage.models;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerLager {
    private final UUID owner;
    private List<StorageItem> items;
    private int unlockedSlots;
    private int capacity;
    private String vacuumFuelMaterial;
    private int vacuumCharge;
    private int storedExp;
    private List<UUID> trustedPlayers;

    public PlayerLager(UUID owner) {
        this.owner = owner;
        this.items = new ArrayList<>();
        this.unlockedSlots = 27;
        this.capacity = 6 * 9 * 64;
        this.vacuumFuelMaterial = null;
        this.vacuumCharge = 0;
        this.storedExp = 0;
        this.trustedPlayers = new ArrayList<>();
    }

    public void addItem(ItemStack item) {
        for (StorageItem storageItem : items) {
            ItemStack stack = storageItem.toItemStack();
            if (stack != null && stack.isSimilar(item)) {
                storageItem.addAmount(item.getAmount());
                return;
            }
        }
        items.add(new StorageItem(item));
    }

    public int addItemWithLimits(ItemStack item, int maxSlots, int maxCapacity) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return 0;
        }

        int freeCapacity = Math.max(0, maxCapacity - getUsedAmount());
        if (freeCapacity <= 0) {
            return 0;
        }

        int toStore = Math.min(item.getAmount(), freeCapacity);
        StorageItem existing = null;

        for (StorageItem storageItem : items) {
            ItemStack stack = storageItem.toItemStack();
            if (stack != null && stack.isSimilar(item)) {
                existing = storageItem;
                break;
            }
        }

        if (existing != null) {
            existing.addAmount(toStore);
            return toStore;
        }

        if (items.size() >= maxSlots) {
            return 0;
        }

        ItemStack oneType = item.clone();
        oneType.setAmount(toStore);
        items.add(new StorageItem(oneType));
        return toStore;
    }

    public void removeItem(ItemStack item, int amount) {
        for (int i = 0; i < items.size(); i++) {
            StorageItem storageItem = items.get(i);
            ItemStack stack = storageItem.toItemStack();
            if (stack != null && stack.isSimilar(item)) {
                if (storageItem.getAmount() <= amount) {
                    items.remove(i);
                } else {
                    storageItem.setAmount(storageItem.getAmount() - amount);
                }
                return;
            }
        }
    }

    public UUID getOwner() {
        return owner;
    }

    public List<StorageItem> getItems() {
        ensureDefaults();
        return items;
    }

    public int removeByMaterial(Material material, int amount) {
        ensureDefaults();
        if (material == null || amount <= 0) {
            return 0;
        }

        int remaining = amount;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            StorageItem storageItem = items.get(i);
            ItemStack stack = storageItem.toItemStack();
            if (stack == null || stack.getType() != material) {
                continue;
            }

            int take = Math.min(storageItem.getAmount(), remaining);
            int newAmount = storageItem.getAmount() - take;
            if (newAmount <= 0) {
                items.remove(i);
                i--;
            } else {
                storageItem.setAmount(newAmount);
            }
            remaining -= take;
        }
        return amount - remaining;
    }

    public int getAmountByMaterial(Material material) {
        ensureDefaults();
        if (material == null) {
            return 0;
        }
        int total = 0;
        for (StorageItem storageItem : items) {
            ItemStack stack = storageItem.toItemStack();
            if (stack != null && stack.getType() == material) {
                total += Math.max(0, storageItem.getAmount());
            }
        }
        return total;
    }

    public int getUsedAmount() {
        ensureDefaults();
        int total = 0;
        for (StorageItem item : items) {
            total += Math.max(0, item.getAmount());
        }
        return total;
    }

    public void setItems(List<StorageItem> items) {
        this.items = items;
    }

    public int getUnlockedSlots() {
        ensureDefaults();
        return unlockedSlots;
    }

    public void setUnlockedSlots(int unlockedSlots) {
        ensureDefaults();
        this.unlockedSlots = Math.max(1, unlockedSlots);
    }

    public boolean upgradeSlot() {
        ensureDefaults();
        if (unlockedSlots > Integer.MAX_VALUE - 27) {
            unlockedSlots = Integer.MAX_VALUE;
            return false;
        }
        unlockedSlots += 27;
        return true;
    }

    public int getCapacity() {
        ensureDefaults();
        return capacity;
    }

    public void setCapacity(int capacity) {
        ensureDefaults();
        this.capacity = Math.max(1, capacity);
    }

    public void upgradeCapacity() {
        ensureDefaults();
        capacity += 9 * 3 * 64;
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

    public void setVacuumCharge(int vacuumCharge) {
        ensureDefaults();
        this.vacuumCharge = Math.max(0, vacuumCharge);
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

    public int getStoredExp() {
        ensureDefaults();
        return storedExp;
    }

    public List<UUID> getTrustedPlayers() {
        ensureDefaults();
        return trustedPlayers;
    }

    public void setTrustedPlayers(List<UUID> trustedPlayers) {
        ensureDefaults();
        this.trustedPlayers = trustedPlayers != null ? trustedPlayers : new ArrayList<>();
    }

    public void setStoredExp(int storedExp) {
        ensureDefaults();
        this.storedExp = Math.max(0, storedExp);
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

    private void ensureDefaults() {
        if (items == null) {
            items = new ArrayList<>();
        }
        if (unlockedSlots <= 0) {
            unlockedSlots = 27;
        }
        if (capacity <= 0) {
            capacity = 6 * 9 * 64;
        }
        if (vacuumCharge < 0) {
            vacuumCharge = 0;
        }
        if (storedExp < 0) {
            storedExp = 0;
        }
        if (trustedPlayers == null) {
            trustedPlayers = new ArrayList<>();
        }
    }
}

