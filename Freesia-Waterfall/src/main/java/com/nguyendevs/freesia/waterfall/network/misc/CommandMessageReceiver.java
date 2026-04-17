package com.nguyendevs.freesia.waterfall.network.misc;

import com.nguyendevs.freesia.waterfall.utils.FriendlyByteBuf;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import io.netty.buffer.Unpooled;

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
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(event.getData()));

        try {
            String command = buffer.readUtf();
            
            com.nguyendevs.freesia.waterfall.Freesia.PROXY_SERVER.getPluginManager().dispatchCommand(player, command);
        } catch (Exception e) {
            com.nguyendevs.freesia.waterfall.Freesia.LOGGER.warning("[Command] Error reading proxy command payload: " + e.getMessage());
        }
    }
}
