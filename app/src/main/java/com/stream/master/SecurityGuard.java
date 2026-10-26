package com.stream.master;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Debug;

import java.io.File;
import java.security.MessageDigest;

/** Lightweight client-side tamper/instrumentation tripwire. Not a cryptographic boundary. */
public final class SecurityGuard {
    private SecurityGuard() {}

    public static boolean shouldBlock(Context context) {
        return Debug.isDebuggerConnected()
                || Debug.waitingForDebugger()
                || hasKnownInstrumentationArtifacts()
                || !isExpectedSigningCertificate(context);
    }

    private static boolean hasKnownInstrumentationArtifacts() {
        String[] paths = {
                "/data/local/tmp/frida-server",
                "/data/local/tmp/re.frida.server",
                "/data/local/tmp/frida-gadget.so",
                "/data/local/tmp/gadget.so"
        };
        for (String path : paths) {
            try { if (new File(path).exists()) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    private static boolean isExpectedSigningCertificate(Context context) {
        String expected = BuildSecurityConfig.EXPECTED_CERT_SHA256;
        if (expected == null || expected.isEmpty()) return true;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 28) {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return false;
                Signature[] signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
                for (Signature sig : signatures) {
                    if (expected.equalsIgnoreCase(sha256(sig.toByteArray()))) return true;
                }
                return false;
            } else {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                if (info.signatures == null) return false;
                for (Signature sig : info.signatures) {
                    if (expected.equalsIgnoreCase(sha256(sig.toByteArray()))) return true;
                }
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(java.util.Locale.US, "%02X", b));
        return out.toString();
    }
}
