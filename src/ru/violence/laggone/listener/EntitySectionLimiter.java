package ru.violence.laggone.listener;

import com.google.common.collect.Maps;
import io.papermc.paper.event.entity.EntityMoveEvent;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EntitySectionLimiter implements Listener {
    private final int limitPerSection;
    private final boolean ignoreNamed;
    private final boolean ignoreTamed;
    private final int removeStrategy;
    private final Set<EntityType> ignoredEntities = EnumSet.noneOf(EntityType.class);
    private final List<CustomLimit> customLimits = new ArrayList<>();
    private final Map<EntityType, CustomLimit[]> limitsByEntityType = Maps.newEnumMap(EntityType.class);
    private final CustomLimit[] emptyCustomLimits = new CustomLimit[0];

    // Move check: the entity types the custom limits name, numbered from 0, and each custom limit's types by number
    private final Map<EntityType, Integer> customTypeIndex = Maps.newEnumMap(EntityType.class);
    private final int[][] customLimitTypes;
    // Move check: the chunks entered during the current tick, each counted once in that tick
    private final Map<World, Long2ObjectOpenHashMap<SectionCensus>> censuses = new IdentityHashMap<>();
    private int censusTick = Integer.MIN_VALUE;

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

                boolean exclusive = keySect.getBoolean("exclusive");
                int limitSection = keySect.getInt("limit-section", 0);
                int limitChunk = keySect.getInt("limit-chunk", 0);

                if (limitSection <= 0 && limitChunk <= 0) {
                    throw new IllegalStateException("Limits are not set for " + key);
                }

                if (limitSection < 0) limitSection = 0;
                if (limitChunk < 0) limitChunk = 0;

                this.customLimits.add(new CustomLimit(entityTypes.toArray(new EntityType[0]), exclusive, limitSection, limitChunk));
            } catch (Exception e) {
                plugin.getLogger().severe("Failed while loading custom limit named " + key);
                e.printStackTrace();
            }
        }

        this.limitPerSection = plugin.getConfig().getInt("entity-section-limiter.limit-per-section", 128);
        this.ignoreNamed = plugin.getConfig().getBoolean("entity-section-limiter.ignore-named", true);
        this.ignoreTamed = plugin.getConfig().getBoolean("entity-section-limiter.ignore-tamed", true);
        this.removeStrategy = plugin.getConfig().getInt("entity-section-limiter.remove-strategy", 0);

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

        this.customLimitTypes = new int[this.customLimits.size()][];
        for (int i = 0; i < this.customLimitTypes.length; i++) {
            EntityType[] types = this.customLimits.get(i).getEntityTypes();
            this.customLimitTypes[i] = new int[types.length];
            for (int j = 0; j < types.length; j++) {
                Integer index = this.customTypeIndex.get(types[j]);
                if (index == null) {
                    index = this.customTypeIndex.size();
                    this.customTypeIndex.put(types[j], index);
                }
                this.customLimitTypes[i][j] = index;
            }
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityMove(EntityMoveEvent event) {
        if (!event.hasExplicitlyChangedBlock()) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        int oldChunkX = Utils.floor(from.getX() / 16);
        int oldChunkY = Utils.floor(from.getY() / 16);
        int oldChunkZ = Utils.floor(from.getZ() / 16);
        int newChunkX = Utils.floor(to.getX() / 16);
        int newChunkY = Utils.floor(to.getY() / 16);
        int newChunkZ = Utils.floor(to.getZ() / 16);

        // Skip if it's not a per chunk section move
        if (oldChunkX == newChunkX && oldChunkY == newChunkY && oldChunkZ == newChunkZ) return;

        LivingEntity entity = event.getEntity();
        if (isSensitiveEntity(entity)) return;

        World world = to.getWorld();
        boolean sameChunk = oldChunkX == newChunkX && oldChunkZ == newChunkZ;
        // A chunk scan lists only entities that are alive, so a dying mover is not counted in its new section
        boolean counted = entity.isValid();
        int type = customTypeIndex(entity.getType());
        SectionCensus census = getCensus(world, newChunkX, newChunkZ, entity, sameChunk, oldChunkY);

        if (!isMoveLimited(census, type, newChunkY, counted, sameChunk)) {
            if (counted) {
                census.add(newChunkY, true, type, 1);
                leaveCensus(world, sameChunk ? census : null, oldChunkX, oldChunkY, oldChunkZ, type);
            }
            return;
        }

        event.setCancelled(true);
        if (removeStrategy == 1) {
            event.getEntity().damage(1000);
        } else {
            event.getEntity().remove();
        }

        // The cancelled move puts the entity back where it came from: it stays counted there unless it died or is gone
        if (counted && !entity.isValid()) {
            leaveCensus(world, sameChunk ? census : null, oldChunkX, oldChunkY, oldChunkZ, type);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Map<Integer, List<Entity>> sectionEntities = Utils.getChunkSectionEntities(event.getChunk());

        // TODO: Count the custom limits

        // Check sections and delete overload section entities
        for (List<Entity> slice : sectionEntities.values()) {
            if (!isAboveSectionLimit(slice.size())) continue;

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

    private boolean isShouldBeCancelled(Entity entity) {
        return isShouldBeCancelled(entity, entity.getLocation());
    }

    private boolean isShouldBeCancelled(Entity entity, Location location) {
        int chunkX = Utils.floor(location.getX() / 16);
        int chunkY = Utils.floor(location.getY() / 16);
        int chunkZ = Utils.floor(location.getZ() / 16);

        return isLimited(entity, location.getWorld(), chunkX, chunkY, chunkZ);
    }

    private boolean isLimited(Entity entity, World world, int chunkX, int chunkY, int chunkZ) {
        Map<Integer, List<Entity>> sectionEntities = Utils.getChunkSectionEntities(world.getChunkAt(chunkX, chunkZ));

        // Full limit
        List<Entity> entities = sectionEntities.get(chunkY);
        if (entities != null) {
            int sectionEntitiesCount = 0;
            for (Entity other : entities) {
                if (isSensitiveEntity(other)) continue;
                if (isAboveSectionLimit(++sectionEntitiesCount)) return true;
            }
        }

        resetCustomLimits();

        // Count custom limits
        for (Map.Entry<Integer, List<Entity>> entry : sectionEntities.entrySet()) {
            int si = entry.getKey();
            if (!entry.getValue().isEmpty()) {
                for (Entity other : entry.getValue()) {
                    for (CustomLimit limit : getCustomLimit(other.getType())) {
                        if (limit.isExclusive() && other.getType() != entity.getType()) continue;
                        if (si == chunkY && limit.countSectionLimit()) return true;
                        if (limit.countChunkLimit()) return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * The answer {@link #isLimited} gives for a mover entering a section of the census's chunk. The census holds the
     * mover in the section it came from when that is in the same chunk; the chunk scan would find it in the new
     * section instead, if it is alive ({@code counted}).
     */
    private boolean isMoveLimited(SectionCensus census, int type, int section, boolean counted, boolean sameChunk) {
        if (isAboveSectionLimit(census.limited(section) + (counted ? 1 : 0))) return true;

        for (int i = 0; i < this.customLimitTypes.length; i++) {
            CustomLimit limit = this.customLimits.get(i);
            int[] types = this.customLimitTypes[i];
            boolean ofLimit = contains(types, type);
            int inSection = 0;
            int inChunk = 0;
            if (limit.isExclusive()) {
                // Only entities of the mover's own type count, and only if the limit names that type
                if (!ofLimit) continue;
                inSection = census.typed(type, section);
                inChunk = census.typedInChunk(type);
            } else {
                for (int t : types) {
                    inSection += census.typed(t, section);
                    inChunk += census.typedInChunk(t);
                }
            }
            if (ofLimit && counted) {
                inSection++;
                if (!sameChunk) inChunk++;
            }
            if (limit.getLimitSection() > 0 && inSection >= limit.getLimitSection()) return true;
            if (limit.getLimitChunk() > 0 && inChunk >= limit.getLimitChunk()) return true;
        }

        return false;
    }

    /**
     * The census of a chunk for the current tick, taken now if this is the first move into the chunk in this tick.
     * After that the moves this limiter allows, and the movers it kills or removes, keep it current.
     */
    private SectionCensus getCensus(World world, int chunkX, int chunkZ, Entity mover, boolean moverFromHere, int moverFromSection) {
        int tick = Bukkit.getCurrentTick();
        if (tick != this.censusTick) {
            // Forget the worlds nothing entered during the last tick
            this.censusTick = tick;
            this.censuses.values().removeIf(Map::isEmpty);
            for (Long2ObjectOpenHashMap<SectionCensus> chunks : this.censuses.values()) chunks.clear();
        }

        Long2ObjectOpenHashMap<SectionCensus> chunks = this.censuses.get(world);
        if (chunks == null) {
            chunks = new Long2ObjectOpenHashMap<>();
            this.censuses.put(world, chunks);
        }

        long key = chunkKey(chunkX, chunkZ);
        SectionCensus census = chunks.get(key);
        if (census == null) {
            census = takeCensus(world, chunkX, chunkZ, mover, moverFromHere, moverFromSection);
            chunks.put(key, census);
        }
        return census;
    }

    /**
     * Counts a chunk's entities by section the way {@link #isLimited} lists them, except the mover: it is counted in the
     * section it came from, or not at all if it came from another chunk, so that this census and those taken earlier in
     * the tick, before it moved, hold it alike.
     */
    private SectionCensus takeCensus(World world, int chunkX, int chunkZ, Entity mover, boolean moverFromHere, int moverFromSection) {
        int minSection = Math.floorDiv(world.getMinHeight(), 16);
        int sections = Math.floorDiv(world.getMaxHeight() - 1, 16) - minSection + 1;
        SectionCensus census = new SectionCensus(minSection, sections, this.customTypeIndex.size());

        for (Entity other : world.getChunkAt(chunkX, chunkZ).getEntities()) {
            int section;
            if (other == mover) {
                if (!moverFromHere) continue;
                section = moverFromSection;
            } else {
                section = Math.floorDiv(Location.locToBlock(other.getY()), 16);
            }

            boolean limited = !isSensitiveEntity(other);
            int type = customTypeIndex(other.getType());
            if (limited || type >= 0) census.add(section, limited, type, 1);
        }

        return census;
    }

    // The entity no longer stands in the section it came from
    private void leaveCensus(World world, SectionCensus sameChunkCensus, int chunkX, int section, int chunkZ, int type) {
        SectionCensus census = sameChunkCensus;
        if (census == null) {
            // Taken this tick, if at all: getCensus has already moved the counts to the current tick
            Long2ObjectOpenHashMap<SectionCensus> chunks = this.censuses.get(world);
            if (chunks != null) census = chunks.get(chunkKey(chunkX, chunkZ));
        }
        if (census != null) census.add(section, true, type, -1);
    }

    private int customTypeIndex(EntityType type) {
        Integer index = this.customTypeIndex.get(type);
        return index != null ? index : -1;
    }

    private static boolean contains(int[] array, int value) {
        for (int element : array) {
            if (element == value) return true;
        }
        return false;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    private boolean isAboveSectionLimit(int count) {
        return 0 < this.limitPerSection && this.limitPerSection < count;
    }

    private CustomLimit[] getCustomLimit(EntityType type) {
        return this.limitsByEntityType.getOrDefault(type, emptyCustomLimits);
    }

    private void resetCustomLimits() {
        for (CustomLimit limit : this.customLimits) limit.reset();
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isSensitiveEntity(Entity entity) {
        EntityType type = entity.getType();

        switch (type) {
            case PLAYER: // Skip players

            case BLOCK_DISPLAY: // Skip displays
            case INTERACTION:
            case ITEM_DISPLAY:
            case TEXT_DISPLAY:

            case ENDER_DRAGON: // Skip bosses

            case ARROW: // Skip arrows
            case SPECTRAL_ARROW:

            case EGG: // Skip throwable
            case SNOWBALL:
            case ENDER_PEARL:
            case ENDER_SIGNAL:
            case SPLASH_POTION:
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
            case FISHING_HOOK:
            case LEASH_HITCH:
                return true;
        }

        if (this.ignoredEntities.contains(type)) return true;
        if (this.ignoreNamed && entity.customName() != null) return true;
        if (this.ignoreTamed && entity instanceof Tameable tameable && tameable.isTamed()) return true;

        return false;
    }
}
