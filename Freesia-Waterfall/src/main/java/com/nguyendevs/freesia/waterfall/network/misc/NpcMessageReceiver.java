package com.nguyendevs.freesia.waterfall.network.misc;

import com.nguyendevs.freesia.waterfall.Freesia;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;

public class NpcMessageReceiver implements Listener {

    public static final String CHANNEL = "freesia:npc";
    private final Map<String, Map<Integer, String>> serverNpcCaches = new java.util.concurrent.ConcurrentHashMap<>();

    public NpcMessageReceiver() {
        Freesia.PROXY_SERVER.registerChannel(CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(CHANNEL)) return;
        
        if (!(event.getReceiver() instanceof ProxiedPlayer watcher)) return;
        if (!(event.getSender() instanceof Server server)) return;

        String serverName = server.getInfo().getName();

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
                Map<Integer, String> npcNames = new java.util.HashMap<>();
                for (int i = 0; i < count; i++) {
                    npcNames.put(in.readInt(), in.readUTF());
                }
                serverNpcCaches.put(serverName, npcNames);
            }
            
            // Mark server as supported if any opcode received
            if (opcode == 0 || opcode == 2 || opcode == 3) {
                if (!serverNpcCaches.containsKey(serverName)) {
                    serverNpcCaches.put(serverName, new java.util.HashMap<>());
                }
            }
        } catch (Exception e) {
            Freesia.LOGGER.warning("[NPC] Error reading proxy payload: " + e.getMessage());
        }
    }

    public Map<Integer, String> getCachedNpcNames(String serverName) {
        return serverNpcCaches.getOrDefault(serverName, java.util.Collections.emptyMap());
    }

    public boolean isSupported(String serverName) {
        return serverNpcCaches.containsKey(serverName);
    }
}
