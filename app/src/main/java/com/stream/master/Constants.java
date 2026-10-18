package com.stream.master;

import android.content.Context;

import org.json.JSONObject;

public class Constants {

    /**
     * Bootstrap URL only. It is returned by the native library.
     * All changeable GitHub/configuration URLs are supplied by /api/config.
     */
    public static String BASE_URL = SecureNetworkClient.getBaseUrl();

    public static String GITHUB_BASE = "";
    public static String GITHUB_PROVIDER_JSON = "";
    public static String GITHUB_CALLBACK_JSON = "";
    public static String GITHUB_HOST_JSON = "";
    public static String GITHUB_CONFIG_JSON = "";
    public static String GITHUB_SUBS_JSON = "";
    public static String GITHUB_PROVIDERS_DIR = "";
    public static String GITHUB_PROVIDER_SIGNATURES_JSON = "";
    public static String GITHUB_UPDATE_JSON = "";

    private static volatile boolean remoteConfigApplied = false;

    public static final String KEY_MOVIE = "MOVIE";
    public static final String PREF_NAME = "app_prefs";

    public static synchronized void loadConfig(Context context) {
        if (validHttpUrl(BASE_URL).isEmpty()) {
            BASE_URL = ensureTrailingSlash(
                    SecureNetworkClient.getBaseUrl()
            );
        }
    }

    /**
     * Apply the URLs returned by the server. No GitHub identity or repository
     * information is stored in the native library anymore.
     *
     * Supports both absolute *_url fields and the server's environment-style
     * fields (github_raw_base/user/repo/branch/path values).
     */
    public static synchronized void applyRemoteConfig(JSONObject config) {
        if (config == null) return;

        String provider = firstUrl(
                config,
                "provider_json_url",
                "github_provider_json_url"
        );

        String callback = firstUrl(
                config,
                "callback_json_url",
                "github_callback_json_url"
        );

        String host = firstUrl(
                config,
                "host_json_url",
                "github_host_json_url"
        );

        String cfg = firstUrl(
                config,
                "config_json_url",
                "github_config_json_url"
        );

        String subs = firstUrl(
                config,
                "subs_json_url",
                "github_subs_json_url"
        );

        String providers = firstUrl(
                config,
                "providers_dir_url",
                "github_providers_dir_url"
        );

        String signatures = firstUrl(
                config,
                "provider_signatures_json_url",
                "github_provider_signatures_json_url"
        );

        String update = firstUrl(
                config,
                "update_json_url",
                "github_update_json_url"
        );

        String base = firstUrl(
                config,
                "github_base_url",
                "github_raw_base_url"
        );

        if (provider.isEmpty()
                || callback.isEmpty()
                || host.isEmpty()
                || cfg.isEmpty()
                || subs.isEmpty()
                || providers.isEmpty()
                || signatures.isEmpty()
                || update.isEmpty()) {

            String raw = validHttpUrl(
                    config.optString("github_raw_base", "")
            );

            String user = clean(
                    config.optString("github_user", "")
            );

            String repo = clean(
                    config.optString("github_repo", "")
            );

            String branch = clean(
                    config.optString("github_branch", "")
            );

            if (raw.isEmpty()) {
                raw = base;
            }

            if (!raw.isEmpty()
                    && !user.isEmpty()
                    && !repo.isEmpty()
                    && !branch.isEmpty()) {

                String root =
                        ensureTrailingSlash(raw)
                                + user
                                + "/"
                                + repo
                                + "/"
                                + branch
                                + "/";

                provider = provider.isEmpty()
                        ? validHttpUrl(
                                root + cleanPath(
                                        config.optString(
                                                "github_provider_json",
                                                "github/provider.json"
                                        )
                                )
                        )
                        : provider;

                callback = callback.isEmpty()
                        ? validHttpUrl(
                                root + cleanPath(
                                        config.optString(
                                                "github_callback_json",
                                                "github/callback.json"
                                        )
                                )
                        )
                        : callback;

                host = host.isEmpty()
                        ? validHttpUrl(
                                root + cleanPath(
                                        config.optString(
                                                "github_host_json",
                                                "github/host.json"
                                        )
                                )
                        )
                        : host;

                cfg = cfg.isEmpty()
                        ? validHttpUrl(
                                root + cleanPath(
                                        config.optString(
                                                "github_config_json",
                                                "github/config.json"
                                        )
                                )
                        )
                        : cfg;

                subs = subs.isEmpty()
                        ? validHttpUrl(
                                root + cleanPath(
                                        config.optString(
                                                "github_subs_json",
                                                "github/subs.json"
                                        )
                                )
                        )
                        : subs;

                providers = providers.isEmpty()
                        ? validHttpUrl(
                                root + cleanPath(
                                        config.optString(
                                                "github_providers_dir",
                                                "github/providers/"
                                        )
                                )
                        )
                        : providers;

                signatures = signatures.isEmpty()
                        ? validHttpUrl(
                                root + cleanPath(
                                        config.optString(
                                                "github_provider_signatures_json",
                                                "github/provider-signatures.json"
                                        )
                                )
                        )
                        : signatures;

                update = update.isEmpty()
                        ? validHttpUrl(
                                root + cleanPath(
                                        config.optString(
                                                "github_update_json",
                                                "github/update.json"
                                        )
                                )
                        )
                        : update;

                base = base.isEmpty() ? root : base;
            }
        }

        if (!base.isEmpty()) {
            GITHUB_BASE = ensureTrailingSlash(base);
        }

        if (!provider.isEmpty()) {
            GITHUB_PROVIDER_JSON = provider;
        }

        if (!callback.isEmpty()) {
            GITHUB_CALLBACK_JSON = callback;
        }

        if (!host.isEmpty()) {
            GITHUB_HOST_JSON = host;
        }

        if (!cfg.isEmpty()) {
            GITHUB_CONFIG_JSON = cfg;
        }

        if (!subs.isEmpty()) {
            GITHUB_SUBS_JSON = subs;
        }

        if (!providers.isEmpty()) {
            GITHUB_PROVIDERS_DIR =
                    ensureTrailingSlash(providers);
        }

        if (!signatures.isEmpty()) {
            GITHUB_PROVIDER_SIGNATURES_JSON = signatures;
        }

        if (!update.isEmpty()) {
            GITHUB_UPDATE_JSON = update;
        }

        remoteConfigApplied =
                !GITHUB_PROVIDER_JSON.isEmpty()
                        && !GITHUB_CALLBACK_JSON.isEmpty()
                        && !GITHUB_PROVIDERS_DIR.isEmpty()
                        && !GITHUB_PROVIDER_SIGNATURES_JSON.isEmpty()
                        && !GITHUB_UPDATE_JSON.isEmpty();
    }

