package com.nguyendevs.freesia.waterfall.command;

import com.nguyendevs.freesia.waterfall.FreesiaConstants;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.network.backend.MasterServerMessageHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class FreesiaCommand extends Command implements TabExecutor {

    public FreesiaCommand() {
        super("freesia", null);
    }

    public static void register() {
        Freesia.PROXY_SERVER.getPluginManager().registerCommand(Freesia.INSTANCE, new FreesiaCommand());
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "listplayers" -> handleListPlayers(sender);
            case "dworkerc" -> handleDispatchWorker(sender, args);
            case "setskin" -> handleSetSkin(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void handleListPlayers(CommandSender sender) {
        if (!sender.hasPermission(FreesiaConstants.PermissionConstants.LIST_PLAYER_COMMAND)) {
            sender.sendMessage(TextComponent.fromLegacyText("§cNo permission."));
            return;
        }

        final Collection<ProxiedPlayer> ysmPlayers = Freesia.PROXY_SERVER
                .getPlayers()
                .stream()
                .filter(player -> Freesia.mapperManager.isPlayerInstalledYsm(player))
                .collect(Collectors.toList());

        Component msg = Freesia.languageManager
                .i18n(FreesiaConstants.LanguageConstants.PLAYER_LIST_HEADER, List.of(), List.of()).appendNewline();
        for (ProxiedPlayer player : ysmPlayers) {
            msg = msg
                    .append(Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.PLAYER_LIST_ENTRY,
                            List.of("name"), List.of(player.getName())))
                    .appendNewline();
        }

        sender.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(msg)));
    }

    private void handleDispatchWorker(CommandSender sender, String[] args) {
        if (!sender.hasPermission(FreesiaConstants.PermissionConstants.DISPATCH_WORKER_COMMAND)) {
            sender.sendMessage(TextComponent.fromLegacyText("§cNo permission."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextComponent.fromLegacyText("Usage: /freesia dworkerc <workerName> <command>"));
            return;
        }

        final String workerName = args[1];
        final StringBuilder commandBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            commandBuilder.append(args[i]).append(" ");
        }
        final String command = commandBuilder.toString().trim();

        MasterServerMessageHandler targetWorker = null;
        for (MasterServerMessageHandler conn : Freesia.registedWorkers.values()) {
            if (workerName.equals(conn.getWorkerName())) {
                targetWorker = conn;
                break;
            }
        }

        if (targetWorker == null) {
            sender.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(
                    Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_NOT_FOUND,
                            List.of(), List.of()))));
            return;
        }

        targetWorker.dispatchCommandToWorker(command, feedback -> {
            if (feedback == null) {
                return;
            }
            sender.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(
                    Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_COMMAND_FEEDBACK,
                            List.of("worker_name", "feedback"), List.of(workerName, feedback.toString())))));
        });
    }

    private void handleSetSkin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(FreesiaConstants.PermissionConstants.SET_SKIN_COMMAND)) {
            sender.sendMessage(TextComponent.fromLegacyText("§cNo permission."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextComponent.fromLegacyText("Usage: /freesia setskin <npc_id> <model_id>"));
            return;
        }

        final int npcId;
        try {
            npcId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextComponent.fromLegacyText("§c<npc_id> must be an integer."));
            return;
        }

        final String modelId = args[2];

        Freesia.mapperManager.npcPersistenceManager.saveAssignment(npcId, UUID.randomUUID(), modelId);
        Freesia.mapperManager.broadcastNpcSkinUpdate(npcId);

        sender.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(
                Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.SETSKIN_SUCCESS,
                        List.of("npc_id", "model_id"), List.of(String.valueOf(npcId), modelId)))));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextComponent.fromLegacyText("§6/freesia §elistplayers"));
        sender.sendMessage(TextComponent.fromLegacyText("§6/freesia §edworkerc §7<worker> <command>"));
        sender.sendMessage(TextComponent.fromLegacyText("§6/freesia §esetskin §7<npc_id> <model_id>"));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String sub : List.of("listplayers", "dworkerc", "setskin")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("dworkerc")) {
                for (MasterServerMessageHandler conn : Freesia.registedWorkers.values()) {
                    if (conn.getWorkerName() != null && conn.getWorkerName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(conn.getWorkerName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("setskin")) {
                Freesia.npcMessageReceiver.getCachedNpcNames().forEach((id, name) -> {
                    String sid = String.valueOf(id);
                    if (sid.startsWith(args[1])) {
                        completions.add(sid);
                    }
                });
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setskin")) {
            Freesia.mapperManager.getNpcModelBinaryCache().keySet().forEach(model -> {
                String suggestion;
                if (model.toLowerCase().endsWith(".ysm")) {
                    suggestion = model.substring(0, model.length() - 4);
                } else {
                    suggestion = model;
                }
                if (suggestion.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(suggestion);
                }
            });
        }

        return completions;
    }
}
