package ru.violence.laggone;

import org.bukkit.plugin.java.JavaPlugin;
import ru.violence.laggone.listener.EntitySectionLimiter;
import ru.violence.laggone.listener.GlobalRedstoneLimiter;
import ru.violence.laggone.listener.PreventEntityPortal;
import ru.violence.laggone.listener.TileEntityLimiter;

public class LagGonePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        new EntitySectionLimiter(this);
        new GlobalRedstoneLimiter(this);
        new TileEntityLimiter(this);
        new PreventEntityPortal(this);
    }
}
