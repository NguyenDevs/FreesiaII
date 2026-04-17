package com.nguyendevs.freesia.waterfall.network.misc;

import com.nguyendevs.freesia.waterfall.Freesia;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class CommandMessageReceiver implements Listener {

    public static final String CHANNEL = "freesia:command";

    public CommandMessageReceiver() {
        Freesia.PROXY_SERVER.registerChannel(CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(CHANNEL)) return;
        
        if (!(event.getSender() instanceof Server)) return;
        if (!(event.getReceiver() instanceof ProxiedPlayer player)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String command = in.readUTF();
            
            Freesia.PROXY_SERVER.getPluginManager().dispatchCommand(player, command);
        } catch (Exception e) {
            Freesia.LOGGER.warning("[Command] Error reading proxy command payload: " + e.getMessage());
        }
    }
}
