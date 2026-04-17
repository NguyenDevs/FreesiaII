package com.nguyendevs.freesia.velocity.network.ysm;

import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.ByteBuf;
import com.nguyendevs.freesia.velocity.Freesia;
import net.kyori.adventure.key.Key;

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
        if (Freesia.PROXY_SERVER.getPlayer(observer).isPresent()) {
            return Freesia.tracker.getCanSee(observer);
        }
        final Set<UUID> allYsm = Freesia.PROXY_SERVER.getAllPlayers().stream()
                .filter(p -> Freesia.mapperManager.isPlayerInstalledYsm(p))
                .map(Player::getUniqueId)
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

