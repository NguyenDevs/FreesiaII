package com.nguyendevs.freesia.velocity.network.ysm;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.FreesiaConfig;
import com.nguyendevs.freesia.velocity.utils.PendingPacket;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

public class MapperSessionProcessor implements SessionListener {
    private final Player bindPlayer;
    private final YsmPacketProxy packetProxy;
    private final YsmMapperPayloadManager mapperPayloadManager;

    private final MultiThreadedQueue<PendingPacket> pendingYsmPacketsInbound = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<UUID> pendingTrackerUpdatesTo = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<byte[]> pendingYsmPacketsOutbound = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<CitizensTrackerUpdate> pendingCitizensTrackerUpdates = new MultiThreadedQueue<>();

    public record CitizensTrackerUpdate(int entityId, byte[] binary) {}

    private volatile Session session;
    private boolean kickMasterWhenDisconnect = true;
    private boolean destroyed = false;
    private InetSocketAddress remoteAddress;

    private static final VarHandle KICK_MASTER_HANDLE = ConcurrentUtil.getVarHandle(MapperSessionProcessor.class,
            "kickMasterWhenDisconnect", boolean.class);
    private static final VarHandle SESSION_HANDLE = ConcurrentUtil.getVarHandle(MapperSessionProcessor.class, "session",
            Session.class);
    private static final VarHandle DESTROYED_HANDLE = ConcurrentUtil.getVarHandle(MapperSessionProcessor.class,
            "destroyed", boolean.class);

    public MapperSessionProcessor(Player bindPlayer, YsmPacketProxy packetProxy,
            YsmMapperPayloadManager mapperPayloadManager) {
        this.bindPlayer = bindPlayer;
        this.packetProxy = packetProxy;
        this.mapperPayloadManager = mapperPayloadManager;
    }

    protected boolean queueTrackerUpdate(UUID target) {
        return this.pendingTrackerUpdatesTo.offer(target);
    }

    protected boolean queueCitizensTrackerUpdate(int entityId, byte[] binary) {
        return this.pendingCitizensTrackerUpdates.offer(new CitizensTrackerUpdate(entityId, binary));
    }

    protected void retireTrackerCallbacks() {
        UUID toSend;
        while ((toSend = this.pendingTrackerUpdatesTo.pollOrBlockAdds()) != null) {
            final Optional<Player> player = Freesia.PROXY_SERVER.getPlayer(toSend);

            if (player.isEmpty()) {
                continue;
            }

            final Player targetPlayer = player.get();

            this.packetProxy.sendEntityStateTo(targetPlayer);
        }

        CitizensTrackerUpdate npcUpdate;
        while ((npcUpdate = this.pendingCitizensTrackerUpdates.pollOrBlockAdds()) != null) {
            this.mapperPayloadManager.sendEntityStateToRaw(this.bindPlayer.getUniqueId(), npcUpdate.entityId(), YsmState.ofBinary(npcUpdate.binary()));
        }
    }

    public boolean sendPacket(Packet packet) {
        final Session sessionObject = (Session) SESSION_HANDLE.getVolatile(this);

        if (sessionObject == null) {
            return false;
        }

        sessionObject.send(packet);
        return true;
    }

    public YsmPacketProxy getPacketProxy() {
        return this.packetProxy;
    }

    protected void setKickMasterWhenDisconnect(boolean kickMasterWhenDisconnect) {
        KICK_MASTER_HANDLE.setVolatile(this, kickMasterWhenDisconnect);
    }

    protected void processPlayerPluginMessage(byte[] packetData) {
        final Session sessionObject = (Session) SESSION_HANDLE.getVolatile(this);

        if (sessionObject == null) {
            throw new IllegalStateException("Processing plugin message on non-connected mapper");
        }

        final ProxyComputeResult result = this.packetProxy.processC2S(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE,
                Unpooled.copiedBuffer(packetData));

        switch (result.result()) {
            case MODIFY -> {
                final ByteBuf finalData = result.data();

                finalData.resetReaderIndex();
                byte[] data = new byte[finalData.readableBytes()];
                finalData.readBytes(data);

                if (!this.pendingYsmPacketsOutbound.offer(data)) {
                    sessionObject.send(
                            new ServerboundCustomPayloadPacket(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE,
                                    data));
                }
            }

            case PASS -> {
                if (!this.pendingYsmPacketsOutbound.offer(packetData)) {
                    sessionObject
                            .send(new ServerboundCustomPayloadPacket(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE,
                                    packetData));
                }
            }
        }
    }

    public Player getBindPlayer() {
        return this.bindPlayer;
    }

