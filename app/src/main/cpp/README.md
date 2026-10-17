# StreamMaster native secure library

`secure_lib.cpp` exposes one JNI method:

- `getBaseUrl()`

(`getMasterSecret()` was removed — the app no longer holds, computes, or sends
any shared secret to authenticate a device or obtain a token. Device auth is
Android Keystore/ECDSA challenge-response only, with no fallback. Any
MASTER_SECRET this project still uses lives server-side only and is never
embedded here. See DeviceIdentity.kt / SecureNetworkClient.kt.)

The base URL is generated at CI time into `generated_secure_config.h` from the
GitHub Actions `BASE_URL` secret. The generated header is ignored by Git and
must never be committed.

The generated value is encrypted with AES-256-GCM using a per-project key
derived with HKDF-SHA256, with a fresh 96-bit random IV on every CI build. The
native library authenticates the ciphertext before returning plaintext to the JVM.

This protects against casual static string extraction. It does not make a
client-side value mathematically unrecoverable: a determined attacker
controlling the client can still observe it when the application requests it —
which is exactly why nothing that actually needs to stay secret (like the
retired MASTER_SECRET) is embedded here anymore.
