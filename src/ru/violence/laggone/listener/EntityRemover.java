package ru.violence.laggone.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import ru.violence.laggone.LagGonePlugin;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public class EntityRemover implements Listener {
    private final Set<EntityType> entitiesToRemove = EnumSet.noneOf(EntityType.class);

    public EntityRemover(LagGonePlugin plugin) {
        if (!plugin.getConfig().getBoolean("entity-remover.enabled")) return;

        for (String entityTypeS : plugin.getConfig().getStringList("entity-remover.types")) {
            try {
                EntityType entityType = EntityType.valueOf(entityTypeS.toUpperCase(Locale.ROOT));
                this.entitiesToRemove.add(entityType);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("[Prevent Entity Portal] Unknown entity type: " + entityTypeS);
            }
        }

        if (!this.entitiesToRemove.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (this.entitiesToRemove.contains(entity.getType())) {
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (this.entitiesToRemove.contains(entity.getType())) {
                entity.remove();
            }
        }
    }
}
