package com.nguyendevs.freesia.common;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

public class CertificatePersistence {
    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static void generateAndSaveSelfSigned(File certFile, File keyFile, String cn) throws Exception {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA", "BC");
        kpGen.initialize(2048, new SecureRandom());
        KeyPair kp = kpGen.generateKeyPair();

        X500Name issuer = new X500Name("CN=" + cn);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 86400000L);
        Date notAfter = new Date(System.currentTimeMillis() + 86400000L * 3650); // 10 years

        X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, kp.getPublic()
        );
        ContentSigner sigGen = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC").build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certGen.build(sigGen));

        saveToPem(certFile, "CERTIFICATE", cert.getEncoded());
        saveToPem(keyFile, "PRIVATE KEY", kp.getPrivate().getEncoded());
        
        EntryPoint.LOGGER_INST.info("\u001B[32m[Security] Successfully generated and saved " + cn + " certificate to " + certFile.getName() + "\u001B[0m");
    }

    private static void saveToPem(File file, String type, byte[] content) throws IOException {
        try (PemWriter writer = new PemWriter(new FileWriter(file))) {
            writer.writeObject(new PemObject(type, content));
        }
    }
}
