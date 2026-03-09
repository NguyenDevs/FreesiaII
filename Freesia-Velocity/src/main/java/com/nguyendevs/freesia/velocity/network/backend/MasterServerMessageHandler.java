package com.nguyendevs.freesia.velocity.network.backend;

import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandlerContext;
import com.nguyendevs.freesia.common.EntryPoint;
import com.nguyendevs.freesia.common.communicating.handler.NettyServerChannelHandlerLayer;
import com.nguyendevs.freesia.common.communicating.message.m2w.M2WDispatchCommandMessage;
import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.events.PlayerEntityDataLoadEvent;
import com.nguyendevs.freesia.velocity.events.PlayerEntityDataStoreEvent;
import com.nguyendevs.freesia.velocity.events.WorkerConnectedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import com.velocitypowered.api.proxy.Player;
import com.nguyendevs.freesia.velocity.network.ysm.MapperSessionProcessor;
import com.nguyendevs.freesia.velocity.network.ysm.YsmState;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

public class MasterServerMessageHandler extends NettyServerChannelHandlerLayer {
    private final Map<Integer, Consumer<String>> pendingCommandDispatches = Maps.newConcurrentMap();
    private final AtomicInteger traceIdGenerator = new AtomicInteger(0);

    private volatile UUID workerUUID;
    private volatile String workerName;

    private volatile boolean commandDispatcherRetired = false;
    private final StampedLock commandDispatchCallbackLock = new StampedLock();

    public void dispatchCommandToWorker(String command, Consumer<Component> onDispatched) {
        final long stamp = this.commandDispatchCallbackLock.readLock();
        try {
            if (this.commandDispatcherRetired) {
                onDispatched.accept(null);
                return;
            }

            final int traceId = this.traceIdGenerator.getAndIncrement();

            final Consumer<String> wrappedDecoder = json -> {
                try {
                    if (json == null) {
                        onDispatched.accept(null);
                        return;
                    }

                    final Component decoded = LegacyComponentSerializer.builder().build().deserialize(json);
                    onDispatched.accept(decoded);
                } catch (Exception e) {
                    EntryPoint.LOGGER_INST.error("Failed to decode command result from worker", e);
                    onDispatched.accept(null);
                }
            };

            this.pendingCommandDispatches.put(traceId, wrappedDecoder);
            this.sendMessage(new M2WDispatchCommandMessage(traceId, command));
        } finally {
            this.commandDispatchCallbackLock.unlockRead(stamp);
        }
    }

    @Nullable
    public UUID getWorkerUUID() {
        return this.workerUUID;
    }

    @Nullable

    public String getWorkerName() {
        return this.workerName;
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) {
        this.retireAllCommandDispatchCallbacks();

        if (this.workerUUID == null) {
            return;
        }

        Freesia.registedWorkers.remove(this.workerUUID);
    }

    private void retireAllCommandDispatchCallbacks() {
        final long stamp = this.commandDispatchCallbackLock.writeLock();
        try {
            this.commandDispatcherRetired = true;
            for (Map.Entry<Integer, Consumer<String>> entry : this.pendingCommandDispatches.entrySet()) {
                entry.getValue().accept(null);
            }

            this.pendingCommandDispatches.clear();
        } finally {
            this.commandDispatchCallbackLock.unlockWrite(stamp);
        }
    }

    @Override
    public CompletableFuture<byte[]> readPlayerData(UUID playerUUID) {
        final CompletableFuture<byte[]> callback = new CompletableFuture<>();

        Optional<Player> playerOpt = Freesia.PROXY_SERVER.getPlayer(playerUUID);
        if (playerOpt.isPresent()) {
            Player player = playerOpt.get();
            MapperSessionProcessor mapperSession = Freesia.mapperManager.getMapperSession(player);
            if (mapperSession != null) {
                YsmState state = mapperSession.getPacketProxy().getCurrentEntityState();
                if (state != null) {
                    try {
                        if (state.isBinary() && state.getBinary() != null) {
                            Freesia.PROXY_SERVER.getEventManager()
                                    .fire(new PlayerEntityDataLoadEvent(playerUUID, state.getBinary()))
                                    .thenApply(PlayerEntityDataLoadEvent::getSerializedNbtData)
                                    .whenComplete((result, ex) -> {
                                        if (ex != null) {
                                            callback.completeExceptionally(ex);
                                            return;
                                        }
                                        callback.complete(result);
                                    });
                            return callback;
                        } else if (!state.isBinary() && state.getNbt() != null) {
                            final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            final DataOutputStream dos = new DataOutputStream(bos);
                            serializer.serializeTag(dos, state.getNbt(), true);
                            dos.flush();

                            Freesia.PROXY_SERVER.getEventManager()
                                    .fire(new PlayerEntityDataLoadEvent(playerUUID, bos.toByteArray()))
                                    .thenApply(PlayerEntityDataLoadEvent::getSerializedNbtData)
                                    .whenComplete((result, ex) -> {
                                        if (ex != null) {
                                            callback.completeExceptionally(ex);
                                            return;
                                        }
                                        callback.complete(result);
                                    });
                            return callback;
                        }
                    } catch (Exception e) {
                        EntryPoint.LOGGER_INST.error("Failed to serialize in-memory player data for " + playerUUID, e);
                    }
                }
            }
        }

        Freesia.realPlayerDataStorageManager
                .loadPlayerData(playerUUID)
                .thenApply(data -> Freesia.PROXY_SERVER
                        .getEventManager()
                        .fire(new PlayerEntityDataLoadEvent(playerUUID, data))
                        .thenApply(PlayerEntityDataLoadEvent::getSerializedNbtData)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                callback.completeExceptionally(ex);
                                return;
                            }

                            callback.complete(result);
                        }));
        return callback;
    }

    @Override
    public CompletableFuture<Void> savePlayerData(UUID playerUUID, byte[] content) {
        final CompletableFuture<Void> callback = new CompletableFuture<>();

        Freesia.PROXY_SERVER
                .getEventManager()
                .fire(new PlayerEntityDataStoreEvent(playerUUID, content))
                .thenAccept(event -> {
                    Freesia.realPlayerDataStorageManager.save(playerUUID, event.getSerializedNbtData())
                            .whenComplete((res, ex) -> {
                                if (ex != null) {
                                    callback.completeExceptionally(ex);
                                    return;
                                }

                                callback.complete(res);
                            });
                });

        return callback;
    }

    @Override
    public void onCommandDispatchResult(int traceId, @Nullable String result) {
        final Consumer<String> removedDecoder = this.pendingCommandDispatches.remove(traceId);

        if (removedDecoder != null) {
            removedDecoder.accept(result);
        }
    }

    @Override
    public void updateWorkerInfo(UUID workerUUID, String workerName) {
        EntryPoint.LOGGER_INST.info("Worker {} (UUID: {}) connected", workerName, workerUUID);

        this.workerName = workerName;
        this.workerUUID = workerUUID;

        Freesia.registedWorkers.put(workerUUID, this);

        Freesia.PROXY_SERVER.getEventManager().fire(new WorkerConnectedEvent(workerUUID, workerName));
    }
}
