package com.stream.master;

import android.content.Context;
import android.util.Base64;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Fetches runtime GitHub/config URLs from /api/config.
 * The server may return a plain JSON object (legacy/current deployment) or
 * the older signed envelope; plain JSON is intentionally supported so the
 * existing server does not need to be changed just for this client update.
 */
public final class RemoteConfigManager {
    private RemoteConfigManager() {}

    private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L;
    private static volatile long lastFetchMs = 0L;
    private static volatile JsonObject config = null;
    private static final AtomicBoolean fetching = new AtomicBoolean(false);
    private static final CopyOnWriteArrayList<Runnable> completionCallbacks = new CopyOnWriteArrayList<>();

    public static void refresh(Context context) { refresh(context, null); }

    public static void refresh(Context context, Runnable onComplete) {
        lastContext = context.getApplicationContext();
        if (onComplete != null) completionCallbacks.add(onComplete);

        long now = System.currentTimeMillis();
        if (now - lastFetchMs < REFRESH_INTERVAL_MS && Constants.hasRemoteGithubConfig()) {
            runCompletionCallbacks();
            return;
        }
        if (!fetching.compareAndSet(false, true)) return;

        try {
            Constants.loadConfig(context);
            String baseValue = Constants.BASE_URL;
            if (baseValue == null || baseValue.trim().isEmpty()) {
                fetching.set(false);
                runCompletionCallbacks();
                return;
            }
            final String base = baseValue.endsWith("/") ? baseValue : baseValue + "/";

            SecureNetworkClient.ensureTokenAvailable(context, success -> {
                if (!success) {
                    fetching.set(false);
                    runCompletionCallbacks();
                    return;
                }
                fetchPlainConfig(context, base);
            });
        } catch (Throwable ignored) {
            fetching.set(false);
            runCompletionCallbacks();
        }
    }

    private static void fetchPlainConfig(Context context, String base) {
        ApiService api = new Retrofit.Builder()
                .baseUrl(base)
                .client(SecureNetworkClient.getClient(context))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);

        api.getRemoteConfig().enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject body = response.body();
                        if (body.has("payload_b64") && body.has("signature")) {
                            // Signed-envelope compatibility is retained for
                            // deployments that still return the older format.
                            applySignedEnvelopeFallback(body);
                        } else {
                            Constants.applyRemoteConfig(new org.json.JSONObject(body.toString()));
                            config = body;
                            if (Constants.hasRemoteGithubConfig()) lastFetchMs = System.currentTimeMillis();
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    fetching.set(false);
                    runCompletionCallbacks();
                }
            }

            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                fetching.set(false);
                runCompletionCallbacks();
            }
        });
    }

    private static void applySignedEnvelopeFallback(JsonObject body) {
        try {
            if (!body.has("success") || !body.get("success").getAsBoolean()) return;
            String b64 = body.has("payload_b64") ? body.get("payload_b64").getAsString() : "";
            String signature = body.has("signature") ? body.get("signature").getAsString() : "";
            String token = SecureStorageManager.getToken(lastContext);
            if (b64.isEmpty() || signature.isEmpty() || token.isEmpty()) return;

            String payload = new String(Base64.decode(b64, Base64.NO_WRAP), StandardCharsets.UTF_8);
            String expected = hmac(payload, token);
            if (!constantTimeEqual(expected, signature)) return;

            org.json.JSONObject parsed = new org.json.JSONObject(payload);
            Constants.applyRemoteConfig(parsed);
            if (Constants.hasRemoteGithubConfig()) {
                config = new com.google.gson.Gson().fromJson(payload, JsonObject.class);
                lastFetchMs = System.currentTimeMillis();
            }
        } catch (Throwable ignored) { }
    }

    private static volatile Context lastContext;

    private static String hmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    private static boolean constantTimeEqual(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    public static JsonObject snapshot() { return config; }

    public static long getLong(String key, long fallback) {
        JsonObject c = config;
        if (c == null || !c.has(key)) return fallback;
        try { return c.get(key).getAsLong(); } catch (Throwable ignored) { return fallback; }
    }

    public static String getString(String key, String fallback) {
        JsonObject c = config;
        if (c == null || !c.has(key)) return fallback;
        try {
            String value = c.get(key).getAsString().trim();
            return value.isEmpty() ? fallback : value;
        } catch (Throwable ignored) { return fallback; }
    }

    private static void runCompletionCallbacks() {
        for (Runnable callback : completionCallbacks) {
            if (completionCallbacks.remove(callback)) {
                try { callback.run(); } catch (Throwable ignored) {}
            }
        }
    }
}
