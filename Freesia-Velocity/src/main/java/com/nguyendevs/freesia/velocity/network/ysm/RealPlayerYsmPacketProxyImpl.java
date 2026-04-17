package com.nguyendevs.freesia.velocity.network.ysm;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.velocity.FreesiaConstants;
import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.FreesiaConfig;
import com.nguyendevs.freesia.velocity.YsmProtocolMetaFile;
import com.nguyendevs.freesia.velocity.events.PlayerYsmHandshakeEvent;
import com.nguyendevs.freesia.velocity.events.PlayerEntityStateChangeEvent;
import com.nguyendevs.freesia.velocity.utils.FriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RealPlayerYsmPacketProxyImpl extends YsmPacketProxyLayer {

    public RealPlayerYsmPacketProxyImpl(Player player) {
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
                        Freesia.LOGGER.info("[DEBUG] YSM2.6 binary state for {} (len={}): {}",
                                this.player.getUsername(), binaryData.length, hex);
                    }
                } else {
                    state = YsmState.ofNbt(this.nbtRemapper.readBound(mcBuffer));
                }

                Freesia.PROXY_SERVER.getEventManager()
                        .fire(new PlayerEntityStateChangeEvent(this.player, workerEntityId, state))
                        .thenAccept(result -> {
                            final YsmState to = result.getEntityState();

                            this.acquireWriteReference();

                            LAST_YSM_ENTITY_DATA_HANDLE.setVolatile(this, to);

                            this.releaseWriteReference();

                            this.notifyFullTrackerUpdates();
                        }).join();
            } catch (Exception e) {
                Freesia.LOGGER.error("Failed to process entity state update packet", e);
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

            Freesia.LOGGER.info("Replying ysm client with server version {}.Can switch model? : {}", backendVersion,
                    canSwitchModel);

            if (this.hasHandshaked) {
                // Synthesize C2S Handshake Request to inform the new backend worker about the
                // client version
                FriendlyByteBuf c2sBuf = new FriendlyByteBuf(Unpooled.buffer());
                c2sBuf.writeByte(YsmProtocolMetaFile
                        .getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.HAND_SHAKE_REQUEST));
                c2sBuf.writeUtf(this.ysmVersion);

                byte[] data = new byte[c2sBuf.readableBytes()];
                c2sBuf.readBytes(data);

                if (this.handler != null) {
                    this.handler.sendPacket(new ServerboundCustomPayloadPacket(
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
                Freesia.LOGGER.info("[DEBUG] [Ver={}] S2C ANIMATION: workerID={}, layer={}, action={}, name={}",
                        this.ysmVersion, workerEntityId, layer, action, animationName);
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
            final ResultedEvent.GenericResult result = Freesia.PROXY_SERVER.getEventManager()
                    .fire(new PlayerYsmHandshakeEvent(this.player)).join().getResult();

            if (!result.isAllowed()) {
                return ProxyComputeResult.ofDrop();
            }

            final String clientYsmVersion = mcBuffer.readUtf();
            this.ysmVersion = clientYsmVersion; // Store the version
            this.hasHandshaked = true; // Mark as handshaked
            Freesia.LOGGER.info("Player {} is connected to the backend with ysm version {}", this.player.getUsername(),
                    clientYsmVersion);
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
                Freesia.LOGGER.info("[DEBUG] [Ver={}] C2S ANIMATION_REQ: action={}, layer={}, name={}, targetID={}",
                        this.ysmVersion, action, layer, animationName, entityId);
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
                    Freesia.LOGGER.info(
                            "[DEBUG] Relaying ANIMATION_REQ to Worker: action={}, name={}, targetWorkerID={} | HEX: {}",
                            action, animationName, mappedEntityId, hexLog.toString());
                }

                return ProxyComputeResult.ofModify(newPacketByteBuf);
            } else {
                if (FreesiaConfig.debug) {
                    Freesia.LOGGER.warn("[DEBUG] Dropping ANIMATION_REQ: could not map realID={} to any worker ID.",
                            entityId);
                }
            }
        }

        return ProxyComputeResult.ofPass();
    }
}
