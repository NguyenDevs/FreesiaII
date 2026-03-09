package com.nguyendevs.freesia.velocity.events;

import java.util.UUID;


public class PlayerEntityDataLoadEvent {
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

