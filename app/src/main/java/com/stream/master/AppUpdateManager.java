package com.stream.master;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * In-app self-update: downloads the new APK, verifies it, then hands it to Android's own
 * PackageInstaller. This is the only legitimate way to self-update outside Google Play - there is
 * no silent/background install path available to a normal (non-system, non-device-owner) app, so
 * the one unavoidable manual step is the system install-confirmation prompt itself.
 *
 * The download uses a fresh, plain OkHttpClient - deliberately NOT SecureNetworkClient.getClient(),
 * whose interceptor attaches our own X-Device-ID and Authorization bearer token to every request.
 * Those headers have no business going to GitHub's release CDN, and SecureNetworkClient's token-
 * refresh/retry behavior is tuned for our own API, not a large binary download from a third party.
 *
 * One instance per Fragment lifecycle, created and held by HomeFragment, because
 * registerForActivityResult() must be called before the Fragment reaches STARTED - see
 * HomeFragment.onCreate().
 */
final class AppUpdateManager {

    private final Fragment fragment;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient downloadClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private final ActivityResultLauncher<Intent> installPermissionLauncher;

    private UpdateInfo pendingInfo;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressText;
    private volatile boolean cancelled;

    AppUpdateManager(@NonNull Fragment fragment) {
        this.fragment = fragment;
        this.installPermissionLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // ACTION_MANAGE_UNKNOWN_APP_SOURCES has no meaningful result payload to read -
                    // we just re-check the actual permission state ourselves once the user is back.
                    if (hasInstallPermission() && pendingInfo != null) {
                        beginDownload(pendingInfo);
                    }
                    // Still not granted: the user backed out on purpose. They can tap
                    // "Update Now" again whenever they're ready - nothing more to do here.
                }
        );
    }

    private Context appContext() {
        return fragment.requireContext().getApplicationContext();
    }

    private boolean hasInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true; // pre-Oreo: no such gate exists
        PackageManager pm = fragment.requireContext().getPackageManager();
        return pm.canRequestPackageInstalls();
    }

    /** Entry point: call when the user taps "Update Now" on an UpdateInfo with hasInAppUpdate() == true. */
    void start(@NonNull UpdateInfo info) {
        if (!fragment.isAdded()) return;
        pendingInfo = info;
        if (hasInstallPermission()) {
            beginDownload(info);
        } else {
            showPermissionRationale();
        }
    }

    private void showPermissionRationale() {
        if (!fragment.isAdded()) return;
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.update_install_permission_title)
                .setMessage(R.string.update_install_permission_message)
                .setCancelable(true)
                .setPositiveButton(R.string.update_open_settings, (d, w) -> {
                    Uri packageUri = Uri.parse("package:" + fragment.requireContext().getPackageName());
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri);
                    try {
                        installPermissionLauncher.launch(intent);
                    } catch (Exception ignored) {
                        // A handful of OEM skins don't support the per-app variant of this screen.
                        try {
                            installPermissionLauncher.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
                        } catch (Exception ignoredToo) {
                            // Nothing more we can do - the user can still install manually via Telegram.
                        }
                    }
                })
                .setNegativeButton(R.string.room_cancel, null)
                .show();
    }

    private void beginDownload(UpdateInfo info) {
        if (!fragment.isAdded()) return;
        cancelled = false;
        showProgressDialog();

        Thread downloadThread = new Thread(() -> runDownload(info), "update-download");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private void showProgressDialog() {
        View view = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.dialog_update_progress, null, false);
        progressBar = view.findViewById(R.id.progressUpdate);
        progressText = view.findViewById(R.id.txtUpdateProgress);

        progressDialog = new AlertDialog.Builder(fragment.requireContext())
                .setView(view)
                .setCancelable(false)
                .create();
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            progressDialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_comic_surface);
        }
        progressDialog.show();
    }

    private void updateProgress(long downloaded, long total) {
        main.post(() -> {
            if (progressBar == null || progressText == null || !fragment.isAdded()) return;
            int pct = total > 0 ? (int) Math.min(100, (downloaded * 100) / total) : 0;
            progressBar.setProgress(pct);
            progressText.setText(String.format(Locale.getDefault(),
                    fragment.getString(R.string.update_progress_fmt),
                    formatBytes(downloaded), formatBytes(total), pct));
        });
    }

    private static String formatBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.US, "%.2f MB", mb);
    }

    private void runDownload(UpdateInfo info) {
        File outDir = new File(appContext().getCacheDir(), "updates");
        if (!outDir.exists()) //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();

        // Clear any previous partial/stale downloads before starting a fresh one.
        File[] stale = outDir.listFiles();
        if (stale != null) {
            for (File f : stale) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        File outFile = new File(outDir, "update.apk");

        Request request = new Request.Builder().url(info.apk_url).build();
        try (Response response = downloadClient.newCall(request).execute()) {
            if (!response.isSuccessful()) { onFailure(); return; }
            ResponseBody body = response.body();
            if (body == null) { onFailure(); return; }

            long total = info.apk_size_bytes > 0 ? info.apk_size_bytes : body.contentLength();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long downloaded = 0;
            long lastReported = 0;

            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelled) return;
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    downloaded += read;
                    // Throttle UI updates (~128KB granularity) instead of posting to the main
                    // thread on every single read on a fast connection.
                    if (downloaded - lastReported > 128 * 1024 || downloaded == total) {
                        updateProgress(downloaded, total);
                        lastReported = downloaded;
                    }
                }
            }
            if (cancelled) return;

            String actualSha = toHex(digest.digest());
            if (info.apk_sha256 != null && !info.apk_sha256.trim().equalsIgnoreCase(actualSha)) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
                onVerifyFailure();
                return;
            }

            onDownloadComplete(outFile);
        } catch (Exception e) {
            onFailure();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    private void onDownloadComplete(File apkFile) {
        main.post(() -> {
            dismissProgress();
            if (!fragment.isAdded()) return;
            Uri apkUri = FileProvider.getUriForFile(
                    fragment.requireContext(),
                    fragment.requireContext().getPackageName() + ".fileprovider",
                    apkFile);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                fragment.startActivity(install);
            } catch (Exception ignored) {
                // Essentially unreachable on a real device (every Android ships a package
                // installer), but never crash the Home screen over it either way.
            }
        });
    }

    private void onFailure() {
        main.post(() -> {
            dismissProgress();
            if (!fragment.isAdded()) return;
            offerRetry(R.string.update_download_failed);
        });
    }

    private void onVerifyFailure() {
        main.post(() -> {
            dismissProgress();
            if (!fragment.isAdded()) return;
            offerRetry(R.string.update_verify_failed);
        });
    }

    private void offerRetry(int messageRes) {
        if (!fragment.isAdded()) return;
        new AlertDialog.Builder(fragment.requireContext())
                .setMessage(messageRes)
                .setPositiveButton(R.string.update_retry, (d, w) -> {
                    if (pendingInfo != null) start(pendingInfo);
                })
                .setNegativeButton(R.string.room_cancel, null)
                .show();
    }

    private void dismissProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try { progressDialog.dismiss(); } catch (Exception ignored) { }
        }
        progressDialog = null;
        progressBar = null;
        progressText = null;
    }

    /** Call from the host Fragment's onDestroy() so a download thread doesn't outlive it. */
    void cancel() {
        cancelled = true;
        dismissProgress();
    }
}
