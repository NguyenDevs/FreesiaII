package com.nguyendevs.freesia.citizens;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FreesiaCitizensPlugin extends JavaPlugin {
    
    public static final String CHANNEL_NAME = "freesia:npc";
    public static FreesiaCitizensPlugin INSTANCE;

    @Override
    public void onEnable() {
        INSTANCE = this;
        
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, CHANNEL_NAME);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, CHANNEL_NAME, new TrackerListener());
        
        getServer().getPluginManager().registerEvents(new TrackerListener(), this);
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
