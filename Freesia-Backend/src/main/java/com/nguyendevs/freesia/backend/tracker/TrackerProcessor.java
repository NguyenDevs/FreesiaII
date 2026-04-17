package com.nguyendevs.freesia.backend.tracker;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.netty.buffer.Unpooled;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import com.nguyendevs.freesia.backend.FreesiaBackend;
import com.nguyendevs.freesia.backend.Utils;
import com.nguyendevs.freesia.backend.event.CyanidinRealPlayerTrackerUpdateEvent;
import com.nguyendevs.freesia.backend.event.CyanidinTrackerScanEvent;
import com.nguyendevs.freesia.backend.utils.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TrackerProcessor implements PluginMessageListener, Listener {
    public static final String CHANNEL_NAME = "freesia:tracker_sync";

    private final Map<UUID, Set<UUID>> entityViewers = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler
    public void onPlayerTrackEntity(@NotNull PlayerTrackEntityEvent trackEvent) {
        final Player watcher = trackEvent.getPlayer();
        final Entity beingWatched = trackEvent.getEntity();

        this.entityViewers
                .computeIfAbsent(beingWatched.getUniqueId(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(watcher.getUniqueId());

        if (beingWatched.hasMetadata("NPC")) {
            int npcId = -1;
            if (FreesiaBackend.isCitizensEnabled()) {
                net.citizensnpcs.api.npc.NPC npc = com.nguyendevs.freesia.backend.citizens.CitizensHook.getNpcByEntity(beingWatched);
                if (npc != null) npcId = npc.getId();
            }
            this.notifyVirtualTrackerUpdate(watcher.getUniqueId(), beingWatched.getUniqueId(), beingWatched.getEntityId(), npcId);
        } else if (beingWatched instanceof Player beingWatchedPlayer) {
            this.playerTrackedPlayer(beingWatchedPlayer, watcher);
        }
    }

    @EventHandler
    public void onPlayerUntrackEntity(@NotNull PlayerUntrackEntityEvent untrackEvent) {
        final Player watcher = untrackEvent.getPlayer();
        final Entity beingWatched = untrackEvent.getEntity();

        Set<UUID> viewers = this.entityViewers.get(beingWatched.getUniqueId());
        if (viewers != null) {
            viewers.remove(watcher.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        this.entityViewers.remove(player.getUniqueId());

        for (Set<UUID> viewers : this.entityViewers.values()) {
            viewers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerAddedToWorld(@NotNull EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Player player) {
            this.playerTrackedPlayer(player, player);
        }
    }

    private void playerTrackedPlayer(@NotNull Player beSeen, @NotNull Player seeing) {
        if (!new CyanidinRealPlayerTrackerUpdateEvent(seeing, beSeen).callEvent()) {
            return;
        }
        this.notifyTrackerUpdate(seeing.getUniqueId(), beSeen.getUniqueId());
    }

    public void notifyTrackerUpdate(UUID watcher, UUID beWatched) {
        final FriendlyByteBuf wrappedUpdatePacket = new FriendlyByteBuf(Unpooled.buffer());

        wrappedUpdatePacket.writeVarInt(2);
        wrappedUpdatePacket.writeUUID(beWatched);
        wrappedUpdatePacket.writeUUID(watcher);

        final Player payload = Utils.randomPlayerIfNotFound(watcher);

        if (payload == null) {
            return;
        }

        payload.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, wrappedUpdatePacket.getBytes());
    }

    public void notifyVirtualTrackerUpdate(UUID watcher, UUID virtualEntityUUID, int entityId, int npcId) {
        final FriendlyByteBuf wrappedUpdatePacket = new FriendlyByteBuf(Unpooled.buffer());

        wrappedUpdatePacket.writeVarInt(3);
        wrappedUpdatePacket.writeUUID(virtualEntityUUID);
        wrappedUpdatePacket.writeVarInt(entityId);
        wrappedUpdatePacket.writeVarInt(npcId);
        wrappedUpdatePacket.writeUUID(watcher);

        final Player payload = Utils.randomPlayerIfNotFound(watcher);

        if (payload == null) {
            return;
        }

        payload.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, wrappedUpdatePacket.getBytes());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player sender, byte @NotNull [] data) {
        if (!channel.equals(CHANNEL_NAME)) {
            return;
        }

        final FriendlyByteBuf packetData = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        if (packetData.readVarInt() == 1) {
            final int callbackId = packetData.readVarInt();
            final UUID requestedPlayerUUID = packetData.readUUID();

            final Player toScan = Objects.requireNonNull(Bukkit.getPlayer(requestedPlayerUUID));

            final Set<UUID> cachedViewers = this.entityViewers.get(requestedPlayerUUID);
            final Set<UUID> result = cachedViewers != null ? new HashSet<>(cachedViewers) : new HashSet<>();

            final CyanidinTrackerScanEvent trackerScanEvent = new CyanidinTrackerScanEvent(result, toScan);

            sender.getScheduler().execute(
                    FreesiaBackend.INSTANCE,
                    () -> {
                        Bukkit.getPluginManager().callEvent(trackerScanEvent);

                        final FriendlyByteBuf reply = new FriendlyByteBuf(Unpooled.buffer());

                        reply.writeVarInt(0);
                        reply.writeVarInt(callbackId);
                        reply.writeVarInt(result.size());

                        for (UUID uuid : result) {
                            reply.writeUUID(uuid);
                        }

                        sender.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, reply.getBytes());
                    },
                    null,
                    1);
        }
    }

}
