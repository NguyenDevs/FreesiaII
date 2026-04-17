package com.nguyendevs.freesia.velocity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.nguyendevs.freesia.common.EntryPoint;
import com.nguyendevs.freesia.common.communicating.NettySocketServer;
import com.nguyendevs.freesia.velocity.command.FreesiaCommand;
import com.nguyendevs.freesia.velocity.i18n.I18NManager;
import com.nguyendevs.freesia.velocity.network.backend.MasterServerMessageHandler;
import com.nguyendevs.freesia.velocity.network.mc.FreesiaPlayerTracker;
import com.nguyendevs.freesia.velocity.network.misc.VirtualPlayerManager;
import com.nguyendevs.freesia.velocity.network.ysm.RealPlayerYsmPacketProxyImpl;
import com.nguyendevs.freesia.velocity.network.ysm.VirtualYsmPacketProxyImpl;
import com.nguyendevs.freesia.velocity.network.ysm.YsmMapperPayloadManager;
import com.nguyendevs.freesia.velocity.network.ysm.YsmState;
import com.nguyendevs.freesia.velocity.storage.DefaultRealPlayerDataStorageManagerImpl;
import com.nguyendevs.freesia.velocity.storage.DefaultVirtualPlayerDataStorageManagerImpl;
import com.nguyendevs.freesia.velocity.storage.IDataStorageManager;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Plugin(id = "freesia", name = "Freesia", version = BuildConstants.VERSION, authors = {
        "NguyenDevs, Earthme, HappyRespawnanchor, xiaozhangup" }, dependencies = @Dependency(id = "packetevents"))
public class Freesia implements PacketListener {
    public static final FreesiaPlayerTracker tracker = new FreesiaPlayerTracker();
    public static final IDataStorageManager realPlayerDataStorageManager = new DefaultRealPlayerDataStorageManagerImpl();
    public static final IDataStorageManager virtualPlayerDataStorageManager = new DefaultVirtualPlayerDataStorageManagerImpl();
    public static final VirtualPlayerManager virtualPlayerManager = new VirtualPlayerManager();
    public static final Map<UUID, MasterServerMessageHandler> registedWorkers = Maps.newConcurrentMap();
    public static final I18NManager languageManager = new I18NManager();
    public static Freesia INSTANCE = null;
    public static Logger LOGGER = null;
    public static ProxyServer PROXY_SERVER = null;
    public static YsmClientKickingDetector kickChecker;
    public static YsmMapperPayloadManager mapperManager;
    public static NettySocketServer masterServer;
    public static com.nguyendevs.freesia.velocity.network.misc.NpcMessageReceiver npcMessageReceiver;

    @Inject
    private Logger logger;
    @Inject
    private ProxyServer proxyServer;

    private static void printLogo() {
        String RESET = "\u001B[0m";
        String PINK = "\u001B[38;5;213m";
        String PURPLE = "\u001B[38;5;99m";
        String GOLD = "\u001B[38;5;220m";
        String AQUA = "\u001B[38;5;117m";

        LOGGER.info("");
        LOGGER.info(PINK + "   ███████╗██████╗ ███████╗███████╗███████╗██╗ █████╗    ██╗██╗" + RESET);
        LOGGER.info(PINK + "   ██╔════╝██╔══██╗██╔════╝██╔════╝██╔════╝██║██╔══██╗   ██║██║" + RESET);
        LOGGER.info(PINK + "   █████╗  ██████╔╝█████╗  █████╗  ███████╗██║███████║   ██║██║" + RESET);
        LOGGER.info(PINK + "   ██╔══╝  ██╔══██╗██╔══╝  ██╔══╝  ╚════██║██║██╔══██║   ██║██║" + RESET);
        LOGGER.info(PURPLE + "   ██║     ██║  ██║███████╗███████╗███████║██║██║  ██║   ██║██║" + RESET);
        LOGGER.info(PURPLE + "   ╚═╝     ╚═╝  ╚═╝╚══════╝╚══════╝╚══════╝╚═╝╚═╝  ╚═╝   ╚═╝╚═╝" + RESET);
        LOGGER.info("");
        LOGGER.info(PURPLE + "              Powered by YesSteveModel & All Contributors" + RESET);
        LOGGER.info(GOLD + "              Version: " + BuildConstants.VERSION + RESET);
        LOGGER.info(AQUA + "              Development by NguyenDevs" + RESET);
        LOGGER.info("");
    }

