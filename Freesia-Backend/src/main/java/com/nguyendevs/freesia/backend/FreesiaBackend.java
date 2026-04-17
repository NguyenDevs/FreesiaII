package com.nguyendevs.freesia.backend;

import com.nguyendevs.freesia.backend.citizens.CitizensSkinChannelHandler;
import com.nguyendevs.freesia.backend.misc.VirtualPlayerManager;
import com.nguyendevs.freesia.backend.tracker.TrackerProcessor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreesiaBackend extends JavaPlugin {
    public static FreesiaBackend INSTANCE;
    private static boolean citizensEnabled = false;

    private final TrackerProcessor trackerProcessor = new TrackerProcessor();
    private final VirtualPlayerManager virtualPlayerManager = new VirtualPlayerManager();

    @Override
    public void onEnable() {
        INSTANCE = this;

        Bukkit.getMessenger().registerIncomingPluginChannel(this, TrackerProcessor.CHANNEL_NAME, this.trackerProcessor);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, TrackerProcessor.CHANNEL_NAME);

        Bukkit.getMessenger().registerIncomingPluginChannel(this, VirtualPlayerManager.CHANNEL_NAME, this.virtualPlayerManager);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, VirtualPlayerManager.CHANNEL_NAME);

        Bukkit.getPluginManager().registerEvents(this.trackerProcessor, this);

        if (Bukkit.getPluginManager().getPlugin("Citizens") != null
                && Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            citizensEnabled = true;
            Bukkit.getMessenger().registerIncomingPluginChannel(this, CitizensSkinChannelHandler.INBOUND_CHANNEL, new CitizensSkinChannelHandler());
            Bukkit.getMessenger().registerOutgoingPluginChannel(this, CitizensSkinChannelHandler.OUTBOUND_CHANNEL);
            getSLF4JLogger().info("\u001B[32m[Freesia] Citizens hook successful — NPC skin support enabled.\u001B[0m");
        } else {
            getSLF4JLogger().info("\u001B[33m[Freesia] Citizens not found — NPC skin support disabled.\u001B[0m");
        }

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&5[&dFreesia&5] &aFreesia Backend plugin enabled successfully!"));
    }

    public static boolean isCitizensEnabled() {
        return citizensEnabled;
    }

    public VirtualPlayerManager getVirtualPlayerManager() {
        return this.virtualPlayerManager;
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&5[&dFreesia&5] &cFreesia Backend plugin disabled!"));
    }
}
