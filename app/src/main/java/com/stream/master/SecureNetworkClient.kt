package com.stream.master

import android.content.Context
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Network security layer.
 *
 * IMPORTANT ARCHITECTURE:
 * - Provider code is still downloaded and executed locally on the user's device.
 * - The API server remains metadata/auth/config + fallback stream extraction.
 * - This class does NOT centralize provider execution or proxy media traffic.
 *
 * Authentication (device-bound identity — the only path, no shared secret):
 * - The token bootstrap uses a per-device, hardware-backed proof: DeviceIdentity
 *   is a Keystore/StrongBox key pair unique to this install, whose private half
 *   never leaves the secure element. This app never holds, computes, or sends
 *   any shared secret to authenticate a device or obtain a token — there is no
 *   fallback that could bypass Keystore/ECDSA.
 * - Flow: register the public key once -> ask the server for a single-use
 *   challenge -> sign it with the Keystore-held private key -> trade that
 *   signature for a short-lived access token + a refresh token.
 * - Tokens are stored only in Keystore-backed EncryptedSharedPreferences.
 */
object SecureNetworkClient {

    fun interface TokenCallback { fun onResult(success: Boolean) }

    init { System.loadLibrary("secure-lib") }

    @Volatile private var client: OkHttpClient? = null
    /** Serialises token refresh/bootstrap so parallel requests share one instead of racing. */
    private val authLock = Any()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @JvmStatic
    fun getClient(context: Context): OkHttpClient =
        client ?: synchronized(this) {
            client ?: buildClient(context).also { client = it }
        }

