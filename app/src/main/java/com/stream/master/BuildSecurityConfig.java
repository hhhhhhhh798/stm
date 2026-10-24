package com.stream.master;

/** Release signing certificate pin. CI replaces this value with the exact release fingerprint. */
public final class BuildSecurityConfig {
    private BuildSecurityConfig() {}

    // Empty for local/Code Assist builds. The GitHub release workflow replaces
    // this line with the SHA-256 fingerprint of the release signing certificate.
    public static final String EXPECTED_CERT_SHA256 = "";
}