    protected void onBackendReady() {
        PendingPacket pendingYsmPacket;
        while ((pendingYsmPacket = this.pendingYsmPacketsInbound.pollOrBlockAdds()) != null) {
            this.processInComingYsmPacket(pendingYsmPacket.channel(), pendingYsmPacket.data());
        }
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ClientboundLoginPacket loginPacket) {
            Freesia.mapperManager.updateWorkerPlayerEntityId(this.bindPlayer, loginPacket.getEntityId());

            byte[] pendingData;
            while ((pendingData = this.pendingYsmPacketsOutbound.pollOrBlockAdds()) != null) {
                session.send(new ServerboundCustomPayloadPacket(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE,
                        pendingData));
            }
        }

        if (packet instanceof ClientboundCustomPayloadPacket payloadPacket) {
            final Key channelKey = payloadPacket.getChannel();
            final byte[] packetData = payloadPacket.getData();

            if (FreesiaConfig.debug) {
                if (channelKey.toString().contains("yes_steve_model")) {
                    Freesia.LOGGER.info("[DEBUG] Received packet from Worker on channel: {} for {} (len={})",
                            channelKey.toString(), this.bindPlayer.getUsername(), packetData.length);
                }
            }

            if (channelKey.toString().equals(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE.toString())) {
                final PendingPacket pendingPacket = new PendingPacket(channelKey, packetData);
                if (!this.pendingYsmPacketsInbound.offer(pendingPacket)) {
                    this.processInComingYsmPacket(channelKey, packetData);
                }
            }
        }

        if (packet instanceof ClientboundPingPacket pingPacket) {
            session.send(new ServerboundPongPacket(pingPacket.getId()));
        }
    }

    private void processInComingYsmPacket(Key channelKey, byte[] packetData) {
        if (FreesiaConfig.debug) {
            final StringBuilder dump = new StringBuilder();
            for (byte b : packetData) {
                dump.append(String.format("%02X ", b));
            }
            Freesia.LOGGER.info("[DEBUG] S2C Packet Data ({} bytes): {}", packetData.length, dump.toString());
        }

        final ProxyComputeResult result = this.packetProxy.processS2C(channelKey, Unpooled.wrappedBuffer(packetData));

        switch (result.result()) {
            case MODIFY -> {
                final ByteBuf finalData = result.data();

                finalData.resetReaderIndex();

                this.packetProxy.sendPluginMessageToOwner(
                        MinecraftChannelIdentifier.create(channelKey.namespace(), channelKey.value()), finalData);
            }

            case PASS ->
                this.packetProxy.sendPluginMessageToOwner(
                        MinecraftChannelIdentifier.create(channelKey.namespace(), channelKey.value()), packetData);
        }
    }

    @Override
    public void packetSending(PacketSendingEvent event) {

    }

    @Override
    public void packetSent(Session session, Packet packet) {

    }

    @Override
    public void packetError(PacketErrorEvent event) {

    }

    @Override
    public void connected(ConnectedEvent event) {
    }

    @Override
    public void disconnecting(DisconnectingEvent event) {

    }

    @Override
    public void disconnected(DisconnectedEvent event) {
        this.detachFromManager(true, event);
    }

    protected void detachFromManager(boolean updateSession, @Nullable DisconnectedEvent disconnectedEvent) {
        Component reason = null;

        if (disconnectedEvent != null) {
            reason = disconnectedEvent.getReason();

            Freesia.LOGGER.info("Mapper session has disconnected for reason(non-deserialized): {}", reason);

            final Throwable thr = disconnectedEvent.getCause();

            if (thr != null) {
                Freesia.LOGGER.error("Mapper session has disconnected for throwable", thr);
            }
        }

        this.mapperPayloadManager.onWorkerSessionDisconnect(this, (boolean) KICK_MASTER_HANDLE.getVolatile(this),
                reason);

        if (updateSession) {
            SESSION_HANDLE.setVolatile(this, null);
        }
    }

    protected void setSession(Session session) {
        SESSION_HANDLE.setVolatile(this, session);
        if (session != null) {
            this.remoteAddress = (InetSocketAddress) session.getRemoteAddress();
        }
    }

    public void destroyAndAwaitDisconnected() {
        if (!DESTROYED_HANDLE.compareAndSet(this, false, true)) {
            this.waitForDisconnected();
            return;
        }

        final Session sessionObject = (Session) SESSION_HANDLE.getVolatile(this);

        if (sessionObject != null) {
            sessionObject.disconnect("DESTROYED");
        } else {
            this.detachFromManager(false, null);
        }

        this.waitForDisconnected();
    }

    protected void waitForDisconnected() {
        while (SESSION_HANDLE.getVolatile(this) != null) {
            Thread.onSpinWait();
        }
    }

    public @Nullable InetSocketAddress getRemoteAddress() {
        if (this.remoteAddress != null) {
            return this.remoteAddress;
        }

        final Session sessionObject = (Session) SESSION_HANDLE.getVolatile(this);

        if (sessionObject == null) {
            return null;
        }

        this.remoteAddress = (InetSocketAddress) sessionObject.getRemoteAddress();
        return this.remoteAddress;
    }
}