    private fun buildClient(context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context.applicationContext))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private class AuthInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            ensureFreshAccessToken(context)

            val sentToken = SecureStorageManager.getToken(context)
            val request = chain.request().newBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "StreamMaster/4.12 Android")
                .addHeader("X-Device-ID", SecureStorageManager.getOrCreateDeviceId(context))
                .apply {
                    if (sentToken.isNotEmpty()) addHeader("Authorization", "Bearer $sentToken")
                }
                .build()

            var response = chain.proceed(request)

            // Server still starting / behind a cold-start proxy: retry idempotent GETs a couple
            // of times instead of surfacing "connection failed" to the user.
            var attempt = 0
            while (request.method == "GET" &&
                (response.code == 502 || response.code == 503 || response.code == 504) &&
                attempt < 2
            ) {
                response.close()
                try { Thread.sleep(1200L * (attempt + 1)) } catch (_: InterruptedException) { }
                response = chain.proceed(request)
                attempt++
            }

            // Only 401 means "token rejected". 403 (ban / forbidden) is NOT fixed by refreshing,
            // and it must never wipe the device identity.
            if (response.code == 401) {
                val newToken = recoverToken(context, sentToken)
                if (newToken != null) {
                    response.close()
                    val retry = request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    response = chain.proceed(retry)
                    if (response.code == 401) {
                        // Server rejects even a fresh token (e.g. it lost this device's key):
                        // drop tokens + the "registered" flag so the next request re-registers.
                        SecureStorageManager.clearToken(context)
                        SecureStorageManager.clearDeviceKeyRegistered(context)
                    }
                } else {
                    SecureStorageManager.clearToken(context)
                }
            }
            return response
        }
    }

    /** Single-flight token recovery after a 401: returns a usable token or null. */
    private fun recoverToken(context: Context, rejectedToken: String): String? =
        synchronized(authLock) {
            val current = SecureStorageManager.getToken(context)
            // Another thread already replaced the rejected token while we waited for the lock.
            if (current.isNotEmpty() && current != rejectedToken && hasValidToken(context)) {
                return current
            }
            if (refreshAccessToken(context) || deviceProofBootstrapBlocking(context)) {
                SecureStorageManager.getToken(context).ifEmpty { null }
            } else null
        }

    /**
     * Base URL used for the device auth bootstrap (register/challenge/verify/refresh).
     * Respects the developer "server_url" override (Constants.BASE_URL) when one is set,
     * so the token is minted on the same server that content requests are sent to. Falls
     * back to the native (encrypted) URL when no override is active.
     */
    private fun effectiveBaseUrl(): String {
        val override = Constants.BASE_URL
        return if (!override.isNullOrBlank() &&
            (override.startsWith("http://") || override.startsWith("https://"))
        ) {
            override
        } else {
            getBaseUrl()
        }
    }

    @JvmStatic
    fun fetchTokenFromServer(context: Context) = ensureTokenAvailable(context, null)

    @JvmStatic
    fun ensureTokenAvailable(context: Context, callback: TokenCallback?) {
        if (hasValidToken(context)) {
            callback?.onResult(true)
            return
        }
        // Everything network-related runs off the calling thread, and all callers share
        // ONE in-flight bootstrap (authLock) instead of each starting their own.
        Thread {
            val ok = synchronized(authLock) {
                hasValidToken(context) ||
                    (SecureStorageManager.getRefreshToken(context).isNotEmpty() && refreshAccessToken(context)) ||
                    deviceProofBootstrapBlocking(context)
            }
            callback?.onResult(ok)
        }.start()
    }

    private fun hasValidToken(context: Context): Boolean =
        SecureStorageManager.getToken(context).isNotEmpty() &&
            SecureStorageManager.getAccessExpiresAt(context) > System.currentTimeMillis() + 60_000L

    // ------------------------------------------------------------------
    // Device-bound bootstrap: Keystore/StrongBox key pair, registered
    // once, then proven by signature on every fresh bootstrap. This is
    // the ONLY way this app obtains a token — there is no shared-secret
    // fallback left in the client.
    // ------------------------------------------------------------------

    private class Challenge(val nonce: String?, val code: Int)

    private fun deviceProofBootstrapBlocking(context: Context): Boolean {
        return try {
            var baseUrl = effectiveBaseUrl().trim()
            if (!baseUrl.endsWith("/")) baseUrl += "/"
            val deviceId = SecureStorageManager.getOrCreateDeviceId(context)

            // Attempt 2 exists for the case where the server no longer knows this device
            // (redeploy on an ephemeral disk, key rotated): forget the local "registered"
            // flag, register again, and retry once. Never loops further.
            for (attempt in 0..1) {
                if (!SecureStorageManager.isDeviceKeyRegistered(context)) {
                    if (!registerDeviceKeyBlocking(context, baseUrl, deviceId)) return false
                }

                val challenge = requestDeviceChallengeBlocking(baseUrl, deviceId)
                val nonce = challenge.nonce
                if (nonce == null) {
                    if (challenge.code == 401 && attempt == 0) {
                        SecureStorageManager.clearDeviceKeyRegistered(context)
                        continue
                    }
                    return false
                }

                val signature = DeviceIdentity.sign(nonce.toByteArray(Charsets.UTF_8))
                val verifyPayload = JSONObject()
                    .put("device_id", deviceId)
                    .put("nonce", nonce)
                    .put("signature", signature)

                val request = Request.Builder()
                    .url(baseUrl + "api/device/verify")
                    .post(verifyPayload.toString().toRequestBody(jsonType))
                    .header("Accept", "application/json")
                    .header("User-Agent", "StreamMaster/4.12 Android")
                    .build()

                var retryWithRegister = false
                val ok = rawClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        saveTokenPayload(context, JSONObject(response.body?.string().orEmpty()))
                    } else {
                        retryWithRegister = attempt == 0 && (response.code == 401 || response.code == 403)
                        false
                    }
                }
                if (ok) return true
                if (retryWithRegister) {
                    SecureStorageManager.clearDeviceKeyRegistered(context)
                    continue
                }
                return false
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * One-time per install: hands the server this device's PUBLIC key. A public key is not a
     * secret, so — unlike before — this call carries no HMAC/MASTER_SECRET at all; the app
     * never has that value to send in the first place. See the server-side comment on
     * /api/device/register for what protects this endpoint instead (rate limiting +
     * trust-on-first-use), now that it is not gated by a shared secret.
     */
    private fun registerDeviceKeyBlocking(context: Context, baseUrl: String, deviceId: String): Boolean {
        return try {
            val payload = JSONObject()
                .put("device_id", deviceId)
                .put("public_key", DeviceIdentity.publicKeyBase64())

            val request = Request.Builder()
                .url(baseUrl + "api/device/register")
                .post(payload.toString().toRequestBody(jsonType))
                .header("Accept", "application/json")
                .header("User-Agent", "StreamMaster/4.12 Android")
                .build()

            rawClient().newCall(request).execute().use { response ->
                val ok = response.isSuccessful
                if (ok) SecureStorageManager.markDeviceKeyRegistered(context)
                ok
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun requestDeviceChallengeBlocking(baseUrl: String, deviceId: String): Challenge {
        return try {
            val request = Request.Builder()
                .url(baseUrl + "api/device/challenge?device_id=" + URLEncoder.encode(deviceId, "UTF-8"))
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "StreamMaster/4.12 Android")
                .build()

            rawClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Challenge(null, response.code)
                val nonce = JSONObject(response.body?.string().orEmpty()).optString("nonce", "")
                Challenge(nonce.ifEmpty { null }, response.code)
            }
        } catch (_: Exception) {
            Challenge(null, -1)
        }
    }

    /** Synchronous refresh used by OkHttp's interceptor. */
    private fun refreshAccessToken(context: Context): Boolean {
        val refresh = SecureStorageManager.getRefreshToken(context)
        if (refresh.isEmpty()) return false

        return try {
            var baseUrl = effectiveBaseUrl().trim()
            if (!baseUrl.endsWith("/")) baseUrl += "/"

            val payload = JSONObject()
                .put("refresh_token", refresh)
                .put("device_id", SecureStorageManager.getOrCreateDeviceId(context))

            val request = Request.Builder()
                .url(baseUrl + "api/refresh")
                .post(payload.toString().toRequestBody(jsonType))
                .header("Accept", "application/json")
                .header("User-Agent", "StreamMaster/4.12 Android")
                .build()

            rawClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val parsed = JSONObject(response.body?.string().orEmpty())
                saveTokenPayload(context, parsed)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureFreshAccessToken(context: Context) {
        if (hasValidToken(context)) return
        synchronized(authLock) {
            if (hasValidToken(context)) return   // another thread finished while we waited
            if (SecureStorageManager.getRefreshToken(context).isNotEmpty() && refreshAccessToken(context)) return
            deviceProofBootstrapBlocking(context)
        }
    }

    private fun saveTokenPayload(context: Context, parsed: JSONObject): Boolean {
        if (!parsed.optBoolean("success", false)) return false
        val access = parsed.optString("access_token", parsed.optString("token", ""))
        if (access.isEmpty()) return false

        val expiresIn = parsed.optLong("expires_in", 900L).coerceAtLeast(60L)
        SecureStorageManager.saveToken(context, access)
        SecureStorageManager.saveAccessExpiresAt(
            context,
            System.currentTimeMillis() + (expiresIn * 1000L)
        )

        val refresh = parsed.optString("refresh_token", "")
        if (refresh.isNotEmpty()) SecureStorageManager.saveRefreshToken(context, refresh)
        return true
    }

    private fun rawClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    @JvmStatic fun updateToken(context: Context, newToken: String) {
        SecureStorageManager.saveToken(context, newToken)
        SecureStorageManager.saveAccessExpiresAt(context, System.currentTimeMillis() + 5 * 60_000L)
        synchronized(this) { client = null }
    }

    /** Drops tokens only. The device id and registration flag must survive. */
    @JvmStatic fun clearToken(context: Context) {
        SecureStorageManager.clearToken(context)
    }

    @JvmStatic external fun getBaseUrl(): String
}
