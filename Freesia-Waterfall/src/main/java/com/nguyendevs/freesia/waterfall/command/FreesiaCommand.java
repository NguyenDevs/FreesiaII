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
import java.util.Map;
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
            case "setskin" -> {
                if (isCitizensSupported(sender)) {
                    handleSetSkin(sender, args);
                } else {
                    sendUsage(sender);
                }
            }
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
    }

    private void handleListPlayers(CommandSender sender) {
        if (!sender.hasPermission(FreesiaConstants.PermissionConstants.LIST_PLAYER_COMMAND)) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_NO_PERMISSION, List.of(), List.of()));
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
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_NO_PERMISSION, List.of(), List.of()));
            return;
        }

        if (args.length < 3) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_USAGE_DWORKERC, List.of(), List.of()));
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
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_NOT_FOUND, List.of(), List.of()));
            return;
        }

        targetWorker.dispatchCommandToWorker(command, feedback -> {
            if (feedback == null) {
                return;
            }
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_COMMAND_FEEDBACK,
                    List.of("worker_name", "feedback"), List.of(workerName, feedback.toString())));
        });
    }

    private void handleSetSkin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(FreesiaConstants.PermissionConstants.SET_SKIN_COMMAND)) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_NO_PERMISSION, List.of(), List.of()));
            return;
        }

        if (args.length < 3) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_USAGE_SETSKIN, List.of(), List.of()));
            return;
        }

        final int npcId;
        try {
            npcId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_CITIZENS_ID_INTEGER,
                    List.of("Citizens_id"), List.of(args[1])));
            return;
        }

        final String modelId = args[2];
        String serverName = null;

        if (args.length >= 4) {
            serverName = args[3];
        } else if (sender instanceof ProxiedPlayer player) {
            serverName = player.getServer() != null ? player.getServer().getInfo().getName() : null;
        }

        if (serverName == null) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_PROVIDE_SERVER_NAME, List.of(), List.of()));
            return;
        }
        
        if (!isCitizensSupported(sender)) {
             sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_NOT_CONNECTED, List.of(), List.of()));
             return;
        }

        Freesia.mapperManager.citizensPersistenceManager.saveAssignment(serverName, npcId, modelId);
        Freesia.mapperManager.broadcastCitizensSkinUpdate(serverName, npcId);

        sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.SETSKIN_SUCCESS,
                List.of("Citizens_id", "model_id"), List.of(String.valueOf(npcId), modelId)));
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(FreesiaConstants.PermissionConstants.RELOAD_COMMAND)) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_NO_PERMISSION, List.of(), List.of()));
            return;
        }

        try {
            com.nguyendevs.freesia.waterfall.FreesiaConfig.init();
            com.nguyendevs.freesia.waterfall.FreesiaSecurityConfig.init();
            Freesia.languageManager.loadLanguageFile(com.nguyendevs.freesia.waterfall.FreesiaConfig.languageName);
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_RELOAD_SUCCESS, List.of(), List.of()));
        } catch (Exception e) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_RELOAD_FAIL,
                    List.of("error"), List.of(e.getMessage() != null ? e.getMessage() : "unknown")));
            Freesia.LOGGER.severe("Failed to reload configurations: " + e.getMessage());
        }
    }

    private void sendUsage(CommandSender sender) {
        sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_USAGE_HEADER, List.of(), List.of()));
        sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_USAGE_LISTPLAYERS, List.of(), List.of()));
        sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_USAGE_DWORKERC, List.of(), List.of()));
        if (isCitizensSupported(sender)) {
            sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_USAGE_SETSKIN, List.of(), List.of()));
        }
        sendMessage(sender, Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.COMMAND_USAGE_RELOAD, List.of(), List.of()));
    }

    private boolean isCitizensSupported(CommandSender sender) {
        if (!(sender instanceof ProxiedPlayer player)) return true;
        String serverName = player.getServer() != null ? player.getServer().getInfo().getName() : null;
        return serverName != null && Freesia.citizensMessageReceiver.isSupported(serverName);
    }

    private void sendMessage(CommandSender sender, Component component) {
        sender.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(component)));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("listplayers");
            subs.add("dworkerc");
            subs.add("reload");
            if (isCitizensSupported(sender)) {
                subs.add("setskin");
            }
            for (String sub : subs) {
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
            } else if (args[0].equalsIgnoreCase("setskin") && isCitizensSupported(sender)) {
                String serverName = sender instanceof ProxiedPlayer player && player.getServer() != null ? player.getServer().getInfo().getName() : null;
                if (serverName != null) {
                    Freesia.citizensMessageReceiver.getCachedNpcNames(serverName).forEach((id, name) -> {
                        String sid = String.valueOf(id);
                        if (sid.startsWith(args[1])) {
                            completions.add(sid);
                        }
                    });
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setskin") && isCitizensSupported(sender)) {
            Freesia.mapperManager.getcitizensModelBinaryCache().keySet().forEach(model -> {
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

        if (args.length == 4 && args[0].equalsIgnoreCase("setskin")) {
            Freesia.PROXY_SERVER.getServers().values().forEach(server -> {
                if (server.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                    completions.add(server.getName());
                }
            });
        }

        return completions;
    }
}
