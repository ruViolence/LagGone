package ru.violence.laggone.listener;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.violence.laggone.LagGonePlugin;

/**
 * Prevents server crash by tabbing the command
 * "/to for(i=0;i<256;i++){for(j=0;j<256;j++){for(k=0;k<256;k++){for(l=0;l<256;l++){ln(pi)}}}}"
 * by completely disabling the "/to" command tabbing.
 */
public class FaweTabCompleteCrash implements Listener {
    public FaweTabCompleteCrash(LagGonePlugin plugin) {
        if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAsyncTabToCommand(AsyncTabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer.startsWith("/to ") || buffer.startsWith("//to ")) {
            event.setCancelled(true);
            event.setHandled(true);
        }
    }
}
