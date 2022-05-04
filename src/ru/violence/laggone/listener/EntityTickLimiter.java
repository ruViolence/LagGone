package ru.violence.laggone.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.violence.laggone.LagGonePlugin;
import x.reaper.event.entity.EntityTickEvent;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public class EntityTickLimiter implements Listener {
    private final int skipPeriod;
    private final Set<EntityType> entityTypes = EnumSet.noneOf(EntityType.class);
    private int currentTick;
    private int nextSkipTime;
    private boolean shouldSkip = false;

    public EntityTickLimiter(LagGonePlugin plugin) {
        int skip = plugin.getConfig().getInt("entity-tick-limiter.skip-period", 1);

        if (skip <= 0) {
            plugin.getLogger().severe("[Entity Tick Limiter] Skip period should more than 0");
            skip = 1;
        }

        this.skipPeriod = skip;

        for (String entityTypeS : plugin.getConfig().getStringList("entity-tick-limiter.entity-types")) {
            try {
                EntityType entityType = EntityType.valueOf(entityTypeS.toUpperCase(Locale.ROOT));
                this.entityTypes.add(entityType);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("[Entity Tick Limiter] Unknown entity type: " + entityTypeS);
            }
        }

        if (plugin.getConfig().getBoolean("entity-tick-limiter.enabled") && !this.entityTypes.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                ++currentTick;

                if (nextSkipTime < currentTick) {
                    nextSkipTime = currentTick + skipPeriod;
                }
                
                shouldSkip = nextSkipTime == currentTick;
            }, 0, 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTick(EntityTickEvent event) {
        if (shouldSkip && entityTypes.contains(event.getEntityType())) {
            event.setCancelled(true);
        }
    }
}

