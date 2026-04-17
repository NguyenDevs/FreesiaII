package com.nguyendevs.freesia.velocity.network.ysm;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.nguyendevs.freesia.velocity.FreesiaConstants;
import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.FreesiaConfig;
import com.nguyendevs.freesia.velocity.YsmProtocolMetaFile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class YsmMapperPayloadManager {
    public static final Key YSM_CHANNEL_KEY_ADVENTURE = Key
            .key(YsmProtocolMetaFile.getYsmChannelNamespace() + ":" + YsmProtocolMetaFile.getYsmChannelPath());
    public static final MinecraftChannelIdentifier YSM_CHANNEL_KEY_VELOCITY = MinecraftChannelIdentifier
            .create(YsmProtocolMetaFile.getYsmChannelNamespace(), YsmProtocolMetaFile.getYsmChannelPath());

    private final Map<UUID, YsmPacketProxy> virtualProxies = Maps.newHashMap();
    private final Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual;

    private final Map<Player, MapperSessionProcessor> mapperSessions = Maps.newConcurrentMap();
    private final Function<Player, YsmPacketProxy> packetProxyCreator;

    private final ReadWriteLock backendIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<InetSocketAddress, Integer> backend2Players = Maps.newLinkedHashMap();
    private final Set<UUID> ysmInstalledPlayers = Sets.newConcurrentHashSet();

    private final Map<InetSocketAddress, Map<Integer, Integer>> worker2PlayerEntityIdCache = Maps.newConcurrentMap();

    public YsmMapperPayloadManager(Function<Player, YsmPacketProxy> packetProxyCreator,
            Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual) {
        this.packetProxyCreator = packetProxyCreator;
        this.packetProxyCreatorVirtual = packetProxyCreatorVirtual;
        this.backend2Players.put(FreesiaConfig.workerMSessionAddress, 1);
    }

    @Nullable
    public MapperSessionProcessor getMapperSession(Player player) {
        return this.mapperSessions.get(player);
    }

    public void onClientYsmHandshakePacketReply(@NotNull Player target) {
        this.ysmInstalledPlayers.add(target.getUniqueId());
    }

    public void updateWorkerPlayerEntityId(Player target, int entityId) {
        final MapperSessionProcessor mapper = this.mapperSessions.get(target);

        if (mapper == null) {
            throw new IllegalStateException("Mapper not created yet!");
        }

        mapper.getPacketProxy().setPlayerWorkerEntityId(entityId);

        final int playerEntityId = mapper.getPacketProxy().getPlayerEntityId();
        final InetSocketAddress remoteAddress = mapper.getRemoteAddress();

        if (playerEntityId != -1 && remoteAddress != null) {
            this.worker2PlayerEntityIdCache.computeIfAbsent(remoteAddress, k -> Maps.newConcurrentMap())
                    .put(entityId, playerEntityId);
        }
    }

    public void updateRealPlayerEntityId(Player target, int entityId) {
        final MapperSessionProcessor mapper = this.mapperSessions.get(target);

        if (mapper == null) {
            throw new IllegalStateException("Mapper not created yet!");
        }

        mapper.getPacketProxy().setPlayerEntityId(entityId);

        final int workerEntityId = mapper.getPacketProxy().getPlayerWorkerEntityId();
        final InetSocketAddress remoteAddress = mapper.getRemoteAddress();

        if (workerEntityId != -1 && remoteAddress != null) {
            this.worker2PlayerEntityIdCache.computeIfAbsent(remoteAddress, k -> Maps.newConcurrentMap())
                    .put(workerEntityId, entityId);
        }
    }

    public CompletableFuture<Boolean> setVirtualPlayerEntityState(UUID playerUUID, NBTCompound nbt) {
        final YsmPacketProxy virtualProxy;

        synchronized (this.virtualProxies) {
            virtualProxy = this.virtualProxies.get(playerUUID);
        }

        if (virtualProxy == null) {
            return CompletableFuture.completedFuture(false);
        }

        virtualProxy.setEntityDataRaw(YsmState.ofNbt(nbt));
        virtualProxy.notifyFullTrackerUpdates();

        final YsmState entityState = virtualProxy.getCurrentEntityState();

        if (entityState == null || entityState.isBinary()) {
            return CompletableFuture.completedFuture(true);
        }

        final NBTCompound entityData = entityState.getNbt();

        final CompletableFuture<Boolean> callback = new CompletableFuture<>();

        Freesia.PROXY_SERVER.getScheduler().buildTask(Freesia.INSTANCE, () -> {
            try {
                final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final DataOutputStream dos = new DataOutputStream(bos);

                serializer.serializeTag(dos, entityData, true);
                dos.flush();

                Freesia.virtualPlayerDataStorageManager.save(playerUUID, bos.toByteArray())
                        .whenComplete((unused, ex) -> {
                            if (ex != null) {
                                callback.completeExceptionally(ex);
                                return;
                            }

                            callback.complete(true);
                        });
            } catch (Exception e) {
                callback.completeExceptionally(e);
            }
        }).schedule();

        return callback;
    }

    public CompletableFuture<Boolean> setVirtualPlayerEntityStateBinary(UUID playerUUID, byte[] binary) {
        final YsmPacketProxy virtualProxy;

        synchronized (this.virtualProxies) {
            virtualProxy = this.virtualProxies.get(playerUUID);
        }

        if (virtualProxy == null) {
            return CompletableFuture.completedFuture(false);
        }

        virtualProxy.setEntityDataRaw(YsmState.ofBinary(binary));
        virtualProxy.notifyFullTrackerUpdates();
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> addVirtualPlayer(UUID playerUUID, int playerEntityId) {
        if (Freesia.PROXY_SERVER.getPlayer(playerUUID).isPresent()) {
            return CompletableFuture.completedFuture(false);
        }

        synchronized (this.virtualProxies) {
            if (this.virtualProxies.containsKey(playerUUID)) {
                final YsmPacketProxy existing = this.virtualProxies.get(playerUUID);
                existing.setPlayerEntityId(playerEntityId);
                return CompletableFuture.completedFuture(true);
            }

            final CompletableFuture<Boolean> callback = new CompletableFuture<>();

            final YsmPacketProxy createdVirtualProxy = this.virtualProxies.computeIfAbsent(playerUUID,
                    this.packetProxyCreatorVirtual);

            Freesia.virtualPlayerDataStorageManager.loadPlayerData(playerUUID).whenComplete((data, ex) -> {
                if (ex != null) {
                    callback.completeExceptionally(ex);
                }

                if (data == null) {
                    createdVirtualProxy.setPlayerEntityId(playerEntityId);
                    createdVirtualProxy.setPlayerWorkerEntityId(0);
                    callback.complete(true);
                    return;
                }

                try {
                    final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                    final NBTCompound read = (NBTCompound) serializer.deserializeTag(
                            NBTLimiter.forBuffer(data, Integer.MAX_VALUE),
                            new DataInputStream(new ByteArrayInputStream(data)), true);

                    createdVirtualProxy.setEntityDataRaw(YsmState.ofNbt(read));
                    createdVirtualProxy.setPlayerEntityId(playerEntityId);
                    createdVirtualProxy.setPlayerWorkerEntityId(0);
                } catch (Exception ex1) {
                    Freesia.LOGGER.warn("[addVirtualPlayer] Corrupt stored data for {}, starting clean: {}",
                            playerUUID, ex1.getMessage());
                    createdVirtualProxy.setPlayerEntityId(playerEntityId);
                    createdVirtualProxy.setPlayerWorkerEntityId(0);
                }

                callback.complete(true);
            });

            return callback;
        }
    }

    public CompletableFuture<Boolean> removeVirtualPlayer(UUID playerUUID) {
        final YsmPacketProxy removedProxy;

        synchronized (this.virtualProxies) {
            removedProxy = this.virtualProxies.remove(playerUUID);

            if (removedProxy == null) {
                return CompletableFuture.completedFuture(false);
            }
        }

        final YsmState entityState = removedProxy.getCurrentEntityState();

        if (entityState == null || entityState.isBinary()) {
            return CompletableFuture.completedFuture(true);
        }

        final NBTCompound entityData = entityState.getNbt();

        final CompletableFuture<Boolean> callback = new CompletableFuture<>();

        Freesia.PROXY_SERVER.getScheduler().buildTask(Freesia.INSTANCE, () -> {
            try {
                final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final DataOutputStream dos = new DataOutputStream(bos);

                serializer.serializeTag(dos, entityData, true);
                dos.flush();

                Freesia.virtualPlayerDataStorageManager.save(playerUUID, bos.toByteArray()).whenComplete((r, e) -> {
                    if (e != null) {
                        callback.completeExceptionally(e);
                        return;
                    }

                    callback.complete(true);
                });
            } catch (Exception e) {
                callback.completeExceptionally(e);
            }
        }).schedule();

        return callback;
    }

    private void disconnectMapperWithoutKickingMaster(@NotNull MapperSessionProcessor connection) {
        connection.setKickMasterWhenDisconnect(false);
        connection.destroyAndAwaitDisconnected();
    }

    public Map<Integer, Integer> collectRealProxy2WorkerEntityId(InetSocketAddress remoteAddress) {
        return Collections.unmodifiableMap(this.worker2PlayerEntityIdCache.getOrDefault(remoteAddress, Map.of()));
    }

    public void autoCreateMapper(Player player) {
        this.createMapperSession(player, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean isPlayerInstalledYsm(@NotNull Player target) {
        return this.ysmInstalledPlayers.contains(target.getUniqueId());
    }

    public boolean isPlayerInstalledYsm(UUID target) {
        return this.ysmInstalledPlayers.contains(target);
    }

    public void onPlayerDisconnect(@NotNull Player player) {
        this.ysmInstalledPlayers.remove(player.getUniqueId());

        final MapperSessionProcessor mapperSession = this.mapperSessions.remove(player);

        if (mapperSession != null) {
            this.disconnectMapperWithoutKickingMaster(mapperSession);

            final int workerEntityId = mapperSession.getPacketProxy().getPlayerWorkerEntityId();
            final InetSocketAddress remoteAddress = mapperSession.getRemoteAddress();

            if (workerEntityId != -1 && remoteAddress != null) {
                final Map<Integer, Integer> workerMap = this.worker2PlayerEntityIdCache.get(remoteAddress);

                if (workerMap != null) {
                    workerMap.remove(workerEntityId);

                    if (workerMap.isEmpty()) {
                        this.worker2PlayerEntityIdCache.remove(remoteAddress);
                    }
                }
            }
        }
    }

    protected void onWorkerSessionDisconnect(@NotNull MapperSessionProcessor mapperSession, boolean kickMaster,
            @Nullable Component reason) {
        if (kickMaster)
            mapperSession.getBindPlayer().disconnect(Freesia.languageManager.i18n(
                    FreesiaConstants.LanguageConstants.WORKER_TERMINATED_CONNECTION,
                    List.of("reason"),
                    List.of(reason == null ? Component.text("DISCONNECTED MANUAL") : reason)));

        this.mapperSessions.remove(mapperSession.getBindPlayer());
    }

    public void onPluginMessageIn(@NotNull Player player, @NotNull MinecraftChannelIdentifier channel,
            byte[] packetData) {
        if (FreesiaConfig.debug) {
            final StringBuilder debugHex = new StringBuilder();
            for (int i = 0; i < Math.min(packetData.length, 16); i++) {
                debugHex.append(String.format("%02X ", packetData[i]));
            }
            Freesia.LOGGER.info("[DEBUG] YSM Packet from {} on {} (len={}): {}", player.getUsername(), channel.getId(),
                    packetData.length, debugHex.toString());
        }

        if (!channel.equals(YSM_CHANNEL_KEY_VELOCITY)) {
            return;
        }

        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null) {
            Freesia.LOGGER.warn("Mapper session not found or ready for player {}", player.getUsername());
            return;
        }

        mapperSession.processPlayerPluginMessage(packetData);
    }

    public void onBackendReady(Player player) {
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null) {
            return;
        }

        mapperSession.onBackendReady();
    }

    public static class SavedProxyState {
        public final YsmState state;
        public final String version;
        public final boolean hasHandshaked;

        public SavedProxyState(YsmState state, String version, boolean hasHandshaked) {
            this.state = state;
            this.version = version;
            this.hasHandshaked = hasHandshaked;
        }
    }

    @Nullable
    public SavedProxyState extractSavedProxyStateAndDisconnect(Player player) {
        final MapperSessionProcessor current = this.mapperSessions.get(player);

        if (current == null) {
            return null;
        }

        YsmPacketProxy proxy = current.getPacketProxy();
        YsmState state = proxy.getCurrentEntityState();
        String version = proxy.getYsmVersion();
        boolean hasHandshaked = proxy.hasHandshaked();

        this.disconnectMapperWithoutKickingMaster(current);
        return new SavedProxyState(state, version, hasHandshaked);
    }

    public boolean hasMapperSession(Player player) {
        return this.mapperSessions.containsKey(player);
    }

    public boolean disconnectAlreadyConnected(Player player) {
        final MapperSessionProcessor current = this.mapperSessions.get(player);

        if (current == null) {
            return false;
        }

        this.disconnectMapperWithoutKickingMaster(current);
        return true;
    }

    public void initMapperPacketProcessor(@NotNull Player player, @Nullable SavedProxyState transferredState) {
        final MapperSessionProcessor possiblyExisting = this.mapperSessions.get(player);

        if (possiblyExisting != null) {
            throw new IllegalStateException("Mapper session already exists for player " + player.getUsername());
        }

        final YsmPacketProxy packetProxy = this.packetProxyCreator.apply(player);

        if (transferredState != null) {
            if (transferredState.state != null) {
                packetProxy.setEntityDataRaw(transferredState.state);
            }
            if (transferredState.version != null) {
                packetProxy.setYsmVersion(transferredState.version);
            }
            packetProxy.setHasHandshaked(transferredState.hasHandshaked);
        }

        final MapperSessionProcessor processor = new MapperSessionProcessor(player, packetProxy, this);

        packetProxy.setParentHandler(processor);

        this.mapperSessions.put(player, processor);
    }

    public void initMapperPacketProcessor(@NotNull Player player) {
        this.initMapperPacketProcessor(player, null);
    }

    public void createMapperSession(@NotNull Player player, @NotNull InetSocketAddress backend) {
        final TcpClientSession mapperSession = new TcpClientSession(
                backend.getHostName(),
                backend.getPort(),
                new MinecraftProtocol(
                        new GameProfile(
                                player.getUniqueId(),
                                player.getUsername()),
                        null));

        final MapperSessionProcessor packetProcessor = this.mapperSessions.get(player);

        if (packetProcessor == null) {
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getUsername());
        }

        packetProcessor.setSession(mapperSession);
        mapperSession.addListener(packetProcessor);

        mapperSession.setFlag(BuiltinFlags.READ_TIMEOUT, 30_000);
        mapperSession.setFlag(BuiltinFlags.WRITE_TIMEOUT, 30_000);

        mapperSession.connect(true, false);
    }

    public void onVirtualPlayerTrackerUpdate(UUID owner, Player watcher) {
        final YsmPacketProxy virtualProxy = this.virtualProxies.get(owner);

        if (virtualProxy == null) {
            return;
        }

        if (this.isPlayerInstalledYsm(watcher)) {
            virtualProxy.sendEntityStateTo(watcher);
        }
    }

    public void onRealPlayerTrackerUpdate(Player beingWatched, Player watcher) {
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(beingWatched);

        if (mapperSession == null) {
            return;
        }

        if (this.isPlayerInstalledYsm(watcher)) {
            if (!mapperSession.queueTrackerUpdate(watcher.getUniqueId())) {
                mapperSession.getPacketProxy().sendEntityStateTo(watcher);
            }
        }
    }

    @Nullable
    private InetSocketAddress selectLessPlayer() {
        this.backendIpsAccessLock.readLock().lock();
        try {
            InetSocketAddress result = null;

            int idx = 0;
            int lastCount = 0;
            for (Map.Entry<InetSocketAddress, Integer> entry : this.backend2Players.entrySet()) {
                final InetSocketAddress currAddress = entry.getKey();
                final int currPlayerCount = entry.getValue();

                if (idx == 0) {
                    lastCount = currPlayerCount;
                    result = currAddress;
                }

                if (currPlayerCount < lastCount) {
                    lastCount = currPlayerCount;
                    result = currAddress;
                }

                idx++;
            }

            return result;
        } finally {
            this.backendIpsAccessLock.readLock().unlock();
        }
    }
}
