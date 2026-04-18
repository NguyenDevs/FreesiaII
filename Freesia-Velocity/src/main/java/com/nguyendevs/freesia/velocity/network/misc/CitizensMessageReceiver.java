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

public class CitizensMessageReceiver {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("freesia", "npc");
    private final Map<String, Map<Integer, String>> serverNpcCaches = new java.util.concurrent.ConcurrentHashMap<>();

    public CitizensMessageReceiver() {
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
                String modelId = in.readUTF();
                
                Freesia.mapperManager.handleCitizensTrackSync(serverName, watcher, npcId, entityId, modelId);
            } else if (opcode == 3) {
                in.readLong(); in.readLong();
                int npcId = in.readInt();

                Freesia.mapperManager.handleCitizensUntrackSync(serverName, watcher, npcId);
            } else if (opcode == 2) {
                int count = in.readInt();
                Map<Integer, String> npcNames = new java.util.HashMap<>();
                for (int i = 0; i < count; i++) {
                    npcNames.put(in.readInt(), in.readUTF());
                }
                serverNpcCaches.put(serverName, npcNames);
            } else if (opcode == 4) {
                int npcId = in.readInt();
                String modelId = in.readUTF();
                Freesia.mapperManager.broadcastCitizensSkinUpdate(serverName, npcId, modelId);
            }
            
            // Mark server as supported if any opcode received
            if (opcode == 0 || opcode == 2 || opcode == 3 || opcode == 4) {
                if (!serverNpcCaches.containsKey(serverName)) {
                    serverNpcCaches.put(serverName, new java.util.HashMap<>());
                }
            }
        } catch (Exception e) {
            Freesia.LOGGER.warn("[Citizens] Error reading proxy payload: " + e.getMessage());
        }
    }

    public Map<Integer, String> getCachedNpcNames(String serverName) {
        return serverNpcCaches.getOrDefault(serverName, java.util.Collections.emptyMap());
    }

    public boolean isSupported(String serverName) {
        return serverNpcCaches.containsKey(serverName);
    }
}
