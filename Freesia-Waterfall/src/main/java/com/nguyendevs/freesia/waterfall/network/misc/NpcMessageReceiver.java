package com.nguyendevs.freesia.waterfall.network.misc;

import com.nguyendevs.freesia.waterfall.Freesia;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;

public class NpcMessageReceiver implements Listener {

    public static final String CHANNEL = "freesia:npc";
    private final Map<Integer, String> cachedNpcNames = new HashMap<>();

    public NpcMessageReceiver() {
        Freesia.PROXY_SERVER.registerChannel(CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(CHANNEL)) return;
        
        if (!(event.getReceiver() instanceof ProxiedPlayer watcher)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            byte opcode = in.readByte();

            if (opcode == 0) { // OP_TRACK_SYNC
                in.readLong(); in.readLong(); // discard UUID
                int npcId = in.readInt();
                int entityId = in.readInt();
                
                Freesia.mapperManager.handleNpcTrackSync(watcher, npcId, entityId);
            } else if (opcode == 2) { // OP_RES_LIST
                int count = in.readInt();
                cachedNpcNames.clear();
                for (int i = 0; i < count; i++) {
                    cachedNpcNames.put(in.readInt(), in.readUTF());
                }
            }
        } catch (Exception e) {
            Freesia.LOGGER.warning("[NPC] Error reading proxy payload: " + e.getMessage());
        }
    }

    public Map<Integer, String> getCachedNpcNames() {
        return cachedNpcNames;
    }
}
