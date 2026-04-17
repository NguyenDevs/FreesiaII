package com.nguyendevs.freesia.npc;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collection;

public class TrackerListener implements Listener, PluginMessageListener {

    private static final byte OP_TRACK_SYNC = 0;
    private static final byte OP_REQ_LIST   = 1;
    private static final byte OP_RES_LIST   = 2;
    private static final byte OP_UNTRACK_SYNC = 3;

    @EventHandler
    public void onUntrackEntity(io.papermc.paper.event.player.PlayerUntrackEntityEvent event) {
        final Entity beingWatched = event.getEntity();
        final Player watcher = event.getPlayer();

        if (beingWatched.hasMetadata("NPC")) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(beingWatched);
            if (npc != null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    dos.writeByte(OP_UNTRACK_SYNC);
                    dos.writeLong(watcher.getUniqueId().getMostSignificantBits());
                    dos.writeLong(watcher.getUniqueId().getLeastSignificantBits());
                    dos.writeInt(npc.getId());
                    dos.flush();
                    
                    FreesiaNPCPlugin.INSTANCE.sendProxyPayload(watcher, bos.toByteArray());
                } catch (Exception e) {
                    FreesiaNPCPlugin.INSTANCE.getLogger().warning("Failed to send untrack sync: " + e.getMessage());
                }
            }
        }
    }

    @EventHandler
    public void onTrackEntity(PlayerTrackEntityEvent event) {
        final Entity beingWatched = event.getEntity();
        final Player watcher = event.getPlayer();

        if (beingWatched.hasMetadata("NPC")) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(beingWatched);
            if (npc != null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    dos.writeByte(OP_TRACK_SYNC);
                    dos.writeLong(watcher.getUniqueId().getMostSignificantBits());
                    dos.writeLong(watcher.getUniqueId().getLeastSignificantBits());
                    dos.writeInt(npc.getId());
                    dos.writeInt(beingWatched.getEntityId());
                    dos.flush();
                    
                    FreesiaNPCPlugin.INSTANCE.sendProxyPayload(watcher, bos.toByteArray());
                } catch (Exception e) {
                    FreesiaNPCPlugin.INSTANCE.getLogger().warning("Failed to send track sync: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!channel.equals(FreesiaNPCPlugin.CHANNEL_NAME)) return;
        
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            byte opcode = in.readByte();
            if (opcode == OP_REQ_LIST) {
                Collection<NPC> npcs = (Collection<NPC>) CitizensAPI.getNPCRegistry().sorted();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                dos.writeByte(OP_RES_LIST);
                dos.writeInt(npcs.size());
                for (NPC npc : npcs) {
                    dos.writeInt(npc.getId());
                    dos.writeUTF(npc.getName());
                }
                dos.flush();
                FreesiaNPCPlugin.INSTANCE.sendProxyPayload(player, bos.toByteArray());
            }
        } catch (Exception e) {
            FreesiaNPCPlugin.INSTANCE.getLogger().warning("Failed to parse plugin messaging: " + e.getMessage());
        }
    }
}
