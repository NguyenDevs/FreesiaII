package com.nguyendevs.freesia.backend;

import com.nguyendevs.freesia.backend.misc.VirtualPlayerManager;
import com.nguyendevs.freesia.backend.tracker.TrackerProcessor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import static net.kyori.adventure.text.format.TextColor.color;

public final class FreesiaBackend extends JavaPlugin {
    public static FreesiaBackend INSTANCE;

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
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&5[&dFreesia&5] &aFreesia Backend plugin enabled successfully!"));

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
