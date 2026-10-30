package com.stream.master;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Glue between the player screen and the watch-party room:
 *  - host: publishes play / pause / seek (plus a heartbeat) to the room;
 *  - guest: follows the host, and cannot control playback;
 *  - both: room badge, chat, member list, invite sharing.
 * Every guest resolves their OWN stream, quality and subtitles through the normal player flow;
 * only "playing / position" is shared.
 */
final class RoomController implements RoomClient.Listener {

    private static final String[] STRING_EXTRAS = {
            "VID", "TITLE", "IMG", "DESC", "RATING", "YEAR", "QUALITY",
            "GENRE", "DIRECTOR", "DURATION", "LANGUAGE", "TMDB_ID", "TYPE"
    };

    private static final long HEARTBEAT_MS = 5000;

    private final PlayerActivity activity;
    private final RoomClient client = RoomClient.get();
    private final Handler main = new Handler(Looper.getMainLooper());

    private ExoPlayer player;
    private Player.Listener playerListener;
    private boolean firstSyncDone;
    private boolean leaving;
    private long lastBlockedToastAt;

    private static final String PILL_POS_X_KEY = "room_pill_x";
    private static final String PILL_POS_Y_KEY = "room_pill_y";

    private TextView pill;
    private TextView ticker;
    private LinearLayout chatList;
    private ScrollView chatScroll;
    private ImageButton btnMic;
    private VoiceChatManager voice;
    private boolean voiceDucked;

    RoomController(PlayerActivity activity) {
        this.activity = activity;
    }

    // ------------------------------------------------------------------ lifecycle

    void onCreate(Intent intent) {
        buildOverlays();
        client.setListener(this);

        btnMic = activity.roomMicButton();
        if (btnMic != null) btnMic.setOnClickListener(v -> onMicButtonClicked());

        String code = intent == null
                ? null
                : RoomClient.parseCode(intent.getStringExtra("ROOM_CODE"));

        if (code != null) {
            String name = intent.getStringExtra("ROOM_NAME");

            if (name == null || name.trim().isEmpty()) {
                name = RoomClient.savedName(activity);
            }

            client.connect(activity, code, name);
        }

        updatePill();
        updateMicButton();
    }

    void onDestroy(boolean finishing) {
        main.removeCallbacksAndMessages(null);

        if (client.isActive() && finishing) {
            leaving = true;
            client.disconnect(true);
        }

        client.setListener(null);

        if (voice != null) { voice.release(); voice = null; }
        onVoiceDuckChanged(false); // never leave a movie player ducked after leaving the room
    }

    void onPlayerCreated(ExoPlayer p) {
        onPlayerReleased();

        player = p;
        firstSyncDone = false;
        // A freshly built ExoPlayer starts at full volume regardless of whether voice chat is
        // already active (e.g. a stream retry or quality switch mid-call) - resync it immediately
        // instead of waiting for the next WebRTC start/stop event, which may not come for a while
        // if the voice session was already continuously active.
        onVoiceDuckChanged(voiceDucked);

        playerListener = new Player.Listener() {

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                publishSoon();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    onReady();
                }

                if (state == Player.STATE_ENDED) {
                    publishSoon();
                }
            }

