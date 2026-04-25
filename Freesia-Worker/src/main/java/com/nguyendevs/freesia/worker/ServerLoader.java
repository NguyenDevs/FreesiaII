package com.nguyendevs.freesia.worker;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import com.nguyendevs.freesia.common.EntryPoint;
import com.nguyendevs.freesia.common.communicating.NettySocketClient;
import com.nguyendevs.freesia.worker.impl.WorkerMessageHandlerImpl;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import com.nguyendevs.freesia.common.communicating.message.w2m.W2MDispatchCommandRequestMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ServerLoader implements DedicatedServerModInitializer {
    public static NettySocketClient clientInstance;
    public static volatile WorkerMessageHandlerImpl workerConnection = new WorkerMessageHandlerImpl();
    public static MinecraftServer SERVER_INST;
    public static WorkerInfoFile workerInfoFile;
    public static Cache<UUID, CompoundTag> playerDataCache;

    public static void connectToBackend() {
        EntryPoint.LOGGER_INST.info("Connecting to the master.");
        clientInstance.connect();
    }

    @Override
    public void onInitializeServer() {
        EntryPoint.initLogger(LogUtils.getLogger());

        try {
            FreesiaWorkerConfig.init();
            workerInfoFile = WorkerInfoFile.readOrCreate(new File("freesia_node_info.bin"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        playerDataCache = CacheBuilder
                .newBuilder()
                .expireAfterWrite(FreesiaWorkerConfig.playerDataCacheInvalidateIntervalSeconds, TimeUnit.SECONDS)
                .build();
        io.netty.handler.ssl.SslContext sslContext = null;
        try {
            if (FreesiaWorkerConfig.enableTls) {
                sslContext = com.nguyendevs.freesia.common.SslUtils.createClientContext(
                        FreesiaWorkerConfig.trustAll,
                        new File("config", FreesiaWorkerConfig.trustCertPath).getAbsolutePath(),
                        new File("config", FreesiaWorkerConfig.workerCertPath).getAbsolutePath(),
                        new File("config", FreesiaWorkerConfig.workerKeyPath).getAbsolutePath()
                );
            }
        } catch (Exception e) {
            EntryPoint.LOGGER_INST.error("Failed to initialize SSL context!", e);
        }

        clientInstance = new NettySocketClient(FreesiaWorkerConfig.masterServiceAddress, c -> workerConnection = new WorkerMessageHandlerImpl(), FreesiaWorkerConfig.reconnectInterval, sslContext) {
            @Override
            protected boolean shouldDoNextReconnect() {
                return SERVER_INST == null || SERVER_INST.isRunning();
            }
        };

        connectToBackend();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("freesia")
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(context -> {
                                String command = StringArgumentType.getString(context, "args");
                                if (clientInstance != null) {
                                    clientInstance.sendToMaster(new W2MDispatchCommandRequestMessage("freesia " + command));
                                }
                                return 1;
                            })));
        });
    }
}

