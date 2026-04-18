package com.nguyendevs.freesia.citizens;

import org.bukkit.Bukkit;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public class FreesiaCitizensPlugin extends JavaPlugin {
    
    public static final String CHANNEL_NAME = "freesia:npc";
    public static FreesiaCitizensPlugin INSTANCE;

    @Override
    public void onEnable() {
        INSTANCE = this;
        
        net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(YsmModelTrait.class));
        
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, CHANNEL_NAME);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, CHANNEL_NAME, new TrackerListener());
        
        getServer().getPluginManager().registerEvents(new TrackerListener(), this);

        var cmd = getCommand("freesia-citizens");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("freesia.citizens.admin")) return true;
                
                if (args.length >= 2 && args[0].equalsIgnoreCase("setmodel")) {
                    NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
                    if (npc == null) {
                        sender.sendMessage(ChatColor.RED + "Vui lòng chọn NPC trước (/npc select <id>)");
                        return true;
                    }
                    String modelId = args[1];
                    npc.getOrAddTrait(YsmModelTrait.class).setModelId(modelId);
                    sender.sendMessage(ChatColor.GREEN + "Đã gán model " + ChatColor.YELLOW + modelId + ChatColor.GREEN + " cho NPC " + ChatColor.YELLOW + npc.getName());
                    
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(bos);
                        dos.writeByte((byte) 4);
                        dos.writeInt(npc.getId());
                        dos.writeUTF(modelId);
                        dos.flush();
                        if (sender instanceof Player p) {
                            sendProxyPayload(p, bos.toByteArray());
                        } else {
                            Player first = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                            if (first != null) sendProxyPayload(first, bos.toByteArray());
                        }
                    } catch (Exception e) {}
                    return true;
                }
                
                sender.sendMessage(ChatColor.GOLD + "--- Freesia Citizens Help ---");
                sender.sendMessage(ChatColor.YELLOW + "/freesia-citizens setmodel <modelId> " + ChatColor.GRAY + "- Gán model YSM");
                return true;
            });
            
            cmd.setTabCompleter((sender, command, alias, args) -> {
                if (!sender.hasPermission("freesia.citizens.admin")) return java.util.Collections.emptyList();
                if (args.length == 1) {
                    return java.util.Arrays.asList("setmodel");
                }
                return java.util.Collections.emptyList();
            });
        }

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6[&eFreesia-Citizens&6] &aFreesia Citizens plugin enabled successfully."));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6[&eFreesia-Citizens&6] &aHooked Citizens for proxy model injection!"));
    }

    @Override
    public void onDisable() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, CHANNEL_NAME);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL_NAME);
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
        "&6[&eFreesia-Citizens&6] &aFreesia Citizens plugin disabled!"));
    }

    public void sendProxyPayload(Player player, byte[] payload) {
        player.sendPluginMessage(this, CHANNEL_NAME, payload);
    }
}
