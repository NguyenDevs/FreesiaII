package com.nguyendevs.freesia.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.nguyendevs.freesia.velocity.FreesiaConstants;
import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.network.backend.MasterServerMessageHandler;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FreesiaCommand {

    public static void register() {
        final CommandMeta meta = Freesia.PROXY_SERVER.getCommandManager()
                .metaBuilder("freesia")
                .plugin(Freesia.INSTANCE)
                .build();

        Freesia.PROXY_SERVER.getCommandManager().register(meta, create());
    }

    public static @NotNull BrigadierCommand create() {
        LiteralCommandNode<CommandSource> root = BrigadierCommand.literalArgumentBuilder("freesia")

                .then(BrigadierCommand.literalArgumentBuilder("listplayers")
                        .requires(source -> source.hasPermission(FreesiaConstants.PermissionConstants.LIST_PLAYER_COMMAND))
                        .executes(context -> {
                            final Collection<Player> ysmPlayers = Freesia.PROXY_SERVER
                                    .getAllPlayers()
                                    .stream()
                                    .filter(player -> Freesia.mapperManager.isPlayerInstalledYsm(player))
                                    .toList();

                            Component msg = Freesia.languageManager
                                    .i18n(FreesiaConstants.LanguageConstants.PLAYER_LIST_HEADER, List.of(), List.of())
                                    .appendNewline();
                            for (Player player : ysmPlayers) {
                                msg = msg.append(Freesia.languageManager.i18n(
                                                FreesiaConstants.LanguageConstants.PLAYER_LIST_ENTRY,
                                                List.of("name"), List.of(player.getUsername())))
                                        .appendNewline();
                            }

                            context.getSource().sendMessage(msg);
                            return Command.SINGLE_SUCCESS;
                        }))

                .then(BrigadierCommand.literalArgumentBuilder("dworkerc")
                        .requires(source -> source.hasPermission(FreesiaConstants.PermissionConstants.DISPATCH_WORKER_COMMAND))
                        .then(BrigadierCommand.requiredArgumentBuilder("workerName", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (MasterServerMessageHandler conn : Freesia.registedWorkers.values()) {
                                        if (conn.getWorkerName() != null) {
                                            builder.suggest(conn.getWorkerName());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .then(BrigadierCommand.requiredArgumentBuilder("mcCommand", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            final CommandSource source = context.getSource();
                                            final String workerName = StringArgumentType.getString(context, "workerName");
                                            final String command = StringArgumentType.getString(context, "mcCommand");

                                            MasterServerMessageHandler targetWorker = null;
                                            for (MasterServerMessageHandler conn : Freesia.registedWorkers.values()) {
                                                if (workerName.equals(conn.getWorkerName())) {
                                                    targetWorker = conn;
                                                    break;
                                                }
                                            }

                                            if (targetWorker == null) {
                                                source.sendMessage(Freesia.languageManager.i18n(
                                                        FreesiaConstants.LanguageConstants.WORKER_NOT_FOUND,
                                                        List.of(), List.of()));
                                                return -1;
                                            }

                                            targetWorker.dispatchCommandToWorker(command, feedback -> {
                                                if (feedback == null) {
                                                    return;
                                                }
                                                source.sendMessage(Freesia.languageManager.i18n(
                                                        FreesiaConstants.LanguageConstants.WORKER_COMMAND_FEEDBACK,
                                                        List.of("worker_name", "feedback"),
                                                        List.of(workerName, feedback.toString())));
                                            });
                                            return Command.SINGLE_SUCCESS;
                                        }))))

                .then(BrigadierCommand.literalArgumentBuilder("setskin")
                        .requires(source -> {
                            if (!source.hasPermission(FreesiaConstants.PermissionConstants.SET_SKIN_COMMAND)) return false;
                            if (!(source instanceof Player player)) return true; // Console always sees it
                            return player.getCurrentServer().map(s -> Freesia.npcMessageReceiver.isSupported(s.getServerInfo().getName())).orElse(false);
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("npcId", IntegerArgumentType.integer(0))
                                .suggests((ctx, builder) -> {
                                    String serverName = null;
                                    if (ctx.getSource() instanceof Player player) {
                                        serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(null);
                                    }
                                    if (serverName != null) {
                                        for (Map.Entry<Integer, String> entry : Freesia.npcMessageReceiver.getCachedNpcNames(serverName).entrySet()) {
                                            builder.suggest(String.valueOf(entry.getKey()));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .then(BrigadierCommand.requiredArgumentBuilder("modelId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String model : Freesia.mapperManager.getNpcModelBinaryCache().keySet()) {
                                                if (model.toLowerCase().endsWith(".ysm")) {
                                                    builder.suggest(model.substring(0, model.length() - 4));
                                                } else {
                                                    builder.suggest(model);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            final CommandSource source = context.getSource();
                                            final int npcId = IntegerArgumentType.getInteger(context, "npcId");
                                            final String modelId = StringArgumentType.getString(context, "modelId");

                                            String serverName = null;
                                            if (source instanceof Player player) {
                                                serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(null);
                                            }

                                            if (serverName == null) {
                                                source.sendMessage(Component.text("§cPlease provide a server name (non-player source or not on a server)."));
                                                return -1;
                                            }

                                            Freesia.mapperManager.npcPersistenceManager.saveAssignment(serverName, npcId, modelId);
                                            Freesia.mapperManager.broadcastNpcSkinUpdate(serverName, npcId);

                                            source.sendMessage(Freesia.languageManager.i18n(
                                                    FreesiaConstants.LanguageConstants.SETSKIN_SUCCESS,
                                                    List.of("npc_id", "model_id"),
                                                    List.of(String.valueOf(npcId), modelId)));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(BrigadierCommand.requiredArgumentBuilder("serverId", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (com.velocitypowered.api.proxy.server.RegisteredServer server : Freesia.PROXY_SERVER.getAllServers()) {
                                                        builder.suggest(server.getServerInfo().getName());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    final CommandSource source = context.getSource();
                                                    final int npcId = IntegerArgumentType.getInteger(context, "npcId");
                                                    final String modelId = StringArgumentType.getString(context, "modelId");
                                                    final String serverName = StringArgumentType.getString(context, "serverId");

                                                    Freesia.mapperManager.npcPersistenceManager.saveAssignment(serverName, npcId, modelId);
                                                    Freesia.mapperManager.broadcastNpcSkinUpdate(serverName, npcId);

                                                    source.sendMessage(Freesia.languageManager.i18n(
                                                            FreesiaConstants.LanguageConstants.SETSKIN_SUCCESS,
                                                            List.of("npc_id", "model_id"),
                                                            List.of(String.valueOf(npcId), modelId)));
                                                    return Command.SINGLE_SUCCESS;
                                                })))))
                .then(BrigadierCommand.literalArgumentBuilder("reload")
                        .requires(source -> source.hasPermission(FreesiaConstants.PermissionConstants.RELOAD_COMMAND))
                        .executes(context -> {
                            final CommandSource source = context.getSource();
                            try {
                                com.nguyendevs.freesia.velocity.FreesiaConfig.init();
                                com.nguyendevs.freesia.velocity.FreesiaSecurityConfig.init();
                                Freesia.languageManager.loadLanguageFile(com.nguyendevs.freesia.velocity.FreesiaConfig.languageName);
                                source.sendMessage(Component.text("§a[Freesia] Proxy configurations reloaded successfully!"));
                            } catch (Exception e) {
                                source.sendMessage(Component.text("§c[Freesia] Failed to reload configurations: " + e.getMessage()));
                                Freesia.LOGGER.error("Failed to reload configurations!", e);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        return new BrigadierCommand(root);
    }
}
