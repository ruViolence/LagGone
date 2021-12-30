package ru.violence.laggone.util;

import lombok.experimental.UtilityClass;
import org.bukkit.Chunk;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class Utils {
    @SuppressWarnings({"ForLoopReplaceableByForEach", "unchecked"})
    public List<Entity>[] getChunkSectionEntities(Chunk chunk) {
        List<Entity>[] sections = new List[16];
        net.minecraft.server.v1_12_R1.Chunk nmsChunk = ((CraftChunk) chunk).getHandle();

        for (int i = 0; i < 16; ++i) {
            List<net.minecraft.server.v1_12_R1.Entity> slice = nmsChunk.entitySlices[i];
            Object[] array = slice.toArray();
            List<Entity> entities = new ArrayList<>(array.length);

            for (int k = 0; k < array.length; ++k) {
                Object obj = array[k];
                if (obj instanceof net.minecraft.server.v1_12_R1.Entity) {
                    entities.add(((net.minecraft.server.v1_12_R1.Entity) obj).getBukkitEntity());
                }
            }

            sections[i] = entities;
        }

        return sections;
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    public int clamp(int input, int min, int max) {
        if (input < min) {
            return min;
        } else if (input > max) {
            return max;
        } else {
            return input;
        }
    }
}
