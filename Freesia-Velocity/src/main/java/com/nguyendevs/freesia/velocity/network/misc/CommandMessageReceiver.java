package com.nguyendevs.freesia.velocity.network.misc;

import com.nguyendevs.freesia.velocity.utils.FriendlyByteBuf;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.Unpooled;

public class CommandMessageReceiver {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("freesia", "command");

    public CommandMessageReceiver() {
        Freesia.PROXY_SERVER.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;
        
        if (!(event.getSource() instanceof ServerConnection serverConn)) return;
        final Player player = serverConn.getPlayer();
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(event.getData()));

        try {
            String command = buffer.readUtf();
            
            com.nguyendevs.freesia.velocity.Freesia.PROXY_SERVER.getCommandManager().executeAsync(player, command);
        } catch (Exception e) {
            com.nguyendevs.freesia.velocity.Freesia.LOGGER.warn("[Command] Error reading proxy command payload: " + e.getMessage(), e);
        }
    }
}
