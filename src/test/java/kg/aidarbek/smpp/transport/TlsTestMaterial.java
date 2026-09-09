package kg.aidarbek.smpp.transport;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Development-only certificate fixtures; never production credentials or a permissive trust manager. */
public final class TlsTestMaterial {
    private TlsTestMaterial() {}
    /** Creates a fixture context.
     * @param identity include the development private key
     * @param trust trust the development certificate, or use an empty trust store
     * @return independently initialized context
     * @throws Exception fixture decoding or JSSE setup failed */
    public static SSLContext context(boolean identity, boolean trust) throws Exception {
        Certificate certificate;
        try (InputStream input = TlsTestMaterial.class.getResourceAsStream("/tls/development-certificate.pem")) {
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        KeyStore trusted = KeyStore.getInstance("PKCS12");
        trusted.load(null, null);
        if (trust) trusted.setCertificateEntry("development", certificate);
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trusted);
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore keys = KeyStore.getInstance("PKCS12");
        keys.load(null, null);
        char[] password = "development-only".toCharArray();
        if (identity) {
            String pem;
            try (InputStream input = TlsTestMaterial.class.getResourceAsStream("/tls/development-key.pem")) {
                pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            }
            byte[] key = Base64.getMimeDecoder()
                    .decode(pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
            keys.setKeyEntry(
                    "development",
                    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key)),
                    password,
                    new Certificate[] {certificate});
        }
        keyManagers.init(keys, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), new SecureRandom());
        return context;
    }
}
