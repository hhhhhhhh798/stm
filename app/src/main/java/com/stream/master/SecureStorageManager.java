package com.stream.master;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import android.os.Build;
import android.provider.Settings;
import java.util.Locale;

public class SecureStorageManager {
    private static final String PREFS_NAME = "secure_prefs";
    private static final String KEY_LANGUAGE = "app_lang";
    private static final String KEY_ACCESS_EXPIRES_AT = "access_expires_at";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_DEVICE_REGISTERED = "device_key_registered";
    private static SharedPreferences sharedPreferences = null;
    private static boolean isInitialized = false;

    public static void init(Context context) {
        if (isInitialized) return;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            isInitialized = true;
        } catch (GeneralSecurityException | IOException e) {
            // Never downgrade secrets to plaintext SharedPreferences.
            // If Keystore-backed storage is unavailable, operate in memory/default-only
            // mode so the app can continue without persisting sensitive values.
            sharedPreferences = null;
            isInitialized = true;
        }
    }

    private static SharedPreferences getPreferences() {
        return sharedPreferences;
    }

    public static void saveString(String key, String value) {
        SharedPreferences prefs = getPreferences();
        if (prefs != null) {
            prefs.edit().putString(key, value).apply();
        }
    }

    public static String getString(String key, String defaultValue) {
        SharedPreferences prefs = getPreferences();
        return prefs != null ? prefs.getString(key, defaultValue) : defaultValue;
    }

    public static void saveLanguage(Context context, String langCode) {
        if (!isInitialized) {
            init(context);
        }
        saveString(KEY_LANGUAGE, langCode);
    }

    public static String getLanguage(Context context) {
        if (!isInitialized) {
            init(context);
        }
        String lang = getString(KEY_LANGUAGE, null);
        if (lang == null || lang.isEmpty()) {
            lang = Locale.getDefault().getLanguage();
        }
        return lang;
    }

    public static void saveToken(Context context, String token) {
        if (!isInitialized) {
            init(context);
        }
        saveString("jwt_token", token);
    }

    public static String getToken(Context context) {
        if (!isInitialized) {
            init(context);
        }
        return getString("jwt_token", "");
    }

    public static void saveAccessExpiresAt(Context context, long expiresAtMs) {
        if (!isInitialized) init(context);
        saveString(KEY_ACCESS_EXPIRES_AT, Long.toString(expiresAtMs));
    }

    /** True once DeviceIdentity's public key has been accepted by the server (POST api/device/register). */
    public static boolean isDeviceKeyRegistered(Context context) {
        if (!isInitialized) init(context);
        return "1".equals(getString(KEY_DEVICE_REGISTERED, ""));
    }

    public static void markDeviceKeyRegistered(Context context) {
        if (!isInitialized) init(context);
        saveString(KEY_DEVICE_REGISTERED, "1");
    }

    public static void clearDeviceKeyRegistered(Context context) {
        if (!isInitialized) init(context);
        SharedPreferences prefs = getPreferences();
        if (prefs != null) prefs.edit().remove(KEY_DEVICE_REGISTERED).apply();
    }

    public static long getAccessExpiresAt(Context context) {
        if (!isInitialized) init(context);
        try { return Long.parseLong(getString(KEY_ACCESS_EXPIRES_AT, "0")); }
        catch (Exception ignored) { return 0L; }
    }

    public static void saveRefreshToken(Context context, String token) {
        if (!isInitialized) init(context);
        saveString(KEY_REFRESH_TOKEN, token);
    }

    public static String getRefreshToken(Context context) {
        if (!isInitialized) init(context);
        return getString(KEY_REFRESH_TOKEN, "");
    }

    public static String getOrCreateDeviceId(Context context) {
        if (!isInitialized) init(context);
        String id = getString(KEY_DEVICE_ID, "");
        if (id != null && id.matches("[0-9a-fA-F]{64}")) return id.toLowerCase(Locale.US);

        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) androidId = "";

        String raw = androidId + "|"
                + Build.MANUFACTURER + "|"
                + Build.MODEL + "|"
                + Build.BRAND + "|"
                + context.getPackageName();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            id = hex.toString();
        } catch (GeneralSecurityException e) {
            id = "";
        }

        if (!id.isEmpty()) saveString(KEY_DEVICE_ID, id);
        return id;
    }

    public static void clearToken(Context context) {
        if (!isInitialized) {
            init(context);
        }
        SharedPreferences prefs = getPreferences();
        if (prefs != null) {
            prefs.edit().remove("jwt_token").remove(KEY_ACCESS_EXPIRES_AT).remove(KEY_REFRESH_TOKEN).apply();
        }
    }

    public static void clearAll(Context context) {
        if (!isInitialized) {
            init(context);
        }
        SharedPreferences prefs = getPreferences();
        if (prefs != null) {
            prefs.edit().clear().apply();
        }
    }
}