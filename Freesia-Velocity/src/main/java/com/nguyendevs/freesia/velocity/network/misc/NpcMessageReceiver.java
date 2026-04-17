package com.nguyendevs.freesia.velocity.network.misc;

import com.nguyendevs.freesia.velocity.Freesia;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;

public class NpcMessageReceiver {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("freesia", "npc");
    private final Map<Integer, String> cachedNpcNames = new HashMap<>();

    public NpcMessageReceiver() {
        Freesia.PROXY_SERVER.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;
        
        if (!(event.getTarget() instanceof Player watcher)) return;
        if (!(event.getSource() instanceof ServerConnection serverConn)) return;

        String serverName = serverConn.getServerInfo().getName();

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            byte opcode = in.readByte();

            if (opcode == 0) { 
                in.readLong(); in.readLong();
                int npcId = in.readInt();
                int entityId = in.readInt();
                
                Freesia.mapperManager.handleNpcTrackSync(serverName, watcher, npcId, entityId);
            } else if (opcode == 3) {
                in.readLong(); in.readLong();
                int npcId = in.readInt();

                Freesia.mapperManager.handleNpcUntrackSync(serverName, watcher, npcId);
            } else if (opcode == 2) {
                int count = in.readInt();
                cachedNpcNames.clear();
                for (int i = 0; i < count; i++) {
                    cachedNpcNames.put(in.readInt(), in.readUTF());
                }
            }
        } catch (Exception e) {
            Freesia.LOGGER.warn("[NPC] Error reading proxy payload: " + e.getMessage());
        }
    }

    public Map<Integer, String> getCachedNpcNames() {
        return cachedNpcNames;
    }
}
