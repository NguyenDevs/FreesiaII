package com.nguyendevs.freesia.velocity.events;

import com.google.common.annotations.Beta;
import com.velocitypowered.api.proxy.Player;
import com.nguyendevs.freesia.velocity.network.ysm.YsmState;

@Beta
public class PlayerEntityStateChangeEvent {
    private final Player actualPlayer;
    private final int entityId;
    private final YsmState entityState;

    public PlayerEntityStateChangeEvent(Player actualPlayer, int entityId, YsmState entityState) {
        this.actualPlayer = actualPlayer;
        this.entityId = entityId;
        this.entityState = entityState;
    }

    public Player getPlayer() {
        return this.actualPlayer;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public YsmState getEntityState() {
        return this.entityState;
    }
}
