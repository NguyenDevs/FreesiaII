package com.nguyendevs.freesia.waterfall.events;

import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

public class PlayerEntityDataLoadEvent extends Event {
    private final UUID player;
    private byte[] serializedNbtData;

    public PlayerEntityDataLoadEvent(UUID player, byte[] serializedNbtData) {
        this.player = player;
        this.serializedNbtData = serializedNbtData;
    }

    public UUID getPlayer() {
        return this.player;
    }

    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }
}

