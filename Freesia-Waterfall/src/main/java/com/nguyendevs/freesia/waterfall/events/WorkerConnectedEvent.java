package com.nguyendevs.freesia.waterfall.events;

import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

public class WorkerConnectedEvent extends Event {
    private final UUID workerUUID;
    private final String workerName;

    public WorkerConnectedEvent(UUID workerUUID, String workerName) {
        this.workerUUID = workerUUID;
        this.workerName = workerName;
    }

    public String getWorkerName() {
        return this.workerName;
    }

    public UUID getWorkerUUID() {
        return this.workerUUID;
    }
}

