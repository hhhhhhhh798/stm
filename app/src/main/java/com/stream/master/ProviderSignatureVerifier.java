package com.stream.master;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Iterator;

/**
 * Verifies provider source before it is executed by V8.
 *
 * The embedded key is public and is not a secret. The private signing key
 * must only exist as a GitHub Actions secret / offline secret.
 */
public final class ProviderSignatureVerifier {
    private ProviderSignatureVerifier() {}

    private static final String ALGORITHM = "SHA256withECDSA";

    // DER-encoded X.509 SubjectPublicKeyInfo, base64.
    private static final String PUBLIC_KEY_B64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEA8cDSnL4W95wBmgv6FrixF9PTJ8Y" +
            "y/uu8WI/dc9GwAgtbTiNuFhD7Q1prvnwrQBohu0Gu/lHzTjZGEIsQnxKaw==";

    public static final class Entry {
        public final String sha256;
        public final byte[] signature;
        Entry(String sha256, byte[] signature) {
            this.sha256 = sha256;
            this.signature = signature;
        }
    }

    public static boolean verify(String sourceUrl, byte[] source, Entry entry) {
        if (sourceUrl == null || source == null || entry == null) return false;
        if (!sourceUrl.startsWith(Constants.GITHUB_PROVIDERS_DIR)) return false;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String digest = hex(md.digest(source));
            if (!digest.equalsIgnoreCase(entry.sha256)) return false;

            PublicKey key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(
                            Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)));

            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(source);
            return verifier.verify(entry.signature);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Entry getEntry(JSONObject manifest, String sourceUrl) {
        try {
            JSONObject providers = manifest.optJSONObject("providers");
            if (providers == null) return null;
            JSONObject item = providers.optJSONObject(sourceUrl);
            if (item == null) return null;

            String sha = item.optString("sha256", "").trim();
            String sig = item.optString("signature", "").trim();
            if (sha.length() != 64 || sig.isEmpty()) return null;

            return new Entry(sha, Base64.decode(sig, Base64.DEFAULT));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static JSONObject downloadManifest() throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(Constants.GITHUB_PROVIDER_SIGNATURES_JSON);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "StreamMaster/4");
            if (conn.getResponseCode() != 200) throw new SecurityException("Provider manifest unavailable");
            byte[] data = readLimited(conn.getInputStream(), 1024 * 1024);
            JSONObject obj = new JSONObject(new String(data, StandardCharsets.UTF_8));
            if (obj.optInt("version", 0) != 1) throw new SecurityException("Unsupported provider manifest");
            if (!"SHA256withECDSA".equals(obj.optString("algorithm"))) {
                throw new SecurityException("Unsupported provider signature algorithm");
            }
            return obj;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0, n;
            while ((n = input.read(buf)) != -1) {
                total += n;
                if (total > max) throw new SecurityException("Manifest too large");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) b.append(String.format(java.util.Locale.US, "%02x", x));
        return b.toString();
    }
}
