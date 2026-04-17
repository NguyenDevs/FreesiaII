package com.nguyendevs.freesia.velocity.network.misc;

import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.FreesiaConstants;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists NPC model assignments across proxy restarts.
 * <p>
 * Each assignment stores: npcId (Citizens int), npcUUID (stable across restarts),
 * and modelId (e.g. "Kipfel").  The UUID lets the proxy preload virtual players
 * immediately on startup without requiring a player to trigger the setskin command.
 */
public class NpcPersistenceManager {

    private static final File ASSIGNMENTS_FILE = new File(FreesiaConstants.FileConstants.PLUGIN_DIR, "npc_assignments.dat");
    private static final File MODEL_CACHE_FILE  = new File(FreesiaConstants.FileConstants.PLUGIN_DIR, "npc_model_cache.dat");

    /** npcId → (npcUUID, modelId) */
    private final Map<Integer, NpcEntry> byId   = new ConcurrentHashMap<>();
    /** npcUUID → modelId  (authoritative for preload) */
    private final Map<UUID, String>      byUuid = new ConcurrentHashMap<>();

    public record NpcEntry(int npcId, UUID npcUUID, String modelId) {}

    // ---------- public API ----------

    public void load() {
        byId.clear();
        byUuid.clear();
        if (!ASSIGNMENTS_FILE.exists()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(ASSIGNMENTS_FILE)))) {
            final int count = in.readInt();
            for (int i = 0; i < count; i++) {
                final int  npcId   = in.readInt();
                final UUID npcUUID = new UUID(in.readLong(), in.readLong());
                final String model = in.readUTF();
                put(npcId, npcUUID, model);
            }
            Freesia.LOGGER.info("[NPC] Loaded {} NPC assignments from disk", byId.size());
        } catch (Exception e) {
            Freesia.LOGGER.warn("[NPC] Failed to load npc_assignments.dat: {}", e.getMessage());
        }
    }

    /** Save a new or updated assignment and flush to disk. */
    public void saveAssignment(int npcId, UUID npcUUID, String modelId) {
        put(npcId, npcUUID, modelId);
        flush();
    }

    /** All assignments keyed by npcUUID — used for proxy-side preload on startup. */
    public Map<UUID, String> getUuidAssignments() { return byUuid; }

    /** All assignments keyed by npcId — used for re-sending setskin to backend. */
    public Map<Integer, NpcEntry> getIdAssignments() { return byId; }

    // ---------- model binary cache ----------

    public Map<String, byte[]> loadModelBinaryCache() {
        final Map<String, byte[]> cache = new HashMap<>();
        if (!MODEL_CACHE_FILE.exists()) return cache;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(MODEL_CACHE_FILE)))) {
            final int count = in.readInt();
            for (int i = 0; i < count; i++) {
                final String model = in.readUTF();
                final int    len   = in.readInt();
                final byte[] data  = new byte[len];
                in.readFully(data);
                cache.put(model, data);
            }
            Freesia.LOGGER.info("[NPC] Loaded {} model binary entries from disk", cache.size());
        } catch (Exception e) {
            Freesia.LOGGER.warn("[NPC] Failed to load npc_model_cache.dat: {}", e.getMessage());
        }
        return cache;
    }

    public void saveModelBinaryCache(Map<String, byte[]> cache) {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(MODEL_CACHE_FILE)))) {
            out.writeInt(cache.size());
            for (Map.Entry<String, byte[]> e : cache.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue().length);
                out.write(e.getValue());
            }
        } catch (Exception e) {
            Freesia.LOGGER.warn("[NPC] Failed to save npc_model_cache.dat: {}", e.getMessage());
        }
    }

    // ---------- internal ----------

    private void put(int npcId, UUID npcUUID, String modelId) {
        byId.put(npcId, new NpcEntry(npcId, npcUUID, modelId));
        byUuid.put(npcUUID, modelId);
    }

    private void flush() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(ASSIGNMENTS_FILE)))) {
            out.writeInt(byId.size());
            for (NpcEntry e : byId.values()) {
                out.writeInt(e.npcId());
                out.writeLong(e.npcUUID().getMostSignificantBits());
                out.writeLong(e.npcUUID().getLeastSignificantBits());
                out.writeUTF(e.modelId());
            }
        } catch (Exception e) {
            Freesia.LOGGER.warn("[NPC] Failed to save npc_assignments.dat: {}", e.getMessage());
        }
    }
}
