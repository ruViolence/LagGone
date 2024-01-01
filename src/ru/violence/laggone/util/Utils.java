package ru.violence.laggone.util;

import lombok.experimental.UtilityClass;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class Utils {
    public @NotNull Map<Integer, List<Entity>> getChunkSectionEntities(@NotNull Chunk chunk) {
        Map<Integer, List<Entity>> sectionsMap = new HashMap<>();

        for (Entity entity : chunk.getEntities()) {
            int posY = entity.getLocation().getBlockY();

            int sectionIndex = Math.floorDiv(posY, 16);
            sectionsMap.computeIfAbsent(sectionIndex, k -> new ArrayList<>()).add(entity);
        }

        return sectionsMap;
    }

    public int floor(double var0) {
        int var2 = (int) var0;
        return var0 < (double) var2 ? var2 - 1 : var2;
    }
}
