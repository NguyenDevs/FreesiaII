package com.nguyendevs.freesia.velocity.network.misc;

import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.FreesiaConstants;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NpcPersistenceManager {

    private static final File ASSIGNMENTS_FILE = new File(FreesiaConstants.FileConstants.PLUGIN_DIR, "npc_assignments.dat");
    private static final File MODEL_CACHE_FILE = new File(FreesiaConstants.FileConstants.PLUGIN_DIR, "npc_model_cache.dat");

    private final Map<Integer, String> npcAssignments = new ConcurrentHashMap<>();

    public Map<Integer, String> loadAssignments() {
        npcAssignments.clear();
        if (!ASSIGNMENTS_FILE.exists()) return npcAssignments;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(ASSIGNMENTS_FILE)))) {
            final int count = in.readInt();
            for (int i = 0; i < count; i++) {
                final int npcId = in.readInt();
                final String modelId = in.readUTF();
                npcAssignments.put(npcId, modelId);
            }
            Freesia.LOGGER.info("[NPC] Loaded {} NPC assignments from disk", npcAssignments.size());
        } catch (Exception e) {
            Freesia.LOGGER.warn("[NPC] Failed to load npc_assignments.dat: {}", e.getMessage());
        }
        return npcAssignments;
    }

    public void saveAssignment(int npcId, String modelId) {
        npcAssignments.put(npcId, modelId);
        persistAssignments();
    }

    private void persistAssignments() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(ASSIGNMENTS_FILE)))) {
            out.writeInt(npcAssignments.size());
            for (Map.Entry<Integer, String> entry : npcAssignments.entrySet()) {
                out.writeInt(entry.getKey());
                out.writeUTF(entry.getValue());
            }
        } catch (Exception e) {
            Freesia.LOGGER.warn("[NPC] Failed to save npc_assignments.dat: {}", e.getMessage());
        }
    }

    public Map<String, byte[]> loadModelBinaryCache() {
        final Map<String, byte[]> cache = new HashMap<>();
        if (!MODEL_CACHE_FILE.exists()) return cache;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(MODEL_CACHE_FILE)))) {
            final int count = in.readInt();
            for (int i = 0; i < count; i++) {
                final String modelPath = in.readUTF();
                final int len = in.readInt();
                final byte[] binary = new byte[len];
                in.readFully(binary);
                cache.put(modelPath, binary);
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
            for (Map.Entry<String, byte[]> entry : cache.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeInt(entry.getValue().length);
                out.write(entry.getValue());
            }
        } catch (Exception e) {
            Freesia.LOGGER.warn("[NPC] Failed to save npc_model_cache.dat: {}", e.getMessage());
        }
    }
}
