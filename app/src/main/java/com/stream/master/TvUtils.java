package com.stream.master;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/** Detects Android TV / Fire TV / set-top boxes (remote-control devices). */
public final class TvUtils {
    private TvUtils() {}

    /** Width (in dp) the TV layout is scaled to. Phone layouts were designed for ~400dp. */
    private static final int TV_TARGET_WIDTH_DP = 700;

    private static volatile Boolean cached;

    public static boolean isTv(Context context) {
        Boolean c = cached;
        if (c != null) return c;
        boolean tv = false;
        try {
            UiModeManager um = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            if (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) tv = true;
            PackageManager pm = context.getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                    || pm.hasSystemFeature("android.hardware.type.television")
                    || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                tv = true;
            }
        } catch (Throwable ignored) { }
        cached = tv;
        return tv;
    }

    /**
     * A 1080p TV reports ~960dp of width, so the phone UI (designed for ~400dp) would look tiny and
     * stretched. On TV we render at a density that gives ~700dp of width instead.
     */
    public static void applyTvDensity(Context base, Configuration config) {
        try {
            DisplayMetrics dm = base.getResources().getDisplayMetrics();
            int widthPx = Math.max(dm.widthPixels, dm.heightPixels);
            int dpi = Math.round(widthPx * 160f / TV_TARGET_WIDTH_DP);
            config.densityDpi = Math.max(240, Math.min(640, dpi));
        } catch (Throwable ignored) { }
    }
}
