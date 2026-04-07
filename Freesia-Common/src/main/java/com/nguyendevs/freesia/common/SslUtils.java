package com.nguyendevs.freesia.common;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.io.File;

public class SslUtils {

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
