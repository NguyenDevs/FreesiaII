package com.nguyendevs.freesia.common;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

public class ServerSslUtils {
    public static SslContext createServerContext(boolean useSelfSigned, String certPath, String keyPath) throws Exception {
        if (useSelfSigned) {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA", "BC");
            kpGen.initialize(2048, new SecureRandom());
            KeyPair kp = kpGen.generateKeyPair();

            X500Name issuer = new X500Name("CN=Freesia");
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date notBefore = new Date(System.currentTimeMillis() - 86400000L);
            Date notAfter = new Date(System.currentTimeMillis() + 86400000L * 365);

            X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, issuer, kp.getPublic()
            );
            ContentSigner sigGen = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC").build(kp.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certGen.build(sigGen));

            EntryPoint.LOGGER_INST.info("\u001B[32m[Security] Generated Self-Signed Certificate for Master Server\u001B[0m");
            return SslContextBuilder.forServer(kp.getPrivate(), cert).build();
        } else {
            EntryPoint.LOGGER_INST.info("\u001B[32m[Security] Loaded Certificate from " + certPath + " for Master Server\u001B[0m");
            return SslContextBuilder.forServer(new File(certPath), new File(keyPath)).build();
        }
    }
}
