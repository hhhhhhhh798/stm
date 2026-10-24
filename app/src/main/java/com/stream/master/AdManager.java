package com.stream.master;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.unity3d.mediation.LevelPlay;
import com.unity3d.mediation.LevelPlayAdError;
import com.unity3d.mediation.LevelPlayAdInfo;
import com.unity3d.mediation.LevelPlayConfiguration;
import com.unity3d.mediation.LevelPlayInitError;
import com.unity3d.mediation.LevelPlayInitListener;
import com.unity3d.mediation.LevelPlayInitRequest;
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd;
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Process-scoped Unity LevelPlay interstitial manager.
 *
 * Configuration is fetched from the authenticated server endpoint.
 * The server returns a short-lived-token-bound HMAC envelope, so the
 * client never contains the Unity App Key or Interstitial Ad Unit ID
 * as build-time constants.
 *
 * Interstitials are never presented while PlayerActivity is visible.
 */
public final class AdManager {

    private static String adErrorMessage(LevelPlayAdError error) {
        if (error == null) return "Unknown error";
        String message = error.getErrorMessage();
        if (message == null || message.trim().isEmpty()) message = String.valueOf(error);
        return message;
    }

    private static final String PREFS = "StreamMasterAdsPrefs";
    private static final String KEY_LAST_SHOWN = "last_shown_ms";

    // Match the previous Smartlink cadence:
    // first automatic attempt after 20 seconds, then every 90 seconds.
    private static final long FIRST_DELAY_MS = 20_000L;
    private static final long REPEAT_DELAY_MS = 90_000L;
    private static final long BACKGROUND_RETRY_MS = 15_000L;
    private static final int MAX_ADS_PER_PROCESS = 30;

    private static final AtomicBoolean INITIALIZATION_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean CONFIG_FETCH_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean GLOBAL_SCHEDULER_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean SHOW_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicInteger SESSION_AD_COUNT = new AtomicInteger(0);

    private static final long PROCESS_START_TIME =
            android.os.SystemClock.elapsedRealtime();

    private static final Handler GLOBAL_HANDLER =
            new Handler(Looper.getMainLooper());

    private static volatile LevelPlayInterstitialAd interstitialAd;
    private static volatile boolean levelPlayInitialized = false;
    private static volatile boolean adsEnabled = true;
    private static volatile long cooldownMs = REPEAT_DELAY_MS;

    @Nullable
    private static volatile WeakReference<Activity> currentActivity;

    private final Context appContext;

    private final Runnable globalAutomaticRunnable = this::runGlobalAutomaticAttempt;

    public AdManager(Context context) {
        appContext = context.getApplicationContext();
        Constants.loadConfig(appContext);
        setCurrentActivity(context instanceof Activity ? (Activity) context : null);
        fetchAndInitializeIfNeeded();
        startGlobalAutomaticScheduler();
    }

    public static void setCurrentActivity(@Nullable Activity activity) {
        if (activity == null) {
            currentActivity = null;
        } else {
            currentActivity = new WeakReference<>(activity);
        }
    }

    public static void clearCurrentActivity(Activity activity) {
        WeakReference<Activity> ref = currentActivity;
        Activity current = ref != null ? ref.get() : null;
        if (current == activity) {
            currentActivity = null;
        }
    }

    /**
     * Compatibility hook retained for older call sites. Playback is excluded
     * from advertisement presentation by BaseActivity.
     */
    public static void setPlayerActive(boolean active) {
        // Intentionally no-op. PlayerActivity never hosts advertisements.
    }

    public static boolean isPlayerActive() {
        return false;
    }

    public void handleClick(@Nullable android.view.View ignoredView) {
        trigger("click");
    }

    public boolean triggerAutomatic() {
        return trigger("automatic");
    }

    public boolean trigger(String reason) {
        if (!adsEnabled || !levelPlayInitialized) {
            return false;
        }
        if (!isAppForeground()) {
            return false;
        }
        if (SESSION_AD_COUNT.get() >= MAX_ADS_PER_PROCESS) {
            return false;
        }
        if (!canShowNow()) {
            return false;
        }

        Activity activity = getCurrentActivity();
        LevelPlayInterstitialAd ad = interstitialAd;

        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || ad == null || !ad.isAdReady()) {
            return false;
        }

        if (!SHOW_IN_FLIGHT.compareAndSet(false, true)) return false;

