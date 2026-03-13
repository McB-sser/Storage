package de.mcbesser.storage.managers;

import de.mcbesser.storage.Storage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecipeManager implements Listener {
    private final Storage plugin;
    public static final NamespacedKey SHULKER_KEY = new NamespacedKey("storage", "shulker_id");
    private NamespacedKey recipeKey;

    public RecipeManager(Storage plugin) {
        this.plugin = plugin;
    }

    public void registerRecipes() {
        recipeKey = new NamespacedKey(plugin, "lager_shulker");
        ItemStack result = new ItemStack(Material.SHULKER_BOX);
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Lager Shulker", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Ein erweiterter Shulker mit Lager-Anbindung.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);

            result.setItemMeta(meta);
        }

        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, result);
        recipe.addIngredient(Material.SHULKER_BOX);
        recipe.addIngredient(Material.ENDER_CHEST);

        plugin.getServer().addRecipe(recipe);

        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            onlinePlayer.discoverRecipe(recipeKey);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (recipeKey == null) {
            return;
        }
        event.getPlayer().discoverRecipe(recipeKey);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (recipeKey == null) {
            return;
        }

        Recipe recipe = event.getRecipe();
        NamespacedKey key = null;
        if (recipe instanceof ShapelessRecipe shapeless) {
            key = shapeless.getKey();
        } else if (recipe instanceof ShapedRecipe shaped) {
            key = shaped.getKey();
        }
        if (key == null || !recipeKey.equals(key)) {
            return;
        }

        ItemStack crafted = event.getCurrentItem();
        if (crafted == null) {
            return;
        }

        ItemMeta meta = crafted.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().set(SHULKER_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
        crafted.setItemMeta(meta);
        event.setCurrentItem(crafted);
    }
}

