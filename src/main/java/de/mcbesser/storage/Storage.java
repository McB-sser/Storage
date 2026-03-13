package de.mcbesser.storage;

import de.mcbesser.storage.managers.LagerManager;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.listeners.MenuListener;
import de.mcbesser.storage.listeners.MiddleClickStoreListener;
import de.mcbesser.storage.listeners.PickBlockListener;
import de.mcbesser.storage.listeners.ShulkerListener;
import de.mcbesser.storage.listeners.BlockListener;
import de.mcbesser.storage.listeners.VacuumListener;
import org.bukkit.plugin.java.JavaPlugin;

public class Storage extends JavaPlugin {
    private static Storage instance;
    private LagerManager lagerManager;
    private RecipeManager recipeManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.lagerManager = new LagerManager(this);
        this.recipeManager = new RecipeManager(this);
        this.recipeManager.registerRecipes();
        getServer().getPluginManager().registerEvents(this.recipeManager, this);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ShulkerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new VacuumListener(this), this);
        getServer().getPluginManager().registerEvents(new PickBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new MiddleClickStoreListener(this), this);

        getLogger().info("Storage erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        if (lagerManager != null) {
            lagerManager.saveAllData();
            lagerManager.shutdown();
        }
        getLogger().info("Storage erfolgreich deaktiviert!");
    }

    public static Storage getInstance() {
        return instance;
    }

    public LagerManager getLagerManager() {
        return lagerManager;
    }
}

