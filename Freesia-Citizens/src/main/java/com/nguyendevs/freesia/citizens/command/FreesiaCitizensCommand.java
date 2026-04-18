package com.nguyendevs.freesia.citizens.command;

import com.nguyendevs.freesia.citizens.FreesiaCitizensPlugin;
import com.nguyendevs.freesia.citizens.trait.YsmModelTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FreesiaCitizensCommand implements CommandExecutor, TabCompleter {

    private final FreesiaCitizensPlugin plugin;

    public FreesiaCitizensCommand(FreesiaCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("freesia.citizens.admin")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("setmodel")) {
            NPC npc = null;
            String modelId = null;

            if (args.length == 2) {
                npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
                modelId = args[1];
                if (npc == null) {
                    plugin.sendMessage(sender, "npc-not-selected");
                    return true;
                }
            } else if (args.length >= 3) {
                try {
                    int npcId = Integer.parseInt(args[1]);
                    npc = CitizensAPI.getNPCRegistry().getById(npcId);
                    modelId = args[2];
                    if (npc == null) {
                        plugin.sendMessage(sender, "npc-not-found", "<id>", String.valueOf(npcId));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
                    modelId = args[1];
                }
            } else {
                plugin.getConfig().getStringList("messages.setmodel-usage").forEach(msg -> 
                    sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));
                return true;
            }

            if (npc != null && modelId != null) {
                npc.getOrAddTrait(YsmModelTrait.class).setModelId(modelId);
                plugin.sendMessage(sender, "setmodel-success", 
                    "<model>", modelId,
                    "<name>", npc.getName(),
                    "<id>", String.valueOf(npc.getId()));

                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    dos.writeByte((byte) 4);
                    dos.writeInt(npc.getId());
                    dos.writeUTF(modelId);
                    dos.flush();
                    if (sender instanceof Player p) {
                        plugin.sendProxyPayload(p, bos.toByteArray());
                    } else {
                        Player first = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                        if (first != null) plugin.sendProxyPayload(first, bos.toByteArray());
                    }
                } catch (Exception e) {}
                return true;
            }
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.sendMessage(sender, "reload-success");
            return true;
        }

        plugin.sendMessage(sender, "help-header");
        plugin.sendMessage(sender, "help-setmodel-selected");
        plugin.sendMessage(sender, "help-setmodel-explicit");
        plugin.sendMessage(sender, "help-reload");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("freesia.citizens.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return Stream.of("setmodel", "reload")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setmodel")) {
            List<String> suggestions = new ArrayList<>();
            CitizensAPI.getNPCRegistry().forEach(npc -> suggestions.add(String.valueOf(npc.getId())));
            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
