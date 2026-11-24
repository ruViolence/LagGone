package ru.violence.laggone.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.violence.laggone.LagGonePlugin;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

public class ItemDropRateLimiter implements Listener {

    private final LagGonePlugin plugin;
    private final Map<UUID, PlayerDropData> playerDrops = new HashMap<>();

    // Настройки лимитов
    private static final int NORMAL_LIMIT_PER_SECOND = 3;
    private static final int ESCALATED_LIMIT_PER_SECOND = 1;
    private static final int ESCALATION_THRESHOLD = 5;
    private static final long ESCALATION_DURATION_MS = 10000; // 10 секунд escalated режима
    private static final long TIME_WINDOW_MS = 1000; // 1 секунда

    public ItemDropRateLimiter(LagGonePlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getConfig().getBoolean("item-drop-rate-limiter.enabled")) return;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Bypass permission
        if (player.hasPermission("laggone.bypass.itemdrop")) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        PlayerDropData dropData = playerDrops.computeIfAbsent(playerId, k -> new PlayerDropData());

        // Очищаем старые записи (за пределами временного окна)
        dropData.cleanOldDrops(currentTime);

        // Проверяем, находится ли игрок в escalated режиме
        boolean isEscalated = dropData.isInEscalatedMode(currentTime);
        int currentLimit = isEscalated ? ESCALATED_LIMIT_PER_SECOND : NORMAL_LIMIT_PER_SECOND;

        // Проверяем лимит
        if (dropData.getDropCount() >= currentLimit) {
            event.setCancelled(true);

            // Отправляем сообщение игроку только раз в секунду, чтобы не спамить
            if (currentTime - dropData.lastWarningTime > 1000) {
                dropData.lastWarningTime = currentTime;
            }
            return;
        }

        // Добавляем дроп в историю
        dropData.addDrop(currentTime);

        // Проверяем, нужно ли активировать escalated режим
        if (!isEscalated && dropData.getDropCount() >= ESCALATION_THRESHOLD) {
            dropData.activateEscalatedMode(currentTime);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Очищаем данные игрока при выходе
        playerDrops.remove(event.getPlayer().getUniqueId());
    }

    private static class PlayerDropData {
        private final LinkedList<Long> dropTimes = new LinkedList<>();
        private long escalatedModeStart = 0;
        private long lastWarningTime = 0;

        public void addDrop(long time) {
            dropTimes.addLast(time);
        }

        public void cleanOldDrops(long currentTime) {
            dropTimes.removeIf(time -> currentTime - time > TIME_WINDOW_MS);
        }

        public int getDropCount() {
            return dropTimes.size();
        }

        public boolean isInEscalatedMode(long currentTime) {
            if (escalatedModeStart == 0) {
                return false;
            }

            // Проверяем, не истекло ли время escalated режима
            if (currentTime - escalatedModeStart > ESCALATION_DURATION_MS) {
                escalatedModeStart = 0;
                return false;
            }

            return true;
        }

        public void activateEscalatedMode(long currentTime) {
            escalatedModeStart = currentTime;
        }
    }
}
