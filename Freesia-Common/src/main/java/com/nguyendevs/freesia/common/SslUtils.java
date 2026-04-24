package com.nguyendevs.freesia.common;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.io.File;

public class SslUtils {

    public static SslContext createClientContext(boolean trustAll, String trustCertPath, String workerCertPath, String workerKeyPath) throws Exception {
        SslContextBuilder builder = SslContextBuilder.forClient();

        if (trustAll) {
            EntryPoint.LOGGER_INST.info("\u001B[33m[Security] Client trusting all certificates (Insecure TrustManager)\u001B[0m");
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        } else {
            File trustFile = new File(trustCertPath);
            if (trustFile.exists()) {
                EntryPoint.LOGGER_INST.info("\u001B[32m[Security] Client loading TrustStore from " + trustCertPath + "\u001B[0m");
                builder.trustManager(trustFile);
            } else {
                EntryPoint.LOGGER_INST.warn("\u001B[31m[Security] Trust certificate not found at " + trustCertPath + ". SSL handshake will likely fail for self-signed certificates unless they are in the system trust store.\u001B[0m");
            }
        }

        if (workerCertPath != null && workerKeyPath != null) {
            File certFile = new File(workerCertPath);
            File keyFile = new File(workerKeyPath);

            if (!certFile.exists() || !keyFile.exists()) {
                EntryPoint.LOGGER_INST.info("\u001B[33m[Security] Worker identity files not found. Generating new self-signed pair...\u001B[0m");
                CertificatePersistence.generateAndSaveSelfSigned(certFile, keyFile, "FreesiaWorker");
            }

            EntryPoint.LOGGER_INST.info("\u001B[32m[Security] mTLS: Providing worker identity from " + workerCertPath + "\u001B[0m");
            builder.keyManager(certFile, keyFile);
        }

        return builder.build();
    }
}
