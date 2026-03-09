package com.nguyendevs.freesia.velocity.events;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;

public class PlayerYsmHandshakeEvent implements ResultedEvent<ResultedEvent.GenericResult> {
    private final Player player;
    private GenericResult result = GenericResult.allowed();

    public PlayerYsmHandshakeEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return this.player;
    }

    @Override
    public GenericResult getResult() {
        return this.result;
    }

    @Override
    public void setResult(GenericResult result) {
        this.result = result;
    }
}

