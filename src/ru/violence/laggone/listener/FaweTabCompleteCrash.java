package ru.violence.laggone.listener;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.sk89q.bukkit.util.DynamicPluginCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.violence.laggone.LagGonePlugin;

/**
 * Prevents server crash by tabbing the command
 * "/to for(i=0;i<256;i++){for(j=0;j<256;j++){for(k=0;k<256;k++){for(l=0;l<256;l++){ln(pi)}}}}"
 * by completely disabling the WorldEdit commands tabbing.
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
        if (buffer.isEmpty() || buffer.charAt(0) != '/') return;

        int spaceIndex = buffer.indexOf(" ");
        if (spaceIndex == -1) return;

        String commandName = buffer.substring(1, spaceIndex);
        Command command = Bukkit.getCommandMap().getCommand(commandName);

        if (command instanceof DynamicPluginCommand) {
            event.setCancelled(true);
            event.setHandled(true);
        }
    }
}
