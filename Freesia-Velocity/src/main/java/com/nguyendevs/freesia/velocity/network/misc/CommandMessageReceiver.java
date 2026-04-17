package com.nguyendevs.freesia.velocity.network.misc;

import com.nguyendevs.freesia.velocity.Freesia;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class CommandMessageReceiver {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("freesia", "command");

    public CommandMessageReceiver() {
        Freesia.PROXY_SERVER.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;
        
        if (!(event.getSource() instanceof ServerConnection serverConn)) return;
        Player player = serverConn.getPlayer();

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String command = in.readUTF();
            
            Freesia.PROXY_SERVER.getCommandManager().executeAsync(player, command);
        } catch (Exception e) {
            Freesia.LOGGER.warn("[Command] Error reading proxy command payload: " + e.getMessage());
        }
    }
}
