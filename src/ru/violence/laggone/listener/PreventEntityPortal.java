package ru.violence.laggone.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import ru.violence.laggone.LagGonePlugin;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public class PreventEntityPortal implements Listener {
    private final Set<EntityType> preventedEntities = EnumSet.noneOf(EntityType.class);

    public PreventEntityPortal(LagGonePlugin plugin) {
        for (String entityTypeS : plugin.getConfig().getStringList("prevent-entity-portal")) {
            try {
                EntityType entityType = EntityType.valueOf(entityTypeS.toUpperCase(Locale.ROOT));
                this.preventedEntities.add(entityType);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("[Prevent Entity Portal] Unknown entity type: " + entityTypeS);
            }
        }
        if (!this.preventedEntities.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        EntityType type = event.getEntity().getType();
        if (this.preventedEntities.contains(type)) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }
}
