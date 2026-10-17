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

    private TextView pill;
    private TextView ticker;
    private LinearLayout chatList;
    private ScrollView chatScroll;
    private ImageButton btnMic;
    private VoiceChatManager voice;

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
    }

    void onPlayerCreated(ExoPlayer p) {
        onPlayerReleased();

        player = p;
        firstSyncDone = false;

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
                state.expectedPositionMs(client.serverNowMs())
        );
    }

    // ------------------------------------------------------------------ RoomClient.Listener

    @Override
    public void onWelcome() {
        updatePill();

        if (voice != null) voice.release();
        voice = new VoiceChatManager(activity, client, client.myId());
        if (!voice.isAvailable()) activity.roomToast(activity.getString(R.string.room_voice_unavailable));
        voice.syncMembers(client.members());
        updateMicButton();

        if (client.isHost()) {
            publishSoon();
        }
    }

    @Override
    public void onMembersChanged() {
        updatePill();
        if (voice != null) voice.syncMembers(client.members());
        updateMicButton();
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

        plp.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        plp.setMargins(
                dp(12),
                dp(64),
                dp(12),
                0
        );

        root.addView(pill, plp);

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

