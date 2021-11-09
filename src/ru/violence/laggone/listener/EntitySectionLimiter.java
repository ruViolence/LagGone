package ru.violence.laggone.listener;

import com.google.common.collect.Maps;
import net.minecraft.server.v1_12_R1.MathHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import ru.violence.laggone.LagGonePlugin;
import ru.violence.laggone.util.Utils;
import x.reaper.event.entity.AnyEntitySpawnEvent;
import x.reaper.event.entity.EntityMoveEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EntitySectionLimiter implements Listener {
    private final int limitPerSection;
    private final boolean ignoreNamed;
    private final boolean ignoreTamed;
    private final Set<EntityType> ignoredEntities = EnumSet.noneOf(EntityType.class);
    private final List<CustomLimit> customLimits = new ArrayList<>();
    private final Map<EntityType, CustomLimit[]> limitsByEntityType = Maps.newEnumMap(EntityType.class);

    public EntitySectionLimiter(LagGonePlugin plugin) {
        ConfigurationSection customLimitsSect = plugin.getConfig().getConfigurationSection("entity-section-limiter.custom-limits");
        for (String key : customLimitsSect.getKeys(false)) {
            try {
                ConfigurationSection keySect = customLimitsSect.getConfigurationSection(key);
                List<EntityType> entityTypes = new ArrayList<>();

                if (keySect.isList("entity")) {
                    List<String> entities = keySect.getStringList("entity");
                    for (String entityName : entities) {
                        EntityType entityType = EntityType.valueOf(entityName);
                        if (!entityTypes.contains(entityType)) {
                            entityTypes.add(entityType);
                        }
                    }
                } else {
                    String entityName = keySect.getString("entity");
                    EntityType entityType = EntityType.valueOf(entityName);
                    entityTypes.add(entityType);
                }

                if (entityTypes.isEmpty()) throw new IllegalStateException("Entity types is empty");

                int limitSection = keySect.getInt("limit-section", 0);
                int limitChunk = keySect.getInt("limit-chunk", 0);

                if (limitSection <= 0 && limitChunk <= 0) {
                    throw new IllegalStateException("Limits are not set for " + key);
                }

                if (limitSection < 0) limitSection = 0;
                if (limitChunk < 0) limitChunk = 0;

                this.customLimits.add(new CustomLimit(entityTypes.toArray(new EntityType[0]), limitSection, limitChunk));
            } catch (Exception e) {
                plugin.getLogger().severe("Failed while loading custom limit named " + key);
                e.printStackTrace();
            }
        }

        this.limitPerSection = plugin.getConfig().getInt("entity-section-limiter.limit-per-section", 128);
        this.ignoreNamed = plugin.getConfig().getBoolean("entity-section-limiter.ignored-named", true);
        this.ignoreTamed = plugin.getConfig().getBoolean("entity-section-limiter.ignored-tamed", true);

        for (String entityTypeS : plugin.getConfig().getStringList("entity-section-limiter.ignored-entity")) {
            try {
                EntityType entityType = EntityType.valueOf(entityTypeS.toUpperCase(Locale.ROOT));
                this.ignoredEntities.add(entityType);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("[Entity Section Limiter] Unknown entity type: " + entityTypeS);
            }
        }

        Map<EntityType, List<CustomLimit>> map = new HashMap<>();
        for (CustomLimit limit : this.customLimits) {
            for (EntityType entityType : limit.getEntityTypes()) {
                List<CustomLimit> limits = map.computeIfAbsent(entityType, t -> new ArrayList<>());
                limits.add(limit);
            }
        }

        for (Map.Entry<EntityType, List<CustomLimit>> entry : map.entrySet()) {
            this.limitsByEntityType.put(entry.getKey(), entry.getValue().toArray(new CustomLimit[0]));
        }
        for (EntityType entityType : EntityType.values()) {
            this.limitsByEntityType.computeIfAbsent(entityType, type -> new CustomLimit[0]);
        }

        if (this.limitPerSection > 0 || !this.customLimits.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCreatureSpawnEvent(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (isSensitiveEntity(event.getEntity())) return;

        if (isShouldBeCancelled(event.getEntity(), event.getLocation())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (isSensitiveEntity(event.getEntity())) return;

        if (isShouldBeCancelled(event.getEntity(), event.getLocation())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (isSensitiveEntity(event.getVehicle())) return;

        if (isShouldBeCancelled(event.getVehicle())) {
            event.setCancelled(true);
            event.getVehicle().remove();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAnyEntitySpawn(AnyEntitySpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (isSensitiveEntity(event.getEntity())) return;

        if (isShouldBeCancelled(event.getEntity(), event.getLocation())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (isSensitiveEntity(event.getEntity())) return;

        if (isShouldBeCancelled(event.getEntity(), event.getTo())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityPortal(EntityPortalEvent event) {
        if (isSensitiveEntity(event.getEntity())) return;

        Location location = event.getTo();
        if (location == null) return; // Ignore null exit

        if (isShouldBeCancelled(event.getEntity(), location)) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    private boolean isShouldBeCancelled(Entity entity) {
        return isShouldBeCancelled(entity, entity.getLocation());
    }

    private boolean isShouldBeCancelled(Entity entity, Location location) {
        int newChunkX = MathHelper.floor(location.getX() / 16);
        int newChunkY = MathHelper.floor(location.getY() / 16);
        int newChunkZ = MathHelper.floor(location.getZ() / 16);

        return isLimited(entity, location.getWorld(), newChunkX, newChunkY, newChunkZ);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityMove(EntityMoveEvent event) {
        if (!event.isPerBlockMove()) return;
        if (isSensitiveEntity(event.getEntity())) return;

        // Skip if the entity is outside of world
        if (event.getNewY() < 0 || 256 <= event.getNewY()) return;

        int oldChunkX = MathHelper.floor(event.getOldX() / 16);
        int oldChunkY = MathHelper.floor(event.getOldY() / 16);
        int oldChunkZ = MathHelper.floor(event.getOldZ() / 16);
        int newChunkX = MathHelper.floor(event.getNewX() / 16);
        int newChunkY = MathHelper.floor(event.getNewY() / 16);
        int newChunkZ = MathHelper.floor(event.getNewZ() / 16);

        // Skip if it's not a per chunk section move
        if (oldChunkX == newChunkX && oldChunkY == newChunkY && oldChunkZ == newChunkZ) return;

        if (isLimited(event.getEntity(), event.getWorld(), newChunkX, newChunkY, newChunkZ)) {
            event.getEntity().remove();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        List<Entity>[] sectionEntities = Utils.getChunkSectionEntities(event.getChunk());

        // TODO: Count the custom limits

        // Check sections and delete overload section entities
        for (List<Entity> slice : sectionEntities) {
            if (slice.size() < this.limitPerSection) continue;

            final int toRemove = slice.size() - this.limitPerSection;
            int removed = 0;

            for (Entity entity : slice) {
                if (isSensitiveEntity(entity)) continue;

                entity.remove();
                if (++removed >= toRemove) {
                    break;
                }
            }
        }
    }

    private boolean isLimited(Entity entity, World world, int chunkX, int chunkY, int chunkZ) {
        List<Entity> sectionEntities = Utils.getChunkSectionEntities(world.getChunkAt(chunkX, chunkZ), chunkY);

        // Full limit
        if (sectionEntities.stream().filter(ent -> !isSensitiveEntity(ent)).count() > this.limitPerSection) {
            return true;
        }

        resetCustomLimits();

        // Count checking entity
        for (CustomLimit limit : getCustomLimit(entity.getType())) {
            if (limit.countSectionLimit()) return true;
        }

        // Count other slice entities
        for (Entity ent : sectionEntities) {
            // Skip checking entity
            if (ent == entity) continue;

            for (CustomLimit limit : getCustomLimit(ent.getType())) {
                if (limit.countSectionLimit()) return true;
            }
        }

        // Count chunks for checking entity
        for (CustomLimit limit : getCustomLimit(entity.getType())) {
            if (limit.countChunkLimit()) return true;
        }

        // Count other chunk entities
        for (Entity ent : world.getChunkAt(chunkX, chunkZ).getEntities()) {
            // Skip checking entity
            if (ent == entity) continue;

            for (CustomLimit limit : getCustomLimit(ent.getType())) {
                if (limit.countChunkLimit()) return true;
            }
        }

        return false;
    }

    private CustomLimit[] getCustomLimit(EntityType type) {
        return this.limitsByEntityType.get(type);
    }

    private void resetCustomLimits() {
        for (CustomLimit limit : this.customLimits) limit.reset();
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isSensitiveEntity(Entity entity) {
        EntityType type = entity.getType();

        switch (type) {
            case PLAYER: // Skip players

            case ENDER_DRAGON: // Skip bosses
            case COMPLEX_PART:
            case ELDER_GUARDIAN:

            case ARROW: // Skip arrows
            case TIPPED_ARROW:
            case SPECTRAL_ARROW:

            case EGG: // Skip throwable
            case SNOWBALL:
            case ENDER_PEARL:
            case ENDER_SIGNAL:
            case SPLASH_POTION:
            case LINGERING_POTION:
            case THROWN_EXP_BOTTLE:
            case FIREBALL:
            case SMALL_FIREBALL:
            case FIREWORK:
            case WITHER_SKULL:

            case DROPPED_ITEM: // Skip other important non-living entities
            case EXPERIENCE_ORB:
            case EVOKER_FANGS:
            case LLAMA_SPIT:
            case LIGHTNING:
            case WEATHER:
            case FISHING_HOOK:
            case LEASH_HITCH:
                return true;
        }

        if (this.ignoredEntities.contains(type)) return true;
        if (this.ignoreNamed && entity.getCustomName() != null) return true;
        if (this.ignoreTamed && entity instanceof Tameable && ((Tameable) entity).isTamed()) return true;

        return false;
    }
}