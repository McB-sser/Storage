package de.mcbesser.storage.models;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save item stack.", e);
        }
    }

    private ItemStack itemFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
}

