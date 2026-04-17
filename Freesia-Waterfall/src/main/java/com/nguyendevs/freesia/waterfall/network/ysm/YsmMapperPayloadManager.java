package com.nguyendevs.freesia.waterfall.network.ysm;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import com.nguyendevs.freesia.waterfall.FreesiaConstants;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.FreesiaConfig;
import com.nguyendevs.freesia.waterfall.YsmProtocolMetaFile;
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
    // Ysm channel key name
    public static final Key YSM_CHANNEL_KEY_ADVENTURE = Key
            .key(YsmProtocolMetaFile.getYsmChannelNamespace() + ":" + YsmProtocolMetaFile.getYsmChannelPath());
    public static final String YSM_CHANNEL_KEY_VELOCITY = YsmProtocolMetaFile.getYsmChannelNamespace() + ":"
            + YsmProtocolMetaFile.getYsmChannelPath();

    // Used for virtual players like NPCs
    private final Map<UUID, YsmPacketProxy> virtualProxies = Maps.newHashMap();
    private final Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual;

    // Player to worker mappers connections
    private final Map<ProxiedPlayer, MapperSessionProcessor> mapperSessions = Maps.newConcurrentMap();
    // Real player proxy factory
    private final Function<ProxiedPlayer, YsmPacketProxy> packetProxyCreator;

    // Backend connect infos
    private final ReadWriteLock backendIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<InetSocketAddress, Integer> backend2Players = Maps.newLinkedHashMap();

    // The players who installed ysm(Used for packet sending reduction)
    private final Set<UUID> ysmInstalledPlayers = Sets.newConcurrentHashSet();

    private final Map<InetSocketAddress, Map<Integer, Integer>> worker2PlayerEntityIdCache = Maps.newConcurrentMap();
    private final Map<String, byte[]> npcModelBinaryCache = Maps.newConcurrentMap();

    public YsmMapperPayloadManager(Function<ProxiedPlayer, YsmPacketProxy> packetProxyCreator,
            Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual) {
        this.packetProxyCreator = packetProxyCreator;
        this.packetProxyCreatorVirtual = packetProxyCreatorVirtual;
        this.backend2Players.put(FreesiaConfig.workerMSessionAddress, 1); // TODO Load balance
    }

    @Nullable
    public MapperSessionProcessor getMapperSession(ProxiedPlayer player) {
        return this.mapperSessions.get(player);
    }

    public void onClientYsmHandshakePacketReply(@NotNull ProxiedPlayer target) {
        this.ysmInstalledPlayers.add(target.getUniqueId());
    }

    public void updateWorkerPlayerEntityId(ProxiedPlayer target, int entityId) {
        final MapperSessionProcessor mapper = this.mapperSessions.get(target);

        if (mapper == null) {
            Freesia.LOGGER
                    .warning("Mapper not created yet for " + target.getName() + "! Skipping worker entity id update.");
            return;
        }

        mapper.getPacketProxy().setPlayerWorkerEntityId(entityId);

        final int playerEntityId = mapper.getPacketProxy().getPlayerEntityId();
        final InetSocketAddress remoteAddress = mapper.getRemoteAddress();

        if (playerEntityId != -1 && remoteAddress != null) {
            this.worker2PlayerEntityIdCache.computeIfAbsent(remoteAddress, k -> Maps.newConcurrentMap())
                    .put(entityId, playerEntityId);
        }
    }

    public void updateRealPlayerEntityId(ProxiedPlayer target, int entityId) {
        final MapperSessionProcessor mapper = this.mapperSessions.get(target);

        if (mapper == null) {
            Freesia.LOGGER.warning(
                    "Mapper not created yet for " + target.getName() + "! Skipping real player entity id update.");
            return;
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

        // Probably be reset
        if (entityState == null || entityState.isBinary()) {
            return CompletableFuture.completedFuture(true);
        }

        final NBTCompound entityData = entityState.getNbt();

        // Async io
        final CompletableFuture<Boolean> callback = new CompletableFuture<>();

        Freesia.PROXY_SERVER.getScheduler().runAsync(Freesia.INSTANCE, () -> {
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
        });

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

    public void cacheNpcModelBinary(String modelPath, byte[] binary) {
        final String key = modelPath.toLowerCase();
        if (!npcModelBinaryCache.containsKey(key)) {
            npcModelBinaryCache.put(key, binary);
            Freesia.LOGGER.info("[YSM] Cached model binary for '" + modelPath + "' (" + binary.length + " bytes)");
        }
    }

    public byte[] getCachedNpcModelBinary(String modelId) {
        byte[] exact = npcModelBinaryCache.get(modelId);
        if (exact != null) return exact;
        return npcModelBinaryCache.get(modelId.toLowerCase());
    }

    public CompletableFuture<Boolean> addVirtualPlayer(UUID playerUUID, int playerEntityId) {
        if (Freesia.PROXY_SERVER.getPlayer(playerUUID) != null) {
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
                    Freesia.LOGGER.log(java.util.logging.Level.WARNING,
                            "[addVirtualPlayer] Corrupt stored data for " + playerUUID + ", starting clean: " + ex1.getMessage());
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

        Freesia.PROXY_SERVER.getScheduler().runAsync(Freesia.INSTANCE, () -> {
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
        });

        return callback;
    }

    private void disconnectMapperWithoutKickingMaster(@NotNull MapperSessionProcessor connection) {
        connection.setKickMasterWhenDisconnect(false);
        connection.destroyAndAwaitDisconnected();
    }

    public Map<Integer, Integer> collectRealProxy2WorkerEntityId(InetSocketAddress remoteAddress) {
        return Collections.unmodifiableMap(this.worker2PlayerEntityIdCache.getOrDefault(remoteAddress, Map.of()));
    }

    public void autoCreateMapper(ProxiedPlayer player) {
        this.createMapperSession(player, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean isPlayerInstalledYsm(@NotNull ProxiedPlayer target) {
        return this.ysmInstalledPlayers.contains(target.getUniqueId());
    }

    public boolean isPlayerInstalledYsm(UUID target) {
        return this.ysmInstalledPlayers.contains(target);
    }

    public void onPlayerDisconnect(@NotNull ProxiedPlayer player) {
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
        // Kick the master it binds
        if (kickMaster)
            mapperSession.getBindPlayer().disconnect(Freesia.languageManager.i18n(
                    FreesiaConstants.LanguageConstants.WORKER_TERMINATED_CONNECTION,
                    List.of("reason"),
                    List.of(reason == null ? Component.text("DISCONNECTED MANUAL") : reason)).toString()); // In Bungee
                                                                                                           // you pass
                                                                                                           // TextComponent
                                                                                                           // or String.
                                                                                                           // Wait, we
                                                                                                           // should use
                                                                                                           // basecomponent
                                                                                                           // if using
                                                                                                           // KYORI
                                                                                                           // directly,
                                                                                                           // but
                                                                                                           // toString
                                                                                                           // for now.
                                                                                                           // Actually,
                                                                                                           // Kyori
                                                                                                           // Bungee
                                                                                                           // adapter
                                                                                                           // exists but
                                                                                                           // let's use
                                                                                                           // BungeeCord
                                                                                                           // string or
                                                                                                           // base
                                                                                                           // components.
                                                                                                           // We'll fix
                                                                                                           // i18n
                                                                                                           // later.

        // Remove from list
        this.mapperSessions.remove(mapperSession.getBindPlayer());
    }

    public void onPluginMessageIn(@NotNull ProxiedPlayer player, @NotNull String channel, byte[] packetData) {
        if (FreesiaConfig.debug) {
            final StringBuilder debugHex = new StringBuilder();
            for (int i = 0; i < Math.min(packetData.length, 16); i++) {
                debugHex.append(String.format("%02X ", packetData[i]));
            }
            Freesia.LOGGER.info("[DEBUG] YSM Packet from " + player.getName() + " on " + channel + " (len="
                    + packetData.length + "): " + debugHex.toString());
        }

        // Check if it is the message of ysm
        if (!channel.equals(YSM_CHANNEL_KEY_VELOCITY)) {
            return;
        }

        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null) {
            // Actually it shouldn't be and never be happened
            Freesia.LOGGER.warning("Mapper session not found or ready for player " + player.getName());
            return;
        }

        mapperSession.processPlayerPluginMessage(packetData);
    }

    public void onBackendReady(ProxiedPlayer player) {
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null) {
            // race condition: already disconnected
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
    public SavedProxyState extractSavedProxyStateAndDisconnect(ProxiedPlayer player) {
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

    public boolean hasMapperSession(ProxiedPlayer player) {
        return this.mapperSessions.containsKey(player);
    }

    public boolean disconnectAlreadyConnected(ProxiedPlayer player) {
        final MapperSessionProcessor current = this.mapperSessions.get(player);

        // Not exists or created
        if (current == null) {
            return false;
        }

        // Will do remove in the callback
        this.disconnectMapperWithoutKickingMaster(current);
        return true;
    }

    public void initMapperPacketProcessor(@NotNull ProxiedPlayer player, @Nullable SavedProxyState transferredState) {
        final MapperSessionProcessor possiblyExisting = this.mapperSessions.get(player);

        if (possiblyExisting != null) {
            throw new IllegalStateException("Mapper session already exists for player " + player.getName());
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

    public void initMapperPacketProcessor(@NotNull ProxiedPlayer player) {
        this.initMapperPacketProcessor(player, null);
    }

    public void createMapperSession(@NotNull ProxiedPlayer player, @NotNull InetSocketAddress backend) {
        // Instance new session
        final TcpClientSession mapperSession = new TcpClientSession(
                backend.getHostName(),
                backend.getPort(),
                new MinecraftProtocol(
                        new GameProfile(
                                player.getUniqueId(),
                                player.getName()),
                        null));

        // Our packet processor for packet forwarding
        final MapperSessionProcessor packetProcessor = this.mapperSessions.get(player);

        if (packetProcessor == null) {
            // Should be created in ServerPreConnectEvent
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getName());
        }

        packetProcessor.setSession(mapperSession);
        mapperSession.addListener(packetProcessor);

        // Default as Minecraft client
        mapperSession.setFlag(BuiltinFlags.READ_TIMEOUT, 30_000);
        mapperSession.setFlag(BuiltinFlags.WRITE_TIMEOUT, 30_000);

        // Do connect
        mapperSession.connect(true, false);
    }

    public void onVirtualPlayerTrackerUpdate(UUID owner, ProxiedPlayer watcher) {
        final YsmPacketProxy virtualProxy = this.virtualProxies.get(owner);

        // There is no specified virtual proxy for the owner
        if (virtualProxy == null) {
            return;
        }

        if (this.isPlayerInstalledYsm(watcher)) {
            virtualProxy.sendEntityStateTo(watcher);
        }
    }

    public void onRealPlayerTrackerUpdate(ProxiedPlayer beingWatched, ProxiedPlayer watcher) {
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(beingWatched);

        // The mapper was created earlier than the player's connection turned in-game
        // state
        // so as the result, we could simply pass it down directly
        if (mapperSession == null) {
            // Should not be happened
            // We use random player as the payload of custom payload of freesia tracker, so
            // there is a possibility
            // that race condition would happen between the disconnect logic and tracker
            // update logic
            // throw new IllegalStateException("???");
            return;
        }

        if (this.isPlayerInstalledYsm(watcher)) { // Skip players who don't install ysm
            // Check if ready
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
