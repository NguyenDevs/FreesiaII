package com.nguyendevs.freesia.velocity.events;

import java.util.UUID;

public class WorkerConnectedEvent {
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

