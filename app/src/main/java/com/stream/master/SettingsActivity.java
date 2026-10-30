package com.stream.master;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class SettingsActivity extends BaseActivity {

    // Must match PlayerActivity.PREF_NAME: it is where every "pos_<vid>", "dur_<vid>" and
    // "history_<vid>" (continue-watching / resume) entry is stored.
    private static final String RESUME_PREF_NAME = "StreamMasterResumePrefs";

    private LinearLayout btnLangAr, btnLangEn, btnLangEs;
    private ImageView imgCheckAr, imgCheckEn, imgCheckEs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        FrameLayout btnBack = findViewById(R.id.btnBack);
        btnLangAr = findViewById(R.id.btnLangAr);
        btnLangEn = findViewById(R.id.btnLangEn);
        btnLangEs = findViewById(R.id.btnLangEs);

        imgCheckAr = findViewById(R.id.imgCheckAr);
        imgCheckEn = findViewById(R.id.imgCheckEn);
        imgCheckEs = findViewById(R.id.imgCheckEs);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        updateActiveLanguage(getCurrentLang());

        if (btnLangAr != null) btnLangAr.setOnClickListener(v -> setAppLocale("ar"));
        if (btnLangEn != null) btnLangEn.setOnClickListener(v -> setAppLocale("en"));
        if (btnLangEs != null) btnLangEs.setOnClickListener(v -> setAppLocale("es"));

        View btnClearWatchHistory = findViewById(R.id.btnClearWatchHistory);
        if (btnClearWatchHistory != null) {
            btnClearWatchHistory.setOnClickListener(v -> confirmClearWatchHistory());
        }
    }

    // ✅ تغيير private → public
    public void setAppLocale(String langCode) {
        if (!getCurrentLang().equals(langCode)) {
            SecureStorageManager.saveLanguage(this, langCode);
            updateActiveLanguage(langCode);

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void updateActiveLanguage(String langCode) {
        if (imgCheckAr != null && imgCheckEn != null && imgCheckEs != null) {
            imgCheckAr.setVisibility(langCode.equals("ar") ? View.VISIBLE : View.INVISIBLE);
            imgCheckEn.setVisibility(langCode.equals("en") ? View.VISIBLE : View.INVISIBLE);
            imgCheckEs.setVisibility(langCode.equals("es") ? View.VISIBLE : View.INVISIBLE);
        }
        if (btnLangAr != null) btnLangAr.setActivated("ar".equals(langCode));
        if (btnLangEn != null) btnLangEn.setActivated("en".equals(langCode));
        if (btnLangEs != null) btnLangEs.setActivated("es".equals(langCode));
    }

    private void confirmClearWatchHistory() {
        SharedPreferences prefs = getSharedPreferences(RESUME_PREF_NAME, MODE_PRIVATE);
        boolean hasAnyHistory = false;
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("pos_") || key.startsWith("dur_") || key.startsWith("history_")) {
                hasAnyHistory = true;
                break;
            }
        }
        if (!hasAnyHistory) {
            showCustomToast(getString(R.string.watch_history_empty_toast));
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.delete_watch_history_title))
                .setMessage(getString(R.string.delete_watch_history_message))
                .setPositiveButton(getString(R.string.delete_confirm), (d, which) -> clearWatchHistory())
                .setNegativeButton(getString(R.string.cancel), null)
                .create();
        showStyledDialog(dialog);
    }

    /** Removes every resume position / duration / continue-watching entry. Never touches favorites or language prefs. */
    private void clearWatchHistory() {
        SharedPreferences prefs = getSharedPreferences(RESUME_PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("pos_") || key.startsWith("dur_") || key.startsWith("history_")) {
                editor.remove(key);
            }
        }
        editor.apply();
        // HomeFragment deliberately skips rebuilding on every onResume (keeps scroll state) -
        // this flag tells it to do a *targeted* Continue Watching refresh just this once.
        HomeFragment.watchHistoryClearedPending = true;
        showCustomToast(getString(R.string.watch_history_cleared_toast));
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_comic_surface);
            }
            int peach = getColor(R.color.comic_peach);
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(peach);
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(peach);
        });
        dialog.show();
    }
}