    private static String firstUrl(JSONObject o, String... keys) {
        for (String key : keys) {
            String value =
                    validHttpUrl(o.optString(key, ""));

            if (!value.isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private static String clean(String value) {
        return value == null
                ? ""
                : value.trim().replace("/", "");
    }

    private static String cleanPath(String value) {
        String v = value == null ? "" : value.trim();

        while (v.startsWith("/")) {
            v = v.substring(1);
        }

        return v;
    }

    private static String validHttpUrl(String value) {
        if (value == null) {
            return "";
        }

        String url = value.trim();

        if (url.isEmpty()) {
            return "";
        }

        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();

            return ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))
                    ? url
                    : "";

        } catch (Exception ignored) {
            return "";
        }
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/")
                ? value
                : value + "/";
    }

    public static boolean hasRemoteGithubConfig() {
        return remoteConfigApplied;
    }

    public static synchronized void resetConfig(Context context) {

        context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        ).edit().clear().apply();

        remoteConfigApplied = false;

        GITHUB_BASE = "";
        GITHUB_PROVIDER_JSON = "";
        GITHUB_CALLBACK_JSON = "";
        GITHUB_HOST_JSON = "";
        GITHUB_CONFIG_JSON = "";
        GITHUB_SUBS_JSON = "";
        GITHUB_PROVIDERS_DIR = "";
        GITHUB_PROVIDER_SIGNATURES_JSON = "";
        GITHUB_UPDATE_JSON = "";

        BASE_URL = SecureNetworkClient.getBaseUrl();
    }
}
