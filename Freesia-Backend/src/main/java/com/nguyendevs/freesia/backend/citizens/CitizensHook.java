package com.nguyendevs.freesia.backend.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;

import java.util.UUID;

public class CitizensHook {
    public static boolean isNpcEntity(Entity entity) {
        return entity.hasMetadata("NPC");
    }

    public static UUID getNpcUUID(int npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null || !npc.isSpawned()) {
            return null;
        }
        return npc.getEntity().getUniqueId();
    }

    public static NPC getNpcById(int npcId) {
        return CitizensAPI.getNPCRegistry().getById(npcId);
    }
}
