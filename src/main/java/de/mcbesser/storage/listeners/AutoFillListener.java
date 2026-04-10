package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.gui.AbstractMenu;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

public class AutoFillListener implements Listener {
    private final Storage plugin;

    public AutoFillListener(Storage plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        checkAndRefill(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof AbstractMenu) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            checkAndRefill(player);
        }
    }

    private void checkAndRefill(Player player) {
        PlayerLager lager = plugin.getLagerManager().getLager(player.getUniqueId());

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }

            String idStr = item.getItemMeta().getPersistentDataContainer().get(RecipeManager.SHULKER_KEY,
                    PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }

            ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(UUID.fromString(idStr));
            String fillMat = settings.getFillItemMaterial();
            if (fillMat == null) {
                continue;
            }

            Material mat = Material.matchMaterial(fillMat);
            if (mat == null) {
                continue;
            }

            int currentAmount = 0;
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null && invItem.getType() == mat) {
                    currentAmount += invItem.getAmount();
                }
            }

            if (currentAmount >= settings.getMinStock()) {
                continue;
            }

            int reserve = Math.max(0, settings.getMinStock());
            int available = Math.max(0, lager.getAmountByMaterial(mat) - reserve);
            if (available <= 0) {
                continue;
            }

            int toTake = Math.min(available, Math.max(1, settings.getWithdrawAmount()));
            toTake = Math.min(toTake, mat.getMaxStackSize());

            ItemStack result = new ItemStack(mat, toTake);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(result);

            int leftoverAmount = 0;
            for (ItemStack leftover : leftovers.values()) {
                leftoverAmount += leftover.getAmount();
            }
            int inserted = toTake - leftoverAmount;

            if (inserted > 0) {
                lager.removeByMaterial(mat, inserted);
                plugin.getLagerManager().saveLager(player.getUniqueId());
                player.sendMessage("Autom. Nachf\u00fcll-Logik: " + inserted + "x " + mat.name() + " entnommen.");
            }
        }
    }
}

