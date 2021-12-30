package ru.violence.laggone.listener;

import lombok.Getter;
import lombok.ToString;
import org.bukkit.entity.EntityType;

@ToString
public class CustomLimit {
    private final @Getter EntityType[] entityTypes;
    private final @Getter int limitSection;
    private final @Getter int limitChunk;
    private final @Getter boolean exclusive;
    private int sectionCount = 0;
    private int chunkCount = 0;

    public CustomLimit(EntityType[] entityTypes, boolean exclusive, int limitSection, int limitChunk) {
        this.entityTypes = entityTypes;
        this.exclusive = exclusive;
        this.limitSection = limitSection;
        this.limitChunk = limitChunk;
    }

    public boolean countSectionLimit() {
        if (this.limitSection <= 0) return false;
        return ++this.sectionCount >= this.limitSection;
    }

    public boolean countChunkLimit() {
        if (this.limitChunk <= 0) return false;
        return ++this.chunkCount >= this.limitChunk;
    }

    public void reset() {
        this.sectionCount = 0;
        this.chunkCount = 0;
    }
}
