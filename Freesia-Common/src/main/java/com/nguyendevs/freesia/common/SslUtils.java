package com.nguyendevs.freesia.common;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;

public class SslUtils {
    public static SslContext createServerContext(boolean useSelfSigned, String certPath, String keyPath) throws Exception {
        if (useSelfSigned) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            EntryPoint.LOGGER_INST.info("\u001B[32m[Security] Generated Self-Signed Certificate for Master Server\u001B[0m");
            return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            EntryPoint.LOGGER_INST.info("\u001B[32m[Security] Loaded Certificate from " + certPath + " for Master Server\u001B[0m");
            return SslContextBuilder.forServer(new File(certPath), new File(keyPath)).build();
        }
    }

    public static SslContext createClientContext(boolean trustAll, String trustCertPath) throws Exception {
        if (trustAll) {
            EntryPoint.LOGGER_INST.info("\u001B[33m[Security] Client trusting all certificates (Insecure TrustManager)\u001B[0m");
            return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } else {
            EntryPoint.LOGGER_INST.info("\u001B[32m[Security] Client loading TrustStore from " + trustCertPath + "\u001B[0m");
            return SslContextBuilder.forClient().trustManager(new File(trustCertPath)).build();
        }
    }
}
