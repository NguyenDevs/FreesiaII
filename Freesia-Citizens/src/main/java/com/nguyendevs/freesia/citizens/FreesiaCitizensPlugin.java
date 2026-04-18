package com.nguyendevs.freesia.citizens;

import com.nguyendevs.freesia.citizens.command.FreesiaCitizensCommand;
import com.nguyendevs.freesia.citizens.listener.TrackerListener;
import com.nguyendevs.freesia.citizens.trait.YsmModelTrait;
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
        
        saveDefaultConfig();
        
        net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(YsmModelTrait.class));
        
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, CHANNEL_NAME);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, CHANNEL_NAME, new TrackerListener());
        
        getServer().getPluginManager().registerEvents(new TrackerListener(), this);

        var cmd = getCommand("freesia-citizens");
        if (cmd != null) {
            FreesiaCitizensCommand handler = new FreesiaCitizensCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
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

    public void sendMessage(org.bukkit.command.CommandSender sender, String key, String... placeholders) {
        String msg = getConfig().getString("messages." + key);
        if (msg == null) return;
        
        String prefix = getConfig().getString("messages.prefix", "");
        msg = prefix + msg;
        
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    public void sendProxyPayload(Player player, byte[] payload) {
        player.sendPluginMessage(this, CHANNEL_NAME, payload);
    }
}
