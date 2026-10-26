package com.stream.master;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.PorterDuff;
import android.util.Rational;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.app.PictureInPictureParams;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.TimeUnit;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import com.eclipsesource.v8.V8;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@UnstableApi
public class PlayerActivity extends BaseActivity {

    private static final String PREF_NAME = "StreamMasterResumePrefs";
    private static final String PREF_SPEED = "StreamMasterSpeedPref";
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36";

    private static final int SEEK_STEP_MS = 10000;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 1000;
    private static final int CONTROLLER_HIDE_DELAY_MS = 3500;
    private static final long MIN_SAVE_POSITION_MS = 5000;
    private static final long END_OF_MEDIA_THRESHOLD_MS = 15000;
    private static final long SUBTITLE_OFFSET_STEP_MS = 500;
    private static final long SUBTITLE_OFFSET_LARGE_STEP_MS = 5000;
    private static final long MAX_SUBTITLE_OFFSET_MS = 60000;
    private static final String SUBTITLE_OFFSET_PREF_PREFIX = "subtitle_offset_";
    private static final String SUBTITLE_RESULTS_CACHE_PREFIX = "subtitle_results_";
    private static final String SUBTITLE_RESULTS_TIME_PREFIX = "subtitle_results_time_";
    private static final long SUBTITLE_RESULTS_CACHE_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final int SUBTITLE_CACHE_VERSION = 2;
    private static final int MAX_AUTO_VIDEO_WIDTH = Integer.MAX_VALUE;
    private static final int MAX_AUTO_VIDEO_HEIGHT = Integer.MAX_VALUE;


    private PlayerView playerView;
    private View loadingOverlay, customControllerOverlay, errorOverlay, endOverlay;
    private View topGradient, bottomGradient;
    private FrameLayout btnBack;
    private TextView tvMovieTitle, tvMovieSubtitle, tvCurrentPosition, tvTotalDuration;
    private TextView btnQuality, chipServer, chipEpisodes;
    private TextView tvErrorMessage, tvGestureFeedbackText;
    private ImageView ivGestureFeedbackIcon;
    private ImageButton btnRewind, btnPlayPause, btnForward, btnSettings, btnRetry, btnReplay;
    private ImageButton btnLock, btnUnlock, btnSubtitles;
    private View lockedOverlay;
    private SeekBar playerSeekBar;
    private LinearLayout gestureFeedbackOverlay;
    private View gestureVolumeHud, gestureBrightnessHud;
    private VerticalLevelView gestureVolumeProgress, gestureBrightnessProgress;
    private TextView gestureVolumeText, gestureBrightnessText;

    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;

    private enum UIState { IDLE, FETCHING, PREPARING, BUFFERING, PLAYING, PAUSED, ERROR, ENDED }
    private UIState currentState = UIState.IDLE;

    private List<StreamResponse.Subtitle> subtitles = new ArrayList<>();
    private final List<StreamResponse.Subtitle> subdlSubtitles = new ArrayList<>();
    private ExecutorService subtitleExecutor;
    private volatile long subtitleRequestGeneration = 0L;
    private volatile boolean subtitleLookupInFlight = false;
    private boolean pendingSubtitleDialogReopen = false;
    private List<StreamResponse.StreamItem> availableStreams = new ArrayList<>();
    private String masterStreamUrl = null;
    private String currentStreamUrl = null;
    private String currentVidKey = null;
    private Map<String, String> streamHeaders = new HashMap<>();
    private long serverMovieDurationMs = 0;
    private boolean hasRestoredPosition = false;
    private float currentSpeed = 1.0f;
    private int currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_SOURCE_RECOVERIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 1200L;
    private int sourceRecoveryCount = 0;
    private long pendingRecoveryPositionMs = -1L;
    private int serverFetchRetryCount = 0;
    private Runnable pendingRetryRunnable;
    private boolean userPaused = false;
    private boolean wasPlayingBeforeStop = false;
    private boolean networkAvailable = true;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private long playerGeneration = 0L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isControllerVisible = true;
    private boolean isControlsLocked = false;
    private boolean isUserSeeking = false;
    private int pendingSeekTargetMs = -1;
    private AlertDialog activeDialog;
    private RoomController roomController;   // watch-party glue (see RoomController)

    private AudioManager audioManager;
    private Vibrator vibrator;
    private float gestureStartX, gestureStartY;
    private long gestureStartPosition = -1;
    private boolean isGestureSeeking, isGestureVolume, isGestureBrightness;
    private long gesturePendingSeekMs = -1;
    private int maxVolume = 15;
    private int startVolume = 0;
    private float startBrightness = 0.5f;

    private Call<StreamResponse> activeNetworkCall;
    private ExecutorService providerExecutor;
    private ExecutorService streamFetchExecutor;
    private Future<?> activeStreamFetchTask;
    private final java.util.concurrent.atomic.AtomicLong streamFetchGeneration = new java.util.concurrent.atomic.AtomicLong(0L);
    private AtomicBoolean providerSearchActive = new AtomicBoolean(false);
    private final Map<String, String> providerCodeCache = new ConcurrentHashMap<>();
    private volatile JSONObject providerSignatureManifest = null;
    private volatile boolean isActivityDestroyed = false;

