package com.nguyendevs.freesia.waterfall.network.ysm;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.FreesiaConfig;
import com.nguyendevs.freesia.waterfall.utils.PendingPacket;
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
    private final ProxiedPlayer bindPlayer;
    private final YsmPacketProxy packetProxy;
    private final YsmMapperPayloadManager mapperPayloadManager;

    private final MultiThreadedQueue<PendingPacket> pendingYsmPacketsInbound = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<UUID> pendingTrackerUpdatesTo = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<byte[]> pendingYsmPacketsOutbound = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<NpcTrackerUpdate> pendingNpcTrackerUpdates = new MultiThreadedQueue<>();

    public record NpcTrackerUpdate(int entityId, byte[] binary) {}

    // Controlled by the VarHandles following
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

    public MapperSessionProcessor(ProxiedPlayer bindPlayer, YsmPacketProxy packetProxy,
            YsmMapperPayloadManager mapperPayloadManager) {
        this.bindPlayer = bindPlayer;
        this.packetProxy = packetProxy;
        this.mapperPayloadManager = mapperPayloadManager;
    }

    protected boolean queueTrackerUpdate(UUID target) {
        return this.pendingTrackerUpdatesTo.offer(target);
    }

    protected boolean queueNpcTrackerUpdate(int entityId, byte[] binary) {
        return this.pendingNpcTrackerUpdates.offer(new NpcTrackerUpdate(entityId, binary));
    }

    protected void retireTrackerCallbacks() {
        UUID toSend;
        while ((toSend = this.pendingTrackerUpdatesTo.pollOrBlockAdds()) != null) {
            final ProxiedPlayer player = Freesia.PROXY_SERVER.getPlayer(toSend);

            if (player == null) {
                continue;
            }

            this.packetProxy.sendEntityStateTo(player);
        }

        NpcTrackerUpdate npcUpdate;
        while ((npcUpdate = this.pendingNpcTrackerUpdates.pollOrBlockAdds()) != null) {
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

    public ProxiedPlayer getBindPlayer() {
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
                    Freesia.LOGGER
                            .info("[DEBUG] Received packet from Worker on channel: " + channelKey.toString() + " for "
                                    + this.bindPlayer.getName() + " (len=" + packetData.length + ")");
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
            Freesia.LOGGER.info("[DEBUG] S2C Packet Data (" + packetData.length + " bytes): " + dump.toString());
        }

        final ProxyComputeResult result = this.packetProxy.processS2C(channelKey, Unpooled.wrappedBuffer(packetData));

        switch (result.result()) {
            case MODIFY -> {
                final ByteBuf finalData = result.data();

                finalData.resetReaderIndex();

                this.packetProxy.sendPluginMessageToOwner(channelKey.namespace() + ":" + channelKey.value(), finalData);
            }

            case PASS ->
                this.packetProxy.sendPluginMessageToOwner(channelKey.namespace() + ":" + channelKey.value(),
                        packetData);
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

            Freesia.LOGGER.info("Mapper session has disconnected for reason(non-deserialized): " + reason);

            final Throwable thr = disconnectedEvent.getCause();

            if (thr != null) {
                Freesia.LOGGER.log(java.util.logging.Level.SEVERE, "Mapper session has disconnected for throwable",
                        thr);
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
        // We will set the session to null after finishing all disconnect logics
        while (SESSION_HANDLE.getVolatile(this) != null) {
            Thread.onSpinWait(); // Spin wait instead of block waiting
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
