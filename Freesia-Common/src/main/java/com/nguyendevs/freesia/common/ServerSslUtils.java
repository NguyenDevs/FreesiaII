package com.nguyendevs.freesia.common;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.File;

public class ServerSslUtils {
    public static SslContext createServerContext(boolean useSelfSigned, String certPath, String keyPath, String trustPath) throws Exception {
        File certFile = new File(certPath);
        File keyFile = new File(keyPath);

        if (useSelfSigned) {
            if (!certFile.exists() || !keyFile.exists()) {
                EntryPoint.LOGGER_INST.info("\u001B[33m[Security] Certificate files not found. Generating new self-signed pair...\u001B[0m");
                CertificatePersistence.generateAndSaveSelfSigned(certFile, keyFile, "FreesiaMaster");
            }
        }

        SslContextBuilder builder = SslContextBuilder.forServer(certFile, keyFile);

        if (trustPath != null) {
            File trustFile = new File(trustPath);
            if (trustFile.exists()) {
                EntryPoint.LOGGER_INST.info("\u001B[32m[Security] mTLS enabled: Loading trust store from " + trustPath + " (Verifying Workers)\u001B[0m");
                builder.trustManager(trustFile);
                builder.clientAuth(ClientAuth.REQUIRE);
            } else {
                EntryPoint.LOGGER_INST.warn("\u001B[33m[Security] Trust store file not found at " + trustPath + ". mTLS will be disabled (Proxy authentication only).\u001B[0m");
            }
        }

        EntryPoint.LOGGER_INST.info("\u001B[32m[Security] Initialized SSL context for Master Server (TLS " + (trustPath != null ? "2-way" : "1-way") + ")\u001B[0m");
        return builder.build();
    }
}
