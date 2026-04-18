package com.nguyendevs.freesia.velocity;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.io.IOException;

public class FreesiaSecurityConfig {
    public static boolean enableTls = true;
    public static boolean useSelfSigned = true;
    public static String certPath = "security/proxy_cert.pem";
    public static String keyPath = "security/proxy_key.pem";
    public static String trustWorkerCertPath = "security/worker_cert.pem";
    public static boolean enableIpFilter = true;
    public static java.util.List<String> allowedWorkerIps = java.util.Arrays.asList("127.0.0.1", "192.168.1.5");

    private static CommentedFileConfig CONFIG_INSTANCE;

    private static void loadOrDefaultValues() {
        enableTls = get("security.enable_tls", enableTls);
        useSelfSigned = get("security.use_self_signed", useSelfSigned);
        certPath = get("security.cert_path", certPath);
        keyPath = get("security.key_path", keyPath);
        trustWorkerCertPath = get("security.trust_worker_cert_path", trustWorkerCertPath);
        enableIpFilter = get("firewall.enable_ip_filter", enableIpFilter);
        allowedWorkerIps = get("firewall.allowed_worker_ips", allowedWorkerIps);
    }

    private static <T> T get(String key, T def) {
        if (!CONFIG_INSTANCE.contains(key)) {
            CONFIG_INSTANCE.add(key, def);
            return def;
        }

        return CONFIG_INSTANCE.get(key);
    }

    public static void init() throws IOException {
        Freesia.LOGGER.info("\u001B[36m[Security] Loading proxy security config.\u001B[0m");

        if (!FreesiaConstants.FileConstants.SECURITY_CONFIG_FILE.exists()) {
            Freesia.LOGGER.info("\u001B[33m[Security] Security config file not found! Creating new.\u001B[0m");
            FreesiaConstants.FileConstants.SECURITY_CONFIG_FILE.createNewFile();
        }

        CONFIG_INSTANCE = CommentedFileConfig.ofConcurrent(FreesiaConstants.FileConstants.SECURITY_CONFIG_FILE);

        CONFIG_INSTANCE.load();

        try {
            loadOrDefaultValues();
        } catch (Exception e) {
            Freesia.LOGGER.error("Failed to load security config!", e);
        }

        CONFIG_INSTANCE.save();
    }
}
