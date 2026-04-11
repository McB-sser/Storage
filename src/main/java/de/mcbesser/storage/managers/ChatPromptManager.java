package de.mcbesser.storage.managers;

import de.mcbesser.storage.Storage;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatPromptManager implements Listener {
    private final Storage plugin;
    private final Map<UUID, PromptSession> sessions = new ConcurrentHashMap<>();

    public ChatPromptManager(Storage plugin) {
        this.plugin = plugin;
    }

    public void requestText(Player player, String title, String hint, Consumer<String> onSubmit, Runnable onCancel) {
        if (player == null) {
            return;
        }

        sessions.put(player.getUniqueId(), new PromptSession(onSubmit, onCancel));
        player.closeInventory();
        player.sendMessage(Component.text(title, NamedTextColor.GOLD));
        if (hint != null && !hint.isBlank()) {
            player.sendMessage(Component.text("Aktuelle Eingabe: " + hint, NamedTextColor.YELLOW));
        }
        player.sendMessage(Component.text("Schreibe deine Eingabe in den Chat. Mit \"abbrechen\" kannst du den Vorgang beenden.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PromptSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("abbrechen") || text.equalsIgnoreCase("cancel")) {
                player.sendMessage(Component.text("Eingabe abgebrochen.", NamedTextColor.YELLOW));
                if (session.onCancel() != null) {
                    session.onCancel().run();
                }
                return;
            }
            session.onSubmit().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private record PromptSession(Consumer<String> onSubmit, Runnable onCancel) {
    }
}
