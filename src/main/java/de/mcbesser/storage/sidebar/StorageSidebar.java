package de.mcbesser.storage.sidebar;

import de.mcbesser.storage.Storage;
import de.mcbesser.storage.managers.RecipeManager;
import de.mcbesser.storage.models.PlayerLager;
import de.mcbesser.storage.models.ShulkerSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class StorageSidebar {
    private static final String OBJECTIVE_NAME = "storage_lager";
    private static final int QUICK_ROW_SIZE = 9;
    private static final int TOTAL_LINES = QUICK_ROW_SIZE + 6;
    private static final TextColor TITLE_ORANGE = TextColor.color(0xFFAA00);
    private static final NamedTextColor INFO_GREEN = NamedTextColor.GREEN;

    private final Storage plugin;
    private final Map<UUID, BoardState> activeBoards = new HashMap<>();
    private final Set<UUID> pendingRefreshes = new HashSet<>();
    private BukkitTask refreshTask;

    public StorageSidebar(Storage plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 10L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        clearAll();
    }

    public void refresh(Player player) {
        HeldStorage heldStorage = resolveActiveStorage(player);
        if (heldStorage == null) {
            clear(player);
            return;
        }

        BoardState boardState = resolveBoardState(player);
        Scoreboard scoreboard = boardState.scoreboard();
        if (boardState.ownedScoreboard() && player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }

        List<RenderedLine> nextLines = buildLines(player, heldStorage);
        List<String> renderedKeys = boardState.renderedKeys();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (boardState.initialized()
                && objective != null
                && objective.getDisplaySlot() == DisplaySlot.SIDEBAR
                && hasSameKeys(renderedKeys, nextLines)) {
            return;
        }

        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    OBJECTIVE_NAME,
                    Criteria.DUMMY,
                    buildTitle(heldStorage)
            );
        }
        if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(buildTitle(heldStorage));

        for (int lineIndex = 0; lineIndex < nextLines.size(); lineIndex++) {
            RenderedLine line = nextLines.get(lineIndex);
            String entry = uniqueEntry(lineIndex);
            Team team = getOrCreateTeam(scoreboard, "line" + lineIndex, entry);
            if (!line.key().equals(renderedKeys.get(lineIndex))) {
                team.prefix(line.component());
                team.suffix(Component.empty());
                renderedKeys.set(lineIndex, line.key());
            }
            objective.getScore(entry).setScore(nextLines.size() - lineIndex);
        }

        for (int lineIndex = nextLines.size(); lineIndex < TOTAL_LINES; lineIndex++) {
            String entry = uniqueEntry(lineIndex);
            scoreboard.resetScores(entry);
            Team team = scoreboard.getTeam("line" + lineIndex);
            if (team != null) {
                team.prefix(Component.empty());
                team.suffix(Component.empty());
            }
            renderedKeys.set(lineIndex, null);
        }
        boardState.markInitialized();
    }

    public void scheduleRefresh(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!pendingRefreshes.add(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingRefreshes.remove(playerId);
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    public void clear(Player player) {
        UUID uuid = player.getUniqueId();
        pendingRefreshes.remove(uuid);
        BoardState active = activeBoards.remove(uuid);
        if (active == null) {
            return;
        }
        removeSidebar(active.scoreboard());
        if (active.ownedScoreboard() && player.getScoreboard() == active.scoreboard()) {
            Scoreboard previous = active.previousScoreboard();
            if (previous != null) {
                player.setScoreboard(previous);
            }
        }
    }

    public void clearAll() {
        pendingRefreshes.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        activeBoards.clear();
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private HeldStorage resolveActiveStorage(Player player) {
        de.mcbesser.storage.managers.StorageDisplayManager.HoveredDisplayInfo hoveredInfo =
                plugin.getStorageDisplayManager().getHoveredDisplayInfo(player);
        if (hoveredInfo != null) {
            HeldStorage storage = buildHeldStorage(player, hoveredInfo.shulkerId().toString());
            if (storage != null) {
                return storage;
            }
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.hasItemMeta() && hand.getType().name().contains("SHULKER_BOX")) {
            ItemMeta meta = hand.getItemMeta();
            if (meta != null) {
                String idValue = meta.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
                HeldStorage storage = buildHeldStorage(player, idValue);
                if (storage != null) {
                    return storage;
                }
            }
        }

        int blockInteractionRange = (int) Math.ceil(plugin.getStorageDisplayManager().getEntityInteractionRange(player));
        Block targetBlock = player.getTargetBlockExact(blockInteractionRange);
        if (targetBlock == null || !(targetBlock.getState() instanceof ShulkerBox shulker)) {
            return null;
        }

        String idValue = shulker.getPersistentDataContainer().get(RecipeManager.SHULKER_KEY, PersistentDataType.STRING);
        return buildHeldStorage(player, idValue);
    }

    private HeldStorage buildHeldStorage(Player player, String idValue) {
        if (idValue == null || idValue.isBlank()) {
            return null;
        }

        UUID shulkerId;
        try {
            shulkerId = UUID.fromString(idValue);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        ShulkerSettings settings = plugin.getLagerManager().getShulkerSettings(shulkerId);
        UUID ownerUuid = resolveOwnerUuid(settings, player);
        PlayerLager lager = plugin.getLagerManager().getLager(ownerUuid);
        return new HeldStorage(shulkerId, ownerUuid, settings, lager);
    }

    private UUID resolveOwnerUuid(ShulkerSettings settings, Player fallbackPlayer) {
        String ownerUuid = settings.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            try {
                return UUID.fromString(ownerUuid);
            } catch (IllegalArgumentException ignored) {
                // Fallback below.
            }
        }
        return fallbackPlayer.getUniqueId();
    }

    private List<RenderedLine> buildLines(Player player, HeldStorage heldStorage) {
        de.mcbesser.storage.managers.StorageDisplayManager.HoveredDisplayInfo hoveredInfo =
                plugin.getStorageDisplayManager().getHoveredDisplayInfo(player);
        if (hoveredInfo != null && hoveredInfo.shulkerId().equals(heldStorage.shulkerId())) {
            return buildHoverLines(hoveredInfo);
        }

        List<RenderedLine> lines = new ArrayList<>(TOTAL_LINES);
        int freeCapacity = Math.max(0, heldStorage.lager().getCapacity() - heldStorage.lager().getUsedAmount());
        int freeSlots = Math.max(0, heldStorage.lager().getUnlockedSlots() - heldStorage.lager().getItems().size());

        lines.add(new RenderedLine(
                "header_spacer",
                Component.text("  ")
        ));
        lines.add(new RenderedLine(
                "free_capacity:" + freeCapacity,
                Component.text("Freier Platz: ", NamedTextColor.YELLOW)
                        .append(Component.text(freeCapacity, NamedTextColor.WHITE))
        ));
        lines.add(new RenderedLine(
                "free_slots:" + freeSlots,
                Component.text("Freie Slots: ", NamedTextColor.YELLOW)
                        .append(Component.text(freeSlots, NamedTextColor.WHITE))
        ));
        lines.add(new RenderedLine(
                "header_info_spacer",
                Component.text("   ")
        ));

        for (int index = 0; index < QUICK_ROW_SIZE; index++) {
            int inventorySlot = 9 + index;
            int displaySlot = index + 1;
            String materialName = heldStorage.settings().getQuickSlots().get(inventorySlot);
            Material material = materialName != null ? Material.matchMaterial(materialName) : null;
            if (material == null) {
                continue;
            }

            int amount = heldStorage.lager().getAmountByMaterial(material);
            lines.add(new RenderedLine(
                "slot:" + displaySlot + ":" + material.name() + ":" + amount,
                    Component.text(displaySlot + ". ", NamedTextColor.GOLD)
                            .append(Component.translatable(material.translationKey()).color(NamedTextColor.GREEN))
                            .append(Component.text(" " + amount, NamedTextColor.WHITE))
            ));
        }

        lines.add(new RenderedLine(
                "spacer",
                Component.text(" ")
        ));
        lines.add(new RenderedLine(
                buildStatusKey(heldStorage.settings()),
                buildStatusLine(heldStorage.settings())
        ));
        return lines;
    }

    private List<RenderedLine> buildHoverLines(
            de.mcbesser.storage.managers.StorageDisplayManager.HoveredDisplayInfo hoveredInfo) {
        int row = hoveredInfo.displaySlot() / QUICK_ROW_SIZE;
        if (row == 0 || row == 5) {
            return buildStructuredHoverLines(hoveredInfo, row == 5);
        }

        List<RenderedLine> lines = new ArrayList<>(TOTAL_LINES);
        String title = hoveredInfo.plainTitle();
        if (title == null || title.isBlank()) {
            title = "Slot";
        }

        lines.add(new RenderedLine("hover_spacer_top", Component.text("  ")));
        int hoverIndex = 0;
        for (String wrappedTitle : wrapText(title, 28, 2)) {
            lines.add(new RenderedLine("hover_title:" + hoverIndex + ":" + wrappedTitle,
                    Component.text(wrappedTitle, NamedTextColor.GOLD)));
            hoverIndex++;
        }
        lines.add(new RenderedLine("hover_spacer_mid", Component.text("   ")));

        List<String> loreLines = hoveredInfo.plainLoreLines(8);
        if (loreLines.isEmpty()) {
            lines.add(new RenderedLine("hover_empty", Component.text("Keine Beschreibung", NamedTextColor.GRAY)));
        } else {
            int loreIndex = 0;
            for (String loreLine : loreLines) {
                List<String> wrappedLines = wrapText(loreLine, 28, 3);
                for (int wrappedIndex = 0; wrappedIndex < wrappedLines.size(); wrappedIndex++) {
                    String wrappedLore = wrappedLines.get(wrappedIndex);
                    lines.add(new RenderedLine("hover_lore:" + loreIndex + ":" + wrappedIndex + ":" + wrappedLore,
                            renderHoverLoreLine(loreLine, wrappedLore, wrappedIndex == 0)));
                    loreIndex++;
                    if (lines.size() >= TOTAL_LINES) {
                        return lines;
                    }
                }
            }
        }
        return lines;
    }

    private List<RenderedLine> buildStructuredHoverLines(
            de.mcbesser.storage.managers.StorageDisplayManager.HoveredDisplayInfo hoveredInfo,
            boolean statefulTitle) {
        List<RenderedLine> lines = new ArrayList<>(TOTAL_LINES);
        String title = hoveredInfo.plainTitle();
        if (title == null || title.isBlank()) {
            title = "Slot";
        }

        lines.add(new RenderedLine("hover_spacer_top", Component.text("  ")));
        addStructuredTitleLines(lines, title, statefulTitle);

        List<String> descriptionLines = new ArrayList<>();
        List<String> actionLines = new ArrayList<>();
        for (String loreLine : hoveredInfo.plainLoreLines(8)) {
            if (loreLine == null || loreLine.isBlank()) {
                continue;
            }
            if (loreLine.startsWith("Funktion:")) {
                descriptionLines.add(loreLine.substring("Funktion:".length()).trim());
            } else {
                actionLines.add(loreLine);
            }
        }

        int lineIndex = lines.size();
        for (String descriptionLine : descriptionLines) {
            for (String wrappedLine : wrapText(descriptionLine, 28, 3)) {
                lines.add(new RenderedLine("hover_desc:" + lineIndex + ":" + wrappedLine,
                        Component.text(wrappedLine, NamedTextColor.WHITE)));
                lineIndex++;
                if (lines.size() >= TOTAL_LINES) {
                    return lines;
                }
            }
        }

        if (!descriptionLines.isEmpty() && !actionLines.isEmpty() && lines.size() < TOTAL_LINES) {
            lines.add(new RenderedLine("hover_gap_after_desc", Component.text(" ")));
        }

        int actionIndex = 0;
        for (String actionLine : actionLines) {
            List<String> wrappedLines = wrapText(actionLine, 28, 3);
            for (int wrappedIndex = 0; wrappedIndex < wrappedLines.size(); wrappedIndex++) {
                String wrappedLine = wrappedLines.get(wrappedIndex);
                lines.add(new RenderedLine("hover_action:" + actionIndex + ":" + wrappedIndex + ":" + wrappedLine,
                        renderHoverLoreLine(actionLine, wrappedLine, wrappedIndex == 0)));
                actionIndex++;
                if (lines.size() >= TOTAL_LINES) {
                    return lines;
                }
            }
        }

        return lines;
    }

    private List<String> wrapText(String text, int maxLength, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String remaining = text.trim();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (remaining.length() <= maxLength) {
                lines.add(remaining);
                break;
            }

            int breakIndex = remaining.lastIndexOf(' ', maxLength);
            if (breakIndex <= 0) {
                breakIndex = maxLength;
            }

            String line = remaining.substring(0, breakIndex).trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
            remaining = remaining.substring(Math.min(breakIndex + 1, remaining.length())).trim();
        }
        return lines;
    }

    private Component renderHoverLoreLine(String originalLine, String wrappedLine, boolean firstWrappedLine) {
        int separatorIndex = originalLine.indexOf(':');
        if (separatorIndex > 0 && firstWrappedLine) {
            String prefix = originalLine.substring(0, separatorIndex + 1);
            if (wrappedLine.startsWith(prefix)) {
                String value = wrappedLine.substring(prefix.length()).trim();
                NamedTextColor valueColor = NamedTextColor.WHITE;
                if (isValueLine(prefix)) {
                    valueColor = INFO_GREEN;
                } else if (isEnabledState(value)) {
                    valueColor = NamedTextColor.GREEN;
                } else if (isDisabledState(value)) {
                    valueColor = NamedTextColor.RED;
                }
                return Component.text(prefix + " ", NamedTextColor.YELLOW)
                        .append(Component.text(value, valueColor));
            }
        }
        return Component.text(wrappedLine, NamedTextColor.WHITE);
    }

    private void addStructuredTitleLines(List<RenderedLine> lines, String title, boolean statefulTitle) {
        if (!statefulTitle) {
            int titleIndex = 0;
            for (String wrappedTitle : wrapText(title, 28, 2)) {
                lines.add(new RenderedLine("hover_title:" + titleIndex + ":" + wrappedTitle,
                        Component.text(wrappedTitle, TITLE_ORANGE)));
                titleIndex++;
            }
            return;
        }

        String state = null;
        String baseTitle = title;
        int separatorIndex = title.lastIndexOf(':');
        if (separatorIndex >= 0) {
            String possibleState = title.substring(separatorIndex + 1).trim();
            if (isEnabledState(possibleState) || isDisabledState(possibleState)) {
                state = possibleState;
                baseTitle = title.substring(0, separatorIndex + 1).trim();
            }
        }

        if (state != null && baseTitle.length() + 1 + state.length() <= 28) {
            lines.add(new RenderedLine("hover_title:0:" + baseTitle + ":" + state,
                    Component.text(baseTitle + " ", TITLE_ORANGE)
                            .append(Component.text(state, isEnabledState(state) ? NamedTextColor.GREEN : NamedTextColor.RED))));
            return;
        }

        int titleIndex = 0;
        for (String wrappedTitle : wrapText(baseTitle, 28, 2)) {
            lines.add(new RenderedLine("hover_title:" + titleIndex + ":" + wrappedTitle,
                    Component.text(wrappedTitle, TITLE_ORANGE)));
            titleIndex++;
        }
        if (state != null && lines.size() < TOTAL_LINES) {
            lines.add(new RenderedLine("hover_title_state:" + state,
                    Component.text(state, isEnabledState(state) ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
    }

    private boolean isValueLine(String prefix) {
        return "Menge:".equals(prefix)
                || "Gespeichert:".equals(prefix)
                || "Freier Platz:".equals(prefix)
                || "Freie Slots:".equals(prefix);
    }

    private boolean isEnabledState(String value) {
        return "AN".equals(value);
    }

    private boolean isDisabledState(String value) {
        return "AUS".equals(value);
    }

    private String buildStatusKey(ShulkerSettings settings) {
        return "status:"
                + settings.isVacuumEnabled()
                + ":"
                + settings.isAutoStore()
                + ":"
                + settings.isVacuumFilterEnabled()
                + ":"
                + settings.isShulkerRefillEnabled()
                + ":"
                + buildFilterModeShort(settings)
                + ":"
                + buildRangeModeShort(settings);
    }

    private Component buildStatusLine(ShulkerSettings settings) {
        return Component.text()
                .append(Component.text("V", NamedTextColor.GOLD))
                .append(stateDot(settings.isVacuumEnabled()))
                .append(Component.text("F", NamedTextColor.GOLD))
                .append(stateDot(settings.isVacuumFilterEnabled()))
                .append(Component.text(":"))
                .append(Component.text(buildFilterModeShort(settings), NamedTextColor.YELLOW))
                .append(Component.text(" "))
                .append(Component.text("M:", NamedTextColor.GOLD))
                .append(Component.text(buildRangeModeShort(settings), NamedTextColor.YELLOW))
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(Component.text("|", NamedTextColor.GRAY))
                .append(Component.text(" "))
                .append(Component.text("\u2b07", NamedTextColor.GOLD))
                .append(stateDot(settings.isAutoStore()))
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(Component.text("|", NamedTextColor.GRAY))
                .append(Component.text(" "))
                .append(Component.text("R", NamedTextColor.GOLD))
                .append(stateDot(settings.isShulkerRefillEnabled()))
                .build();
    }

    private Component stateDot(boolean enabled) {
        return Component.text("\u25cf", enabled ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private String buildFilterModeShort(ShulkerSettings settings) {
        return settings.isVacuumFilterEnabled() ? "ON" : "OFF";
    }

    private String buildRangeModeShort(ShulkerSettings settings) {
        String mode = settings.getVacuumRangeMode();
        if (mode == null) {
            return "-";
        }
        return switch (mode) {
            case "CHUNK_3X3" -> "3x3";
            case "MANUAL" -> "MAN";
            default -> "1x1";
        };
    }

    private Component buildTitle(HeldStorage heldStorage) {
        String ownerName = heldStorage.settings().getOwnerName();
        if (ownerName == null || ownerName.isBlank()) {
            ownerName = "Unbekannt";
        }
        return Component.text("Lager: " + ownerName, TITLE_ORANGE, TextDecoration.BOLD);
    }

    private boolean hasSameKeys(List<String> renderedKeys, List<RenderedLine> nextLines) {
        if (nextLines.size() > renderedKeys.size()) {
            return false;
        }
        for (int i = 0; i < nextLines.size(); i++) {
            if (!nextLines.get(i).key().equals(renderedKeys.get(i))) {
                return false;
            }
        }
        for (int i = nextLines.size(); i < renderedKeys.size(); i++) {
            if (renderedKeys.get(i) != null) {
                return false;
            }
        }
        return true;
    }

    private void removeSidebar(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return;
        }
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        for (int lineIndex = 0; lineIndex < TOTAL_LINES; lineIndex++) {
            Team team = scoreboard.getTeam("line" + lineIndex);
            if (team != null) {
                team.unregister();
            }
        }
    }

    private BoardState resolveBoardState(Player player) {
        UUID uuid = player.getUniqueId();
        Scoreboard current = player.getScoreboard();
        BoardState existing = activeBoards.get(uuid);
        if (existing != null) {
            if (current != existing.scoreboard()) {
                existing.updatePreviousScoreboard(current);
            }
            return existing;
        }

        BoardState next = new BoardState(current, Bukkit.getScoreboardManager().getNewScoreboard());
        activeBoards.put(uuid, next);
        return next;
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, String name, String entry) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        if (!team.hasEntry(entry)) {
            Set<String> existingEntries = Set.copyOf(team.getEntries());
            for (String existing : existingEntries) {
                team.removeEntry(existing);
            }
            team.addEntry(entry);
        }
        return team;
    }

    private String uniqueEntry(int index) {
        return org.bukkit.ChatColor.values()[index].toString();
    }

    private record HeldStorage(UUID shulkerId, UUID ownerUuid, ShulkerSettings settings, PlayerLager lager) {
    }

    private record RenderedLine(String key, Component component) {
    }

    private static final class BoardState {
        private Scoreboard previousScoreboard;
        private final Scoreboard scoreboard;
        private final List<String> renderedKeys;
        private boolean initialized;

        private BoardState(Scoreboard previousScoreboard, Scoreboard scoreboard) {
            this.previousScoreboard = previousScoreboard;
            this.scoreboard = scoreboard;
            this.renderedKeys = new ArrayList<>(Collections.nCopies(TOTAL_LINES, null));
        }

        private Scoreboard previousScoreboard() {
            return previousScoreboard;
        }

        private void updatePreviousScoreboard(Scoreboard previousScoreboard) {
            if (previousScoreboard != null && previousScoreboard != scoreboard) {
                this.previousScoreboard = previousScoreboard;
            }
        }

        private Scoreboard scoreboard() {
            return scoreboard;
        }

        private boolean ownedScoreboard() {
            return true;
        }

        private List<String> renderedKeys() {
            return renderedKeys;
        }

        private boolean initialized() {
            return initialized;
        }

        private void markInitialized() {
            initialized = true;
        }
    }
}
