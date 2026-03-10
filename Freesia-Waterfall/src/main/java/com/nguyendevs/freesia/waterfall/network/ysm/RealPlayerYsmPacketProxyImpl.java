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

            if (!this.isEntityStateOfSelf(workerEntityId)) { // Check if the packet is current player and drop to
                                                             // prevent incorrect broadcasting
                return ProxyComputeResult.ofDrop(); // Do not process the entity state if it is not ours
            }

            try {
                YsmState state;
                if (this.ysmVersion.startsWith("2.6")) {
                    // YSM 2.6.x uses binary format: Strings (ModelPath, ModelName, AnimationName) +
                    // raw bytes
                    // We just capture the whole thing as binary for now
                    byte[] binaryData = new byte[mcBuffer.readableBytes()];
                    mcBuffer.readBytes(binaryData);
                    state = YsmState.ofBinary(binaryData);
                } else {
                    // Legacy NBT format
                    state = YsmState.ofNbt(this.nbtRemapper.readBound(mcBuffer));
                }

                PlayerEntityStateChangeEvent result = Freesia.PROXY_SERVER.getPluginManager()
                        .callEvent(new PlayerEntityStateChangeEvent(this.player, workerEntityId, state));

                final YsmState to = result.getEntityState();

                this.acquireWriteReference(); // Acquire write reference

                LAST_YSM_ENTITY_DATA_HANDLE.setVolatile(this, to);

                this.releaseWriteReference(); // Release write reference

                this.notifyFullTrackerUpdates(); // Notify updates
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
                // Synthesize C2S Handshake Request to inform the new backend worker about the
                // client version
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

            final Map<Integer, Integer> collectedPaddingWorkerEntityId = Freesia.mapperManager
                    .collectRealProxy2WorkerEntityId();

            // remap the entity id
            int idx = 0;
            for (int singleWorkerEntityId : entityIds) {
                final Integer targetProxyId = collectedPaddingWorkerEntityId
                        .get(singleWorkerEntityId);

                if (targetProxyId == null) {
                    continue;
                }

                entityIdsRemapped[idx] = targetProxyId; // we are on backend side
                idx++;
            }

            // re-send packet as it's much cheaper than modify
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

            final Map<Integer, Integer> collectedPaddingWorkerEntityId = Freesia.mapperManager
                    .collectRealProxy2WorkerEntityId();

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
            this.ysmVersion = clientYsmVersion; // Store the version
            this.hasHandshaked = true; // Mark as handshaked
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
                // Reverse lookup: find the worker ID belonging to the real entity ID
                final Map<Integer, Integer> workerToProxy = Freesia.mapperManager
                        .collectRealProxy2WorkerEntityId();
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
