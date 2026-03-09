package com.nguyendevs.freesia.velocity.events;

import java.util.UUID;

public class PlayerEntityDataStoreEvent {
    private final UUID player;
    private byte[] serializedNbtData;

    public PlayerEntityDataStoreEvent(UUID player, byte[] serializedNbtData) {
        this.player = player;
        this.serializedNbtData = serializedNbtData;
    }

    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }

    public UUID getPlayer() {
        return this.player;
    }
}