        try {
            ad.showAd(activity);
            return true;
        } catch (Throwable error) {
            SHOW_IN_FLIGHT.set(false);
            return false;
        }
    }

    /**
     * Compatibility method for existing call sites. Page entry no longer
     * uses a separate browser-opening ad flow.
     */
    public void schedulePageAd() {
        // The global scheduler is responsible for cadence process-wide.
    }

    public void cancelPageAd() {
        // No page-local advertisement timer exists anymore.
    }

    private void fetchAndInitializeIfNeeded() {

        if (levelPlayInitialized || INITIALIZATION_STARTED.get()
                || CONFIG_FETCH_IN_FLIGHT.get()) {
            return;
        }

        if (!CONFIG_FETCH_IN_FLIGHT.compareAndSet(false, true)) return;

        String base = Constants.BASE_URL;
        if (base == null || base.trim().isEmpty()) {
            CONFIG_FETCH_IN_FLIGHT.set(false);
            return;
        }

        if (!base.endsWith("/")) base += "/";
        final String configUrl = base + "api/ads/config";


        // Do not race the first ad-config request against bootstrap/refresh.
        // The normal API client already owns the complete authentication flow.
        SecureNetworkClient.ensureTokenAvailable(appContext, success -> {
            if (!success) {
                CONFIG_FETCH_IN_FLIGHT.set(false);
                return;
            }

            requestAdsConfig(configUrl, false);
        });
    }

    private void requestAdsConfig(String configUrl, boolean retryAfter401) {
        Request request = new Request.Builder()
                .url(configUrl)
                .get()
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "StreamMaster/4.12 Android")
                .build();

        try {
            SecureNetworkClient.getClient(appContext).newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    CONFIG_FETCH_IN_FLIGHT.set(false);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response r = response) {

                        if (r.code() == 401 && !retryAfter401) {
                            SecureNetworkClient.ensureTokenAvailable(appContext, success -> {
                                if (success) {
                                    requestAdsConfig(configUrl, true);
                                } else {
                                    CONFIG_FETCH_IN_FLIGHT.set(false);
                                }
                            });
                            return;
                        }

                        if (!r.isSuccessful() || r.body() == null) {
                            CONFIG_FETCH_IN_FLIGHT.set(false);
                            return;
                        }

                        String body = r.body().string();
                        JSONObject envelope = new JSONObject(body);
                        if (!envelope.optBoolean("success", false)) {
                            return;
                        }

                        String payloadB64 = envelope.optString("payload_b64", "");
                        String signature = envelope.optString("signature", "");
                        if (payloadB64.isEmpty() || signature.isEmpty()) {
                            return;
                        }

                        byte[] payloadBytes = android.util.Base64.decode(payloadB64, android.util.Base64.NO_WRAP);
                        String payload = new String(payloadBytes, StandardCharsets.UTF_8);

                        String token = SecureStorageManager.getToken(appContext);
                        if (token.isEmpty()) {
                            return;
                        }

                        String expected = hmac(payload, token);
                        if (!constantTimeEqual(expected, signature)) {
                            return;
                        }

                        JSONObject config = new JSONObject(payload);
                        int version = config.optInt("version", 0);
                        if (version < 1) {
                            return;
                        }

                        adsEnabled = config.optBoolean("ads_enabled", true);
                        long configuredCooldown = config.optLong("interstitial_cooldown_sec", 90L);
                        cooldownMs = Math.max(15_000L,
                                Math.min(86_400_000L, configuredCooldown * 1000L));

                        if (!adsEnabled) {

                            return;
                        }

                        String appKey = config.optString("app_key", "").trim();
                        String adUnitId = config.optString("interstitial_ad_unit_id", "").trim();

                        if (appKey.isEmpty() || adUnitId.isEmpty()) {
                            return;
                        }

                        initializeLevelPlay(appKey, adUnitId);
                    } catch (Throwable error) {
                    } finally {
                        CONFIG_FETCH_IN_FLIGHT.set(false);
                    }
                }
            });
        } catch (Throwable error) {
            CONFIG_FETCH_IN_FLIGHT.set(false);
        }
    }

    private void initializeLevelPlay(String appKey, String adUnitId) {

        if (!INITIALIZATION_STARTED.compareAndSet(false, true)) {
            return;
        }

        Activity activity = getCurrentActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            INITIALIZATION_STARTED.set(false);
            return;
        }

        LevelPlayInitRequest initRequest =
                new LevelPlayInitRequest.Builder(appKey).build();

        LevelPlay.init(activity, initRequest, new LevelPlayInitListener() {
            @Override
            public void onInitFailed(@Nullable LevelPlayInitError error) {
                levelPlayInitialized = false;
                INITIALIZATION_STARTED.set(false);

            }

            @Override
            public void onInitSuccess(LevelPlayConfiguration configuration) {
                levelPlayInitialized = true;
                createInterstitialAd(adUnitId);

            }
        });
    }

    private void createInterstitialAd(String adUnitId) {
        try {
            LevelPlayInterstitialAd ad =
                    new LevelPlayInterstitialAd(adUnitId);

            ad.setListener(new LevelPlayInterstitialAdListener() {
                @Override
                public void onAdLoaded(@androidx.annotation.NonNull LevelPlayAdInfo adInfo) {

                }

                @Override
                public void onAdLoadFailed(@androidx.annotation.NonNull LevelPlayAdError error) {
                    SHOW_IN_FLIGHT.set(false);
                }

                @Override
                public void onAdDisplayed(@androidx.annotation.NonNull LevelPlayAdInfo adInfo) {
                    SESSION_AD_COUNT.incrementAndGet();
                    markShown();
                    SHOW_IN_FLIGHT.set(false);

                }

                @Override
                public void onAdDisplayFailed(
                        @androidx.annotation.NonNull LevelPlayAdError error,
                        @androidx.annotation.NonNull LevelPlayAdInfo adInfo) {
                    SHOW_IN_FLIGHT.set(false);

                    ad.loadAd();
                }

                @Override
                public void onAdClicked(@androidx.annotation.NonNull LevelPlayAdInfo adInfo) {

                }

                @Override
                public void onAdClosed(@androidx.annotation.NonNull LevelPlayAdInfo adInfo) {
                    SHOW_IN_FLIGHT.set(false);
                    if (adsEnabled) ad.loadAd();

                }

                @Override
                public void onAdInfoChanged(@androidx.annotation.NonNull LevelPlayAdInfo adInfo) {
                    // Optional LevelPlay bid information update.
                }
            });

            interstitialAd = ad;

            ad.loadAd();
        } catch (Throwable error) {
            interstitialAd = null;
            levelPlayInitialized = false;
            INITIALIZATION_STARTED.set(false);

        }
    }

    private boolean canShowNow() {
        long last = appContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SHOWN, 0L);

        if (last <= 0L) {
            long elapsed =
                    android.os.SystemClock.elapsedRealtime() - PROCESS_START_TIME;
            return elapsed >= FIRST_DELAY_MS;
        }

        return System.currentTimeMillis() - last >= cooldownMs;
    }

    private void markShown() {
        appContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SHOWN, System.currentTimeMillis())
                .apply();
    }

    private void startGlobalAutomaticScheduler() {
        if (!GLOBAL_SCHEDULER_STARTED.compareAndSet(false, true)) {
            return;
        }
        scheduleNextGlobalAutomatic(true);
    }

    private void scheduleNextGlobalAutomatic(boolean first) {
        GLOBAL_HANDLER.removeCallbacks(globalAutomaticRunnable);

        long delay = first
                ? FIRST_DELAY_MS
                : REPEAT_DELAY_MS;

        GLOBAL_HANDLER.postDelayed(globalAutomaticRunnable, delay);
    }

    private void runGlobalAutomaticAttempt() {
        if (!isAppForeground()) {
            GLOBAL_HANDLER.postDelayed(
                    globalAutomaticRunnable,
                    BACKGROUND_RETRY_MS);
            return;
        }

        trigger("automatic_global");
        scheduleNextGlobalAutomatic(false);
    }

    private boolean isAppForeground() {
        Activity activity = getCurrentActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;

        try {
            ActivityManager manager =
                    (ActivityManager) appContext.getSystemService(
                            Context.ACTIVITY_SERVICE);

            if (manager == null) return true;

            java.util.List<ActivityManager.RunningAppProcessInfo> processes =
                    manager.getRunningAppProcesses();

            if (processes == null) return false;

            String packageName = appContext.getPackageName();

            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (packageName.equals(process.processName)) {
                    return process.importance ==
                            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                            || process.importance ==
                            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    @Nullable
    private static Activity getCurrentActivity() {
        WeakReference<Activity> ref = currentActivity;
        return ref != null ? ref.get() : null;
    }
    private static String hmac(String data, String key) throws Exception {
        javax.crypto.Mac mac =
                javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return android.util.Base64.encodeToString(
                mac.doFinal(data.getBytes(StandardCharsets.UTF_8)),
                android.util.Base64.NO_WRAP);
    }

    private static boolean constantTimeEqual(String a, String b) {
        try {
            return MessageDigest.isEqual(
                    a.getBytes(StandardCharsets.UTF_8),
                    b.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void release() {
        // Process-scoped manager: activity destruction must not destroy LevelPlay.
        clearCurrentActivityIfOwned();
    }

    private void clearCurrentActivityIfOwned() {
        Activity current = getCurrentActivity();
        if (current != null && current.isFinishing()) {
            currentActivity = null;
        }
    }
}
