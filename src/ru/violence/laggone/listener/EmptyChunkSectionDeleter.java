package ru.violence.laggone.listener;

import net.minecraft.server.v1_12_R1.Blocks;
import net.minecraft.server.v1_12_R1.ChunkSection;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import ru.violence.laggone.LagGonePlugin;

public class EmptyChunkSectionDeleter implements Listener {
    public EmptyChunkSectionDeleter(LagGonePlugin plugin) {
        if (plugin.getConfig().getBoolean("delete-empty-chunk-sections")) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        for (Chunk chunk : event.getWorld().getLoadedChunks()) {
            processChunk(chunk);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChunkSave(ChunkUnloadEvent event) {
        if (event.isSaveChunk()) {
            processChunk(event.getChunk());
        }
    }

    private void processChunk(Chunk chunk) {
        ChunkSection[] sections = ((CraftChunk) chunk).getHandle().getSections();
        boolean hasSkyLight = ((CraftWorld) chunk.getWorld()).getHandle().worldProvider.m();

        sections_deleting:
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        // Check blocks
                        if (section.getType(x, y, z).getBlock() != Blocks.AIR) continue sections_deleting;
                        // Check lighting
                        int blockLight = section.c(x, y, z);
                        if (blockLight != 0 && blockLight != 15) continue sections_deleting;
                        if (hasSkyLight) {
                            int skyLight = section.b(x, y, z);
                            if (skyLight != 0 && skyLight != 15) continue sections_deleting;
                        }
                    }
                }
            }

            // Delete section
            sections[i] = null;
        }
    }
}
