package de.mcbesser.storage.models;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class StorageItem {
    private String base64Data;
    private int amount;
    private transient ItemStack cachedItem;
    private transient String cachedMaterial;

    public StorageItem(ItemStack item) {
        this.base64Data = itemToBase64(item);
        this.amount = item.getAmount();
        cacheItem(item);
    }

    public StorageItem(String base64Data, int amount) {
        this.base64Data = base64Data;
        this.amount = amount;
    }

    public ItemStack toItemStack() {
        if (cachedItem == null) {
            ItemStack decoded = itemFromBase64(base64Data);
            if (decoded == null) {
                return null;
            }
            cacheItem(decoded);
        }
        return cachedItem.clone();
    }

    public String getMaterial() {
        if (cachedMaterial == null) {
            ItemStack item = toItemStack();
            cachedMaterial = item != null ? item.getType().name() : "AIR";
        }
        return cachedMaterial;
    }

    public int getAmount() {
        return amount;
    }

    public String getBase64Data() {
        return base64Data;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void addAmount(int amount) {
        this.amount += amount;
    }

    private String itemToBase64(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private void cacheItem(ItemStack item) {
        cachedItem = item.clone();
        cachedItem.setAmount(1); // Amount is tracked separately by this storage entry.
        cachedMaterial = cachedItem.getType().name();
    }

    private ItemStack itemFromBase64(String data) {
        try {
            // The MIME decoder also accepts Base64Coder's legacy line-wrapped values.
            byte[] bytes = Base64.getMimeDecoder().decode(data);
            if (isLegacyObjectStream(bytes)) {
                return deserializeLegacy(bytes);
            }
            return ItemStack.deserializeBytes(bytes);
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isLegacyObjectStream(byte[] bytes) {
        return bytes.length >= 4
                && (bytes[0] & 0xff) == 0xac
                && (bytes[1] & 0xff) == 0xed
                && bytes[2] == 0
                && bytes[3] == 5;
    }

    @SuppressWarnings("deprecation")
    private ItemStack deserializeLegacy(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        }
    }
}

