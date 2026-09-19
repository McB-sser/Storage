package de.mcbesser.storage.models;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class StorageItem {
    private String base64Data;
    private int amount;

    public StorageItem(ItemStack item) {
        this.base64Data = itemToBase64(item);
        this.amount = item.getAmount();
    }

    public StorageItem(String base64Data, int amount) {
        this.base64Data = base64Data;
        this.amount = amount;
    }

    public ItemStack toItemStack() {
        ItemStack item = itemFromBase64(base64Data);
        if (item != null) {
            item.setAmount(1); // Set to 1 so isSimilar works correctly, amount is handled separately
        }
        return item;
    }

    public String getMaterial() {
        ItemStack item = toItemStack();
        return (item != null) ? item.getType().name() : "AIR";
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

