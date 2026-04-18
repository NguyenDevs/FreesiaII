package com.nguyendevs.freesia.velocity.network.misc;

import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.FreesiaConstants;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class CitizensPersistenceManager {

    private static final File MODEL_CACHE_FILE  = new File(FreesiaConstants.FileConstants.PLUGIN_DIR, "citizens_model_cache.dat");

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
            Freesia.LOGGER.info("[Citizens] Loaded {} model binary entries from cache", cache.size());
        } catch (Exception e) {
            Freesia.LOGGER.warn("[Citizens] Failed to load citizens_model_cache.dat: {}", e.getMessage());
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
            Freesia.LOGGER.warn("[Citizens] Failed to save citizens_model_cache.dat: {}", e.getMessage());
        }
    }
}