            @Override
            public void onPositionDiscontinuity(
                    @NonNull Player.PositionInfo oldPosition,
                    @NonNull Player.PositionInfo newPosition,
                    int reason
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK
                        || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    publishSoon();
                }
            }
        };

        player.addListener(playerListener);

        main.removeCallbacks(heartbeat);
        main.postDelayed(heartbeat, HEARTBEAT_MS);
    }

    void onPlayerReleased() {
        main.removeCallbacks(heartbeat);
        main.removeCallbacks(publishRunnable);

        if (player != null && playerListener != null) {
            player.removeListener(playerListener);
        }

        player = null;
        playerListener = null;
    }

    // ------------------------------------------------------------------ roles

    boolean isGuestMode() {
        return client.isActive() && !client.isHost();
    }

    /**
     * Called before any user action that changes playback.
     * Guests may not control playback.
     */
    boolean blocksControl() {
        if (!isGuestMode()) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();

        if (now - lastBlockedToastAt > 2000) {
            lastBlockedToastAt = now;

            activity.roomToast(
                    activity.getString(R.string.room_only_host)
            );
        }

        return true;
    }

    // ------------------------------------------------------------------ host: publish

    private final Runnable publishRunnable = this::publishNow;

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            publishNow();
            main.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private void publishSoon() {
        if (!client.isActive() || !client.isHost()) {
            return;
        }

        main.removeCallbacks(publishRunnable);

        main.postDelayed(
                publishRunnable,
                120
        );
    }

    private void publishNow() {
        if (!client.isActive()
                || !client.isHost()
                || player == null) {
            return;
        }

        boolean playing =
                player.getPlayWhenReady()
                        && player.getPlaybackState() != Player.STATE_ENDED;

        client.sendState(
                playing,
                player.getCurrentPosition(),
                player.getPlaybackParameters().speed
        );
    }

    // ------------------------------------------------------------------ guest: follow

    private void onReady() {
        if (firstSyncDone) {
            return;
        }

        firstSyncDone = true;

        if (isGuestMode()) {
            applyRemote(client.lastState());
        } else {
            publishSoon();
        }
    }

    private void applyRemote(RoomClient.RemoteState state) {
        if (state == null
                || player == null
                || !isGuestMode()) {
            return;
        }

        activity.applyRemotePlayback(
                state.playing,
                state.expectedPositionMs(client.serverNowMs()),
                state.rate
        );
    }

    // ------------------------------------------------------------------ RoomClient.Listener

    @Override
    public void onWelcome() {
        updatePill();

        if (voice != null) voice.release();
        voice = new VoiceChatManager(activity, client, client.myId(), this::onVoiceDuckChanged);
        if (!voice.isAvailable()) activity.roomToast(activity.getString(R.string.room_voice_unavailable));
        voice.syncMembers(client.members());
        updateMicButton();
        refreshVoiceDuck();
        fetchTurnCredentials();

        if (client.isHost()) {
            publishSoon();
        }
    }

    @Override
    public void onMembersChanged() {
        updatePill();
        if (voice != null) voice.syncMembers(client.members());
        updateMicButton();
        refreshVoiceDuck();
    }

    @Override
    public void onHostChanged(boolean isMe) {
        updatePill();

        activity.roomToast(
                activity.getString(
                        isMe
                                ? R.string.room_you_are_host
                                : R.string.room_host_changed
                )
        );

        if (isMe) {
            publishSoon();
        }
    }

    @Override
    public void onState(
            @NonNull RoomClient.RemoteState state
    ) {
        if (!firstSyncDone
                && (player == null
                || player.getPlaybackState() != Player.STATE_READY)) {
            return;
        }

        applyRemote(state);
    }

    @Override
    public void onChat(
            @NonNull RoomClient.ChatMessage m
    ) {
        if (chatList != null) {
            appendChatRow(m);
            scrollChatToEnd();
        } else if (!m.fromId.equals(client.myId())) {
            showTicker(
                    m.name + ": " + m.text
            );
        }
    }

    @Override
    public void onVoiceState(@NonNull String memberId) {
        if (memberId.equals(client.myId())) {
            RoomClient.Member me = findMember(memberId);
            if (voice != null && me != null) voice.setMicEnabled(me.mic && !me.mutedByHost);
            updateMicButton();
        }
        refreshVoiceDuck(); // any member's mic changing can start/stop the ducking
    }

    @Override
    public void onSignal(@NonNull String fromId, @NonNull JSONObject data) {
        if (voice != null) voice.onSignal(fromId, data);
    }

    @Override
    public void onConnection(boolean connected) {
        updatePill();
    }

    @Override
    public void onServerError(
            @NonNull String code
    ) {
        if ("rate".equals(code)) {
            activity.roomToast(
                    activity.getString(
                            R.string.room_slow_down
                    )
            );
        } else if ("muted_by_host".equals(code)) {
            if (voice != null) voice.setMicEnabled(false);
            updateMicButton();
            refreshVoiceDuck();
            activity.roomToast(activity.getString(R.string.room_muted_by_host_toast));
        }
    }

    @Override
    public void onClosed(
            @NonNull String reason
    ) {
        updatePill();

        if (voice != null) { voice.release(); voice = null; }
        updateMicButton();
        refreshVoiceDuck();

        if (leaving) {
            return;
        }

        int res;
        boolean leavePlayer = false;

        switch (reason) {
            case "kicked":
                res = R.string.room_kicked;
                leavePlayer = true;
                break;

            case "not_found":
                res = R.string.room_not_found;
                leavePlayer = true;
                break;

            case "full":
                res = R.string.room_full;
                leavePlayer = true;
                break;

            case "connection_lost":
                res = R.string.room_connection_lost;
                break;

            case "replaced":
                // Another connection of this same device took the seat over. The room itself is fine,
                // so don't stay silently disconnected: tell the user.
                activity.roomToast(activity.getString(R.string.room_connection_lost));
                return;

            default:
                res = R.string.room_ended;
                break;
        }

        activity.roomToast(
                activity.getString(res)
        );

        if (leavePlayer) {
            activity.finish();
        }
    }

    // ------------------------------------------------------------------ overlays

    private void buildOverlays() {
        ViewGroup content =
                activity.findViewById(android.R.id.content);

        if (content == null
                || content.getChildCount() == 0
                || !(content.getChildAt(0) instanceof FrameLayout)) {
            return;
        }

        FrameLayout root =
                (FrameLayout) content.getChildAt(0);

        pill = new TextView(activity);

        pill.setTextColor(
                activity.getColor(R.color.comic_peach)
        );

        pill.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                12
        );

        pill.setTypeface(
                pill.getTypeface(),
                Typeface.BOLD
        );

        pill.setPadding(
                dp(12),
                dp(6),
                dp(12),
                dp(6)
        );

        pill.setBackground(
                rounded(
                        Color.argb(200, 20, 17, 14),
                        16
                )
        );

        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setVisibility(View.GONE);
        pill.setOnClickListener(v -> showMenu());

        FrameLayout.LayoutParams plp =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        android.content.SharedPreferences pillPrefs =
                activity.getSharedPreferences(Constants.PREF_NAME, android.content.Context.MODE_PRIVATE);
        int savedPillX = pillPrefs.getInt(PILL_POS_X_KEY, Integer.MIN_VALUE);
        int savedPillY = pillPrefs.getInt(PILL_POS_Y_KEY, Integer.MIN_VALUE);

        if (savedPillX != Integer.MIN_VALUE && savedPillY != Integer.MIN_VALUE) {
            // The user has dragged it before - restore that exact spot (absolute top-left
            // offset), instead of the default centered position below.
            plp.gravity = Gravity.TOP | Gravity.START;
            plp.leftMargin = savedPillX;
            plp.topMargin = savedPillY;
        } else {
            plp.gravity =
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL;

            plp.setMargins(
                    dp(12),
                    dp(64),
                    dp(12),
                    0
            );
        }

        root.addView(pill, plp);
        makePillDraggable(pill, root, pillPrefs);

        ticker = new TextView(activity);

        ticker.setTextColor(Color.WHITE);

        ticker.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                14
        );

        ticker.setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
        );

        ticker.setMaxWidth(
                (int) (
                        activity
                                .getResources()
                                .getDisplayMetrics()
                                .widthPixels * 0.6f
                )
        );

        ticker.setBackground(
                rounded(
                        Color.argb(190, 20, 17, 14),
                        14
                )
        );

        // FIX:
        // TEXT_DIRECTION_ANYRTL غير موجود في بعض إصدارات SDK.
        // FIRST_STRONG يدعم العربية والإنجليزية تلقائياً.
        ticker.setTextDirection(
                View.TEXT_DIRECTION_FIRST_STRONG
        );

        ticker.setVisibility(View.GONE);

        FrameLayout.LayoutParams tlp =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        tlp.gravity =
                Gravity.BOTTOM | Gravity.START;

        tlp.setMargins(
                dp(16),
                0,
                dp(16),
                dp(96)
        );

        root.addView(ticker, tlp);
    }

    /**
     * Lets the user drag the room-code pill anywhere on screen (so it never sits over something
     * they're trying to watch) and remembers where they left it, in the standard "distinguish a
     * tap from a drag by total finger movement" pattern. A tap (movement under TAP_SLOP_DP) still
     * opens showMenu() exactly as before - the OnClickListener set on `pill` is never removed,
     * it's just that consuming ACTION_DOWN here (returning true) means it will only actually fire
     * via performClick() below, not automatically.
     */
    private static final int PILL_DRAG_TAP_SLOP_DP = 8;

    private void makePillDraggable(TextView pillView, FrameLayout root, android.content.SharedPreferences prefs) {
        final float[] touchStart = new float[2];      // raw touch coords at ACTION_DOWN
        final int[] marginStart = new int[2];          // pill's left/top margin at ACTION_DOWN
        final boolean[] dragging = {false};
        final int slopPx = dp(PILL_DRAG_TAP_SLOP_DP);

        pillView.setOnTouchListener((v, event) -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();

            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: {
                    touchStart[0] = event.getRawX();
                    touchStart[1] = event.getRawY();
                    // Normalize to an absolute TOP|START position the first time this view is
                    // dragged, even if it's still showing the default CENTER_HORIZONTAL layout -
                    // otherwise the drag math below would be relative to the wrong anchor.
                    if ((lp.gravity & Gravity.START) == 0) {
                        lp.gravity = Gravity.TOP | Gravity.START;
                        lp.leftMargin = v.getLeft();
                        lp.topMargin = v.getTop();
                        v.setLayoutParams(lp);
                    }
                    marginStart[0] = lp.leftMargin;
                    marginStart[1] = lp.topMargin;
                    dragging[0] = false;
                    return true;
                }
                case android.view.MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - touchStart[0];
                    float dy = event.getRawY() - touchStart[1];
                    if (!dragging[0] && Math.hypot(dx, dy) > slopPx) dragging[0] = true;
                    if (!dragging[0]) return true;

                    int maxLeft = Math.max(0, root.getWidth() - v.getWidth());
                    int maxTop = Math.max(0, root.getHeight() - v.getHeight());
                    lp.leftMargin = clamp((int) (marginStart[0] + dx), 0, maxLeft);
                    lp.topMargin = clamp((int) (marginStart[1] + dy), 0, maxTop);
                    v.setLayoutParams(lp);
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: {
                    if (dragging[0]) {
                        prefs.edit()
                                .putInt(PILL_POS_X_KEY, lp.leftMargin)
                                .putInt(PILL_POS_Y_KEY, lp.topMargin)
                                .apply();
                    } else {
                        v.performClick(); // a genuine tap - still opens showMenu() as before
                    }
                    dragging[0] = false;
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updatePill() {
        if (pill == null) {
            return;
        }

        if (!client.isActive()
                || client.code() == null) {

            pill.setVisibility(View.GONE);
            return;
        }

        String label =
                String.format(
                        Locale.getDefault(),
                        activity.getString(
                                R.string.room_pill_fmt
                        ),
                        client.code(),
                        Math.max(
                                1,
                                client.members().size()
                        )
                );

        if (!client.isConnected()) {
            label = label + activity.getString(R.string.room_reconnecting_suffix);
        }

        pill.setText(label);
        pill.setVisibility(View.VISIBLE);
    }

    private final Runnable hideTicker =
            () -> {
                if (ticker != null) {
                    ticker.setVisibility(View.GONE);
                }
            };

    private void showTicker(String text) {
        if (ticker == null) {
            return;
        }

        ticker.setText(text);
        ticker.setVisibility(View.VISIBLE);

        main.removeCallbacks(hideTicker);

        main.postDelayed(
                hideTicker,
                6000
        );
    }

    // ------------------------------------------------------------------ menu

    void showMenu() {
        List<String> options = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();

        final boolean active =
                client.isActive();

        if (!active) {
            options.add(
                    activity.getString(
                            R.string.room_create
                    )
            );

            icons.add(
                    R.drawable.ic_group
            );

            activity.showRoomOptions(
                    activity.getString(
                            R.string.room_title
                    ),
                    options,
                    toArray(icons),
                    which -> startHosting()
            );

            return;
        }

        options.add(
                activity.getString(
                        R.string.room_chat
                )
        );
        icons.add(R.drawable.ic_chat);

        options.add(
                activity.getString(
                        R.string.room_share
                )
        );
        icons.add(R.drawable.ic_share);

        options.add(
                String.format(
                        Locale.getDefault(),
                        activity.getString(
                                R.string.room_members_fmt
                        ),
                        Math.max(
                                1,
                                client.members().size()
                        )
                )
        );
        icons.add(R.drawable.ic_group);

        final boolean host =
                client.isHost();

        options.add(
                activity.getString(
                        host
                                ? R.string.room_end
                                : R.string.room_leave
                )
        );
        icons.add(
                R.drawable.ic_close_material
        );

        activity.showRoomOptions(
                activity.getString(
                        R.string.room_title
                ),
                options,
                toArray(icons),
                which -> {
                    switch (which) {
                        case 0:
                            showChat();
                            break;

                        case 1:
                            shareInvite(client.code());
                            break;

                        case 2:
                            showMembers();
                            break;

                        case 3:
                            leaveOrEnd(host);
                            break;

                        default:
                            break;
                    }
                }
        );
    }

    private static int[] toArray(
            List<Integer> list
    ) {
        int[] a =
                new int[list.size()];

        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }

        return a;
    }

    private void leaveOrEnd(
            boolean host
    ) {
        leaving = true;

        if (host) {
            client.endRoom();
        }

        main.postDelayed(
                () -> {
                    client.disconnect(!host);
                    leaving = false;
                    updatePill();
                },
                host ? 300 : 0
        );
    }

    // ------------------------------------------------------------------ hosting

    private void startHosting() {
        JSONObject media =
                mediaFromIntent(
                        activity.getIntent()
                );

        activity.roomToast(
                activity.getString(
                        R.string.room_creating
                )
        );

        RoomClient.createRoom(
                activity,
                media,
                (code, error) -> {
                    if (code == null) {
                        activity.roomToast(
                                activity.getString(
                                        "too_many_rooms".equals(error)
                                                ? R.string.room_too_many
                                                : R.string.room_create_failed
                                )
                        );

                        return;
                    }

                    String name =
                            RoomClient.savedName(activity);

                    if (name.isEmpty()) {
                        name =
                                Build.MODEL == null
                                        ? ""
                                        : Build.MODEL;
                    }

                    client.connect(
                            activity,
                            code,
                            name
                    );

                    client.setListener(this);

                    updatePill();

                    shareInvite(code);
                }
        );
    }

    private static JSONObject mediaFromIntent(
            Intent intent
    ) {
        JSONObject media =
                new JSONObject();

        try {
            for (String key : STRING_EXTRAS) {
                String v =
                        intent.getStringExtra(key);

                if (v != null) {
                    media.put(key, v);
                }
            }

            if (!media.has("TYPE")) {
                media.put(
                        "TYPE",
                        "movie"
                );
            }

            if (intent.hasExtra("SEASON")) {
                media.put(
                        "SEASON",
                        intent.getIntExtra(
                                "SEASON",
                                0
                        )
                );
            }

            if (intent.hasExtra("EPISODE")) {
                media.put(
                        "EPISODE",
                        intent.getIntExtra(
                                "EPISODE",
                                0
                        )
                );
            }

        } catch (JSONException ignored) {
        }

        return media;
    }

    private void shareInvite(
            String code
    ) {
        if (code == null) {
            return;
        }

        Intent send =
                new Intent(
                        Intent.ACTION_SEND
                );

        send.setType("text/plain");

        send.putExtra(
                Intent.EXTRA_TEXT,
                activity.getString(
                        R.string.room_share_text,
                        code,
                        RoomClient.shareLink(code)
                )
        );

        try {
            activity.startActivity(
                    Intent.createChooser(
                            send,
                            activity.getString(
                                    R.string.room_share
                            )
                    )
            );
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ members

    private void showMembers() {
        final List<RoomClient.Member> list =
                client.members();

        List<String> names =
                new ArrayList<>();

        List<Integer> rowIcons =
                new ArrayList<>();

        for (RoomClient.Member m : list) {
            StringBuilder sb =
                    new StringBuilder(m.name);

            if (m.id.equals(client.myId())) {
                sb.append("  ·  ")
                        .append(
                                activity.getString(
                                        R.string.room_you_tag
                                )
                        );
            }

            if (!m.online) {
                sb.append(
                        activity.getString(
                                R.string.room_reconnecting_suffix
                        )
                );
            }

            names.add(sb.toString());

            // One icon per row in this design system: host identity takes priority over mic state,
            // since who's hosting matters more at a glance than whether they're currently speaking.
            rowIcons.add(
                    m.host
                            ? R.drawable.ic_host_crown
                            : (m.mic && !m.mutedByHost ? R.drawable.ic_mic : R.drawable.ic_mic_off)
            );
        }

        final boolean iAmHost =
                client.isHost();

        activity.showRoomOptions(
                String.format(
                        Locale.getDefault(),
                        activity.getString(
                                R.string.room_members_fmt
                        ),
                        list.size()
                ),
                names,
                toArray(rowIcons),
                which -> {
                    if (!iAmHost
                            || which < 0
                            || which >= list.size()) {
                        return;
                    }

                    final RoomClient.Member target =
                            list.get(which);

                    if (target.id.equals(
                            client.myId()
                    )) {
                        return;
                    }

                    showModerationMenu(target);
                }
        );
    }

    /** Host-only contextual menu for one participant: Mute/Unmute (whichever applies) and Kick. */
    private void showModerationMenu(RoomClient.Member target) {
        List<String> actions = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();

        actions.add(
                activity.getString(
                        target.mutedByHost ? R.string.room_unmute : R.string.room_mute
                )
        );
        icons.add(target.mutedByHost ? R.drawable.ic_mic : R.drawable.ic_mic_off);

        actions.add(activity.getString(R.string.room_kick));
        icons.add(R.drawable.ic_close_material);

        activity.showRoomOptions(
                String.format(
                        Locale.getDefault(),
                        activity.getString(R.string.room_participant_actions_fmt),
                        target.name
                ),
                actions,
                toArray(icons),
                which -> {
                    if (which == 0) {
                        if (target.mutedByHost) client.unmuteMember(target.id);
                        else client.muteMember(target.id);
                        return;
                    }

                    if (which != 1) return;

                    AlertDialog dialog =
                            new AlertDialog.Builder(
                                    activity
                            )
                                    .setMessage(
                                            String.format(
                                                    Locale.getDefault(),
                                                    activity.getString(
                                                            R.string.room_kick_confirm_fmt
                                                    ),
                                                    target.name
                                            )
                                    )
                                    .setPositiveButton(
                                            R.string.room_kick,
                                            (d, w) ->
                                                    client.kick(
                                                            target.id
                                                    )
                                    )
                                    .setNegativeButton(
                                            R.string.room_cancel,
                                            null
                                    )
                                    .create();

                    activity.showRoomDialog(
                            dialog
                    );
                }
        );
    }

    // ------------------------------------------------------------------ voice

    // How much the movie is ducked while any real voice-chat audio (ours or a remote peer's) is
    // actually flowing. Not near-silent (people still want to glance at the screen while talking)
    // and not barely-reduced (the point is you can hear each other). Tune this single constant if
    // the balance ever needs adjusting - nothing else needs to change.
    private static final float VOICE_DUCK_VOLUME = 0.50f;
    private static final float VOICE_FULL_VOLUME = 1.0f;

    /**
     * Called directly by VoiceChatManager's AudioSessionGuard the instant a real WebRTC audio
     * session starts or stops - see VoiceChatManager's class doc ("REVISION 2") for why this
     * replaced relying on Android's automatic audio-focus ducking. This is the ONLY thing that
     * changes the movie's volume for voice chat; PlayerActivity builds ExoPlayer with
     * handleAudioFocus=false specifically so nothing else can fight over it.
     */
    private void onVoiceDuckChanged(boolean ducked) {
        voiceDucked = ducked;
        if (player == null) return;
        try { player.setVolume(ducked ? VOICE_DUCK_VOLUME : VOICE_FULL_VOLUME); } catch (Exception ignored) { }
    }

    /**
     * The movie is ducked ONLY while somebody in the room actually has their mic open (server-confirmed
     * voice-state of every online, non-host-muted member, including me). It used to follow libwebrtc's
     * native playout/record start-stop callbacks, but WebRTC keeps its playout running for as long as a
     * peer connection exists - even with every mic off - so the movie stayed ducked (and, depending on
     * the device and who connected first, sometimes didn't) for the whole call. Signaling state is
     * deterministic: mic open -> duck, last mic closed -> back to full volume, immediately.
     */
    private void refreshVoiceDuck() {
        boolean talking = false;
        if (voice != null && client.isActive()) {
            for (RoomClient.Member m : client.members()) {
                if (m.online && m.mic && !m.mutedByHost) { talking = true; break; }
            }
        }
        if (talking != voiceDucked) onVoiceDuckChanged(talking);
    }

    /**
     * Fetches short-lived TURN credentials (see /api/turn-credentials' own doc) and, if any came
     * back, upgrades the current VoiceChatManager's ICE server list in place - see
     * VoiceChatManager.addTurnServer()'s doc for why this is safe to do after connections may
     * already exist. Silent no-op on any failure or if the server hasn't configured TURN yet
     * (empty urls is the documented "not configured" response, not an error) - voice chat must
     * keep working STUN-only exactly as before either way.
     */
    private void fetchTurnCredentials() {
        final VoiceChatManager target = voice; // capture: `voice` may be replaced/nulled by the time this returns
        try {
            String base = Constants.BASE_URL.endsWith("/") ? Constants.BASE_URL : Constants.BASE_URL + "/";
            ApiService api = new Retrofit.Builder()
                    .baseUrl(base)
                    .client(SecureNetworkClient.getClient(activity))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);

            api.getTurnCredentials().enqueue(new Callback<TurnCredentialsResponse>() {
                @Override public void onResponse(Call<TurnCredentialsResponse> call, Response<TurnCredentialsResponse> response) {
                    try {
                        TurnCredentialsResponse body = response.body();
                        if (body == null || !body.hasTurnServer() || target != voice || voice == null) return;
                        voice.addTurnServer(body.data.urls, body.data.username, body.data.credential);
                    } catch (Throwable ignored) {
                        // Never let an optional TURN upgrade crash the room - STUN-only keeps working.
                    }
                }
                @Override public void onFailure(Call<TurnCredentialsResponse> call, Throwable t) { /* STUN-only remains fine */ }
            });
        } catch (Exception ignored) { /* STUN-only remains fine */ }
    }

    private RoomClient.Member findMember(String id) {
        if (id == null) return null;
        for (RoomClient.Member m : client.members()) {
            if (m.id.equals(id)) return m;
        }
        return null;
    }

    private void onMicButtonClicked() {
        if (!client.isActive()) return;

        activity.requestMicPermission(granted -> {
            if (!granted) {
                activity.roomToast(activity.getString(R.string.room_mic_permission_denied));
                return;
            }
            toggleMic();
        });
    }

    private void toggleMic() {
        if (voice == null || !voice.isAvailable()) {
            activity.roomToast(activity.getString(R.string.room_voice_unavailable));
            return;
        }

        RoomClient.Member me = findMember(client.myId());
        if (me != null && me.mutedByHost) {
            activity.roomToast(activity.getString(R.string.room_muted_by_host_toast));
            return;
        }

        // The mic doesn't actually flip until the server echoes voice-state back (onVoiceState);
        // this keeps the client thin and the server authoritative, consistent with every other
        // room action.
        client.setMic(!voice.isMicEnabled());
    }

    private void updateMicButton() {
        if (btnMic == null) return;

        boolean active = client.isActive();
        btnMic.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!active) return;

        boolean on = voice != null && voice.isMicEnabled();
        btnMic.setImageResource(on ? R.drawable.ic_mic : R.drawable.ic_mic_off);
        btnMic.setSelected(on);

        RoomClient.Member me = findMember(client.myId());
        boolean lockedOff = me != null && me.mutedByHost;
        // Left tappable (not disabled) even while host-muted so a tap still explains why via toggleMic()'s toast.
        btnMic.setAlpha(lockedOff ? 0.45f : 1f);
    }

    // ------------------------------------------------------------------ chat

    private void showChat() {
        LinearLayout root =
                new LinearLayout(activity);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                Color.rgb(28, 24, 21)
        );

        root.setPadding(
                dp(16),
                dp(10),
                dp(16),
                dp(12)
        );

        LinearLayout header =
                new LinearLayout(activity);

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView title =
                new TextView(activity);

        title.setText(
                R.string.room_chat
        );

        title.setTextColor(
                activity.getColor(
                        R.color.text_primary
                )
        );

        title.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                20
        );

        title.setTypeface(
                title.getTypeface(),
                Typeface.BOLD
        );

        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        ImageButton close =
                new ImageButton(activity);

        close.setImageResource(
                R.drawable.ic_close_material
        );

        close.setColorFilter(
                activity.getColor(
                        R.color.text_secondary
                ),
                PorterDuff.Mode.SRC_IN
        );

        close.setBackgroundResource(
                R.drawable.bg_icon_ripple
        );

        close.setContentDescription(
                activity.getString(
                        R.string.back
                )
        );

        close.setOnClickListener(
                v -> activity.closeRoomSheet()
        );

        header.addView(
                close,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );

        root.addView(
                header,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56)
                )
        );

        chatScroll =
                new ScrollView(activity);

        chatList =
                new LinearLayout(activity);

        chatList.setOrientation(
                LinearLayout.VERTICAL
        );

        chatScroll.addView(
                chatList,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(
                chatScroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        for (RoomClient.ChatMessage m :
                client.chat()) {

            appendChatRow(m);
        }

        LinearLayout inputRow =
                new LinearLayout(activity);

        inputRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        final EditText input =
                new EditText(activity);

        input.setHint(
                R.string.room_chat_hint
        );

        input.setHintTextColor(
                activity.getColor(
                        R.color.text_muted
                )
        );

        input.setTextColor(
                activity.getColor(
                        R.color.text_primary
                )
        );

        input.setSingleLine(true);

        input.setImeOptions(
                EditorInfo.IME_ACTION_SEND
        );

        input.setMaxEms(40);

        inputRow.addView(
                input,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        ImageButton send =
                new ImageButton(activity);

        send.setImageResource(
                R.drawable.ic_send
        );

        send.setColorFilter(
                activity.getColor(
                        R.color.comic_peach
                ),
                PorterDuff.Mode.SRC_IN
        );

        send.setBackgroundResource(
                R.drawable.bg_icon_ripple
        );

        send.setContentDescription(
                activity.getString(
                        R.string.room_send
                )
        );

        inputRow.addView(
                send,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );

        root.addView(
                inputRow,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        final Runnable submit =
                () -> {
                    String text =
                            input.getText()
                                    .toString()
                                    .trim();

                    if (text.isEmpty()) {
                        return;
                    }

                    client.sendChat(text);

                    input.setText("");
                };

        send.setOnClickListener(
                v -> submit.run()
        );

        input.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId ==
                            EditorInfo.IME_ACTION_SEND) {

                        submit.run();
                        return true;
                    }

                    return false;
                }
        );

        activity.showRoomSheet(
                root,
                () -> {
                    chatList = null;
                    chatScroll = null;
                }
        );

        scrollChatToEnd();
    }

    private void appendChatRow(
            RoomClient.ChatMessage m
    ) {
        if (chatList == null) {
            return;
        }

        SpannableStringBuilder sb =
                new SpannableStringBuilder();

        boolean mine =
                m.fromId.equals(
                        client.myId()
                );

        String name =
                mine
                        ? activity.getString(
                                R.string.room_you_tag
                        )
                        : m.name;

        sb.append(name)
                .append(": ");

        sb.setSpan(
                new StyleSpan(
                        Typeface.BOLD
                ),
                0,
                sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        sb.setSpan(
                new ForegroundColorSpan(
                        activity.getColor(
                                mine
                                        ? R.color.text_secondary
                                        : R.color.comic_peach
                        )
                ),
                0,
                sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        sb.append(m.text);

        TextView row =
                new TextView(activity);

        row.setText(sb);

        row.setTextColor(
                activity.getColor(
                        R.color.text_primary
                )
        );

        row.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                15
        );

        // FIX:
        // TEXT_DIRECTION_ANYRTL غير موجود في بعض إصدارات SDK.
        // FIRST_STRONG يحدد الاتجاه حسب أول حرف قوي.
        row.setTextDirection(
                View.TEXT_DIRECTION_FIRST_STRONG
        );

        row.setPadding(
                0,
                dp(5),
                0,
                dp(5)
        );

        chatList.addView(row);
    }

    private void scrollChatToEnd() {
        if (chatScroll != null) {
            chatScroll.post(
                    () -> {
                        if (chatScroll != null) {
                            chatScroll.fullScroll(
                                    View.FOCUS_DOWN
                            );
                        }
                    }
            );
        }
    }

    // ------------------------------------------------------------------ helpers

    private int dp(int v) {
        return Math.round(
                v * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private GradientDrawable rounded(
            int color,
            int radiusDp
    ) {
        GradientDrawable d =
                new GradientDrawable();

        d.setColor(color);

        d.setCornerRadius(
                dp(radiusDp)
        );

        return d;
    }
}

