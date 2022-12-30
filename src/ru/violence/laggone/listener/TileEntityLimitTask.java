package ru.violence.laggone.listener;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.scheduler.BukkitRunnable;
import ru.violence.laggone.LagGonePlugin;

public class TileEntityLimitTask extends BukkitRunnable {
    private final LagGonePlugin plugin;
    private final boolean notifyBlockRemove;
    private final int limit;

    public TileEntityLimitTask(LagGonePlugin plugin) {
        this.plugin = plugin;
        this.notifyBlockRemove = plugin.getConfig().getBoolean("tile-entity-limiter.notify-block-remove");
        this.limit = plugin.getConfig().getInt("tile-entity-limiter.limit");
        if (plugin.getConfig().getBoolean("tile-entity-limiter.enabled")) {
            runTaskTimer(plugin, 0, plugin.getConfig().getLong("tile-entity-limiter.period"));
        }
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                BlockState[] tileEntities = chunk.getTileEntities();
                if (tileEntities.length <= limit) continue;

                int toDelete = tileEntities.length - limit;

                for (int i = 0; i < toDelete; i++) {
                    BlockState tileEntity = tileEntities[i];
                    tileEntity.getBlock().setTypeIdAndData(0, (byte) 0, false, notifyBlockRemove, true);
                }

                plugin.getLogger().warning("Deleted " + toDelete + " tile entities from chunk " + world.getName() + " " + chunk.getX() + " " + chunk.getZ());
            }
        }
    }
}
