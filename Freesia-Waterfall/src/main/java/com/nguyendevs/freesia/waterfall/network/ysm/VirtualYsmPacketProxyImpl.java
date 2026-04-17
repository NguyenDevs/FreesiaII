package com.nguyendevs.freesia.waterfall.network.ysm;

import io.netty.buffer.ByteBuf;
import com.nguyendevs.freesia.waterfall.Freesia;
import net.kyori.adventure.key.Key;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class VirtualYsmPacketProxyImpl extends YsmPacketProxyLayer {
    public VirtualYsmPacketProxyImpl(UUID virtualPlayerUUID) {
        super(virtualPlayerUUID);
    }

    @Override
    public CompletableFuture<Set<UUID>> fetchTrackerList(UUID observer) {
        if (Freesia.PROXY_SERVER.getPlayer(observer) != null) {
            return Freesia.tracker.getCanSee(observer);
        }
        final Set<UUID> allYsm = Freesia.PROXY_SERVER.getPlayers().stream()
                .filter(p -> Freesia.mapperManager.isPlayerInstalledYsm(p))
                .map(ProxiedPlayer::getUniqueId)
                .collect(Collectors.toSet());
        return CompletableFuture.completedFuture(allYsm);
    }

    @Override
    public ProxyComputeResult processS2C(Key channelKey, ByteBuf copiedPacketData) {
        return null;
    }

    @Override
    public ProxyComputeResult processC2S(Key channelKey, ByteBuf copiedPacketData) {
        return null;
    }
}

