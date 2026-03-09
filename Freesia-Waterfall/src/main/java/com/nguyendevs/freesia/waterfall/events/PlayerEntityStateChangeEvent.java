package com.nguyendevs.freesia.waterfall.events;

import com.google.common.annotations.Beta;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Event;

import com.nguyendevs.freesia.waterfall.network.ysm.YsmState;

@Beta
public class PlayerEntityStateChangeEvent extends Event {
    private final ProxiedPlayer actualPlayer;
    private final int entityId;
    private final YsmState entityState;

    public PlayerEntityStateChangeEvent(ProxiedPlayer actualPlayer, int entityId, YsmState entityState) {
        this.actualPlayer = actualPlayer;
        this.entityId = entityId;
        this.entityState = entityState;
    }

    public ProxiedPlayer getPlayer() {
        return this.actualPlayer;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public YsmState getEntityState() {
        return this.entityState;
    }
}

