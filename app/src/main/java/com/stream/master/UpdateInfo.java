package com.stream.master;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class UpdateInfo {
    @SerializedName("latest_version_code")
    public int latest_version_code;
    @SerializedName("latest_version_name")
    public String latest_version_name;
    @SerializedName("messages")
    public Map<String, String> messages;
    @SerializedName("telegram_url")
    public String telegram_url;

    // In-app update (see AppUpdateManager.java). All three travel together - apk_url is the
    // GitHub Release's stable .../releases/latest/download/... link; apk_sha256 is verified
    // against the downloaded bytes before the install prompt is ever shown, so a corrupted or
    // tampered-with download is caught locally instead of being handed to PackageInstaller.
    @SerializedName("apk_url")
    public String apk_url;
    @SerializedName("apk_sha256")
    public String apk_sha256;
    @SerializedName("apk_size_bytes")
    public long apk_size_bytes;

    /** True only when every field the in-app download flow needs is actually present and well-formed. */
    public boolean hasInAppUpdate() {
        return apk_url != null && apk_url.trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://")
                && apk_sha256 != null && apk_sha256.trim().length() == 64;
    }

    public String getMessage(String lang) {
        if (messages != null && messages.containsKey(lang)) {
            return messages.get(lang);
        }
        return messages != null ? messages.get("en") : "A new update is available!";
    }
}