package com.nguyendevs.freesia.waterfall.network.misc;

import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.FreesiaConstants;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NpcPersistenceManager {

    private static final File ASSIGNMENTS_FILE = new File(FreesiaConstants.FileConstants.PLUGIN_DIR, "npc_assignments.dat");
    private static final File MODEL_CACHE_FILE  = new File(FreesiaConstants.FileConstants.PLUGIN_DIR, "npc_model_cache.dat");

    private final Map<String, Map<Integer, NpcEntry>> byServerId = new ConcurrentHashMap<>();

    public record NpcEntry(int npcId, String modelId) {}

    public void load() {
        byServerId.clear();
        if (!ASSIGNMENTS_FILE.exists()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(ASSIGNMENTS_FILE)))) {
            final int serverCount = in.readInt();
            for (int i = 0; i < serverCount; i++) {
                String serverId = in.readUTF();
                int npcCount = in.readInt();
                Map<Integer, NpcEntry> serverMap = new ConcurrentHashMap<>();
                for (int j = 0; j < npcCount; j++) {
                    int npcId = in.readInt();
                    String modelId = in.readUTF();
                    serverMap.put(npcId, new NpcEntry(npcId, modelId));
                }
                byServerId.put(serverId, serverMap);
            }
            Freesia.LOGGER.info("[NPC] Loaded assignments for " + byServerId.size() + " servers");
        } catch (Exception e) {
            Freesia.LOGGER.warning("[NPC] Failed to load npc_assignments.dat: " + e.getMessage());
        }
    }

    public void saveAssignment(String serverId, int npcId, String modelId) {
        byServerId.computeIfAbsent(serverId, k -> new ConcurrentHashMap<>()).put(npcId, new NpcEntry(npcId, modelId));
        flush();
    }

    public Map<String, Map<Integer, NpcEntry>> getServerIdAssignments() { return byServerId; }

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
            Freesia.LOGGER.info("[NPC] Loaded " + cache.size() + " model binary entries");
        } catch (Exception e) {
            Freesia.LOGGER.warning("[NPC] Failed to load npc_model_cache.dat: " + e.getMessage());
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
            Freesia.LOGGER.warning("[NPC] Failed to save npc_model_cache.dat: " + e.getMessage());
        }
    }

    private void flush() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(ASSIGNMENTS_FILE)))) {
            out.writeInt(byServerId.size());
            for (Map.Entry<String, Map<Integer, NpcEntry>> serverEntry : byServerId.entrySet()) {
                out.writeUTF(serverEntry.getKey());
                out.writeInt(serverEntry.getValue().size());
                for (NpcEntry e : serverEntry.getValue().values()) {
                    out.writeInt(e.npcId());
                    out.writeUTF(e.modelId());
                }
            }
        } catch (Exception e) {
            Freesia.LOGGER.warning("[NPC] Failed to save npc_assignments.dat: " + e.getMessage());
        }
    }
}
