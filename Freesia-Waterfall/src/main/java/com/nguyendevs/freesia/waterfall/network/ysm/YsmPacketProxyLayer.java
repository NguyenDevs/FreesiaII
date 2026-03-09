package com.nguyendevs.freesia.waterfall.network.ysm;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.FreesiaConstants;
import com.nguyendevs.freesia.waterfall.YsmProtocolMetaFile;
import com.nguyendevs.freesia.waterfall.network.mc.NbtRemapper;
import com.nguyendevs.freesia.waterfall.network.mc.impl.StandardNbtRemapperImpl;
import com.nguyendevs.freesia.waterfall.utils.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class YsmPacketProxyLayer implements YsmPacketProxy {
    protected final ProxiedPlayer player;

    protected final UUID playerUUID;
    protected final NbtRemapper nbtRemapper = new StandardNbtRemapperImpl();

    protected volatile MapperSessionProcessor handler;

    protected String ysmVersion = "2.4.1"; // Default to legacy version

    private int playerEntityId = -1;
    private int workerEntityId = -1;

    private int entityDataReferenceCount = 0;

    private YsmState lastYsmEntityData = null;

    private boolean proxyReady = false;

    protected static final VarHandle ENTITY_DATA_REF_COUNT_HANDLE = ConcurrentUtil
            .getVarHandle(YsmPacketProxyLayer.class, "entityDataReferenceCount", int.class);
    protected static final VarHandle PROXY_READY_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyLayer.class,
            "proxyReady", boolean.class);

    protected static final VarHandle PLAYER_ENTITY_ID_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyLayer.class,
            "playerEntityId", int.class);
    protected static final VarHandle WORKER_ENTITY_ID_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyLayer.class,
            "workerEntityId", int.class);

    protected static final VarHandle LAST_YSM_ENTITY_DATA_HANDLE = ConcurrentUtil
            .getVarHandle(YsmPacketProxyLayer.class, "lastYsmEntityData", YsmState.class);

    protected YsmPacketProxyLayer(UUID playerUUID) {
        this.player = Freesia.PROXY_SERVER.getPlayer(playerUUID); // Get if it is a real player
        this.playerUUID = playerUUID;
    }

    protected YsmPacketProxyLayer(@NotNull ProxiedPlayer player) {
        this.player = player;
        this.playerUUID = player.getUniqueId();
    }

    protected void releaseWriteReference() {
        if (!ENTITY_DATA_REF_COUNT_HANDLE.compareAndSet(this, -1, 0)) {
            throw new IllegalStateException("Releasing when not write-locked");
        }
    }

    protected void acquireWriteReference() {
        int failureCount = 0;
        for (;;) {
            for (int i = 0; i < failureCount; i++) {
                ConcurrentUtil.backoff();
            }

            final int curr = (int) ENTITY_DATA_REF_COUNT_HANDLE.getVolatile(this);

            if (curr > 0 || curr == -1) {
                failureCount++;
                continue;
            }

            if (!ENTITY_DATA_REF_COUNT_HANDLE.compareAndSet(this, curr, -1)) {
                failureCount++;
                continue;
            }

            break;
        }
    }

    protected void releaseReadReference() {
        int failureCount = 0;
        for (;;) {
            for (int i = 0; i < failureCount; i++) {
                ConcurrentUtil.backoff();
            }

            final int curr = (int) ENTITY_DATA_REF_COUNT_HANDLE.getVolatile(this);

            if (curr == -1) {
                throw new IllegalStateException("Cannot release read reference when write locked");
            }

            if (curr == 0) {
                throw new IllegalStateException("Setting reference count down to a value lower than 0!");
            }

            if (!ENTITY_DATA_REF_COUNT_HANDLE.compareAndSet(this, curr, curr - 1)) {
                failureCount++;
                continue;
            }

            break;
        }
    }

    protected void acquireReadReference() {
        int failureCount = 0;
        for (;;) {
            for (int i = 0; i < failureCount; i++) {
                ConcurrentUtil.backoff();
            }

            final int curr = (int) ENTITY_DATA_REF_COUNT_HANDLE.getVolatile(this);

            if (curr == -1) {
                failureCount++;
                continue;
            }

            if (!ENTITY_DATA_REF_COUNT_HANDLE.compareAndSet(this, curr, curr + 1)) {
                failureCount++;
                continue;
            }

            break;
        }
    }

    @Nullable
    @Override
    public ProxiedPlayer getOwner() {
        return this.player;
    }

    protected boolean isEntityStateOfSelf(int entityId) {
        final int currentWorkerEntityId = (int) WORKER_ENTITY_ID_HANDLE.getVolatile(this);

        if (currentWorkerEntityId == -1) {
            return false;
        }

        return currentWorkerEntityId == entityId;
    }

    @Override
    public void setParentHandler(MapperSessionProcessor handler) {
        this.handler = handler;
    }

    @Override
    public void sendEntityStateTo(@NotNull ProxiedPlayer target) {
        this.acquireReadReference();

        final int currEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);
        final YsmState currEntityData = (YsmState) LAST_YSM_ENTITY_DATA_HANDLE.getVolatile(this);

        this.releaseReadReference();
        if (currEntityId == -1 || currEntityData == null) {
            return;
        }

        this.sendEntityStateToRaw(target.getUniqueId(), currEntityId, currEntityData);
    }

    @Override
    public String getYsmVersion() {
        return this.ysmVersion;
    }

    @Override
    public void setYsmVersion(String version) {
        if (version != null) {
            this.ysmVersion = version;
        }
    }

    @Override
    public void setEntityDataRaw(YsmState data) {
        LAST_YSM_ENTITY_DATA_HANDLE.setVolatile(this, data);
    }

    @Override
    public void notifyFullTrackerUpdates() {
        this.acquireReadReference();

        final YsmState currEntityData = (YsmState) LAST_YSM_ENTITY_DATA_HANDLE.getVolatile(this);
        final int currEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);

        this.releaseReadReference();

        if (currEntityId == -1 || currEntityData == null) {
            return;
        }

        if (PROXY_READY_HANDLE.compareAndSet(this, false, true)) {
            if (this.handler != null) {
                this.handler.retireTrackerCallbacks();
            }
        }

        this.sendEntityStateToRaw(this.playerUUID, currEntityId, currEntityData);

        this.fetchTrackerList(this.playerUUID).whenComplete((result, ex) -> {
            if (ex != null) {
                Freesia.LOGGER.log(java.util.logging.Level.WARNING, "Failed to fetch tracker list for player uuid "
                        + (this.player != null ? this.player.getUniqueId() : this.playerUUID) + ": " + ex);
                return;
            }

            for (UUID toSend : result) {
                final ProxiedPlayer queryResult = Freesia.PROXY_SERVER.getPlayer(toSend);

                if (queryResult != null) {
                    if (Freesia.mapperManager.isPlayerInstalledYsm(toSend)) {
                        this.sendEntityStateToRaw(toSend, currEntityId, currEntityData);
                    }
                }
            }
        });
    }

    public abstract CompletableFuture<Set<UUID>> fetchTrackerList(UUID observer);

    @Override
    public void executeMolang(String expression) {
        final int playerEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);

        if (playerEntityId == -1 || this.player == null) {
            return;
        }

        final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());
        wrappedPacketData.writeByte(YsmProtocolMetaFile
                .getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.MOLANG_EXECUTE));
        wrappedPacketData.writeVarIntArray(new int[] { playerEntityId });
        wrappedPacketData.writeUtf(expression);

        this.sendPluginMessageToOwner(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
    }

    @Override
    public void executeMolang(int[] entityIds, String expression) {
        if (this.player == null) {
            return;
        }

        final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());
        wrappedPacketData.writeByte(YsmProtocolMetaFile
                .getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.MOLANG_EXECUTE));
        wrappedPacketData.writeVarIntArray(entityIds);
        wrappedPacketData.writeUtf(expression);

        this.sendPluginMessageToOwner(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
    }

    protected void sendEntityStateToRaw(@NotNull UUID receiverUUID, int entityId, YsmState data) {
        try {
            final ProxiedPlayer queryResult = Freesia.PROXY_SERVER.getPlayer(receiverUUID);

            if (queryResult == null) {
                return;
            }

            final ProxiedPlayer receiver = queryResult;
            final Object targetChannel = PacketEvents.getAPI().getProtocolManager().getChannel(receiver.getUniqueId());
            if (targetChannel == null) {
                return;
            }

            final ClientVersion clientVersion = PacketEvents.getAPI().getProtocolManager()
                    .getClientVersion(targetChannel);

            final int targetProtocolVer = clientVersion.getProtocolVersion();
            final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());

            wrappedPacketData.writeByte(4);
            wrappedPacketData.writeVarInt(entityId);
            if (data.isBinary()) {
                wrappedPacketData.writeBytes(data.getBinary());
            } else {
                wrappedPacketData.writeBytes(
                        this.nbtRemapper.shouldRemap(targetProtocolVer)
                                ? this.nbtRemapper.remapToMasterVer(data.getNbt())
                                : this.nbtRemapper.remapToWorkerVer(data.getNbt()));
            }

            this.sendPluginMessageTo(receiver, YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
        } catch (Exception e) {
            Freesia.LOGGER.log(java.util.logging.Level.SEVERE, "Error in encoding nbt or sending packet!", e);
        }
    }

    @Override
    public YsmState getCurrentEntityState() {
        return (YsmState) LAST_YSM_ENTITY_DATA_HANDLE.getVolatile(this);
    }

    @Override
    public void setPlayerWorkerEntityId(int id) {
        final boolean successfullyUpdated = WORKER_ENTITY_ID_HANDLE.compareAndSet(this, -1, id);

        if (successfullyUpdated) {
            this.notifyFullTrackerUpdates();
        }
    }

    @Override
    public void setPlayerEntityId(int id) {
        final boolean successfullyUpdated = PLAYER_ENTITY_ID_HANDLE.compareAndSet(this, -1, id);

        if (successfullyUpdated) {
            this.notifyFullTrackerUpdates();
        }
    }

    @Override
    public int getPlayerEntityId() {
        return (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);
    }

    @Override
    public int getPlayerWorkerEntityId() {
        return (int) WORKER_ENTITY_ID_HANDLE.getVolatile(this);
    }
}
