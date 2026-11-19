package ru.violence.laggone.listener;

import java.util.Arrays;

/**
 * One chunk's entities counted by section for the move check of {@link EntitySectionLimiter}: the entities the section
 * limit counts (those that are not sensitive), and the entities of each type a custom limit names, by section and in
 * the whole chunk.
 */
final class SectionCensus {
    private int minSection;
    private int[] limited;
    private final int[][] typed;
    private final int[] typedInChunk;

    SectionCensus(int minSection, int sections, int types) {
        this.minSection = minSection;
        this.limited = new int[sections];
        this.typed = new int[types][sections];
        this.typedInChunk = new int[types];
    }

    /**
     * @param type the entity's custom type index, or -1 if no custom limit names its type
     */
    void add(int section, boolean limitedEntity, int type, int delta) {
        int i = section - this.minSection;
        if (i < 0 || i >= this.limited.length) {
            grow(section);
            i = section - this.minSection;
        }
        if (limitedEntity) this.limited[i] += delta;
        if (type >= 0) {
            this.typed[type][i] += delta;
            this.typedInChunk[type] += delta;
        }
    }

    int limited(int section) {
        int i = section - this.minSection;
        return i >= 0 && i < this.limited.length ? this.limited[i] : 0;
    }

    int typed(int type, int section) {
        int i = section - this.minSection;
        return i >= 0 && i < this.limited.length ? this.typed[type][i] : 0;
    }

    int typedInChunk(int type) {
        return this.typedInChunk[type];
    }

    // An entity below or above the world's height
    private void grow(int section) {
        int newMin = Math.min(this.minSection, section);
        int newMax = Math.max(this.minSection + this.limited.length - 1, section);
        int shift = this.minSection - newMin;
        this.limited = shifted(this.limited, newMax - newMin + 1, shift);
        for (int t = 0; t < this.typed.length; t++) {
            this.typed[t] = shifted(this.typed[t], newMax - newMin + 1, shift);
        }
        this.minSection = newMin;
    }

    private static int[] shifted(int[] counts, int size, int shift) {
        int[] grown = new int[size];
        System.arraycopy(counts, 0, grown, shift, counts.length);
        return grown;
    }

    @Override
    public String toString() {
        return "SectionCensus{minSection=" + this.minSection + ", limited=" + Arrays.toString(this.limited) + "}";
    }
}
