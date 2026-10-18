package com.stream.master;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.graphics.Color;

import androidx.fragment.app.FragmentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class BaseActivity extends FragmentActivity {

    private AdManager adManager;

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setupGlobalSearch();
    }

    private void setupGlobalSearch() {
        View search = findViewById(R.id.globalSearchButton);
        if (search != null) {
            search.setOnClickListener(v -> {
                if (this instanceof MainActivity) {
                    ((MainActivity) this).openSearch();
                } else {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.putExtra("OPEN_SEARCH", true);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                }
            });
        }
    }

    // ============================================================
    // CUSTOM TOAST
    // ============================================================

    public void showCustomToast(
            String message
    ) {

        View layout =
                LayoutInflater
                        .from(this)
                        .inflate(
                                R.layout.custom_toast,
                                null
                        );

        TextView text =
                layout.findViewById(
                        R.id.text
                );

        text.setText(message);

        Toast toast =
                new Toast(this);

        toast.setDuration(
                Toast.LENGTH_SHORT
        );

        toast.setView(layout);
        toast.show();
    }

    // ============================================================
    // LANGUAGE
    // ============================================================

    @Override
    protected void attachBaseContext(
            Context newBase
    ) {

        String defaultSystemLang =
                Locale.getDefault()
                        .getLanguage();

        String lang =
                SecureStorageManager
                        .getLanguage(
                                newBase
                        );

        if (
                lang == null
                        || lang.isEmpty()
        ) {

            lang =
                    defaultSystemLang;
        }

        Locale locale =
                new Locale(lang);

        Locale.setDefault(locale);

        Configuration config =
                new Configuration(
                        newBase
                                .getResources()
                                .getConfiguration()
                );

        config.setLocale(locale);
        if (TvUtils.isTv(newBase)) TvUtils.applyTvDensity(newBase, config);

        super.attachBaseContext(
                newBase
                        .createConfigurationContext(
                                config
                        )
        );
    }

    // ============================================================
    // ACTIVITY LIFECYCLE
    // ============================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        // Phones/tablets stay portrait (this used to be forced in the manifest, which broke
        // TVs). PlayerActivity manages its own landscape orientation.
        if (!TvUtils.isTv(this) && lockPortraitOnPhones()) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        super.onCreate(
                savedInstanceState
        );

        if (TvUtils.isTv(this)) installTvFocusSupport();

        // Android 15 / targetSdk 35: explicitly opt into edge-to-edge
        // and apply system-bar insets to the Activity content root.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                getWindow().getDecorView(),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );
                    View content = findViewById(android.R.id.content);
                    if (content != null) {
                        content.setPadding(
                                bars.left,
                                bars.top,
                                bars.right,
                                bars.bottom
                        );
                    }
                    return insets;
                }
        );
        ViewCompat.requestApplyInsets(getWindow().getDecorView());

        // The player is intentionally ad-free. Do not create or register an
        // advertisement host for PlayerActivity.
        if (!(this instanceof PlayerActivity)) {
            adManager = new AdManager(this);
            AdManager.setCurrentActivity(this);
        } else {
            AdManager.clearCurrentActivity(this);
        }
        RemoteConfigManager.refresh(this);
    }

    /** Activities that manage their own orientation (the player) return false. */
    protected boolean lockPortraitOnPhones() {
        return !(this instanceof PlayerActivity);
    }

    // ============================================================
    // TV / REMOTE CONTROL SUPPORT
    // ============================================================

    private final android.os.Handler tvFocusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tvFocusRunnable = () -> {
        View root = getWindow() != null ? getWindow().getDecorView() : null;
        if (root != null) TvFocus.prepare(root);
    };

    /** Makes every clickable view (also ones created later, e.g. list items) reachable with the D-pad. */
    private void installTvFocusSupport() {
        View root = getWindow().getDecorView();
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            tvFocusHandler.removeCallbacks(tvFocusRunnable);
            tvFocusHandler.postDelayed(tvFocusRunnable, 250L);
        });
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(androidx.fragment.app.FragmentManager fm,
                                                     androidx.fragment.app.Fragment f,
                                                     View v, Bundle savedInstanceState) {
                        tvFocusHandler.removeCallbacks(tvFocusRunnable);
                        tvFocusHandler.postDelayed(tvFocusRunnable, 250L);
                    }
                }, true);
    }

    @Override
    protected void onResume() {

        if (SecurityGuard.shouldBlock(this)) {
            finishAndRemoveTask();
            return;
        }

        super.onResume();

        if (!(this instanceof PlayerActivity)) {
            if (adManager == null) {
                adManager = new AdManager(this);
            }
            AdManager.setCurrentActivity(this);
        } else {
            AdManager.clearCurrentActivity(this);
        }
        RemoteConfigManager.refresh(this);
    }

    @Override
    protected void onPause() {
        AdManager.clearCurrentActivity(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        tvFocusHandler.removeCallbacksAndMessages(null);
        AdManager.clearCurrentActivity(this);
        if (adManager != null) {
            adManager.release();
        }
        super.onDestroy();
    }

    // ============================================================
    // LANGUAGE HELPERS
    // ============================================================

    public String getCurrentLang() {

        String lang =
                SecureStorageManager
                        .getLanguage(
                                this
                        );

        if (
                lang == null
                        || lang.isEmpty()
        ) {

            lang =
                    Locale.getDefault()
                            .getLanguage();
        }

        return lang;
    }

    public void setAppLocale(
            String langCode
    ) {

        if (
                !getCurrentLang()
                        .equals(langCode)
        ) {

            SecureStorageManager
                    .saveLanguage(
                            this,
                            langCode
                    );

            recreate();
        }
    }
}