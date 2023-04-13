package ru.violence.laggone.listener;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.violence.laggone.LagGonePlugin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TileEntityLimiter implements Listener {
    private final LagGonePlugin plugin;
    private final boolean notifyBlockRemove;
    private final int limit;

    public TileEntityLimiter(LagGonePlugin plugin) {
        this.plugin = plugin;
        this.notifyBlockRemove = plugin.getConfig().getBoolean("tile-entity-limiter.notify-block-remove");
        this.limit = plugin.getConfig().getInt("tile-entity-limiter.limit");
        if (plugin.getConfig().getBoolean("tile-entity-limiter.enabled")) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            new TileEntityLimitTask(plugin);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChunkSave(ChunkUnloadEvent event) {
        if (event.isSaveChunk()) {
            processChunk(event.getChunk());
        }
    }

    private void processChunk(Chunk chunk) {
        BlockState[] tileEntities = chunk.getTileEntities();
        if (tileEntities.length <= limit) return;

        int toDelete = tileEntities.length - limit;

        for (int i = 0; i < toDelete; i++) {
            BlockState tileEntity = tileEntities[i];
            tileEntity.getBlock().setTypeIdAndData(0, (byte) 0, false, notifyBlockRemove, true);
        }

        plugin.getLogger().warning("Deleted " + toDelete + " tile entities from chunk " + chunk.getWorld().getName() + " " + chunk.getX() + " " + chunk.getZ());
    }

    public class TileEntityLimitTask extends BukkitRunnable {
        private boolean processing = false;

        TileEntityLimitTask(LagGonePlugin plugin) {
            long period = plugin.getConfig().getLong("tile-entity-limiter.period");
            if (period > 0) {
                runTaskTimer(plugin, 0, period);
            }
        }

        @Override
        public void run() {
            if (processing) return;
            processing = true;

            List<Chunk> chunks = Bukkit.getWorlds()
                    .stream()
                    .flatMap(world -> Arrays.stream(world.getLoadedChunks()))
                    .collect(Collectors.toList());

            new BukkitRunnable() {
                int index = 0;

                @Override
                public void run() {
                    if (index >= chunks.size()) {
                        cancel();
                        processing = false;
                        return;
                    }

                    Chunk chunk = chunks.get(index++);
                    if (chunk.isLoaded()) processChunk(chunk);
                }
            }.runTaskTimer(plugin, 1, 1);
        }
    }
}
