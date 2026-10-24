package com.stream.master;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 3000;
    private boolean tokenFetched = false;
    private boolean delayFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        if (!TvUtils.isTv(this)) setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onCreate(savedInstanceState);
        if (SecurityGuard.shouldBlock(this)) {
            finishAndRemoveTask();
            return;
        }
        setContentView(R.layout.activity_splash);

        LottieAnimationView lottieView = findViewById(R.id.splashLottie);
        if (lottieView != null) {
            lottieView.playAnimation();
        }

        fetchTokenFromServer();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            delayFinished = true;
            proceedToMain();
        }, SPLASH_DELAY);
    }

    private static final int TOKEN_TIMEOUT_MS = 8000;
    private boolean started = false;

    /** Waits for the real token result (bounded), instead of pretending it already arrived. */
    private void fetchTokenFromServer() {
        SecureNetworkClient.ensureTokenAvailable(this, success -> runOnUiThread(this::useStoredToken));
        new Handler(Looper.getMainLooper()).postDelayed(this::useStoredToken, TOKEN_TIMEOUT_MS);
    }

    private void useStoredToken() {
        tokenFetched = true;
        proceedToMain();
    }

    private void proceedToMain() {
        if (started || isFinishing() || isDestroyed()) return;
        if (tokenFetched && delayFinished) {
            started = true;
            Intent next = new Intent(this, MainActivity.class);
            android.net.Uri data = getIntent() == null ? null : getIntent().getData();
            if (data != null && "streammaster".equals(data.getScheme()) && "room".equals(data.getHost())) {
                String code = RoomClient.parseCode(data.getLastPathSegment());
                if (code != null) next.putExtra("ROOM_CODE", code);
            }
            startActivity(next);
            overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
            finish();
        }
    }
}