    // Watch Party mic permission (voice chat). Registered as a field so it is ready before onCreate.
    private java.util.function.Consumer<Boolean> pendingMicPermissionCallback;
    private final androidx.activity.result.ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
                java.util.function.Consumer<Boolean> cb = pendingMicPermissionCallback;
                pendingMicPermissionCallback = null;
                if (cb != null) cb.accept(granted);
            });

    // Subtitle state lives outside ExoPlayer so it survives quality changes,
    // retries and network recovery.
    private StreamResponse.Subtitle activeSubtitle;
    private File activeSubtitleFile;
    private boolean subtitleEnabled = false;
    private long subtitleOffsetMs = 0L;
    private long subtitleRequestId = 0L;

    // External subtitle renderer: subtitles are drawn by PlayerView's SubtitleView
    // without replacing/re-preparing the video MediaItem. This keeps playback running
    // while a subtitle is downloaded or selected.
    private final List<LocalSubtitleCue> localSubtitleCues = new ArrayList<>();
    private final Runnable subtitleRenderRunnable = new Runnable() {
        @Override public void run() {
            renderLocalSubtitle();
            if (!isActivityDestroyed) mainHandler.postDelayed(this, 100L);
        }
    };

    private static final class LocalSubtitleCue {
        final long startMs;
        final long endMs;
        final String text;
        LocalSubtitleCue(long startMs, long endMs, String text) {
            this.startMs = startMs; this.endMs = endMs; this.text = text;
        }
    }

    private static class ProviderResult {
        String url;
        Map<String, String> headers;
        List<StreamResponse.Subtitle> subtitles;
        String providerName;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupEdgeToEdgeWindow();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        setupNetworkMonitoring();

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentSpeed = prefs.getFloat(PREF_SPEED, 1.0f);

        providerExecutor = Executors.newFixedThreadPool(3);
        streamFetchExecutor = Executors.newCachedThreadPool();
        subtitleExecutor = Executors.newFixedThreadPool(2);

        initViews();
        setupGradients();

        currentVidKey = getIntent().getStringExtra("VID");
        String title = getIntent().getStringExtra("TITLE");
        tvMovieTitle.setText((title != null && !title.trim().isEmpty()) ? title : getString(R.string.live_stream));

        roomController = new RoomController(this);
        roomController.onCreate(getIntent());

        if (currentVidKey != null && !currentVidKey.isEmpty()) {
            // Episodes are not movies: they must not show up in the movie "continue watching" list.
            if (!isSeriesPlayback()) saveWatchHistoryMetadata();
            fetchStream(currentVidKey);
        } else {
            updateUIState(UIState.ERROR, getString(R.string.invalid_vid_code));
        }
    }

    private void setupEdgeToEdgeWindow() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        hideSystemBars();
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        customControllerOverlay = findViewById(R.id.customControllerOverlay);
        errorOverlay = findViewById(R.id.errorOverlay);
        endOverlay = findViewById(R.id.endOverlay);

        topGradient = findViewById(R.id.topGradient);
        bottomGradient = findViewById(R.id.bottomGradient);
        btnBack = findViewById(R.id.btnBack);
        tvMovieTitle = findViewById(R.id.tvMovieTitle);
        tvMovieSubtitle = findViewById(R.id.tvMovieSubtitle);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        btnRewind = findViewById(R.id.btnRewind);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnForward = findViewById(R.id.btnForward);
        btnSettings = findViewById(R.id.btnSettings);
        btnRetry = findViewById(R.id.btnRetry);
        btnReplay = findViewById(R.id.btnReplay);
        btnQuality = findViewById(R.id.btnQuality);
        btnSubtitles = findViewById(R.id.btnSubtitles);
        chipServer = findViewById(R.id.chipServer);
        chipEpisodes = findViewById(R.id.chipEpisodes);
        btnLock = findViewById(R.id.btnLock);
        btnUnlock = findViewById(R.id.btnUnlock);
        lockedOverlay = findViewById(R.id.lockedOverlay);
        playerSeekBar = findViewById(R.id.playerSeekBar);
        tvCurrentPosition = findViewById(R.id.tvCurrentPosition);
        tvTotalDuration = findViewById(R.id.tvTotalDuration);
        gestureFeedbackOverlay = findViewById(R.id.gestureFeedbackOverlay);
        tvGestureFeedbackText = findViewById(R.id.tvGestureFeedbackText);
        ivGestureFeedbackIcon = findViewById(R.id.ivGestureFeedbackIcon);
        gestureVolumeHud = findViewById(R.id.gestureVolumeHud);
        gestureBrightnessHud = findViewById(R.id.gestureBrightnessHud);
        gestureVolumeProgress = findViewById(R.id.gestureVolumeProgress);
        gestureBrightnessProgress = findViewById(R.id.gestureBrightnessProgress);
        gestureVolumeText = findViewById(R.id.gestureVolumeText);
        gestureBrightnessText = findViewById(R.id.gestureBrightnessText);

        playerView.setKeepContentOnPlayerReset(true);
        playerView.setResizeMode(currentResizeMode);
        chipEpisodes.setVisibility(View.GONE);

        setupSubtitleStyle();
        mainHandler.removeCallbacks(subtitleRenderRunnable);
        mainHandler.post(subtitleRenderRunnable);
        setupGestures();
        setupControlListeners();
        setupSeekBarListener();

        ViewCompat.setOnApplyWindowInsetsListener(customControllerOverlay, (v, insets) -> {
            int statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int navBarLeft = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).left;
            int navBarRight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).right;
            View topBar = findViewById(R.id.topBarLayout);
            if (topBar != null) topBar.setPadding(navBarLeft + 16, statusBar + 12, navBarRight + 16, 0);
            View chips = findViewById(R.id.chipsContainer);
            if (chips != null) chips.setPadding(navBarLeft, 0, navBarRight, navBarBottom + 12);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupSubtitleStyle() {
        playerView.post(() -> {
            if (isActivityDestroyed) return;
            SubtitleView subtitleView = playerView.getSubtitleView();
            if (subtitleView != null) {
                int paddingBottom = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
                subtitleView.setPadding(0, 0, 0, paddingBottom);
                float textSizeSp = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 24f : 18f;
                subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                subtitleView.setStyle(new androidx.media3.ui.CaptionStyleCompat(
                        Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, null));
            }
        });
    }

    private void setupGradients() {
        if (topGradient != null) {
            topGradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.parseColor("#E6000000"), Color.parseColor("#80000000"), Color.TRANSPARENT}));
        }
        if (bottomGradient != null) {
            bottomGradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{Color.parseColor("#F2000000"), Color.parseColor("#90000000"), Color.TRANSPARENT}));
        }
    }

    private void exitPlayer() {
        releasePlayer();
        finish();
    }

    private void setupControlListeners() {
        btnBack.setOnClickListener(v -> exitPlayer());
        btnPlayPause.setOnClickListener(v -> {
            scheduleControllerHide();
            if (player == null) return;
            if (roomBlocks()) return;   // watch party: only the host controls playback

            // Use the user's playWhenReady intent rather than isPlaying().
            // During buffering ExoPlayer reports isPlaying=false even though
            // playback is still intended to continue. This keeps the control
            // state stable during weak/no network conditions.
            if (player.getPlayWhenReady() && !userPaused) {
                userPaused = true;
                player.setPlayWhenReady(false);
                btnPlayPause.setImageResource(R.drawable.ic_play);
                currentState = UIState.PAUSED;
            } else {
                userPaused = false;
                retryCount = 0;
                player.setPlayWhenReady(true);
                btnPlayPause.setImageResource(R.drawable.ic_pause);
            }
            hapticTick();
        });
        btnRewind.setOnClickListener(v -> seekRelative(-SEEK_STEP_MS));
        btnForward.setOnClickListener(v -> seekRelative(SEEK_STEP_MS));

        btnQuality.setOnClickListener(v -> { scheduleControllerHide(); showQualitySelector(); });
        btnSubtitles.setOnClickListener(v -> { scheduleControllerHide(); showSubtitleSelector(); });
        btnSubtitles.setOnLongClickListener(v -> {
            if (activeSubtitle != null) {
                showSubtitleSyncDialog();
            } else {
                showSubtitleSelector();
            }
            return true;
        });
        btnSettings.setOnClickListener(v -> { scheduleControllerHide(); showSettingsMenu(); });
        btnLock.setOnClickListener(v -> lockControls());
        if (btnUnlock != null) btnUnlock.setOnClickListener(v -> unlockControls());

        if (btnRetry != null) btnRetry.setOnClickListener(v -> retryPlayback());
        if (btnReplay != null) btnReplay.setOnClickListener(v -> {
            if (player != null && !roomBlocks()) {
                userPaused = false;
                retryCount = 0;
                player.seekTo(0);
                player.setPlayWhenReady(true);
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                updateUIState(UIState.PREPARING, null);
            }
        });
    }

    private void updateUIState(UIState newState, String message) {
        if (isActivityDestroyed) return;
        currentState = newState;

        loadingOverlay.setVisibility(View.GONE);
        errorOverlay.setVisibility(View.GONE);
        endOverlay.setVisibility(View.GONE);

        switch (newState) {
            case FETCHING:
            case PREPARING:
                // Initial/recovery load: spinner only. Hide playback controls
                // while a source is being resolved so stale controls cannot be
                // tapped during a reconnect.
                loadingOverlay.setVisibility(View.VISIBLE);
                if (customControllerOverlay != null) {
                    customControllerOverlay.animate().cancel();
                    customControllerOverlay.setVisibility(View.GONE);
                    isControllerVisible = false;
                }
                if (player != null) {
                    btnPlayPause.setImageResource(R.drawable.ic_pause);
                }
                break;

            case BUFFERING:
                // A network stall is NOT a user pause. Keep the pause icon visible
                // so the UI communicates "still playing / waiting for data".
                // The native Media3 buffering spinner is disabled; the custom
                // beige loading indicator is used instead.
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                break;

            case PLAYING:
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                if (!isControllerVisible && !isInPictureInPictureMode() && !isControlsLocked) {
                    showController();
                } else {
                    scheduleControllerHide();
                }
                break;

            case PAUSED:
                btnPlayPause.setImageResource(R.drawable.ic_play);
                cancelControllerHide();
                if (!isControllerVisible && !isInPictureInPictureMode()) showController();
                break;

            case ERROR:
                tvErrorMessage.setText(message != null ? message : getString(R.string.playback_error));
                btnRetry.setVisibility(View.VISIBLE);
                errorOverlay.setVisibility(View.VISIBLE);
                cancelControllerHide();
                if (customControllerOverlay != null) {
                    customControllerOverlay.animate().cancel();
                    customControllerOverlay.setVisibility(View.GONE);
                    isControllerVisible = false;
                }
                break;

            case ENDED:
                endOverlay.setVisibility(View.VISIBLE);
                btnPlayPause.setImageResource(R.drawable.ic_play);
                cancelControllerHide();
                showController();
                clearSavedPosition();
                break;

            case IDLE:
            default:
                break;
        }
    }

    /** Series TMDB id of the episode being played ("" for movies). */
    private String seriesTmdbId() {
        String id = getIntent().getStringExtra("TMDB_ID");
        return id == null ? "" : id.trim();
    }

    /** True when the player was opened for a series episode (TYPE = "tv" + the series TMDB id). */
    private boolean isSeriesPlayback() {
        return "tv".equalsIgnoreCase(getIntent().getStringExtra("TYPE")) && !seriesTmdbId().isEmpty();
    }

    private void fetchStream(String vid) {
        if (isActivityDestroyed || isFinishing() || vid == null || vid.trim().isEmpty()) return;
        updateUIState(UIState.FETCHING, getString(R.string.preparing_stream));

        // The JS movie providers only know movie ids ("movie" is hard-coded there), and TMDB movie / tv
        // ids overlap - never send an episode through them. Episodes go straight to api/stream.
        if (isSeriesPlayback()) {
            serverFetchRetryCount = 0;
            fetchStreamFromServer(vid);
            return;
        }

        // Invalidate every older provider search before starting a new one.
        // This prevents a slow previous search from delaying recovery/resume.
        final long fetchGeneration = streamFetchGeneration.incrementAndGet();
        providerSearchActive.set(false);
        if (activeStreamFetchTask != null) {
            try { activeStreamFetchTask.cancel(true); } catch (Exception ignored) {}
            activeStreamFetchTask = null;
        }

        providerSearchActive.set(true);
        serverFetchRetryCount = 0;

        if (streamFetchExecutor == null || streamFetchExecutor.isShutdown()) {
            streamFetchExecutor = Executors.newCachedThreadPool();
        }

        activeStreamFetchTask = streamFetchExecutor.submit(() -> {
            try {
                String jsonStr = getCachedOrDownloadCode("provider_list_json", Constants.GITHUB_PROVIDER_JSON);
                if (jsonStr == null || jsonStr.isEmpty()) throw new Exception("Provider list unavailable");
                JSONObject providerObj = new JSONObject(jsonStr);
                JSONArray providers = providerObj.getJSONArray("providers");
                List<String> providerNames = new ArrayList<>();
                for (int i = 0; i < providers.length(); i++) providerNames.add(providers.getString(i));
                Collections.sort(providerNames, (a, b) -> {
                    if (a.toLowerCase().contains("showbox")) return -1;
                    if (b.toLowerCase().contains("showbox")) return 1;
                    return 0;
                });

                ExecutorCompletionService<ProviderResult> completionService = new ExecutorCompletionService<>(providerExecutor);
                int submittedTasks = 0;
                for (String providerName : providerNames) {
                    if (!providerSearchActive.get() || fetchGeneration != streamFetchGeneration.get() || isActivityDestroyed) break;
                    String providerCode = getCachedOrDownloadCode(providerName, providerName);
                    if (providerCode != null && !providerCode.isEmpty()) {
                        completionService.submit(new ProviderTask(providerName, providerCode, vid, fetchGeneration));
                        submittedTasks++;
                    }
                }

                ProviderResult validResult = null;
                for (int i = 0; i < submittedTasks; i++) {
                    if (!providerSearchActive.get() || fetchGeneration != streamFetchGeneration.get()) break;
                    Future<ProviderResult> future = completionService.take();
                    ProviderResult result = future.get();
                    if (result != null && result.url != null && !result.url.isEmpty()
                            && fetchGeneration == streamFetchGeneration.get() && providerSearchActive.get()) {
                        validResult = result;
                        providerSearchActive.set(false);
                        break;
                    }
                }

                if (validResult != null && !isActivityDestroyed && fetchGeneration == streamFetchGeneration.get()) {
                    final ProviderResult finalRes = validResult;
                    mainHandler.post(() -> {
                        if (isActivityDestroyed || isFinishing() || fetchGeneration != streamFetchGeneration.get()) return;
                        masterStreamUrl = finalRes.url;
                        serverFetchRetryCount = 0;
                        streamHeaders = finalRes.headers != null ? finalRes.headers : new HashMap<>();
                        subtitles = finalRes.subtitles != null ? finalRes.subtitles : new ArrayList<>();
                        availableStreams = new ArrayList<>();
                        chipServer.setText(finalRes.providerName.replace(".js", "").toUpperCase(Locale.ROOT));
                        long resumePosition = consumePendingRecoveryPosition();
                        initializePlayer(masterStreamUrl, "Auto", resumePosition);
                        scheduleSubtitleDiscovery(vid);
                    });
                } else if (fetchGeneration == streamFetchGeneration.get() && !isActivityDestroyed) {
                    mainHandler.post(() -> {
                        if (fetchGeneration != streamFetchGeneration.get() || isActivityDestroyed) return;
                        updateUIState(UIState.FETCHING, getString(R.string.connecting_to_server));
                        fetchStreamFromServer(vid);
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (fetchGeneration == streamFetchGeneration.get() && !isActivityDestroyed) {
                    mainHandler.post(() -> {
                        if (fetchGeneration != streamFetchGeneration.get() || isActivityDestroyed) return;
                        updateUIState(UIState.FETCHING, getString(R.string.connecting_to_server));
                        fetchStreamFromServer(vid);
                    });
                }
            }
        });
    }

    private void scheduleSubtitleDiscovery(String vid) {
        // Playback first. Subtitle discovery starts shortly after the media
        // request so token/bootstrap work can never delay the first frame.
        mainHandler.postDelayed(() -> {
            if (!isActivityDestroyed && !isFinishing() && player != null) {
                // The supplied production server returns subtitles directly from /api/stream.
                // Only fall back to the optional subtitle-search endpoint when none were
                // delivered with the stream response.
                if (getAllSubtitleOptions().isEmpty()) {
                    requestSubtitlesFromServer(vid);
                }
            }
        }, 350L);
    }

    private class ProviderTask implements Callable<ProviderResult> {
        private final String providerName;
        private final String code;
        private final String vid;
        private final long fetchGeneration;

        public ProviderTask(String providerName, String code, String vid, long fetchGeneration) {
            this.providerName = providerName;
            this.code = code;
            this.vid = vid;
            this.fetchGeneration = fetchGeneration;
        }

        @Override
        public ProviderResult call() {
            if (!providerSearchActive.get() || fetchGeneration != streamFetchGeneration.get()) return null;
            V8 v8 = null;
            try {
                v8 = V8.createV8Runtime();
                setupV8Environment(v8);

                String movieInfoJson = "{\"tmdb_id\":\"" + vid + "\",\"type\":\"movie\"}";
                v8.executeScript("var movieInfo = " + movieInfoJson + ";");
                v8.executeScript("var __result = null, __error = null;");
                v8.executeScript(code);

                String entryPoint = "";
                if (providerName.toLowerCase(Locale.ROOT).contains("showbox")) {
                    entryPoint = "getStreamsFromTmdbId('movie', movieInfo.tmdb_id, null, null)";
                } else if (providerName.toLowerCase(Locale.ROOT).contains("vixsrc")) {
                    entryPoint = "getVixsrcStreams(movieInfo.tmdb_id, 'movie')";
                }

                if (!entryPoint.isEmpty()) {
                    String execJs = "if(typeof " + entryPoint.split("\\(")[0] + " === 'function') { " +
                            entryPoint + ".then(res => { " +
                            "  if(res && res.streams && res.streams.length > 0) __result = JSON.stringify({ url: res.streams[0].url, headers: res.streams[0].headers || {}, subtitles: res.subtitles || [] }); " +
                            "  else if(res && res.length > 0) __result = JSON.stringify(res[0]); " +
                            "  else __error = 'No stream'; " +
                            "}).catch(e => { __error = e.toString(); }); } else { __error = 'Method not found'; }";
                    v8.executeScript(execJs);
                }

                long start = System.currentTimeMillis();
                while (providerSearchActive.get() && fetchGeneration == streamFetchGeneration.get()) {
                    boolean hasResult = v8.executeBooleanScript("__result !== null || __error !== null");
                    if (hasResult) break;
                    if (System.currentTimeMillis() - start > 10000) break;
                    Thread.sleep(50);
                }

                if (!providerSearchActive.get() || fetchGeneration != streamFetchGeneration.get()) return null;

                Object resultObj = v8.get("__result");
                if (resultObj != null) {
                    JSONObject resObj = new JSONObject(resultObj.toString());
                    ProviderResult pr = new ProviderResult();
                    pr.url = resObj.optString("url");
                    pr.providerName = providerName;
                    
                    pr.headers = new HashMap<>();
                    if (resObj.has("headers")) {
                        JSONObject hObj = resObj.getJSONObject("headers");
                        Iterator<String> keys = hObj.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            pr.headers.put(k, hObj.getString(k));
                        }
                    }

                    pr.subtitles = new ArrayList<>();
                    if (resObj.has("subtitles")) {
                        JSONArray sArr = resObj.getJSONArray("subtitles");
                        for (int s = 0; s < sArr.length(); s++) {
                            JSONObject subObj = sArr.getJSONObject(s);
                            StreamResponse.Subtitle sub = new StreamResponse.Subtitle();
                            sub.setUrl(subObj.optString("url"));
                            sub.setLabel(subObj.optString("label", getString(R.string.arabic)));
                            sub.setLanguage(subObj.optString("language", "ar"));
                            pr.subtitles.add(sub);
                        }
                    }
                    return pr.url != null && !pr.url.isEmpty() ? pr : null;
                }
            } catch (Exception e) {
                // no-op
            } finally {
                if (v8 != null) {
                    try { v8.release(); } catch (Exception ignored) {}
                }
            }
            return null;
        }
    }

    private static final int MAX_PROVIDER_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PROVIDER_REDIRECTS = 3;

    /** Providers are public code, but their network bridge is still constrained to HTTPS/public hosts. */
    private static HttpURLConnection openProviderConnection(String urlString, String method, int redirects) throws Exception {
        if (redirects > MAX_PROVIDER_REDIRECTS) throw new SecurityException("Too many provider redirects");
        URI uri = new URI(urlString);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null || host.isEmpty()) throw new SecurityException("Provider URL must be HTTPS");
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new SecurityException("Provider URL resolves to a private/local address");
            }
        }
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    private static String readLimited(InputStream input, int maxBytes) throws Exception {
        byte[] buffer = new byte[8192];
        int total = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = input.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) throw new SecurityException("Provider response too large");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }

    private void setupV8Environment(V8 v8) {
        v8.registerJavaMethod((receiver, parameters) -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = parameters.getString(0);
                String method = parameters.getString(1).toUpperCase(Locale.ROOT);
                String headersJson = parameters.length() > 2 ? parameters.getString(2) : "{}";
                String body = parameters.length() > 3 ? parameters.getString(3) : null;
                conn = openProviderConnection(urlStr, method, 0);
                JSONObject hObj = new JSONObject(headersJson);
                Iterator<String> keys = hObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (!k.equalsIgnoreCase("Host") && !k.equalsIgnoreCase("Content-Length")) conn.setRequestProperty(k, hObj.getString(k));
                }
                if (!hObj.has("User-Agent")) conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
                if (body != null && !body.isEmpty() && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                    if (body.length() > 512 * 1024) throw new SecurityException("Provider request too large");
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
                }
                int code = conn.getResponseCode();
                if (code / 100 == 3) {
                    String location = conn.getHeaderField("Location");
                    if (location != null) {
                        HttpURLConnection redirected = openProviderConnection(new URL(conn.getURL(), location).toString(), method, 1);
                        conn.disconnect();
                        conn = redirected;
                        int redirectedCode = conn.getResponseCode();
                        code = redirectedCode;
                    }
                }
                InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String data = is == null ? "" : readLimited(is, MAX_PROVIDER_RESPONSE_BYTES);
                JSONObject res = new JSONObject();
                res.put("status", code);
                res.put("data", data);
                return res.toString();
            } catch (Exception e) {
                return "{\"status\":599,\"data\":\"\"}";
            } finally { if (conn != null) conn.disconnect(); }
        }, "__nativeHttp");

        v8.registerJavaMethod((receiver, parameters) -> {
            try {
                String algo = parameters.getString(0).toUpperCase(Locale.ROOT);
                if(algo.equals("MD5") || algo.equals("SHA1") || algo.equals("SHA-1") || algo.equals("SHA-256")) {
                    MessageDigest digest = MessageDigest.getInstance(algo.replace("-", ""));
                    byte[] hash = digest.digest(parameters.getString(1).getBytes(StandardCharsets.UTF_8));
                    StringBuilder hexString = new StringBuilder();
                    for (byte b : hash) {
                        String hex = Integer.toHexString(0xff & b);
                        if (hex.length() == 1) hexString.append('0');
                        hexString.append(hex);
                    }
                    return hexString.toString();
                }
            } catch (Exception ignored) {}
            return "";
        }, "__nativeCryptoHash");

        v8.registerJavaMethod((receiver, parameters) -> {
            try {
                String html = parameters.getString(0);
                String selector = parameters.getString(1);
                String action = parameters.getString(2);
                String attrName = parameters.length() > 3 ? parameters.getString(3) : "";
                
                Document doc = Jsoup.parse(html);
                Elements els = doc.select(selector);
                if (els.isEmpty()) return "";
                if ("text".equals(action)) return els.first().text();
                if ("attr".equals(action)) return els.first().attr(attrName);
                if ("html".equals(action)) return els.first().html();
            } catch (Exception e) { /* no-op */ }
            return "";
        }, "__nativeCheerio");

        String polyfills = 
            "var module = { exports: {} }; var exports = module.exports; var global = this;\n" +
            "var require = function(name) {\n" +
            "  if(name === 'axios') return {\n" +
            "    get: function(url, opts){ var r=JSON.parse(__nativeHttp(url, 'GET', JSON.stringify(opts&&opts.headers?opts.headers:{}), null)); if(r.status>=200&&r.status<300){ try{r.data=JSON.parse(r.data);}catch(e){} return Promise.resolve(r);} return Promise.reject(r); },\n" +
            "    post: function(url, body, opts){ var b=typeof body==='object'?JSON.stringify(body):body; var r=JSON.parse(__nativeHttp(url, 'POST', JSON.stringify(opts&&opts.headers?opts.headers:{}), b)); if(r.status>=200&&r.status<300){ try{r.data=JSON.parse(r.data);}catch(e){} return Promise.resolve(r);} return Promise.reject(r); }\n" +
            "  };\n" +
            "  if(name === 'crypto') return {\n" +
            "    createHash: function(algo) { return { data: '', update: function(d){ this.data+=d; return this; }, digest: function(){ return __nativeCryptoHash(algo, this.data); } }; }\n" +
            "  };\n" +
            "  if(name === 'cheerio') return {\n" +
            "    load: function(html) { return function(selector) { return { text: function(){ return __nativeCheerio(html, selector, 'text'); }, attr: function(a){ return __nativeCheerio(html, selector, 'attr', a); }, html: function(){ return __nativeCheerio(html, selector, 'html'); } }; } }\n" +
            "  };\n" +
            "  if(name === 'node-cache') return class { constructor(){this.c={};} get(k){return this.c[k];} set(k,v){this.c[k]=v;} };\n" +
            "  return {};\n" +
            "};";
        v8.executeScript(polyfills);
    }

    private String getCachedOrDownloadCode(String key, String urlStr) {
        if (providerCodeCache.containsKey(key)) return providerCodeCache.get(key);
        HttpURLConnection conn = null;
        try {
            // Provider source is executable code. Never execute it unless its
            // detached ECDSA signature and SHA-256 digest both verify.
            if (!urlStr.startsWith(Constants.GITHUB_PROVIDERS_DIR)) return null;
            JSONObject manifest = providerSignatureManifest;
            if (manifest == null) {
                manifest = ProviderSignatureVerifier.downloadManifest();
                providerSignatureManifest = manifest;
            }

            conn = openProviderConnection(urlStr, "GET", 0);
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
            if (conn.getResponseCode() != 200) return null;

            byte[] sourceBytes;
            try (InputStream in = conn.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0, n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_PROVIDER_RESPONSE_BYTES) throw new SecurityException("Provider response too large");
                    out.write(buffer, 0, n);
                }
                sourceBytes = out.toByteArray();
            }

            ProviderSignatureVerifier.Entry entry =
                    ProviderSignatureVerifier.getEntry(manifest, urlStr);
            if (!ProviderSignatureVerifier.verify(urlStr, sourceBytes, entry)) {
                return null;
            }

            String code = new String(sourceBytes, StandardCharsets.UTF_8);
            providerCodeCache.put(key, code);
            return code;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void fetchStreamFromServer(String vid) {
        // Provider resolution is finished/failed; stop its polling loops so a
        // late provider result cannot race the server fallback.
        providerSearchActive.set(false);
        streamFetchGeneration.incrementAndGet();
        if (activeStreamFetchTask != null) {
            try { activeStreamFetchTask.cancel(true); } catch (Exception ignored) {}
            activeStreamFetchTask = null;
        }
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);
        ApiService apiService = new Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(ApiService.class);

        // Episode: the server resolves (series TMDB id, season, episode); movie: the movie id.
        activeNetworkCall = isSeriesPlayback()
                ? apiService.getEpisodeStream(
                        seriesTmdbId(), "tv",
                        getIntent().getIntExtra("SEASON", 0),
                        getIntent().getIntExtra("EPISODE", 0))
                : apiService.getMovieStream(vid);
        activeNetworkCall.enqueue(new Callback<StreamResponse>() {
            @Override
            public void onResponse(Call<StreamResponse> call, Response<StreamResponse> response) {
                if (isActivityDestroyed || isFinishing()) return;
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
                    retryServerStreamFetch(vid, getString(R.string.server_connection_error));
                    return;
                }
                StreamResponse envelope = response.body();
                final String ticket = envelope.getStreamSession();
                if (ticket != null && !ticket.isEmpty()) {
                    resolveServerStreamSession(ticket, vid);
                    return;
                }

                StreamResponse.MovieData data = envelope.getData();
                applyServerStreamData(data, vid);
            }

            @Override
            public void onFailure(Call<StreamResponse> call, Throwable t) {
                if (isActivityDestroyed || isFinishing()) return;
                retryServerStreamFetch(vid, getString(R.string.network_connection_failed));
            }
        });
    }

    private void retryServerStreamFetch(String vid, String finalMessage) {
        if (isActivityDestroyed || isFinishing()) return;
        if (serverFetchRetryCount < 2) {
            serverFetchRetryCount++;
            updateUIState(UIState.FETCHING, null);
            final int attempt = serverFetchRetryCount;
            mainHandler.postDelayed(() -> {
                if (isActivityDestroyed || isFinishing()) return;
                fetchStreamFromServer(vid);
            }, 1200L * attempt);
        } else {
            updateUIState(UIState.ERROR, finalMessage);
        }
    }

    private void resolveServerStreamSession(String ticket, String vid) {
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);
        ApiService apiService = new Retrofit.Builder()
                .baseUrl(Constants.BASE_URL.endsWith("/") ? Constants.BASE_URL : Constants.BASE_URL + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(ApiService.class);

        apiService.resolveStreamSession(ticket).enqueue(new Callback<StreamResponse>() {
            @Override
            public void onResponse(Call<StreamResponse> call, Response<StreamResponse> response) {
                if (isActivityDestroyed || isFinishing()) return;
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
                    retryServerStreamFetch(vid, getString(R.string.server_connection_error));
                    return;
                }
                serverFetchRetryCount = 0;
                applyServerStreamData(response.body().getData(), vid);
            }

            @Override
            public void onFailure(Call<StreamResponse> call, Throwable t) {
                if (isActivityDestroyed || isFinishing()) return;
                retryServerStreamFetch(vid, getString(R.string.network_connection_failed));
            }
        });
    }

    private void applyServerStreamData(StreamResponse.MovieData data, String vid) {
        if (data == null) {
            updateUIState(UIState.ERROR, getString(R.string.no_stream_url));
            return;
        }
        masterStreamUrl = data.getStreamUrl();
        if (masterStreamUrl == null || masterStreamUrl.isEmpty()) {
            updateUIState(UIState.ERROR, getString(R.string.no_stream_url));
            return;
        }
        serverFetchRetryCount = 0;
        streamHeaders = data.getHeaders() != null ? data.getHeaders() : new HashMap<>();
        if (data.getDuration() > 0) serverMovieDurationMs = data.getDuration() * 60 * 1000L;
        availableStreams = data.getStreams() != null ? data.getStreams() : new ArrayList<>();
        // IMPORTANT: the server's /api/stream response already carries subtitle
        // metadata from the provider. Preserve it instead of discarding it.
        subtitles = data.getSubtitles() != null
                ? new ArrayList<>(data.getSubtitles())
                : new ArrayList<>();

        // Some providers expose only the legacy single subtitle_url field.
        // Convert it into the normal subtitle list so the same player UI handles it.
        if (subtitles.isEmpty()) {
            String legacySubtitleUrl = data.getSubtitleUrl();
            if (legacySubtitleUrl != null && !legacySubtitleUrl.trim().isEmpty()) {
                StreamResponse.Subtitle legacy = new StreamResponse.Subtitle();
                legacy.setUrl(legacySubtitleUrl.trim());
                legacy.setLanguage("ar");
                legacy.setLabel(getString(R.string.arabic));
                subtitles.add(legacy);
            }
        }

        chipServer.setText(getString(R.string.server_1));
        long resumePosition = consumePendingRecoveryPosition();
        initializePlayer(masterStreamUrl, "Auto", resumePosition);
        scheduleSubtitleDiscovery(vid);
    }

    private void requestSubtitlesFromServer(String vid) {
        if (subtitleExecutor == null || isActivityDestroyed || vid == null || vid.trim().isEmpty()) {
            pendingSubtitleDialogReopen = false;
            return;
        }

        // Serve cached subtitle metadata immediately. This makes repeat opens and
        // Continue Watching effectively instant while a stale cache can still be
        // refreshed silently in the background.
        boolean cacheFresh = loadCachedSubtitleResults(vid);
        if (cacheFresh) {
            if (pendingSubtitleDialogReopen) {
                pendingSubtitleDialogReopen = false;
                showSubtitleSelector();
            }
            return;
        }

        // Avoid piling multiple identical subtitle searches onto the executor.
        if (subtitleLookupInFlight) return;
        subtitleLookupInFlight = true;
        final long generation = ++subtitleRequestGeneration;
        final String type = getIntent().getStringExtra("TYPE") != null ? getIntent().getStringExtra("TYPE") : "movie";
        final int seasonValue = getIntent().getIntExtra("SEASON", -1);
        final int episodeValue = getIntent().getIntExtra("EPISODE", -1);
        final Integer season = seasonValue > 0 ? seasonValue : null;
        final Integer episode = episodeValue > 0 ? episodeValue : null;
        // SubDL is keyed by the series TMDB id (not by the per-episode resume key).
        final String subtitleTmdbId = isSeriesPlayback() ? seriesTmdbId() : vid;

        subtitleExecutor.execute(() -> {
            try {
                ApiService api = new Retrofit.Builder()
                        .baseUrl(Constants.BASE_URL.endsWith("/") ? Constants.BASE_URL : Constants.BASE_URL + "/")
                        .client(SecureNetworkClient.getClient(PlayerActivity.this))
                        .addConverterFactory(GsonConverterFactory.create())
                        .build().create(ApiService.class);

                Response<SubtitleSearchResponse> response =
                        api.getSubtitles(subtitleTmdbId, type, season, episode).execute();

                if (!response.isSuccessful()) {
                    subtitleLookupInFlight = false;
                    mainHandler.post(this::notifySubtitleRetryFinishedEmpty);
                    return;
                }

                SubtitleSearchResponse body = response.body();
                if (body == null || !body.isSuccess()) {
                    subtitleLookupInFlight = false;
                    mainHandler.post(this::notifySubtitleRetryFinishedEmpty);
                    return;
                }

                List<StreamResponse.Subtitle> found = new ArrayList<>();
                for (SubtitleSearchResponse.SubtitleItem item : body.getSubtitles()) {
                    if (item == null || item.getUrl().isEmpty()) continue;
                    StreamResponse.Subtitle sub = new StreamResponse.Subtitle();
                    sub.setUrl(item.getUrl());
                    sub.setLanguage(item.getLanguage());
                    sub.setLabel(item.getLabel());
                    sub.setFormat(item.getFormat());
                    found.add(sub);
                }

                cacheSubtitleResults(vid, found);

                subtitleLookupInFlight = false;
                mainHandler.post(() -> {
                    if (isActivityDestroyed || generation != subtitleRequestGeneration) return;
                    synchronized (subdlSubtitles) {
                        subdlSubtitles.clear();
                        subdlSubtitles.addAll(found);
                    }
                    // Do not restart the video. The user can choose the subtitle from the menu.
                    if (pendingSubtitleDialogReopen) {
                        pendingSubtitleDialogReopen = false;
                        showSubtitleSelector();
                    }
                });
            } catch (Exception e) {
                subtitleLookupInFlight = false;
                // Subtitle discovery must never stop video playback.
                mainHandler.post(this::notifySubtitleRetryFinishedEmpty);
            }
        });
    }

    private boolean loadCachedSubtitleResults(String vid) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(SUBTITLE_RESULTS_CACHE_PREFIX + stableHash(vid), null);
            if (raw == null || raw.isEmpty()) return false;

            JSONArray array = new JSONArray(raw);
            List<StreamResponse.Subtitle> cached = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "").trim();
                if (url.isEmpty()) continue;
                StreamResponse.Subtitle sub = new StreamResponse.Subtitle();
                sub.setUrl(url);
                sub.setLanguage(item.optString("language", ""));
                sub.setLabel(item.optString("label", ""));
                sub.setFormat(item.optString("format", ""));
                cached.add(sub);
            }
            if (cached.isEmpty()) return false;

            synchronized (subdlSubtitles) {
                subdlSubtitles.clear();
                subdlSubtitles.addAll(cached);
            }

            long savedAt = prefs.getLong(SUBTITLE_RESULTS_TIME_PREFIX + stableHash(vid), 0L);
            return savedAt > 0L && (System.currentTimeMillis() - savedAt) < SUBTITLE_RESULTS_CACHE_TTL_MS;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void cacheSubtitleResults(String vid, List<StreamResponse.Subtitle> found) {
        try {
            JSONArray array = new JSONArray();
            if (found != null) {
                for (StreamResponse.Subtitle sub : found) {
                    if (sub == null || sub.getUrl() == null || sub.getUrl().trim().isEmpty()) continue;
                    JSONObject item = new JSONObject();
                    item.put("url", sub.getUrl().trim());
                    item.put("language", sub.getLanguage() != null ? sub.getLanguage() : "");
                    item.put("label", sub.getLabel() != null ? sub.getLabel() : "");
                    item.put("format", sub.getFormat() != null ? sub.getFormat() : "");
                    array.put(item);
                }
            }
            getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putString(SUBTITLE_RESULTS_CACHE_PREFIX + stableHash(vid), array.toString())
                    .putLong(SUBTITLE_RESULTS_TIME_PREFIX + stableHash(vid), System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) { }
    }



    private long getSavedPlaybackPosition() {
        if (currentVidKey == null || currentVidKey.isEmpty()) return 0L;
        return Math.max(0L, getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong("pos_" + currentVidKey, 0L));
    }



    /** Called when a user-triggered subtitle retry finishes with nothing found. */
    private void notifySubtitleRetryFinishedEmpty() {
        if (isActivityDestroyed) return;
        if (pendingSubtitleDialogReopen) {
            pendingSubtitleDialogReopen = false;
            showCustomToast(getString(R.string.no_subtitles_found));
        }
    }

    private List<StreamResponse.Subtitle> getAllSubtitleOptions() {
        LinkedHashMap<String, StreamResponse.Subtitle> merged = new LinkedHashMap<>();
        if (subtitles != null) {
            for (StreamResponse.Subtitle s : subtitles) {
                if (s != null && s.getUrl() != null && !s.getUrl().trim().isEmpty()) {
                    merged.put(s.getUrl().trim().toLowerCase(Locale.ROOT), s);
                }
            }
        }
        synchronized (subdlSubtitles) {
            for (StreamResponse.Subtitle s : subdlSubtitles) {
                if (s != null && s.getUrl() != null && !s.getUrl().trim().isEmpty()) {
                    merged.putIfAbsent(s.getUrl().trim().toLowerCase(Locale.ROOT), s);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private File prepareSubtitleForPlayback(StreamResponse.Subtitle sub) throws Exception {
        if (sub == null) throw new IllegalArgumentException("Subtitle is null");

        String source = sub.getUrl();
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty subtitle URL");
        }
        source = source.trim();

        File dir = new File(getCacheDir(), "subtitles");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create subtitle cache");
        }

        String safe = stableHash(source);
        File normalized = findExistingNormalizedSubtitle(dir, safe);

        if (normalized == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(source)
                    .get()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new java.io.IOException("Subtitle download failed");
                }

                byte[] body = response.body().bytes();
                if (body.length == 0 || body.length > 15 * 1024 * 1024) {
                    throw new java.io.IOException("Invalid subtitle size");
                }

                String lower = source.toLowerCase(Locale.ROOT);
                boolean isZip = lower.contains(".zip")
                        || (body.length >= 4 && body[0] == 'P' && body[1] == 'K');

                if (isZip) {
                    File zip = new File(dir, safe + ".zip");
                    try (FileOutputStream fos = new FileOutputStream(zip)) {
                        fos.write(body);
                    }

                    File extracted = extractBestSubtitle(zip, dir, safe);
                    zip.delete();

                    String ext = extracted.getName().toLowerCase(Locale.ROOT).endsWith(".vtt")
                            ? ".vtt"
                            : (extracted.getName().toLowerCase(Locale.ROOT).endsWith(".ass")
                            ? ".ass" : ".srt");

                    normalized = new File(
                            dir,
                            safe + "_v" + SUBTITLE_CACHE_VERSION + "_utf8" + ext
                    );

                    try {
                        normalizeSubtitleEncoding(extracted, normalized, sub.getLanguage());
                    } finally {
                        extracted.delete();
                    }
                } else {
                    String ext = resolveSubtitleExtension(sub, source, body);
                    normalized = new File(
                            dir,
                            safe + "_v" + SUBTITLE_CACHE_VERSION + "_utf8" + ext
                    );

                    File raw = new File(dir, safe + ".raw");
                    try (FileOutputStream fos = new FileOutputStream(raw)) {
                        fos.write(body);
                    }
                    try {
                        normalizeSubtitleEncoding(raw, normalized, sub.getLanguage());
                    } finally {
                        raw.delete();
                    }
                }
            }
        }

        if (normalized == null || !normalized.exists() || normalized.length() == 0) {
            throw new java.io.IOException("Normalized subtitle is empty");
        }

        long offset = getSavedSubtitleOffset(source);
        if (offset == 0L) return normalized;

        String ext = normalized.getName().substring(normalized.getName().lastIndexOf('.'));
        File adjusted = new File(
                dir,
                stableHash(source + "|" + offset) + "_v" + SUBTITLE_CACHE_VERSION
                        + "_offset" + getOffsetSafeName(offset) + ext
        );

        if (!adjusted.exists() || adjusted.length() == 0) {
            applySubtitleTimingOffset(normalized, adjusted, offset);
        }

        return adjusted;
    }

    private File findExistingNormalizedSubtitle(File dir, String safeHash) {
        if (dir == null || !dir.exists()) return null;
        File[] files = dir.listFiles((directory, name) ->
                name.startsWith(safeHash + "_v" + SUBTITLE_CACHE_VERSION + "_utf8")
                        && (name.endsWith(".srt") || name.endsWith(".vtt")
                        || name.endsWith(".ass") || name.endsWith(".ssa")));
        if (files == null || files.length == 0) return null;
        return files[0];
    }

    private String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) {
                out.append(String.format(Locale.US, "%02x", b));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String getOffsetSafeName(long offset) {
        return (offset >= 0 ? "p" : "m") + Math.abs(offset);
    }

    private long getSavedSubtitleOffset(String source) {
        if (source == null || source.isEmpty()) return 0L;
        return getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(SUBTITLE_OFFSET_PREF_PREFIX + stableHash(source), 0L);
    }

    private void saveSubtitleOffset(String source, long offsetMs) {
        if (source == null || source.isEmpty()) return;
        long safeOffset = Math.max(
                -MAX_SUBTITLE_OFFSET_MS,
                Math.min(MAX_SUBTITLE_OFFSET_MS, offsetMs)
        );
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(SUBTITLE_OFFSET_PREF_PREFIX + stableHash(source), safeOffset)
                .apply();
    }

    private String resolveSubtitleExtension(
            StreamResponse.Subtitle sub,
            String source,
            byte[] body
    ) {
        String format = sub != null ? sub.getFormat() : null;
        if (format != null) {
            String f = format.trim().toLowerCase(Locale.ROOT);
            if (f.contains("vtt")) return ".vtt";
            if (f.contains("ass")) return ".ass";
            if (f.contains("ssa")) return ".ssa";
            if (f.contains("srt") || f.contains("subrip")) return ".srt";
        }

        String lower = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (lower.contains(".vtt")) return ".vtt";
        if (lower.contains(".ass")) return ".ass";
        if (lower.contains(".ssa")) return ".ssa";

        if (body != null && body.length > 0) {
            String head = new String(body, 0, Math.min(body.length, 512), StandardCharsets.UTF_8)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (head.startsWith("webvtt")) return ".vtt";
            if (head.startsWith("[script info]") || head.contains("dialogue:")) return ".ass";
        }

        return ".srt";
    }

    private void applySubtitleTimingOffset(File input, File output, long offsetMs) throws Exception {
        String outputName = output.getName().toLowerCase(Locale.ROOT);
        String text = new String(readFileBytes(input), StandardCharsets.UTF_8);

        if (outputName.endsWith(".ass") || outputName.endsWith(".ssa")) {
            text = shiftAssSubtitleTimestamps(text, offsetMs);
        } else {
            text = shiftSrtVttTimestamps(text, offsetMs);
        }

        try (FileOutputStream fos = new FileOutputStream(output)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String shiftSrtVttTimestamps(String text, long offsetMs) {
        Pattern pattern = Pattern.compile(
                "(\\d{1,3}:\\d{2}:\\d{2}[,.]\\d{1,3})"
                        + "(\\s*-->\\s*)"
                        + "(\\d{1,3}:\\d{2}:\\d{2}[,.]\\d{1,3})"
        );

        Matcher matcher = pattern.matcher(text);
        StringBuffer out = new StringBuffer(text.length() + 64);

        while (matcher.find()) {
            String start = shiftTimestampToken(matcher.group(1), offsetMs);
            String end = shiftTimestampToken(matcher.group(3), offsetMs);
            matcher.appendReplacement(
                    out,
                    Matcher.quoteReplacement(start + matcher.group(2) + end)
            );
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String shiftAssSubtitleTimestamps(String text, long offsetMs) {
        Pattern pattern = Pattern.compile(
                "(?m)^(Dialogue:\\s*[^,]*,)"
                        + "(\\d+:\\d{2}:\\d{2}[.,]\\d{1,3})"
                        + "(,)"
                        + "(\\d+:\\d{2}:\\d{2}[.,]\\d{1,3})"
        );

        Matcher matcher = pattern.matcher(text);
        StringBuffer out = new StringBuffer(text.length() + 64);

        while (matcher.find()) {
            String start = shiftTimestampToken(matcher.group(2), offsetMs);
            String end = shiftTimestampToken(matcher.group(4), offsetMs);
            matcher.appendReplacement(
                    out,
                    Matcher.quoteReplacement(
                            matcher.group(1) + start + matcher.group(3) + end
                    )
            );
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String shiftTimestampToken(String token, long offsetMs) {
        boolean comma = token.indexOf(',') >= 0;
        char separator = comma ? ',' : '.';
        String normalized = token.replace(',', '.');
        String[] hms = normalized.split(":");
        if (hms.length != 3) return token;

        try {
            long hours = Long.parseLong(hms[0]);
            long minutes = Long.parseLong(hms[1]);
            String[] secParts = hms[2].split("\\.", -1);
            long seconds = Long.parseLong(secParts[0]);
            int millis = 0;

            if (secParts.length > 1) {
                String fraction = secParts[1];
                if (fraction.length() == 1) fraction += "00";
                else if (fraction.length() == 2) fraction += "0";
                else if (fraction.length() > 3) fraction = fraction.substring(0, 3);
                millis = Integer.parseInt(fraction);
            }

            long total = hours * 3600000L
                    + minutes * 60000L
                    + seconds * 1000L
                    + millis
                    + offsetMs;
            total = Math.max(0L, total);

            long outHours = total / 3600000L;
            long outMinutes = (total % 3600000L) / 60000L;
            long outSeconds = (total % 60000L) / 1000L;
            long outMillis = total % 1000L;

            return String.format(
                    Locale.US,
                    "%d:%02d:%02d%c%03d",
                    outHours,
                    outMinutes,
                    outSeconds,
                    separator,
                    outMillis
            );
        } catch (Exception e) {
            return token;
        }
    }

    private File extractBestSubtitle(File zipFile, File outputDir, String safeName) throws Exception {
        if (zipFile == null || !zipFile.isFile()) {
            throw new java.io.FileNotFoundException("Subtitle ZIP not found");
        }
        if (outputDir == null || !outputDir.exists() && !outputDir.mkdirs()) {
            throw new java.io.IOException("Cannot create subtitle extraction directory");
        }

        File bestFile = null;
        int bestScore = Integer.MIN_VALUE;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zipFile))) {
            ZipEntry entry;
            int index = 0;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) continue;

                    String entryName = entry.getName();
                    if (entryName == null || entryName.isEmpty()) continue;

                    String lower = entryName.toLowerCase(Locale.ROOT);
                    boolean isSrt = lower.endsWith(".srt");
                    boolean isVtt = lower.endsWith(".vtt");
                    if (!isSrt && !isVtt) continue;

                    // Avoid ZIP-slip paths: only use the final filename.
                    String fileName = new File(entryName).getName();
                    if (fileName.isEmpty()) fileName = "subtitle_" + index + (isVtt ? ".vtt" : ".srt");

                    int score = isSrt ? 100 : 90;
                    String name = fileName.toLowerCase(Locale.ROOT);
                    if (name.contains("arab") || name.contains("ara") || name.contains("عرب")) score += 20;
                    if (name.contains("english") || name.contains("eng")) score += 2;

                    File candidate = new File(outputDir, safeName + "_extracted_" + index + (isVtt ? ".vtt" : ".srt"));
                    long written = 0;
                    try (FileOutputStream fos = new FileOutputStream(candidate)) {
                        int n;
                        while ((n = zis.read(buffer)) != -1) {
                            written += n;
                            if (written > 15L * 1024L * 1024L) {
                                throw new java.io.IOException("Subtitle file too large");
                            }
                            fos.write(buffer, 0, n);
                        }
                    }

                    if (written == 0) {
                        candidate.delete();
                        continue;
                    }

                    if (score > bestScore) {
                        if (bestFile != null) bestFile.delete();
                        bestFile = candidate;
                        bestScore = score;
                    } else {
                        candidate.delete();
                    }
                } finally {
                    zis.closeEntry();
                }
                index++;
            }
        }

        if (bestFile == null || !bestFile.exists() || bestFile.length() == 0) {
            throw new java.io.IOException("No SRT/VTT subtitle found in ZIP");
        }
        return bestFile;
    }

    /**
     * Converts subtitle text to UTF-8 for Media3.
     *
     * Many Arabic SRT files are encoded as Windows-1256 rather than UTF-8.
     * Media3 expects normal text input and can otherwise display Arabic as
     * replacement characters (????). We first honor BOMs and valid UTF-8,
     * then use Windows-1256 for Arabic subtitles and Windows-1252 as the
     * conservative fallback for other legacy subtitle files.
     */
    private void normalizeSubtitleEncoding(File input, File output, String language) throws Exception {
        byte[] data = readFileBytes(input);
        String text = decodeSubtitleText(data, language);

        // Remove a Unicode BOM if a subtitle contained one after decoding.
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        try (FileOutputStream fos = new FileOutputStream(output)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] readFileBytes(File file) throws Exception {
        long length = file.length();
        if (length <= 0 || length > 15L * 1024L * 1024L) {
            throw new java.io.IOException("Invalid subtitle file size");
        }

        byte[] data = new byte[(int) length];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int n = fis.read(data, offset, data.length - offset);
                if (n == -1) break;
                offset += n;
            }
            if (offset != data.length) {
                byte[] resized = new byte[offset];
                System.arraycopy(data, 0, resized, 0, offset);
                data = resized;
            }
        }
        return data;
    }

    private String decodeSubtitleText(byte[] data, String language) throws Exception {
        int offset = 0;

        // UTF-8 BOM
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            offset = 3;
            return new String(data, offset, data.length - offset, StandardCharsets.UTF_8);
        }

        // UTF-16 little-endian BOM
        if (data.length >= 2
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xFE) {
            offset = 2;
            return new String(data, offset, data.length - offset, StandardCharsets.UTF_16LE);
        }

        // UTF-16 big-endian BOM
        if (data.length >= 2
                && (data[0] & 0xFF) == 0xFE
                && (data[1] & 0xFF) == 0xFF) {
            offset = 2;
            return new String(data, offset, data.length - offset, StandardCharsets.UTF_16BE);
        }

        // Keep correctly encoded subtitles untouched.
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException ignored) {
            // Fall through to the legacy charset handling below.
        }

        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT);
        if (lang.startsWith("ar") || lang.contains("arab")) {
            return new String(data, Charset.forName("windows-1256"));
        }

        // Some providers still publish legacy Western subtitle files.
        return new String(data, Charset.forName("windows-1252"));
    }

    private void applyExternalSubtitle(StreamResponse.Subtitle sub) {
        if (sub == null || subtitleExecutor == null) return;

        final long requestId = ++subtitleRequestId;
        final String source = sub.getUrl();

        subtitleExecutor.execute(() -> {
            try {
                File file = prepareSubtitleForPlayback(sub);
                List<LocalSubtitleCue> parsed = parseLocalSubtitleFile(file);
                if (isActivityDestroyed || requestId != subtitleRequestId) return;

                mainHandler.post(() -> {
                    if (isActivityDestroyed || requestId != subtitleRequestId) return;
                    activeSubtitle = sub;
                    activeSubtitleFile = file;
                    subtitleOffsetMs = getSavedSubtitleOffset(source);
                    subtitleEnabled = true;
                    synchronized (localSubtitleCues) {
                        localSubtitleCues.clear();
                        localSubtitleCues.addAll(parsed);
                    }
                    renderLocalSubtitle();
                });
            } catch (Exception ignored) {
                // Subtitle failure must never stop or re-prepare video playback.
            }
        });
    }

    private MediaItem.SubtitleConfiguration buildSubtitleConfiguration(
            File file,
            StreamResponse.Subtitle sub
    ) {
        return new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(detectSubtitleMimeType(file != null ? file.getName() : ""))
                .setLanguage(
                        normalizeSubtitleLanguage(
                                sub.getLanguage(),
                                sub.getLabel()
                        )
                )
                .setLabel(
                        sub.getLabel() != null && !sub.getLabel().trim().isEmpty()
                                ? sub.getLabel()
                                : normalizeSubtitleLanguage(
                                        sub.getLanguage(),
                                        null
                                )
                )
                .build();
    }

    private void applySubtitleTrackSelection() {
        // External subtitles are rendered by SubtitleView. Never attach a new
        // SubtitleConfiguration to the active MediaItem here: doing so would
        // force ExoPlayer to re-prepare the movie and can cause a long stall.
        if (trackSelector == null) return;
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true));
        renderLocalSubtitle();
    }

    private List<MediaItem.SubtitleConfiguration> buildSubtitleConfigs() {
        // Intentionally empty. External subtitles are rendered without changing
        // the video MediaSource after playback has started.
        return new ArrayList<>();
    }

    private void renderLocalSubtitle() {
        if (playerView == null) return;
        SubtitleView subtitleView = playerView.getSubtitleView();
        if (subtitleView == null) return;

        if (!subtitleEnabled || player == null) {
            subtitleView.setCues(Collections.emptyList());
            return;
        }

        final long position = Math.max(0L, player.getCurrentPosition());
        LocalSubtitleCue visible = null;
        synchronized (localSubtitleCues) {
            for (LocalSubtitleCue cue : localSubtitleCues) {
                if (position >= cue.startMs && position < cue.endMs) {
                    visible = cue;
                    break;
                }
            }
        }

        if (visible == null) {
            subtitleView.setCues(Collections.emptyList());
        } else {
            subtitleView.setCues(Collections.singletonList(
                    new Cue.Builder().setText(visible.text).build()
            ));
        }
    }

    private List<LocalSubtitleCue> parseLocalSubtitleFile(File file) throws Exception {
        if (file == null || !file.exists()) throw new java.io.IOException("Subtitle file missing");
        String text = new String(readFileBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace('\r', '\n');
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ass") || lower.endsWith(".ssa")) return parseAssSubtitle(text);
        return parseSrtOrVttSubtitle(text);
    }

    private List<LocalSubtitleCue> parseSrtOrVttSubtitle(String text) {
        List<LocalSubtitleCue> result = new ArrayList<>();
        String[] blocks = text.replace("\uFEFF", "").split("\\n\\s*\\n");
        Pattern timePattern = Pattern.compile("(?m)(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})");
        for (String block : blocks) {
            Matcher m = timePattern.matcher(block);
            if (!m.find()) continue;
            long start = parseSubtitleTime(m.group(1));
            long end = parseSubtitleTime(m.group(2));
            if (start < 0 || end < 0 || end <= start) continue;
            String body = block.substring(m.end()).trim();
            body = body.replaceAll("<[^>]+>", "");
            body = body.replaceAll("(?m)^[0-9]+\\s*$", "");
            if (body.isEmpty()) continue;
            result.add(new LocalSubtitleCue(start, end, body));
        }
        Collections.sort(result, (a,b) -> Long.compare(a.startMs,b.startMs));
        return result;
    }

    private List<LocalSubtitleCue> parseAssSubtitle(String text) {
        List<LocalSubtitleCue> result = new ArrayList<>();
        String[] lines = text.replace("\uFEFF", "").split("\n");
        for (String line : lines) {
            String l = line.trim();
            if (!l.regionMatches(true, 0, "Dialogue:", 0, 9)) continue;
            String data = l.substring(9).trim();
            String[] parts = data.split(",", 10);
            if (parts.length < 10) continue;
            long start = parseAssTime(parts[1].trim());
            long end = parseAssTime(parts[2].trim());
            if (start < 0 || end < 0 || end <= start) continue;
            String body = parts[9].replace("\\N", "\n").replace("\\n", "\n");
            body = body.replaceAll("\\\\{[^}]*\\}", "").trim();
            if (!body.isEmpty()) result.add(new LocalSubtitleCue(start,end,body));
        }
        Collections.sort(result, (a,b) -> Long.compare(a.startMs,b.startMs));
        return result;
    }

    private long parseSubtitleTime(String value) {
        try {
            String[] p = value.trim().replace(',', '.').split(":");
            if (p.length != 3) return -1L;
            String[] sec = p[2].split("\\.");
            long h = Long.parseLong(p[0]);
            long min = Long.parseLong(p[1]);
            long ms = sec.length > 1 ? Long.parseLong((sec[1] + "000").substring(0,3)) : 0L;
            return h * 3600000L + min * 60000L + Long.parseLong(sec[0]) * 1000L + ms;
        } catch (Exception e) { return -1L; }
    }

    private long parseAssTime(String value) {
        try {
            String[] p = value.trim().split(":");
            if (p.length != 3) return -1L;
            String[] sec = p[2].split("\\.");
            long h = Long.parseLong(p[0]);
            long min = Long.parseLong(p[1]);
            long cs = sec.length > 1 ? Long.parseLong((sec[1] + "00").substring(0,2)) : 0L;
            return h * 3600000L + min * 60000L + Long.parseLong(sec[0]) * 1000L + cs * 10L;
        } catch (Exception e) { return -1L; }
    }

    private long consumePendingRecoveryPosition() {
        long position = pendingRecoveryPositionMs;
        pendingRecoveryPositionMs = -1L;
        return Math.max(0L, position);
    }

    private void initializePlayer(String url, String initialQualityLabel, long resumePositionMs) {
        // A guest follows the host: a locally saved resume point must not fight the room.
        if (roomController != null && roomController.isGuestMode()) resumePositionMs = 0;
        if (!validateStreamUrl(url)) {
            updateUIState(UIState.ERROR, getString(R.string.invalid_video_data));
            return;
        }

        cancelPendingRetry();
        updateUIState(UIState.PREPARING, null);
        releasePlayer();
        hasRestoredPosition = resumePositionMs > MIN_SAVE_POSITION_MS;
        final long thisGeneration = ++playerGeneration;

        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setMaxVideoSize(MAX_AUTO_VIDEO_WIDTH, MAX_AUTO_VIDEO_HEIGHT));

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(30000, 120000, 2500, 5000)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build();

        Map<String, String> headers = buildHeaders(null);
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000);

        List<MediaItem.SubtitleConfiguration> subtitleConfigs = buildSubtitleConfigs();

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(url.toLowerCase(Locale.ROOT).contains(".m3u8") ? MimeTypes.APPLICATION_M3U8 : null)
                .setSubtitleConfigurations(subtitleConfigs)
                .build();

        MediaSource mediaSource = url.toLowerCase(Locale.ROOT).contains(".m3u8") 
                ? new HlsMediaSource.Factory(dataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(mediaItem)
                : new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem);

        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                // handleAudioFocus=false: Watch Party voice chat ducks this player's volume
                // directly and deterministically (see VoiceChatManager/RoomController) instead of
                // through Android's audio-focus grant/revoke callbacks. That system is well-known
                // to get stuck - even ExoPlayer's own issue tracker documents a real case
                // (google/ExoPlayer#7182) of a VoIP app never releasing focus back, leaving
                // ExoPlayer "waiting indefinitely" at low volume. Letting us set player.volume
                // ourselves removes that dependency entirely: it cannot get stuck, because
                // nothing else is relied upon to hand control back.
                .setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                                .build(),
                        /* handleAudioFocus= */ false)
                .build();

        playerView.setPlayer(player);
        player.setPlaybackParameters(new PlaybackParameters(currentSpeed));
        player.addListener(playerListener);
        if (roomController != null) roomController.onPlayerCreated(player);
        player.setMediaSource(mediaSource, resumePositionMs > 0 ? resumePositionMs : C.TIME_UNSET);
        player.prepare();
        player.setPlayWhenReady(!userPaused);
        applySubtitleTrackSelection();

        currentStreamUrl = url;
        btnQuality.setText(initialQualityLabel);
        tvMovieSubtitle.setText(initialQualityLabel);
    }

    private boolean validateStreamUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String normalizeSubtitleLanguage(String language, String label) {
        String value = language;
        if (value == null || value.trim().isEmpty()) value = inferLanguageFromLabel(label);
        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.startsWith("ar") || value.contains("arab") || value.contains("عرب")) return "ar";
        if (value.startsWith("en") || value.contains("eng")) return "en";
        if (value.startsWith("es") || value.contains("span") || value.contains("esp")) return "es";
        if (value.startsWith("fr") || value.contains("fren") || value.contains("fra")) return "fr";
        if (value.startsWith("de")) return "de";
        if (value.startsWith("it")) return "it";
        if (value.startsWith("pt")) return "pt";
        return value.isEmpty() ? "und" : value;
    }

    private String detectSubtitleMimeType(String url) {
        String lowerUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains(".srt")) return MimeTypes.APPLICATION_SUBRIP;
        if (lowerUrl.contains(".ass") || lowerUrl.contains(".ssa")) return MimeTypes.TEXT_SSA;
        return MimeTypes.TEXT_VTT;
    }

    private String inferLanguageFromLabel(String label) {
        if (label == null) return "ar";
        String l = label.toLowerCase(Locale.ROOT);
        if (l.contains("arab") || l.contains("عرب")) return "ar";
        if (l.contains("eng")) return "en";
        if (l.contains("span") || l.contains("esp")) return "es";
        if (l.contains("fren") || l.contains("fra")) return "fr";
        return "ar";
    }

    private void switchToUrl(String url, Map<String, String> extraHeaders, String qualityLabel) {
        if (player == null || url == null || url.isEmpty() || url.equals(currentStreamUrl)) return;

        try {
            updateUIState(UIState.BUFFERING, null);

            long resumePos = Math.max(0L, player.getCurrentPosition());
            boolean wasPlaying = player.getPlayWhenReady();
            DefaultTrackSelector.Parameters currentParams = trackSelector.getParameters();

            Map<String, String> headers = buildHeaders(extraHeaders);
            DefaultHttpDataSource.Factory dsFactory = new DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(headers)
                    .setAllowCrossProtocolRedirects(true);

            boolean isHls = url.toLowerCase(Locale.ROOT).contains(".m3u8");

            MediaItem newItem = new MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMimeType(isHls ? MimeTypes.APPLICATION_M3U8 : null)
                    .setSubtitleConfigurations(buildSubtitleConfigs())
                    .build();

            MediaSource newSource = isHls
                    ? new HlsMediaSource.Factory(dsFactory)
                            .setAllowChunklessPreparation(true)
                            .createMediaSource(newItem)
                    : new DefaultMediaSourceFactory(dsFactory)
                            .createMediaSource(newItem);

            player.setMediaSource(newSource, resumePos);
            trackSelector.setParameters(currentParams);
            player.prepare();
            player.setPlayWhenReady(wasPlaying);
            applySubtitleTrackSelection();

            currentStreamUrl = url;
            btnQuality.setText(qualityLabel);
            tvMovieSubtitle.setText(qualityLabel);
        } catch (Exception e) {
            updateUIState(UIState.PLAYING, null);
            showCustomToast(getString(R.string.quality_change_failed));
        }
    }

    private Map<String, String> buildHeaders(Map<String, String> extraHeaders) {
        Map<String, String> merged = new HashMap<>();
        if (streamHeaders != null) merged.putAll(streamHeaders);
        if (extraHeaders != null) merged.putAll(extraHeaders);
        if (!merged.containsKey("Referer") && masterStreamUrl != null && masterStreamUrl.contains("vixsrc")) {
            merged.put("Referer", "https://vixsrc.to/");
        }
        if (!merged.containsKey("User-Agent")) merged.put("User-Agent", DEFAULT_USER_AGENT);

        Map<String, String> sanitized = new HashMap<>();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            String key = entry.getKey().replaceAll("[\\r\\n]", "").trim();
            String value = entry.getValue() != null ? entry.getValue().replaceAll("[\\r\\n]", "").trim() : "";
            if (!key.isEmpty() && !value.isEmpty()) sanitized.put(key, value);
        }
        return sanitized;
    }


    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (isActivityDestroyed || player == null) return;
            switch (playbackState) {
                case Player.STATE_READY:
                    // A stable READY state means recovery succeeded; start fresh
                    // retry budgets for any later network interruption.
                    retryCount = 0;
                    updateUIState(
                            player.getPlayWhenReady()
                                    ? UIState.PLAYING
                                    : UIState.PAUSED,
                            null
                    );

                    long total = safeDuration(player.getDuration());

                    if (total <= 0 && serverMovieDurationMs > 0) {
                        total = serverMovieDurationMs;
                    }

                    playerSeekBar.setMax(
                            (int) Math.min(total, Integer.MAX_VALUE)
                    );

                    tvTotalDuration.setText(formatTime(total));

                    if (!hasRestoredPosition && currentVidKey != null) {
                        restoreSavedPosition(total);
                        hasRestoredPosition = true;
                    }

                    mainHandler.removeCallbacks(updateProgressRunnable);
                    mainHandler.post(updateProgressRunnable);
                    renderLocalSubtitle();

                    break;
                case Player.STATE_BUFFERING:
                    updateUIState(userPaused ? UIState.PAUSED : UIState.BUFFERING, null);
                    break;
                case Player.STATE_ENDED:

                    updateUIState(UIState.ENDED, null);

                    mainHandler.removeCallbacks(updateProgressRunnable);

                    break;
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isActivityDestroyed || player == null) return;

            // Media3 reports isPlaying=false while buffering. Do not turn the
            // pause icon into a play triangle during a network stall.
            if (player.getPlaybackState() == Player.STATE_BUFFERING) {
                // Buffering is not a user pause. Keep Pause visible while the
                // player intends to continue. Only a real user pause shows Play.
                currentState = userPaused ? UIState.PAUSED : UIState.BUFFERING;
                btnPlayPause.setImageResource(userPaused ? R.drawable.ic_play : R.drawable.ic_pause);
                return;
            }

            if (player.getPlaybackState() != Player.STATE_ENDED) {
                updateUIState(isPlaying ? UIState.PLAYING : UIState.PAUSED, null);
            }
        }

        @Override
        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                             @NonNull Player.PositionInfo newPosition,
                                             @Player.DiscontinuityReason int reason) {
            if (isActivityDestroyed || player == null) return;

            // Refresh our external subtitle renderer immediately after a seek.
            // The renderer is position-driven and never seeks the video itself.
            renderLocalSubtitle();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            if (isActivityDestroyed || player == null) return;

            if (userPaused) return;

            final long currentPos = Math.max(0, player.getCurrentPosition());
            if (retryCount < MAX_RETRIES) {
                retryCount++;
                long delay = Math.min(12000L, RETRY_BASE_DELAY_MS * (1L << Math.min(3, retryCount - 1)));
                updateUIState(UIState.BUFFERING, null);
                scheduleRetry(delay, currentPos);
                return;
            }

            // A stream URL can expire while the movie is playing. Retrying the
            // same URL forever cannot fix that. Refresh the provider/server
            // source silently and continue from the last known position.
            if (sourceRecoveryCount < MAX_SOURCE_RECOVERIES && currentVidKey != null && !currentVidKey.isEmpty()) {
                sourceRecoveryCount++;
                retryCount = 0;
                pendingRecoveryPositionMs = currentPos;
                updateUIState(UIState.FETCHING, null);
                // Refresh the server session first instead of starting the slow
                // provider-resolution chain again after every playback error.
                fetchStreamFromServer(currentVidKey);
                return;
            }

            String message = getString(R.string.playback_error);
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
                message = networkAvailable ? getString(R.string.network_connection_failed) : getString(R.string.network_disconnected);
            } else if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                message = getString(R.string.http_error);
            }
            updateUIState(UIState.ERROR, message);
        }
    };

    private void saveWatchHistoryMetadata() {
        if (currentVidKey == null || currentVidKey.isEmpty()) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("vid", currentVidKey);
            obj.put("title", getIntent().getStringExtra("TITLE"));
            obj.put("img", getIntent().getStringExtra("IMG"));
            obj.put("desc", getIntent().getStringExtra("DESC"));
            obj.put("rating", getIntent().getStringExtra("RATING"));
            obj.put("year", getIntent().getStringExtra("YEAR"));
            obj.put("quality", getIntent().getStringExtra("QUALITY"));
            obj.put("genre", getIntent().getStringExtra("GENRE"));
            obj.put("director", getIntent().getStringExtra("DIRECTOR"));
            obj.put("duration", getIntent().getStringExtra("DURATION"));
            obj.put("language", getIntent().getStringExtra("LANGUAGE"));
            obj.put("updatedAt", System.currentTimeMillis());
            obj.put("durationMs", 0L);
            getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putString("history_" + currentVidKey, obj.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void restoreSavedPosition(long total) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long savedPos = prefs.getLong("pos_" + currentVidKey, 0);
        if (savedPos > MIN_SAVE_POSITION_MS && player != null && !(roomController != null && roomController.isGuestMode())) {
            if (total > 0 && savedPos < total - END_OF_MEDIA_THRESHOLD_MS) {
                player.seekTo(savedPos);
            }
        }
    }

    private void persistPlaybackPosition() {
        if (player == null || currentVidKey == null) return;
        long current = player.getCurrentPosition();
        if (current <= 0) return;
        long total = safeDuration(player.getDuration());
        if (total <= 0) total = serverMovieDurationMs;

        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        if (total > 0 && current > total - END_OF_MEDIA_THRESHOLD_MS) {
            editor.remove("pos_" + currentVidKey);
            editor.remove("dur_" + currentVidKey);
            editor.remove("history_" + currentVidKey);
        } else if (current > MIN_SAVE_POSITION_MS) {
            editor.putLong("pos_" + currentVidKey, current);
            // Lightweight duration store for BOTH movies and episodes, so any screen (e.g. the
            // episode list) can compute a watched-fraction without needing the movie-only
            // "history_" continue-watching blob below.
            if (total > 0) editor.putLong("dur_" + currentVidKey, total);
            // Episodes keep their resume position but never create a movie "continue watching" entry.
            if (!isSeriesPlayback()) {
                try {
                    JSONObject obj = new JSONObject(getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("history_" + currentVidKey, "{}"));
                    obj.put("updatedAt", System.currentTimeMillis());
                    obj.put("positionMs", current);
                    obj.put("durationMs", total);
                    editor.putString("history_" + currentVidKey, obj.toString());
                } catch (Exception ignored) { }
            }
        }
        editor.apply();
    }

    private void clearSavedPosition() {
        if (currentVidKey != null) {
            getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .remove("pos_" + currentVidKey)
                    .remove("dur_" + currentVidKey)
                    .remove("history_" + currentVidKey)
                    .apply();
        }
    }

    private final Runnable updateProgressRunnable = new Runnable() {
        private int tickCount = 0;
        @Override
        public void run() {
            if (isActivityDestroyed || player == null) return;
            if (player.isPlaying() && !isUserSeeking && !isGestureSeeking) {
                long current = player.getCurrentPosition();
                long total = safeDuration(player.getDuration());
                if (total <= 0) total = serverMovieDurationMs;

                if (total > 0) {
                    playerSeekBar.setMax((int) Math.min(total, Integer.MAX_VALUE));
                    playerSeekBar.setProgress((int) Math.min(Math.max(current, 0), total));
                    tvTotalDuration.setText(formatTime(total));
                }
                tvCurrentPosition.setText(formatTime(current));

                if (++tickCount >= 5) {
                    persistPlaybackPosition();
                    tickCount = 0;
                }
            }
            mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
        }
    };

    private void showQualitySelector() {
        if (player == null) { showCustomToast(getString(R.string.player_not_ready)); return; }
        if (availableStreams != null && availableStreams.size() > 1) showQualitySelectorFromServerStreams();
        else showQualitySelectorFromTracks();
    }

    private void showQualitySelectorFromServerStreams() {
        Map<String, StreamResponse.StreamItem> qualityMap = new LinkedHashMap<>();
        for (StreamResponse.StreamItem item : availableStreams) {
            if (item == null || item.getUrl() == null || item.getUrl().isEmpty()) continue;
            String q = item.getQuality() != null ? item.getQuality() : (item.getHeight() > 0 ? item.getHeight() + "p" : "");
            if (!q.isEmpty() && !qualityMap.containsKey(q)) qualityMap.put(q, item);
        }
        if (qualityMap.isEmpty()) { showQualitySelectorFromTracks(); return; }

        List<String> sortedQualities = new ArrayList<>(qualityMap.keySet());
        Collections.sort(sortedQualities, (a, b) -> extractHeightValue(a) - extractHeightValue(b));

        List<String> labels = new ArrayList<>();
        List<StreamResponse.StreamItem> items = new ArrayList<>();
        labels.add(getString(R.string.auto_quality)); items.add(null);
        for (String q : sortedQualities) { labels.add(q); items.add(qualityMap.get(q)); }

        showQualityDialog(labels, which -> {
            if (which == 0) {
                trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(MAX_AUTO_VIDEO_WIDTH, MAX_AUTO_VIDEO_HEIGHT));
                switchToUrl(masterStreamUrl, null, "Auto");
            } else {
                StreamResponse.StreamItem item = items.get(which);
                if (item != null) switchToUrl(item.getUrl(), item.getHeaders(), labels.get(which));
            }
        });
    }

    private int extractHeightValue(String qualityLabel) {
        try { return Integer.parseInt(qualityLabel.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private void showQualitySelectorFromTracks() {
        Tracks tracks = player.getCurrentTracks();
        List<Format> formats = new ArrayList<>();
        List<TrackGroup> groups = new ArrayList<>();
        List<Integer> trackIndexes = new ArrayList<>();

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format f = group.getTrackFormat(i);
                if (f.height <= 0) continue;
                boolean exists = false;
                for (Format existing : formats) if (existing.height == f.height) { exists = true; break; }
                if (exists) continue;
                formats.add(f); groups.add(group.getMediaTrackGroup()); trackIndexes.add(i);
            }
        }

        List<String> labels = new ArrayList<>();
        List<Integer> sortedIndexes = new ArrayList<>();
        labels.add(getString(R.string.auto_quality)); sortedIndexes.add(-1);

        if (!formats.isEmpty()) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < formats.size(); i++) order.add(i);
            Collections.sort(order, (a, b) -> formats.get(a).height - formats.get(b).height);
            for (int idx : order) { labels.add(formats.get(idx).height + "p"); sortedIndexes.add(idx); }
        }

        showQualityDialog(labels, which -> {
            if (which == 0) {
                trackSelector.setParameters(trackSelector.buildUponParameters().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setMaxVideoSize(MAX_AUTO_VIDEO_WIDTH, MAX_AUTO_VIDEO_HEIGHT));
                btnQuality.setText("Auto"); tvMovieSubtitle.setText("Auto");
            } else {
                int idx = sortedIndexes.get(which);
                trackSelector.setParameters(trackSelector.buildUponParameters().clearVideoSizeConstraints().clearOverridesOfType(C.TRACK_TYPE_VIDEO).addOverride(new TrackSelectionOverride(groups.get(idx), trackIndexes.get(idx))));
                btnQuality.setText(labels.get(which)); tvMovieSubtitle.setText(labels.get(which));
            }
        });
    }

    private void showQualityDialog(List<String> labels, QualitySelectionListener listener) {
        showPremiumOptionDialog(getString(R.string.select_video_quality), labels, -1, listener);
    }

    private interface QualitySelectionListener { void onSelected(int which); }

    private void showSubtitleSelector() {
        if (player == null || trackSelector == null) return;

        List<StreamResponse.Subtitle> all = getAllSubtitleOptions();

        // The server fetch runs on a background thread and can still be in flight
        // (especially when resuming straight into a movie). Rather than show an
        // empty list, retry once and reopen automatically when results land.
        if (all.isEmpty() && currentVidKey != null && !currentVidKey.trim().isEmpty()) {
            pendingSubtitleDialogReopen = true;
            requestSubtitlesFromServer(currentVidKey);
            showCustomToast(getString(R.string.fetching_subtitles));
            return;
        }
        pendingSubtitleDialogReopen = false;
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.subtitles_off));

        for (StreamResponse.Subtitle sub : all) {
            String label = sub.getLabel();
            if (label == null || label.trim().isEmpty()) {
                label = normalizeSubtitleLanguage(sub.getLanguage(), null);
            }
            if (activeSubtitle != null
                    && activeSubtitle.getUrl() != null
                    && activeSubtitle.getUrl().equalsIgnoreCase(sub.getUrl())
                    && subtitleOffsetMs != 0L) {
                label += String.format(Locale.US, "  [%+.1fs]", subtitleOffsetMs / 1000f);
            }
            options.add(label);
        }

        options.add("Subtitle sync");

        int selected = 0;
        if (subtitleEnabled && activeSubtitle != null) {
            String activeUrl = activeSubtitle.getUrl();
            for (int i = 0; i < all.size(); i++) {
                if (activeUrl != null && activeUrl.equalsIgnoreCase(all.get(i).getUrl())) {
                    selected = i + 1;
                    break;
                }
            }
        }

        showPremiumOptionDialog(getString(R.string.subtitles_dialog_title), options, selected, which -> {
            if (which == 0) {
                subtitleEnabled = false;
                synchronized (localSubtitleCues) { localSubtitleCues.clear(); }
                renderLocalSubtitle();
                return;
            }
            if (which == options.size() - 1) {
                if (activeSubtitle == null) showCustomToast(getString(R.string.select_subtitle_first));
                else showSubtitleSyncDialog();
                return;
            }
            applyExternalSubtitle(all.get(which - 1));
        });
    }

    private void showSubtitleSyncDialog() {
        if (activeSubtitle == null) {
            showCustomToast(getString(R.string.select_subtitle_first));
            return;
        }

        final String[] labels = new String[]{
                "Subtitle -5s", "Subtitle -0.5s", "Reset sync",
                "Subtitle +0.5s", "Subtitle +5s"
        };

        showPremiumOptionDialog(
                "Subtitle sync  ·  " + formatSubtitleOffset(subtitleOffsetMs),
                new ArrayList<>(java.util.Arrays.asList(labels)),
                -1,
                which -> {
                    long delta;
                    switch (which) {
                        case 0: delta = -SUBTITLE_OFFSET_LARGE_STEP_MS; break;
                        case 1: delta = -SUBTITLE_OFFSET_STEP_MS; break;
                        case 2: delta = -subtitleOffsetMs; break;
                        case 3: delta = SUBTITLE_OFFSET_STEP_MS; break;
                        case 4: delta = SUBTITLE_OFFSET_LARGE_STEP_MS; break;
                        default: delta = 0L;
                    }
                    subtitleOffsetMs = Math.max(-MAX_SUBTITLE_OFFSET_MS,
                            Math.min(MAX_SUBTITLE_OFFSET_MS, subtitleOffsetMs + delta));
                    saveSubtitleOffset(activeSubtitle.getUrl(), subtitleOffsetMs);
                    reapplyActiveSubtitleWithoutChangingPosition();
                }
        );
    }

    private void showAudioTrackSelector() {
        if (player == null || trackSelector == null) return;

        Tracks tracks = player.getCurrentTracks();
        List<TrackGroup> audioGroups = new ArrayList<>();
        List<Integer> audioTrackIndexes = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format f = group.getTrackFormat(i);
                audioGroups.add(group.getMediaTrackGroup());
                audioTrackIndexes.add(i);
                labels.add(f.language != null
                        ? new Locale(f.language).getDisplayLanguage()
                        : getString(R.string.audio_prefix) + " " + (labels.size() + 1));
            }
        }

        if (labels.size() <= 1) {
            showCustomToast(getString(R.string.single_audio_track));
            return;
        }

        showPremiumOptionDialog(getString(R.string.select_audio_track), labels, -1, which -> {
            trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .addOverride(new TrackSelectionOverride(
                                    audioGroups.get(which), audioTrackIndexes.get(which)))
            );
            showCustomToast(getString(R.string.selected_prefix) + labels.get(which));
        });
    }

    private void showSpeedSelector() {
        final String[] speeds = {
                "0.5x", "0.75x", getString(R.string.speed_normal),
                "1.25x", "1.5x", "1.75x", "2.0x"
        };
        final float[] values = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

        int selected = 2;
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            float distance = Math.abs(values[i] - currentSpeed);
            if (distance < closest) {
                closest = distance;
                selected = i;
            }
        }

        showPremiumOptionDialog(getString(R.string.playback_speed),
                new ArrayList<>(java.util.Arrays.asList(speeds)), selected, which -> {
                    currentSpeed = values[which];
                    if (player != null) player.setPlaybackParameters(new PlaybackParameters(currentSpeed));
                    getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit().putFloat(PREF_SPEED, currentSpeed).apply();
                            });
    }

    private String formatSpeedLabel(float speed) {
        if (Math.abs(speed - 1.0f) < 0.001f) return "1.0x";
        return String.format(Locale.US, "%.2fx", speed)
                .replace(".00", ".0").replace(".50", ".5");
    }

    private void showSettingsMenu() {
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.setting_speed) + "  ·  " + formatSpeedLabel(currentSpeed));
        options.add(getString(R.string.setting_quality) + "  ·  " + btnQuality.getText());
        options.add(getString(R.string.setting_subtitles));
        options.add(getString(R.string.setting_audio));
        options.add(getString(R.string.setting_aspect_ratio));
        options.add(getString(R.string.setting_external_player));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            options.add(getString(R.string.setting_pip));
        }

        // Leading icons, same order as the options above (Material icons).
        List<Integer> iconList = new ArrayList<>();
        iconList.add(R.drawable.ic_speed);
        iconList.add(R.drawable.ic_hd);
        iconList.add(R.drawable.ic_closed_caption);
        iconList.add(R.drawable.ic_audiotrack);
        iconList.add(R.drawable.ic_aspect_ratio);
        iconList.add(R.drawable.ic_open_in_new);
        if (options.size() > 6) iconList.add(R.drawable.ic_pip);
        final int roomIndex = options.size();
        options.add(getString(R.string.room_menu_entry));
        iconList.add(R.drawable.ic_group);
        int[] icons = new int[iconList.size()];
        for (int k = 0; k < icons.length; k++) icons[k] = iconList.get(k);

        showPremiumOptionDialog(getString(R.string.player_settings), options, icons, -1, which -> {
            scheduleControllerHide();
            if (which == roomIndex) { if (roomController != null) roomController.showMenu(); return; }
            switch (which) {
                case 0: showSpeedSelector(); break;
                case 1: showQualitySelector(); break;
                case 2: showSubtitleSelector(); break;
                case 3: showAudioTrackSelector(); break;
                case 4: toggleAspectRatio(); break;
                case 5: openInExternalPlayer(); break;
                case 6: enterPipMode(); break;
            }
        });
    }

    private void toggleAspectRatio() {
        if (playerView == null) return;
        if (currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
            showCustomToast(getString(R.string.aspect_fill));
        } else if (currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            showCustomToast(getString(R.string.aspect_zoom));
        } else {
            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
            showCustomToast(getString(R.string.aspect_fit));
        }
        playerView.setResizeMode(currentResizeMode);
    }

    private GradientDrawable createPlayerSidePanelBackground() {
        // Full-height side sheet: deliberately square and borderless so no
        // curved top/bottom edge or rear outline is visible.
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(28, 24, 21)); // sheet_surface
        return background;
    }

    private void showPremiumOptionDialog(
            String title,
            List<String> options,
            int selectedIndex,
            final QualitySelectionListener listener
    ) {
        showPremiumOptionDialog(title, options, null, selectedIndex, listener);
    }

    /**
     * Player side sheet.
     *  - selectedIndex == -1  -> navigation list (icon, label, current value, chevron)
     *  - selectedIndex >= 0   -> single choice list (radio indicator on the left of the label)
     * Rows are 56dp+, borderless, with ripple feedback; the close button is a 48dp target with a
     * perfectly centred 24dp icon (Material "close").
     */
    private void showPremiumOptionDialog(
            String title,
            List<String> options,
            int[] iconRes,
            int selectedIndex,
            final QualitySelectionListener listener
    ) {
        if (isActivityDestroyed) return;
        dismissActiveDialog();

        final int peach = getColor(R.color.comic_peach);
        final int textPrimary = getColor(R.color.text_primary);
        final int textSecondary = getColor(R.color.text_secondary);
        final int textMuted = getColor(R.color.text_muted);
        final boolean singleChoice = selectedIndex >= 0;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        root.setPadding(dp(20), dp(12), dp(20), dp(12));
        root.setBackground(createPlayerSidePanelBackground());

        // ---- header: title + close (same 56dp height, both vertically centred)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleView.setMaxLines(2);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_close_material);
        close.setColorFilter(textSecondary, PorterDuff.Mode.SRC_IN);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setPadding(0, 0, 0, 0);
        close.setBackgroundResource(R.drawable.bg_icon_ripple);
        close.setContentDescription(getString(R.string.back));
        close.setClickable(true);
        close.setFocusable(true);
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.line_default));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divLp.setMargins(0, dp(4), 0, dp(8));
        root.addView(divider, divLp);

        // ---- list
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(8));

        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            final boolean isSelected = singleChoice && index == selectedIndex;

            // "Label  ·  value" -> label on the start side, value muted on the end side
            String raw = options.get(i) == null ? "" : options.get(i);
            String labelText = raw;
            String valueText = null;
            int sep = raw.indexOf("  ·  ");
            if (sep > 0) {
                labelText = raw.substring(0, sep);
                valueText = raw.substring(sep + 5);
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(56));
            row.setPadding(dp(12), dp(6), dp(12), dp(6));
            row.setBackgroundResource(R.drawable.bg_row_ripple);
            row.setClickable(true);
            row.setFocusable(true);

            if (singleChoice) {
                ImageView radio = new ImageView(this);
                radio.setImageResource(isSelected ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
                radio.setColorFilter(isSelected ? peach : textMuted, PorterDuff.Mode.SRC_IN);
                LinearLayout.LayoutParams radioLp = new LinearLayout.LayoutParams(dp(24), dp(24));
                radioLp.setMarginEnd(dp(16));
                row.addView(radio, radioLp);
            } else if (iconRes != null && index < iconRes.length && iconRes[index] != 0) {
                ImageView icon = new ImageView(this);
                icon.setImageResource(iconRes[index]);
                icon.setColorFilter(peach, PorterDuff.Mode.SRC_IN);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(24), dp(24));
                iconLp.setMarginEnd(dp(16));
                row.addView(icon, iconLp);
            }

            TextView label = new TextView(this);
            label.setText(labelText);
            label.setTextColor(isSelected ? peach : textPrimary);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            label.setTypeface(label.getTypeface(), isSelected ? Typeface.BOLD : Typeface.NORMAL);
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setMaxLines(2);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (valueText != null) {
                TextView value = new TextView(this);
                value.setText(valueText);
                value.setTextColor(textMuted);
                value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                value.setMaxLines(1);
                LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                valueLp.setMarginStart(dp(12));
                row.addView(value, valueLp);
            }

            if (!singleChoice) {
                ImageView chevron = new ImageView(this);
                chevron.setImageResource(R.drawable.ic_chevron_end);
                chevron.setColorFilter(textMuted, PorterDuff.Mode.SRC_IN);
                LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(dp(24), dp(24));
                chevronLp.setMarginStart(dp(4));
                row.addView(chevron, chevronLp);
            }

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(2));
            list.addView(row, rowLp);
            row.setOnClickListener(v -> {
                dismissActiveDialog();
                if (listener != null) listener.onSelected(index);
            });
        }

        scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                .setView(root).create();

        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) activeDialog = null;
        });
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() == null) return;
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.68f);
            dialog.getWindow().setGravity(Gravity.END);
            dialog.getWindow().setWindowAnimations(R.style.PlayerSidePanelAnimation);
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int maxWidth = dp(430);
            lp.width = Math.min(maxWidth, Math.max(dp(300), (int) (screenWidth * 0.46f)));
            lp.height = screenHeight;
            lp.gravity = Gravity.END;
            dialog.getWindow().setAttributes(lp);
        });
        dialog.show();
        activeDialog = dialog;
        // Re-apply after show because some Android builds reset gravity/size.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.END);
            dialog.getWindow().setWindowAnimations(R.style.PlayerSidePanelAnimation);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Watch-party hooks (used by RoomController)
    // ------------------------------------------------------------------------------------------
    private long lastRemoteSeekAtMs;

    private boolean roomBlocks() {
        return roomController != null && roomController.blocksControl();
    }

    void roomToast(String message) {
        if (!isActivityDestroyed) showCustomToast(message);
    }

    /** Real hardware button, part of activity_player.xml topRightActions - RoomController shows/hides and icons it. */
    ImageButton roomMicButton() {
        return findViewById(R.id.btnMic);
    }

    boolean hasMicPermission() {
        return androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Requests RECORD_AUDIO if needed; [callback] always fires exactly once, with the end result. */
    void requestMicPermission(java.util.function.Consumer<Boolean> callback) {
        if (hasMicPermission()) { callback.accept(true); return; }
        pendingMicPermissionCallback = granted -> {
            if (!granted && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    this, android.Manifest.permission.RECORD_AUDIO)) {
                // false here (after already asking once) means "don't ask again" / permanently denied.
                roomToast(getString(R.string.room_mic_permission_settings));
            }
            callback.accept(granted);
        };
        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO);
    }

    void showRoomOptions(String title, List<String> options, int[] icons, java.util.function.IntConsumer onPick) {
        showPremiumOptionDialog(title, options, icons, -1, which -> onPick.accept(which));
    }

    void showRoomDialog(AlertDialog dialog) {
        showManagedDialog(dialog);
    }

    void closeRoomSheet() {
        dismissActiveDialog();
    }

    /** Side sheet with arbitrary content (the room chat). */
    void showRoomSheet(View content, Runnable onDismiss) {
        if (isActivityDestroyed) return;
        dismissActiveDialog();
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                .setView(content).create();
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) activeDialog = null;
            if (onDismiss != null) onDismiss.run();
        });
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() == null) return;
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setDimAmount(0.35f);
            dialog.getWindow().setWindowAnimations(R.style.PlayerSidePanelAnimation);
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            lp.width = Math.min(dp(430), Math.max(dp(300), (int) (screenWidth * 0.46f)));
            lp.height = getResources().getDisplayMetrics().heightPixels;
            lp.gravity = Gravity.END;
            dialog.getWindow().setAttributes(lp);
        });
        dialog.show();
        activeDialog = dialog;
    }

    /**
     * Guest side: make the local player match the host. A small drift is left alone (each corrective
     * seek costs a re-buffer), a large one is corrected, at most once every few seconds.
     */
    void applyRemotePlayback(boolean playing, long positionMs) {
        if (player == null || isActivityDestroyed) return;

        long duration = safeDuration(player.getDuration());
        long target = Math.max(0, duration > 0 ? Math.min(positionMs, duration) : positionMs);
        long drift = Math.abs(player.getCurrentPosition() - target);
        long now = android.os.SystemClock.elapsedRealtime();
        boolean seek = playing
                ? drift > 2500 && now - lastRemoteSeekAtMs > 3000
                : drift > 800;
        if (seek) {
            lastRemoteSeekAtMs = now;
            player.seekTo(target);
        }

        boolean playingNow = player.getPlayWhenReady() && !userPaused;
        if (playing && !playingNow) {
            userPaused = false;
            retryCount = 0;
            player.setPlayWhenReady(true);
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else if (!playing && playingNow) {
            userPaused = true;
            player.setPlayWhenReady(false);
            btnPlayPause.setImageResource(R.drawable.ic_play);
            currentState = UIState.PAUSED;
        }
    }

    private void showManagedDialog(AlertDialog dialog) {
        if (isActivityDestroyed) return;
        dismissActiveDialog();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_player_side_panel);
            }
            int peach = getColor(R.color.comic_peach);
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(peach);
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(peach);
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(peach);
            if (dialog.getListView() != null) {
                dialog.getListView().setDivider(null);
                dialog.getListView().setPadding(dp(8), dp(8), dp(8), dp(8));
                dialog.getListView().setSelector(R.drawable.dialog_item_selector);
            }
        });
        dialog.show();
        activeDialog = dialog;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String formatSubtitleOffset(long offsetMs) {
        return String.format(
                Locale.US,
                "%+.1fs",
                offsetMs / 1000f
        );
    }

    private void reapplyActiveSubtitleWithoutChangingPosition() {
        if (activeSubtitle == null || activeSubtitleFile == null || subtitleExecutor == null) return;
        final StreamResponse.Subtitle selected = activeSubtitle;
        final long requestId = ++subtitleRequestId;
        subtitleExecutor.execute(() -> {
            try {
                File file = prepareSubtitleForPlayback(selected);
                List<LocalSubtitleCue> parsed = parseLocalSubtitleFile(file);
                if (isActivityDestroyed || requestId != subtitleRequestId) return;
                mainHandler.post(() -> {
                    if (isActivityDestroyed || requestId != subtitleRequestId) return;
                    activeSubtitleFile = file;
                    synchronized (localSubtitleCues) {
                        localSubtitleCues.clear();
                        localSubtitleCues.addAll(parsed);
                    }
                    renderLocalSubtitle();
                });
            } catch (Exception ignored) { }
        });

        showCustomToast(
                getString(R.string.subtitle_sync_prefix) + " " + formatSubtitleOffset(subtitleOffsetMs)
        );
    }

    private void dismissActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) activeDialog.dismiss();
        activeDialog = null;
    }

    private void setupGestures() {
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                if (isActivityDestroyed || activeDialog != null || player == null || isInPictureInPictureMode() || isControlsLocked) return false;
                gestureStartX = e.getX(); gestureStartY = e.getY();
                gestureStartPosition = player.getCurrentPosition();
                isGestureSeeking = isGestureVolume = isGestureBrightness = false;
                if (audioManager != null) startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                startBrightness = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (isActivityDestroyed || activeDialog != null || player == null || gestureStartPosition < 0 || isInPictureInPictureMode() || isControlsLocked) return false;
                float deltaX = e2.getX() - gestureStartX;
                float deltaY = gestureStartY - e2.getY();
                float viewWidth = playerView.getWidth();
                float viewHeight = playerView.getHeight();
                if (viewWidth <= 0 || viewHeight <= 0) return false;

                if (!isGestureSeeking && !isGestureVolume && !isGestureBrightness) {
                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 40) isGestureSeeking = true;
                    else if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > 40) {
                        if (gestureStartX < viewWidth * 0.45f) isGestureBrightness = true;
                        else if (gestureStartX > viewWidth * 0.55f) isGestureVolume = true;
                    }
                }

                if (isGestureSeeking) {
                    long total = safeDuration(player.getDuration());
                    if (total <= 0) total = serverMovieDurationMs;
                    long maxSweep = Math.min(180000L, total / 10);
                    long seekOffsetMs = (long) ((deltaX / viewWidth) * maxSweep);
                    long targetPos = Math.max(0, Math.min(total > 0 ? total : Long.MAX_VALUE, gestureStartPosition + seekOffsetMs));
                    
                    gesturePendingSeekMs = targetPos;
                    tvCurrentPosition.setText(formatTime(targetPos));
                    if (total > 0) playerSeekBar.setMax((int) Math.min(total, Integer.MAX_VALUE));
                    playerSeekBar.setProgress((int) Math.min(targetPos, Integer.MAX_VALUE));
                    showGestureSeekFeedback(seekOffsetMs / 1000, targetPos, total);
                    return true;
                }

                if (isGestureVolume && audioManager != null) {
                    int deltaVol = (int) ((deltaY / viewHeight) * maxVolume * 1.5f);
                    int targetVol = Math.max(0, Math.min(maxVolume, startVolume + deltaVol));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);
                    showGestureLevelFeedback(getString(R.string.volume), (int) ((targetVol / (float) maxVolume) * 100));
                    return true;
                }

                if (isGestureBrightness) {
                    float targetBrightness = Math.max(0.01f, Math.min(1.0f, startBrightness + (deltaY / viewHeight)));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = targetBrightness;
                    getWindow().setAttributes(lp);
                    showGestureLevelFeedback(getString(R.string.brightness), (int) (targetBrightness * 100));
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (isActivityDestroyed || isInPictureInPictureMode()) return false;
                if (isControlsLocked) return true;
                if (isControllerVisible) hideController(); else showController();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (isActivityDestroyed || player == null || isInPictureInPictureMode() || isControlsLocked) return false;
                // RTL-oriented feel: right side of the screen = backward, left side = forward.
                if (playerView.getWidth() > 0) seekRelative(e.getX() < playerView.getWidth() * 0.55f ? SEEK_STEP_MS : -SEEK_STEP_MS);
                return true;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (isGestureSeeking && player != null && gesturePendingSeekMs >= 0) {
                    if (!roomBlocks()) player.seekTo(gesturePendingSeekMs);
                    gesturePendingSeekMs = -1;
                }
                isGestureSeeking = isGestureVolume = isGestureBrightness = false;
                mainHandler.postDelayed(() -> {
                    if (gestureFeedbackOverlay != null) gestureFeedbackOverlay.setVisibility(View.GONE);
                    if (gestureVolumeHud != null) gestureVolumeHud.setVisibility(View.GONE);
                    if (gestureBrightnessHud != null) gestureBrightnessHud.setVisibility(View.GONE);
                }, 500);
            }
            return true;
        });
    }

    private void showGestureSeekFeedback(long secondsOffset, long targetMs, long durationMs) {
        if (gestureFeedbackOverlay != null) {
            gestureFeedbackOverlay.setVisibility(View.VISIBLE);
            if (gestureVolumeHud != null) gestureVolumeHud.setVisibility(View.GONE);
            if (gestureBrightnessHud != null) gestureBrightnessHud.setVisibility(View.GONE);

            // Keep seek feedback away from the center of the video. Forward
            // gestures appear on the right edge; rewind gestures on the left.
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) gestureFeedbackOverlay.getLayoutParams();
            lp.gravity = Gravity.CENTER_VERTICAL
                    | (secondsOffset >= 0 ? Gravity.END : Gravity.START);
            int margin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());
            if (secondsOffset >= 0) {
                lp.rightMargin = margin;
                lp.leftMargin = 0;
            } else {
                lp.leftMargin = margin;
                lp.rightMargin = 0;
            }
            gestureFeedbackOverlay.setLayoutParams(lp);

            ivGestureFeedbackIcon.setImageResource(
                    secondsOffset >= 0 ? R.drawable.ic_fast_forward : R.drawable.ic_fast_rewind);
            tvGestureFeedbackText.setText(
                    formatTime(targetMs) + (durationMs > 0 ? " / " + formatTime(durationMs) : ""));
        }
    }

    private void showGestureLevelFeedback(String icon, int percentage) {
        if (gestureFeedbackOverlay != null) {
            gestureFeedbackOverlay.setVisibility(View.GONE);
            View hud = icon.equals(getString(R.string.volume)) ? gestureVolumeHud : gestureBrightnessHud;
            VerticalLevelView bar = icon.equals(getString(R.string.volume)) ? gestureVolumeProgress : gestureBrightnessProgress;
            TextView text = icon.equals(getString(R.string.volume)) ? gestureVolumeText : gestureBrightnessText;
            if (gestureVolumeHud != null) gestureVolumeHud.setVisibility(View.GONE);
            if (gestureBrightnessHud != null) gestureBrightnessHud.setVisibility(View.GONE);
            if (hud != null) hud.setVisibility(View.VISIBLE);
            if (bar != null) bar.setProgress(percentage);
            if (text != null) text.setText(percentage + "%");
        }
    }

    private void seekRelative(long offsetMs) {
        if (player == null) return;
        if (roomBlocks()) return;
        long duration = safeDuration(player.getDuration());
        long target = Math.max(0, player.getCurrentPosition() + offsetMs);
        if (duration > 0) target = Math.min(target, duration);
        player.seekTo(target);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(30);
        }
        showGestureSeekFeedback(offsetMs > 0 ? 10 : -10, target, duration);
        mainHandler.postDelayed(() -> {
            if (gestureFeedbackOverlay != null) gestureFeedbackOverlay.setVisibility(View.GONE);
            if (gestureVolumeHud != null) gestureVolumeHud.setVisibility(View.GONE);
            if (gestureBrightnessHud != null) gestureBrightnessHud.setVisibility(View.GONE);
        }, 800);
    }

    private void setupSeekBarListener() {
        if (TvUtils.isTv(this)) {
            // Arrow keys on the focused seek bar jump +/-10s (the bar's range is in ms, so its
            // built-in key handling would move it 1 ms per press and never commit the seek).
            playerSeekBar.setOnKeyListener((v, keyCode, e) -> {
                if (e.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    seekRelative(keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? -SEEK_STEP_MS : SEEK_STEP_MS);
                    return true;
                }
                return false;
            });
        }
        playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                pendingSeekTargetMs = progress;
                tvCurrentPosition.setText(formatTime(progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { isUserSeeking = true; cancelControllerHide(); }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (player != null && pendingSeekTargetMs >= 0 && !roomBlocks()) player.seekTo(Math.max(0, Math.min(pendingSeekTargetMs, safeDuration(player.getDuration()))));
                pendingSeekTargetMs = -1;
                scheduleControllerHide();
            }
        });
    }

    private void lockControls() {
        isControlsLocked = true;
        hapticTick();
        hideController();
        if (lockedOverlay != null) lockedOverlay.setVisibility(View.VISIBLE);
    }

    private void unlockControls() {
        isControlsLocked = false;
        hapticTick();
        if (lockedOverlay != null) lockedOverlay.setVisibility(View.GONE);
        showController();
    }

    private void showController() {
        if (isActivityDestroyed || isInPictureInPictureMode() || isControlsLocked) return;
        customControllerOverlay.setVisibility(View.VISIBLE);
        customControllerOverlay.animate().alpha(1.0f).setDuration(200).withEndAction(() -> {
            isControllerVisible = true;
            scheduleControllerHide();
        }).start();
        hideSystemBars();
    }

    private void hideController() {
        if (isActivityDestroyed) return;
        cancelControllerHide();
        customControllerOverlay.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
            customControllerOverlay.setVisibility(View.GONE);
            isControllerVisible = false;
        }).start();
        hideSystemBars();
    }

    private void scheduleControllerHide() {
        cancelControllerHide();
        if (player != null && player.isPlaying() && !isUserSeeking) {
            mainHandler.postDelayed(hideRunnable, CONTROLLER_HIDE_DELAY_MS);
        }
    }

    private void cancelControllerHide() { mainHandler.removeCallbacks(hideRunnable); }
    private final Runnable hideRunnable = this::hideController;

    private void scheduleRetry(long delayMs, long positionMs) {
        cancelPendingRetry();
        final String retryUrl = currentStreamUrl;
        final String quality = btnQuality != null ? btnQuality.getText().toString() : "Auto";
        final long generation = playerGeneration;
        pendingRetryRunnable = () -> {
            pendingRetryRunnable = null;
            // Guard against a retry firing after the user has already left the
            // screen (activity stopped) or the player generation changed. This
            // avoids a queued retry silently spinning up a brand new ExoPlayer
            // (with playWhenReady=true) after the user believes the video is
            // closed, which would otherwise resume audio in the background.
            if (isActivityDestroyed || isFinishing() || userPaused || retryUrl == null || generation != playerGeneration) return;
            if (!networkAvailable) {
                scheduleRetry(3000L, positionMs);
                return;
            }
            initializePlayer(retryUrl, quality, positionMs);
        };
        mainHandler.postDelayed(pendingRetryRunnable, delayMs);
    }

    private void cancelPendingRetry() {
        if (pendingRetryRunnable != null) {
            mainHandler.removeCallbacks(pendingRetryRunnable);
            pendingRetryRunnable = null;
        }
    }

    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkAvailable = hasUsableNetwork();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    mainHandler.post(() -> {
                        networkAvailable = true;
                        if (currentState == UIState.ERROR && !userPaused && currentStreamUrl != null) retryPlayback();
                        else if (currentState == UIState.BUFFERING && !userPaused && player != null) player.play();
                    });
                }
                @Override public void onLost(Network network) {
                    mainHandler.post(() -> networkAvailable = hasUsableNetwork());
                }
            };
            try { connectivityManager.registerDefaultNetworkCallback(networkCallback); } catch (Exception e) { /* no-op */ }
        }
    }

    private boolean hasUsableNetwork() {
        if (connectivityManager == null) return true;
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void unregisterNetworkMonitoring() {
        if (connectivityManager != null && networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
            networkCallback = null;
        }
    }

    private void hapticTick() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK));
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(20);
        } catch (Exception ignored) {}
    }

    private void retryPlayback() {
        userPaused = false;
        retryCount = 0;
        sourceRecoveryCount = 0;
        cancelPendingRetry();
        long position = player != null ? Math.max(0, player.getCurrentPosition()) : 0L;
        if (masterStreamUrl != null) {
            initializePlayer(masterStreamUrl, btnQuality.getText().toString(), position);
        } else if (currentVidKey != null) {
            pendingRecoveryPositionMs = position;
            fetchStream(currentVidKey);
        }
    }

    private long safeDuration(long duration) { return (duration == C.TIME_UNSET || duration < 0) ? 0 : duration; }

    private String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long totalSecs = millis / 1000;
        long hrs = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        return hrs > 0 ? String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs) : String.format(Locale.US, "%02d:%02d", mins, secs);
    }

    private void openInExternalPlayer() {
        if (currentStreamUrl == null || currentStreamUrl.isEmpty()) { showCustomToast(getString(R.string.no_video_url)); return; }
        long pos = player != null ? player.getCurrentPosition() : 0;
        releasePlayer();
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(currentStreamUrl), "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String title = tvMovieTitle.getText().toString();
            intent.putExtra("title", title);
            intent.putExtra("position", (int)pos);
            if (!subtitles.isEmpty() && subtitles.get(0).getUrl() != null) {
                intent.putExtra("subs", new String[]{subtitles.get(0).getUrl()});
                intent.putExtra("subtitle_url", subtitles.get(0).getUrl());
            }
            if (streamHeaders != null) {
                intent.putExtra("headers", new HashMap<>(streamHeaders));
                if (streamHeaders.containsKey("User-Agent")) intent.putExtra("User-Agent", streamHeaders.get("User-Agent"));
                if (streamHeaders.containsKey("Referer")) intent.putExtra("Referer", streamHeaders.get("Referer"));
            }
            intent.setPackage("com.mediaon.apt");
            if (getPackageManager().resolveActivity(intent, 0) != null) startActivity(intent);
            else {
                intent.setPackage(null);
                Intent chooser = Intent.createChooser(intent, getString(R.string.choose_external_player));
                if (chooser.resolveActivity(getPackageManager()) != null) startActivity(chooser);
                else showCustomToast(getString(R.string.no_external_player_installed));
            }
        } catch (Exception e) { showCustomToast(getString(R.string.external_player_error)); }
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try { enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build()); } 
            catch (Exception e) { /* no-op */ }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            hideController();
            loadingOverlay.setVisibility(View.GONE);
        } else {
            if (currentState != UIState.PLAYING) showController();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Do not enter PiP automatically. Leaving the player/app must pause
        // playback and release the audio focus instead of leaking audio in
        // the background. PiP remains available only through the explicit
        // PiP control.
        if (player != null && !isInPictureInPictureMode()) {
            wasPlayingBeforeStop = player.getPlayWhenReady() && !userPaused;
            player.setPlayWhenReady(false);
            player.pause();
            persistPlaybackPosition();
        }
    }

    private void releasePlayer() {
        cancelPendingRetry();
        if (player != null) {
            persistPlaybackPosition();
            if (roomController != null) roomController.onPlayerReleased();
            player.removeListener(playerListener);
            if (playerView != null) playerView.setPlayer(null);
            player.stop();
            player.release();
            player = null;
        }
        mainHandler.removeCallbacks(updateProgressRunnable);
        cancelControllerHide();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) {
            if (wasPlayingBeforeStop && !userPaused && !isInPictureInPictureMode()) {
                wasPlayingBeforeStop = false;
                player.play();
            }
            mainHandler.post(updateProgressRunnable);
            mainHandler.post(subtitleRenderRunnable);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (activeDialog != null) dismissActiveDialog();

        // IMPORTANT — this is the fix for audio continuing after leaving/closing PiP:
        //
        // onStop() only fires once the Activity (and any PiP window it had)
        // is truly gone from the screen:
        //   - the user swiped the PiP window away / tapped its close (X) button
        //   - the system evicted our PiP session in favor of another app's PiP
        //   - the user left the app entirely without ever entering PiP
        //
        // While the user is actually watching inside a live PiP window,
        // Android only calls onPause() here — onStop() is NOT invoked, so
        // playback correctly keeps going. That means there is no need (and it
        // is unsafe) to gate this pause behind isInPictureInPictureMode():
        // per Android's own docs, that flag is expected to have already
        // flipped to false by the time onStop() runs after a PiP dismissal,
        // but on some OEM builds (MIUI/Samsung/etc.) that ordering is not
        // reliably guaranteed. If it hasn't flipped yet, the old
        // "!isInPictureInPictureMode()" guard here would skip the pause
        // entirely, leaving the ExoPlayer instance — and its audio track —
        // running invisibly in the background even though the user closed
        // the video. Pausing unconditionally in onStop() removes that race:
        // any time the Activity is genuinely stopped, playback stops with it.
        if (player != null) {
            wasPlayingBeforeStop = player.getPlayWhenReady() && !userPaused;
            player.setPlayWhenReady(false);
            player.pause();
            persistPlaybackPosition();
        }
        mainHandler.removeCallbacks(subtitleRenderRunnable);
    }

    // ---- Remote control (Android TV / Fire TV) ------------------------------------------------
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && player != null && !isControlsLocked && !isInPictureInPictureMode()
                && (activeDialog == null || !activeDialog.isShowing())
                && TvUtils.isTv(this)
                && !(roomController != null && roomController.isGuestMode())) {
            int k = event.getKeyCode();
            boolean playing = player.getPlayWhenReady() && !userPaused;
            switch (k) {
                case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case android.view.KeyEvent.KEYCODE_HEADSETHOOK:
                    btnPlayPause.performClick();
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:
                    if (!playing) btnPlayPause.performClick();
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:
                    if (playing) btnPlayPause.performClick();
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                case android.view.KeyEvent.KEYCODE_MEDIA_NEXT:
                    seekRelative(SEEK_STEP_MS);
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_REWIND:
                case android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    seekRelative(-SEEK_STEP_MS);
                    return true;
                default:
                    break;
            }
            if (!isControllerVisible) {
                // Controls hidden: arrows seek, OK/Up/Down bring the controls back.
                switch (k) {
                    case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                        seekRelative(-SEEK_STEP_MS);
                        showController();
                        return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                        seekRelative(SEEK_STEP_MS);
                        showController();
                        return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
                    case android.view.KeyEvent.KEYCODE_ENTER:
                    case android.view.KeyEvent.KEYCODE_NUMPAD_ENTER:
                    case android.view.KeyEvent.KEYCODE_DPAD_UP:
                    case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                        showController();
                        btnPlayPause.post(() -> btnPlayPause.requestFocus());
                        return true;
                    default:
                        break;
                }
            } else {
                scheduleControllerHide(); // keep the controls up while the user navigates them
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (activeDialog != null && activeDialog.isShowing()) dismissActiveDialog();
        else exitPlayer();
    }

    @Override
    protected void onDestroy() {

        isActivityDestroyed = true;
        if (roomController != null) roomController.onDestroy(isFinishing());

        providerSearchActive.set(false);
        cancelPendingRetry();
        unregisterNetworkMonitoring();

        if (activeNetworkCall != null) {
            activeNetworkCall.cancel();
        }

        if (activeStreamFetchTask != null) {
            try { activeStreamFetchTask.cancel(true); } catch (Exception ignored) {}
            activeStreamFetchTask = null;
        }

        if (streamFetchExecutor != null) {
            streamFetchExecutor.shutdownNow();
        }

        if (providerExecutor != null) {
            providerExecutor.shutdownNow();
        }

        if (subtitleExecutor != null) {
            subtitleLookupInFlight = false;
            subtitleExecutor.shutdownNow();
        }

        mainHandler.removeCallbacksAndMessages(null);

        releasePlayer();

        super.onDestroy();
    }
}