    @Subscribe
    public void onProxyStart(ProxyInitializeEvent event) {
        INSTANCE = this;
        LOGGER = this.logger;
        PROXY_SERVER = this.proxyServer;

        EntryPoint.initLogger(this.logger); // Common module

        printLogo();

        LOGGER.info("Loading config file and i18n");
        try {
            FreesiaConfig.init();
            FreesiaSecurityConfig.init();
            languageManager.loadLanguageFile(FreesiaConfig.languageName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Registering events and packet listeners.");
        mapperManager = new YsmMapperPayloadManager(RealPlayerYsmPacketProxyImpl::new, VirtualYsmPacketProxyImpl::new);
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);
        this.proxyServer.getChannelRegistrar().register(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY);
        tracker.init();
        tracker.addRealPlayerTrackerEventListener(mapperManager::onRealPlayerTrackerUpdate);
        tracker.addVirtualPlayerTrackerEventListener(mapperManager::onVirtualPlayerTrackerUpdate);

        virtualPlayerManager.init();
        
        npcMessageReceiver = new com.nguyendevs.freesia.velocity.network.misc.NpcMessageReceiver();
        this.proxyServer.getEventManager().register(this, npcMessageReceiver);

        io.netty.handler.ssl.SslContext sslContext = null;
        try {
            if (FreesiaSecurityConfig.enableTls) {
                sslContext = com.nguyendevs.freesia.common.ServerSslUtils.createServerContext(
                    FreesiaSecurityConfig.useSelfSigned, 
                    java.nio.file.Paths.get(FreesiaConstants.FileConstants.PLUGIN_DIR.getAbsolutePath()).resolve(FreesiaSecurityConfig.certPath).toFile().getAbsolutePath(), 
                    java.nio.file.Paths.get(FreesiaConstants.FileConstants.PLUGIN_DIR.getAbsolutePath()).resolve(FreesiaSecurityConfig.keyPath).toFile().getAbsolutePath()
                );
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize SSL context!", e);
        }
        com.nguyendevs.freesia.common.communicating.FreesiaIpFilterHandler ipFilter = new com.nguyendevs.freesia.common.communicating.FreesiaIpFilterHandler(
                FreesiaSecurityConfig.enableIpFilter,
                FreesiaSecurityConfig.allowedWorkerIps
        );
        masterServer = new NettySocketServer(FreesiaConfig.masterServiceAddress, c -> new MasterServerMessageHandler(), sslContext, ipFilter);
        masterServer.bind();

        LOGGER.info("Initiating client kicker.");

        kickChecker = new YsmClientKickingDetector();
        kickChecker.bootstrap();

        LOGGER.info("Registering commands");
        FreesiaCommand.register();

    }

    @Subscribe
    public EventTask onPlayerDisconnect(@NotNull DisconnectEvent event) {
        final Player targetPlayer = event.getPlayer();

        return EventTask.async(() -> {
            mapperManager.onPlayerDisconnect(targetPlayer);
            kickChecker.onPlayerLeft(targetPlayer);
        });
    }

    @Subscribe
    public EventTask onPlayerConnected(@NotNull ServerConnectedEvent event) {
        final Player targetPlayer = event.getPlayer();

        return EventTask.async(() -> {
            this.logger.info("Initiating mapper session for player {}", targetPlayer.getUsername());

            kickChecker.onPlayerJoin(targetPlayer);
        });
    }

    @Subscribe
    public EventTask onServerPreConnect(@NotNull ServerPreConnectEvent event) {
        final Player player = event.getPlayer();

        return EventTask.async(() -> {
            final boolean potentialDisconnected = mapperManager.hasMapperSession(player);
            final YsmMapperPayloadManager.SavedProxyState oldState = mapperManager
                    .extractSavedProxyStateAndDisconnect(player);

            if (potentialDisconnected) {
                logger.info("Player {} has changed backend server. Reconnecting mapper session", player.getUsername());
            }
            mapperManager.initMapperPacketProcessor(player, oldState);
            mapperManager.autoCreateMapper(player);
        });
    }

    @Subscribe
    public void onChannelMsg(@NotNull PluginMessageEvent event) {
        final ChannelIdentifier identifier = event.getIdentifier();
        final byte[] data = event.getData();

        if ((identifier instanceof MinecraftChannelIdentifier mineId) && (event.getSource() instanceof Player player)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            mapperManager.onPluginMessageIn(player, mineId, data);
        }
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            final WrapperPlayServerJoinGame playerSpawnPacket = new WrapperPlayServerJoinGame(event);
            final Player target = event.getPlayer();

            logger.info("Entity id update for player {} to {}", target.getUsername(), playerSpawnPacket.getEntityId());

            mapperManager.updateRealPlayerEntityId(target, playerSpawnPacket.getEntityId());

            PROXY_SERVER.getScheduler().buildTask(this, () -> mapperManager.onBackendReady(target)).schedule();
        }
    }
}
