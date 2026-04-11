package de.mcbesser.storage;

import de.mcbesser.storage.managers.LagerManager;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.managers.ChatPromptManager;
import de.mcbesser.storage.listeners.MenuListener;
import de.mcbesser.storage.listeners.MiddleClickStoreListener;
import de.mcbesser.storage.listeners.PickBlockListener;
import de.mcbesser.storage.listeners.ShulkerListener;
import de.mcbesser.storage.listeners.BlockListener;
import de.mcbesser.storage.listeners.StorageSidebarListener;
import de.mcbesser.storage.listeners.VacuumListener;
import org.bukkit.plugin.java.JavaPlugin;
import de.mcbesser.storage.sidebar.StorageSidebar;

public class Storage extends JavaPlugin {
    private static Storage instance;
    private LagerManager lagerManager;
    private RecipeManager recipeManager;
    private StorageSidebar storageSidebar;
    private ChatPromptManager chatPromptManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.lagerManager = new LagerManager(this);
        this.recipeManager = new RecipeManager(this);
        this.storageSidebar = new StorageSidebar(this);
        this.chatPromptManager = new ChatPromptManager(this);
        this.recipeManager.registerRecipes();
        getServer().getPluginManager().registerEvents(this.recipeManager, this);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ShulkerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new VacuumListener(this), this);
        getServer().getPluginManager().registerEvents(new PickBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new MiddleClickStoreListener(this), this);
        getServer().getPluginManager().registerEvents(new StorageSidebarListener(this, storageSidebar), this);
        getServer().getPluginManager().registerEvents(chatPromptManager, this);
        storageSidebar.start();

        getLogger().info("Storage erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        if (storageSidebar != null) {
            storageSidebar.stop();
        }
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

    public StorageSidebar getStorageSidebar() {
        return storageSidebar;
    }

    public ChatPromptManager getChatPromptManager() {
        return chatPromptManager;
    }
}

