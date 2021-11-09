package ru.violence.laggone.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import ru.violence.laggone.LagGonePlugin;

public class GlobalRedstoneLimiter implements Listener {
    private final int limit;
    private final long tickLength;
    private long lastTickTime = System.currentTimeMillis();
    private int counter = 0;

    public GlobalRedstoneLimiter(LagGonePlugin plugin) {
        this.limit = plugin.getConfig().getInt("global-redstone-limiter.limit", 1024);
        this.tickLength = 50 * plugin.getConfig().getLong("global-redstone-limiter.tick-length", 5);
        if (plugin.getConfig().getBoolean("global-redstone-limiter.enabled", true)) {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> this.lastTickTime = System.currentTimeMillis(), 1, 1);
            Bukkit.getScheduler().runTaskTimer(plugin, () -> this.counter = 0, 20, 20);
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (checkLimit()) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (checkLimit()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (checkLimit()) {
            event.setCancelled(true);
        }
    }

    private boolean checkLimit() {
        if (this.counter > this.limit) {
            return true;
        }

        if ((System.currentTimeMillis() - this.lastTickTime) < this.tickLength) {
            return false;
        }

        ++this.counter;
        return false;
    }
}
