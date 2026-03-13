package de.mcbesser.storage.listeners;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.gui.QuickSlotsView;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.ShulkerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class ShulkerListener implements Listener {
    private final Storage plugin;

    public ShulkerListener(Storage plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.getType().toString().contains("SHULKER_BOX"))
            return;
        if (!item.hasItemMeta())
            return;

        String idStr = item.getItemMeta().getPersistentDataContainer().get(RecipeManager.SHULKER_KEY,
                PersistentDataType.STRING);
        if (idStr == null)
            return;

        UUID shulkerId = UUID.fromString(idStr);
        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        if (settings.getOwnerUuid() == null || settings.getOwnerUuid().isBlank()) {
            settings.setOwnerUuid(player.getUniqueId().toString());
        }
        if (settings.getOwnerName() == null || settings.getOwnerName().isBlank()) {
            settings.setOwnerName(player.getName());
        }
        plugin.getLagerManager().saveShulkerSettings(shulkerId);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String ownerName = settings.getOwnerName() == null || settings.getOwnerName().isBlank()
                    ? player.getName()
                    : settings.getOwnerName();
            meta.displayName(Component.text("Lager von: " + ownerName, NamedTextColor.GOLD));
            item.setItemMeta(meta);
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking())
                return;

            event.setCancelled(true);
            new QuickSlotsView(plugin, shulkerId).open(player);
        }
    }
}

