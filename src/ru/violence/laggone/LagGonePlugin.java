package ru.violence.laggone;

import org.bukkit.plugin.java.JavaPlugin;
import ru.violence.laggone.listener.EmptyChunkSectionDeleter;
import ru.violence.laggone.listener.EntitySectionLimiter;
import ru.violence.laggone.listener.FaweTabCompleteCrash;
import ru.violence.laggone.listener.GlobalRedstoneLimiter;
import ru.violence.laggone.listener.PreventEntityPortal;

public class LagGonePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        new EntitySectionLimiter(this);
        new GlobalRedstoneLimiter(this);
        new EmptyChunkSectionDeleter(this);
        new PreventEntityPortal(this);
        new FaweTabCompleteCrash(this);
    }
}
