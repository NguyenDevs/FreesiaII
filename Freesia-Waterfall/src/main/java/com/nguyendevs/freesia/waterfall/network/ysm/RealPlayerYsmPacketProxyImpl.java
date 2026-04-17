package com.nguyendevs.freesia.waterfall.network.ysm;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.waterfall.FreesiaConstants;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.FreesiaConfig;
import com.nguyendevs.freesia.waterfall.YsmProtocolMetaFile;
import com.nguyendevs.freesia.waterfall.events.PlayerYsmHandshakeEvent;
import com.nguyendevs.freesia.waterfall.events.PlayerEntityStateChangeEvent;
import com.nguyendevs.freesia.waterfall.utils.FriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RealPlayerYsmPacketProxyImpl extends YsmPacketProxyLayer {

    public RealPlayerYsmPacketProxyImpl(ProxiedPlayer player) {
        super(player);
    }

    @Override
    public CompletableFuture<Set<UUID>> fetchTrackerList(UUID observer) {
        return Freesia.tracker.getCanSee(observer);
    }

    @Override
    public ProxyComputeResult processS2C(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        if (packetId == YsmProtocolMetaFile
                .getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.ENTITY_DATA_UPDATE)) {
            final int workerEntityId = mcBuffer.readVarInt();

            if (!this.isEntityStateOfSelf(workerEntityId)) { 
                return ProxyComputeResult.ofDrop(); 
            }

            try {
                YsmState state;
                if (this.ysmVersion.startsWith("2.6")) {
                    byte[] binaryData = new byte[mcBuffer.readableBytes()];
                    mcBuffer.readBytes(binaryData);
                    state = YsmState.ofBinary(binaryData);
                    if (FreesiaConfig.debug) {
                        final StringBuilder hex = new StringBuilder();
                        for (int i = 0; i < Math.min(binaryData.length, 32); i++) {
                            hex.append(String.format("%02X ", binaryData[i]));
                        }
                        Freesia.LOGGER.info("[DEBUG] YSM2.6 binary state for " + this.player.getName()
                                + " (len=" + binaryData.length + "): " + hex);
                    }
                    try {
                        final FriendlyByteBuf probe = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(binaryData));
                        final String modelPath = probe.readUtf();
                        if (!modelPath.isEmpty()) {
                            Freesia.mapperManager.cacheCitizensModelBinary(modelPath, binaryData);
                        }
                    } catch (Exception ignored) {}
                } else {
                    state = YsmState.ofNbt(this.nbtRemapper.readBound(mcBuffer));
                }

                PlayerEntityStateChangeEvent result = Freesia.PROXY_SERVER.getPluginManager()
                        .callEvent(new PlayerEntityStateChangeEvent(this.player, workerEntityId, state));

                final YsmState to = result.getEntityState();

                this.acquireWriteReference(); 

                LAST_YSM_ENTITY_DATA_HANDLE.setVolatile(this, to);

                this.releaseWriteReference(); 

                this.notifyFullTrackerUpdates();
            } catch (Exception e) {
                Freesia.LOGGER.log(java.util.logging.Level.SEVERE, "Failed to process entity state update packet", e);
            }

            return ProxyComputeResult.ofDrop();
        }

        if (packetId == YsmProtocolMetaFile
                .getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.HAND_SHAKE_CONFIRMED)) {
            final String backendVersion = mcBuffer.readUtf();
            boolean canSwitchModel = true;
            if (mcBuffer.isReadable()) {
                canSwitchModel = mcBuffer.readBoolean();
            }

            Freesia.LOGGER.info("Replying ysm client with server version " + backendVersion + ".Can switch model? : "
                    + canSwitchModel);

            if (this.hasHandshaked) {
                FriendlyByteBuf c2sBuf = new FriendlyByteBuf(Unpooled.buffer());
                c2sBuf.writeByte(YsmProtocolMetaFile
                        .getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.HAND_SHAKE_REQUEST));
                c2sBuf.writeUtf(this.ysmVersion);

                byte[] data = new byte[c2sBuf.readableBytes()];
                c2sBuf.readBytes(data);

                if (this.handler != null) {
                    this.handler.sendPacket(
                            new org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket(
                                    YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, data));
                }

                return ProxyComputeResult.ofDrop();
            }

            return ProxyComputeResult.ofPass();
        }

        if (packetId == YsmProtocolMetaFile
                .getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.MOLANG_EXECUTE)) {
            final int[] entityIds = mcBuffer.readVarIntArray();
            final int[] entityIdsRemapped = new int[entityIds.length];
            final String expression = mcBuffer.readUtf();

            final InetSocketAddress remoteAddress = this.handler == null ? null : this.handler.getRemoteAddress();
            final Map<Integer, Integer> collectedPaddingWorkerEntityId = remoteAddress == null ? Map.of() : Freesia.mapperManager
                    .collectRealProxy2WorkerEntityId(remoteAddress);
            int idx = 0;
            for (int singleWorkerEntityId : entityIds) {
                final Integer targetProxyId = collectedPaddingWorkerEntityId
                        .get(singleWorkerEntityId);

                if (targetProxyId == null) {
                    continue;
                }

                entityIdsRemapped[idx] = targetProxyId; 
                idx++;
            }
            this.executeMolang(entityIdsRemapped, expression);
            return ProxyComputeResult.ofDrop();
        }

        if (packetId == YsmProtocolMetaFile
                .getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.ANIMATION)) {
            final int workerEntityId = mcBuffer.readVarInt();
            final byte layer = mcBuffer.readByte();

            int action = 0;
            String animationName;

            if (this.ysmVersion.startsWith("2.6")) {
                action = mcBuffer.readVarInt();
                animationName = mcBuffer.readUtf();
            } else {
                animationName = mcBuffer.readUtf();
            }

            if (FreesiaConfig.debug) {
                Freesia.LOGGER.info("[DEBUG] [Ver=" + this.ysmVersion + "] S2C ANIMATION: workerID=" + workerEntityId
                        + ", layer=" + layer + ", action=" + action + ", name=" + animationName);
            }

            final InetSocketAddress remoteAddress = this.handler == null ? null : this.handler.getRemoteAddress();
            final Map<Integer, Integer> collectedPaddingWorkerEntityId = remoteAddress == null ? Map.of() : Freesia.mapperManager
                    .collectRealProxy2WorkerEntityId(remoteAddress);

            final Integer targetProxyId = collectedPaddingWorkerEntityId.get(workerEntityId);

            if (targetProxyId != null) {
                final FriendlyByteBuf newPacketByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                newPacketByteBuf.writeByte(YsmProtocolMetaFile
                        .getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.ANIMATION));
                newPacketByteBuf.writeVarInt(targetProxyId);
                newPacketByteBuf.writeByte(layer);

                if (this.ysmVersion.startsWith("2.6")) {
                    newPacketByteBuf.writeVarInt(action);
                }
                newPacketByteBuf.writeUtf(animationName);

                this.broadcastYsmPacketToTrackers(newPacketByteBuf.copy());

                return ProxyComputeResult.ofModify(newPacketByteBuf);
            }
        }

        return ProxyComputeResult.ofPass();
    }

    @Override
    public ProxyComputeResult processC2S(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        if (packetId == YsmProtocolMetaFile
                .getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.HAND_SHAKE_REQUEST)) {
            PlayerYsmHandshakeEvent event = Freesia.PROXY_SERVER.getPluginManager()
                    .callEvent(new PlayerYsmHandshakeEvent(this.player, true));

            if (!event.isAllowed()) {
                return ProxyComputeResult.ofDrop();
            }

            final String clientYsmVersion = mcBuffer.readUtf();
            this.ysmVersion = clientYsmVersion;
            this.hasHandshaked = true;
            Freesia.LOGGER.info("Player " + this.player.getName() + " is connected to the backend with ysm version "
                    + clientYsmVersion);
            Freesia.mapperManager.onClientYsmHandshakePacketReply(this.player);
        }

        if (packetId == YsmProtocolMetaFile
                .getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.MOLANG_EXECUTE_REQ)) {
            final String molangExpression = mcBuffer.readUtf();
            final int currWorkerEntityId = this.getPlayerWorkerEntityId();

            if (currWorkerEntityId != -1) {
                final FriendlyByteBuf newPacketByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                newPacketByteBuf.writeByte(YsmProtocolMetaFile
                        .getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.MOLANG_EXECUTE_REQ));
                newPacketByteBuf.writeUtf(molangExpression);
                newPacketByteBuf.writeVarInt(currWorkerEntityId);
                return ProxyComputeResult.ofModify(newPacketByteBuf);
            }
        }

        if (packetId == YsmProtocolMetaFile
                .getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.ANIMATION_REQ)) {

            final int action;
            String animationName = "";
            byte layer = 0;
            int entityId;

            if (this.ysmVersion.startsWith("2.6")) {
                action = mcBuffer.readVarInt();
                animationName = mcBuffer.readUtf();
                entityId = mcBuffer.readVarInt();
            } else {
                action = mcBuffer.readByte();
                layer = mcBuffer.readByte();
                entityId = mcBuffer.readVarInt();
            }

            if (FreesiaConfig.debug) {
                Freesia.LOGGER.info("[DEBUG] [Ver=" + this.ysmVersion + "] C2S ANIMATION_REQ: action=" + action
                        + ", layer=" + layer + ", name=" + animationName + ", targetID=" + entityId);
            }

            final int mappedEntityId;
            if (entityId == -1) {
                mappedEntityId = -1;
            } else {
                final InetSocketAddress remoteAddress = this.handler == null ? null : this.handler.getRemoteAddress();
                final Map<Integer, Integer> workerToProxy = remoteAddress == null ? Map.of() : Freesia.mapperManager
                        .collectRealProxy2WorkerEntityId(remoteAddress);
                int foundWorkerId = -1;
                for (Map.Entry<Integer, Integer> entry : workerToProxy.entrySet()) {
                    if (entry.getValue().equals(entityId)) {
                        foundWorkerId = entry.getKey();
                        break;
                    }
                }
                mappedEntityId = foundWorkerId;
            }

            if (mappedEntityId != -1 || entityId == -1) {
                final FriendlyByteBuf newPacketByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                newPacketByteBuf.writeByte(YsmProtocolMetaFile
                        .getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.ANIMATION_REQ));

                if (this.ysmVersion.startsWith("2.6")) {
                    newPacketByteBuf.writeVarInt(action);
                    newPacketByteBuf.writeUtf(animationName);
                } else {
                    newPacketByteBuf.writeByte((byte) action);
                    newPacketByteBuf.writeByte(layer);
                }

                newPacketByteBuf.writeVarInt(mappedEntityId);

                if (FreesiaConfig.debug) {
                    final ByteBuf dumpBuf = newPacketByteBuf.copy();
                    final StringBuilder hexLog = new StringBuilder();
                    while (dumpBuf.isReadable()) {
                        hexLog.append(String.format("%02X ", dumpBuf.readByte()));
                    }
                    Freesia.LOGGER.info("[DEBUG] Relaying ANIMATION_REQ to Worker: action=" + action
                            + ", name=" + animationName + ", targetWorkerID=" + mappedEntityId + " | HEX: "
                            + hexLog.toString());
                }

                return ProxyComputeResult.ofModify(newPacketByteBuf);
            } else {
                if (FreesiaConfig.debug) {
                    Freesia.LOGGER.warning(
                            "[DEBUG] Dropping ANIMATION_REQ: could not map realID=" + entityId + " to any worker ID.");
                }
            }
        }

        return ProxyComputeResult.ofPass();
    }
}
