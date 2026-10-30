package com.stream.master;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Watch-party room client: REST (create / preview) + one WebSocket per room.
 *
 * Protocol (JSON text frames) - see server/realtime/gateway.js:
 *   server -> welcome | join | leave | presence | host | state | chat | voice-state | signal | closed | kicked | error | pong
 *   client -> state (host only) | chat | ping | kick (host) | end (host) | leave | voice-state | mute (host) | unmute (host) | signal
 *
 * All Listener callbacks are delivered on the main thread. The instance is a process-wide singleton
 * so a room survives the activity being recreated (rotation, PiP) while the player is open.
 */
public final class RoomClient {

    public static final Pattern CODE_PATTERN = Pattern.compile("^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{6}$");
    public static final String LINK_PREFIX = "streammaster://room/";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    // ~15 minutes of retrying (the delay tops out at 15 s). The server keeps a seat for 45 s, but a
    // phone that loses signal for a minute must still be able to come back to the room by itself.
    private static final int MAX_RECONNECT_ATTEMPTS = 60;
    // A 400/404 on the socket upgrade only means "room gone" if it keeps happening; a proxy or a
    // server restart can return one for a moment.
    private static final int NOT_FOUND_CONFIRMATIONS = 3;
    private static final int CHAT_KEEP = 100;

    // ------------------------------------------------------------------ public types
    public static final class Member {
        public final String id;
        public String name;
        public boolean host;
        public boolean online = true;
        public boolean mic;
        public boolean mutedByHost;
        Member(String id, String name) { this.id = id; this.name = name; }
    }

    public static final class ChatMessage {
        public final long id;
        public final String fromId;
        public final String name;
        public final String text;
        public final long at;
        ChatMessage(long id, String fromId, String name, String text, long at) {
            this.id = id; this.fromId = fromId; this.name = name; this.text = text; this.at = at;
        }
    }

    /** Last playback state published by the host. */
    public static final class RemoteState {
        public final boolean playing;
        public final long posMs;
        public final long atServerMs;
        public final float rate;
        RemoteState(boolean playing, long posMs, long atServerMs, float rate) {
            this.playing = playing; this.posMs = posMs; this.atServerMs = atServerMs; this.rate = rate;
        }
        /** Where the host is right now, given the current server time. */
        public long expectedPositionMs(long serverNowMs) {
            if (!playing) return posMs;
            return posMs + Math.max(0L, Math.round((serverNowMs - atServerMs) * rate));
        }
    }

    public interface Listener {
        void onWelcome();
        void onMembersChanged();
        void onHostChanged(boolean isMe);
        void onState(@NonNull RemoteState state);
        void onChat(@NonNull ChatMessage message);
        /** A participant's mic / host-mute state changed (self or someone else). */
        void onVoiceState(@NonNull String memberId);
        /** An opaque WebRTC offer/answer/ice-candidate payload relayed from another member. */
        void onSignal(@NonNull String fromId, @NonNull JSONObject data);
        /** Final: the room is over for this client (ended, kicked, not found, full, connection lost for good). */
        void onClosed(@NonNull String reason);
        void onConnection(boolean connected);
        void onServerError(@NonNull String code);
    }

    public interface ResultCallback<T> {
        void onResult(@Nullable T value, @Nullable String error);
    }

    // ------------------------------------------------------------------ singleton
    private static final RoomClient INSTANCE = new RoomClient();
    public static RoomClient get() { return INSTANCE; }
    private RoomClient() { }

    private final Handler main = new Handler(Looper.getMainLooper());

    private Context appContext;
    private WebSocket socket;
    private OkHttpClient wsClient;
    private Listener listener;

    private boolean wantConnected;
    private boolean connected;
    private int socketGeneration;      // ignores callbacks from sockets we already replaced
    private int reconnectAttempts;
    private int notFoundStrikes;
    private long lastOpenAttemptAt;
    private final Runnable reconnectTask = new Runnable() {
        @Override public void run() { if (wantConnected) openSocket(); }
    };
    private ConnectivityManager.NetworkCallback networkCallback;

    private String code;
    private String displayName = "";
    private String myId;
    private String hostId;
    private boolean isHost;
    private JSONObject media;
    private RemoteState lastState;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final List<ChatMessage> chat = new ArrayList<>();

    private long serverOffsetMs;
    private long bestRttMs = Long.MAX_VALUE;
    private long bestRttAtMs;

    private final Runnable pingTask = new Runnable() {
        @Override public void run() {
            if (!wantConnected) return;
            sendRaw(json("t", "ping", "c", System.currentTimeMillis()));
            main.postDelayed(this, 15000);
        }
    };

    // ------------------------------------------------------------------ accessors (main thread)
    public boolean isActive() { return wantConnected && code != null; }
    public boolean isHost() { return isHost; }
    public boolean isConnected() { return connected; }
    @Nullable public String code() { return code; }
    @Nullable public String myId() { return myId; }
    @Nullable public JSONObject media() { return media; }
    @Nullable public RemoteState lastState() { return lastState; }
    public long serverNowMs() { return System.currentTimeMillis() + serverOffsetMs; }

    public List<Member> members() { return new ArrayList<>(members.values()); }
    public List<ChatMessage> chat() { return Collections.unmodifiableList(new ArrayList<>(chat)); }

    public void setListener(@Nullable Listener l) { listener = l; }

    // ------------------------------------------------------------------ name
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("StreamMasterRoomPrefs", Context.MODE_PRIVATE);
    }
    public static String savedName(Context c) { return prefs(c).getString("name", ""); }
    public static void saveName(Context c, String name) { prefs(c).edit().putString("name", name == null ? "" : name.trim()).apply(); }

    // ------------------------------------------------------------------ links
    public static String shareLink(String code) { return LINK_PREFIX + code; }

    /** Accepts "abc123", "ABC123" or "streammaster://room/ABC123"; returns null if it is not a valid code. */
    @Nullable
    public static String parseCode(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim();
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        s = s.toUpperCase(Locale.ROOT);
        return CODE_PATTERN.matcher(s).matches() ? s : null;
    }

    // ------------------------------------------------------------------ REST
    private static String httpBase() {
        String base = Constants.BASE_URL == null ? "" : Constants.BASE_URL.trim();
        return base.endsWith("/") ? base : base + "/";
    }

    /** POST /api/rooms with the media descriptor (the same extras PlayerActivity plays). */
    public static void createRoom(Context context, JSONObject mediaJson, ResultCallback<String> cb) {
        OkHttpClient client = SecureNetworkClient.getClient(context);
        JSONObject body = new JSONObject();
        try { body.put("media", mediaJson); } catch (JSONException e) { cb.onResult(null, "bad_media"); return; }
        Request request = new Request.Builder()
                .url(httpBase() + "api/rooms")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { deliver(cb, null, "network"); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    JSONObject o = parse(r);
                    if (r.code() == 201 && o != null && o.optString("code").length() == 6) {
                        deliver(cb, o.optString("code"), null);
                    } else {
                        deliver(cb, null, errorOf(r.code(), o));
                    }
                } catch (Exception e) { deliver(cb, null, "network"); }
            }
        });
    }

    /** GET /api/rooms/{code}: what is being watched, and by whom. */
    public static void preview(Context context, String code, ResultCallback<JSONObject> cb) {
        OkHttpClient client = SecureNetworkClient.getClient(context);
        Request request = new Request.Builder().url(httpBase() + "api/rooms/" + code).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { deliver(cb, null, "network"); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    JSONObject o = parse(r);
                    JSONObject room = o == null ? null : o.optJSONObject("room");
                    if (r.isSuccessful() && room != null) deliver(cb, room, null);
                    else deliver(cb, null, errorOf(r.code(), o));
                } catch (Exception e) { deliver(cb, null, "network"); }
            }
        });
    }

    @Nullable
    private static JSONObject parse(Response r) {
        try {
            String s = r.body() == null ? "" : r.body().string();
            return s.isEmpty() ? null : new JSONObject(s);
        } catch (Exception e) { return null; }
    }

    private static String errorOf(int http, @Nullable JSONObject o) {
        String m = o == null ? "" : o.optString("message", "");
        if (!m.isEmpty()) return m;
        return http == 404 ? "room_not_found" : http == 429 ? "rate_limited" : "network";
    }

    private static <T> void deliver(ResultCallback<T> cb, T value, String error) {
        new Handler(Looper.getMainLooper()).post(() -> cb.onResult(value, error));
    }

    // ------------------------------------------------------------------ connection
    /** Opens (or re-opens) the WebSocket for [code]. Safe to call again for the same room. */
    public void connect(Context context, String roomCode, String name) {
        appContext = context.getApplicationContext();
        if (wantConnected && roomCode.equals(code)) {
            if (name != null && !name.trim().isEmpty()) displayName = name.trim();
            return; // already in this room
        }
        if (wantConnected) disconnect(true);
        code = roomCode;
        displayName = name == null ? "" : name.trim();
        wantConnected = true;
        reconnectAttempts = 0;
        resetRoomState();
        openSocket();
        main.removeCallbacks(pingTask);
        main.postDelayed(pingTask, 3000);
        registerNetworkCallback();
    }

    /**
     * When the phone gets a network back (wifi <-> mobile data, coming out of a tunnel) retry the
     * socket right away instead of waiting for the next backoff step.
     */
    private void registerNetworkCallback() {
        if (networkCallback != null || appContext == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(@NonNull Network network) {
                    main.post(() -> {
                        if (!wantConnected || connected) return;
                        // registerDefaultNetworkCallback() reports the current network immediately:
                        // don't tear down a socket we only just started opening.
                        if (SystemClock.elapsedRealtime() - lastOpenAttemptAt < 4000) return;
                        reconnectAttempts = 0;
                        main.removeCallbacks(reconnectTask);
                        openSocket();
                    });
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (Throwable t) {
            networkCallback = null;
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null || appContext == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Throwable ignored) { }
        networkCallback = null;
    }

    /** Leaves the room. [notifyServer] lets the server hand the host role over immediately. */
    public void disconnect(boolean notifyServer) {
        if (notifyServer && socket != null && connected) sendRaw(json("t", "leave"));
        wantConnected = false;
        connected = false;
        socketGeneration++;
        main.removeCallbacks(pingTask);
        main.removeCallbacksAndMessages(null);
        unregisterNetworkCallback();
        WebSocket s = socket;
        socket = null;
        if (s != null) {
            try { s.close(1000, "left"); } catch (Exception ignored) { }
        }
        resetRoomState();
        code = null;
    }

    private void resetRoomState() {
        myId = null; hostId = null; isHost = false; media = null; lastState = null;
        members.clear(); chat.clear();
        bestRttMs = Long.MAX_VALUE;
    }

    private void openSocket() {
        if (appContext == null || code == null) return;
        String http = httpBase();
        String ws = http.startsWith("https") ? "wss" + http.substring(5) : "ws" + http.substring(4);
        Uri.Builder url = Uri.parse(ws + "ws/rooms").buildUpon().appendQueryParameter("code", code);
        if (!displayName.isEmpty()) url.appendQueryParameter("name", displayName);

        if (wsClient == null) {
            // Same client as the API (auth interceptor adds the token headers), but without the
            // 60s read timeout: a room socket is idle for long stretches. OkHttp pings keep it alive.
            wsClient = SecureNetworkClient.getClient(appContext).newBuilder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .pingInterval(20, TimeUnit.SECONDS)
                    .build();
        }
        lastOpenAttemptAt = SystemClock.elapsedRealtime();
        final int generation = ++socketGeneration;
        WebSocket stale = socket;
        if (stale != null) { try { stale.cancel(); } catch (Exception ignored) { } }
        socket = wsClient.newWebSocket(new Request.Builder().url(url.build().toString()).build(), new WebSocketListener() {
            @Override public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                main.post(() -> { if (generation == socketGeneration) { connected = true; reconnectAttempts = 0; notFoundStrikes = 0; if (listener != null) listener.onConnection(true); } });
            }
            @Override public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                main.post(() -> {
                    if (generation != socketGeneration) return;
                    // A malformed message, or a bug in one listener callback, must never take the whole
                    // app down: log it and keep the room alive.
                    try { handleMessage(text); } catch (Throwable t) { android.util.Log.w("RoomClient", "message handling failed", t); }
                });
            }
            @Override public void onClosed(@NonNull WebSocket ws, int codeNum, @NonNull String reason) {
                main.post(() -> { if (generation == socketGeneration) handleClosed(codeNum, reason); });
            }
            @Override public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
                final int http = response == null ? 0 : response.code();
                main.post(() -> { if (generation == socketGeneration) handleFailure(http); });
            }
        });
    }

    private void handleClosed(int closeCode, String reason) {
        connected = false;
        if (listener != null) listener.onConnection(false);
        switch (closeCode) {
            case 4003: finish("kicked"); return;
            case 4004: finish("not_found"); return;
            case 4008: finish("full"); return;
            case 4001: finish("replaced"); return;
            // The server always sends a 'closed' message (handled in handleMessage) before closing
            // with 1000. A bare 1000 that arrives without it came from something in between (proxy,
            // load balancer, server restart) - not from the room ending - so reconnect and let the
            // server answer 4004 if the room really is gone.
            default: scheduleReconnect();
        }
    }

    private void handleFailure(int httpStatus) {
        connected = false;
        if (listener != null) listener.onConnection(false);
        if (httpStatus == 404 || httpStatus == 400) {
            if (++notFoundStrikes >= NOT_FOUND_CONFIRMATIONS) { finish("not_found"); return; }
        } else {
            notFoundStrikes = 0;
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!wantConnected) return;
        if (++reconnectAttempts > MAX_RECONNECT_ATTEMPTS) { finish("connection_lost"); return; }
        long delay = Math.min(15000L, 1000L << Math.min(reconnectAttempts - 1, 4));
        main.removeCallbacks(reconnectTask);
        main.postDelayed(reconnectTask, delay);
    }

    private void finish(String reason) {
        boolean wasActive = wantConnected;
        wantConnected = false;
        connected = false;
        socketGeneration++;
        main.removeCallbacks(pingTask);
        main.removeCallbacks(reconnectTask);
        unregisterNetworkCallback();
        WebSocket s = socket;
        socket = null;
        if (s != null) { try { s.cancel(); } catch (Exception ignored) { } }
        if (wasActive && listener != null) listener.onClosed(reason);
        resetRoomState();
        code = null;
    }

    // ------------------------------------------------------------------ incoming
    private void handleMessage(String text) {
        final JSONObject m;
        try { m = new JSONObject(text); } catch (JSONException e) { return; }
        final String t = m.optString("t");
        switch (t) {
            case "welcome": onWelcome(m); break;
            case "join": {
                JSONObject mem = m.optJSONObject("member");
                if (mem != null) {
                    Member nm = new Member(mem.optString("id"), mem.optString("name"));
                    nm.mic = mem.optBoolean("mic", false);
                    nm.mutedByHost = mem.optBoolean("mutedByHost", false);
                    members.put(nm.id, nm);
                    if (listener != null) listener.onMembersChanged();
                }
                break;
            }
            case "leave":
                members.remove(m.optString("id"));
                if (listener != null) listener.onMembersChanged();
                break;
            case "presence": {
                Member mem = members.get(m.optString("id"));
                if (mem != null) {
                    mem.online = m.optBoolean("online", true);
                    if (m.has("mic")) mem.mic = m.optBoolean("mic", false);
                    if (listener != null) listener.onMembersChanged();
                }
                break;
            }
            case "host": {
                hostId = m.optString("id");
                isHost = hostId.equals(myId);
                for (Member mem : members.values()) mem.host = mem.id.equals(hostId);
                if (listener != null) { listener.onMembersChanged(); listener.onHostChanged(isHost); }
                break;
            }
            case "state": {
                RemoteState s = new RemoteState(m.optBoolean("playing"), m.optLong("pos"), m.optLong("at"), (float) m.optDouble("rate", 1.0));
                lastState = s;
                if (listener != null && !isHost) listener.onState(s);
                break;
            }
            case "chat": addChat(m); break;
            case "voice-state": {
                Member mem = members.get(m.optString("id"));
                if (mem != null) {
                    mem.mic = m.optBoolean("mic", false);
                    mem.mutedByHost = m.optBoolean("mutedByHost", false);
                    if (listener != null) listener.onVoiceState(mem.id);
                }
                break;
            }
            case "signal": {
                String from = m.optString("from");
                JSONObject data = m.optJSONObject("data");
                if (listener != null && !from.isEmpty() && data != null) listener.onSignal(from, data);
                break;
            }
            case "pong": onPong(m); break;
            case "closed": finish(m.optString("reason", "ended")); break;
            case "kicked": finish("kicked"); break;
            case "error": if (listener != null) listener.onServerError(m.optString("code")); break;
            default: break;
        }
    }

    private void onWelcome(JSONObject m) {
        JSONObject you = m.optJSONObject("you");
        JSONObject room = m.optJSONObject("room");
        if (you == null || room == null) return;
        myId = you.optString("id");
        isHost = you.optBoolean("host");
        hostId = room.optString("hostId", "");
        media = room.optJSONObject("media");
        members.clear();
        JSONArray arr = room.optJSONArray("members");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Member mem = new Member(o.optString("id"), o.optString("name"));
                mem.host = o.optBoolean("host");
                mem.online = o.optBoolean("online", true);
                mem.mic = o.optBoolean("mic", false);
                mem.mutedByHost = o.optBoolean("mutedByHost", false);
                members.put(mem.id, mem);
            }
        }
        long serverNow = room.optLong("serverNow", 0);
        if (serverNow > 0) serverOffsetMs = serverNow - System.currentTimeMillis(); // refined by ping/pong below
        JSONObject st = room.optJSONObject("state");
        if (st != null) {
            lastState = new RemoteState(st.optBoolean("playing"), st.optLong("pos"), st.optLong("at"), (float) st.optDouble("rate", 1.0));
        }
        chat.clear();
        JSONArray hist = m.optJSONArray("history");
        if (hist != null) for (int i = 0; i < hist.length(); i++) { JSONObject o = hist.optJSONObject(i); if (o != null) addChatQuiet(o); }
        if (listener != null) {
            listener.onWelcome();
            if (!isHost && lastState != null) listener.onState(lastState);
        }
        sendRaw(json("t", "ping", "c", System.currentTimeMillis())); // first clock sync right away
    }

    private void onPong(JSONObject m) {
        long now = System.currentTimeMillis();
        long sent = m.optLong("c");
        long serverNow = m.optLong("now");
        long rtt = now - sent;
        if (sent <= 0 || rtt < 0 || rtt > 10000 || serverNow <= 0) return;
        // Keep the offset measured with the lowest round-trip (least queueing noise); refresh after a minute.
        if (rtt <= bestRttMs || now - bestRttAtMs > 60000) {
            bestRttMs = rtt;
            bestRttAtMs = now;
            serverOffsetMs = serverNow + rtt / 2 - now;
        }
    }

    private void addChat(JSONObject m) {
        ChatMessage msg = addChatQuiet(m);
        if (msg != null && listener != null) listener.onChat(msg);
    }

    @Nullable
    private ChatMessage addChatQuiet(JSONObject m) {
        JSONObject from = m.optJSONObject("from");
        if (from == null) return null;
        ChatMessage msg = new ChatMessage(m.optLong("id"), from.optString("id"), from.optString("name"), m.optString("text"), m.optLong("at"));
        for (ChatMessage c : chat) if (c.id == msg.id) return null; // duplicate after a reconnect
        chat.add(msg);
        while (chat.size() > CHAT_KEEP) chat.remove(0);
        return msg;
    }

    // ------------------------------------------------------------------ outgoing
    public void sendState(boolean playing, long posMs, float rate) {
        if (!isHost) return;
        sendRaw(json("t", "state", "playing", playing, "pos", Math.max(0, posMs), "rate", rate));
    }

    public void sendChat(String text) {
        if (text == null || text.trim().isEmpty()) return;
        sendRaw(json("t", "chat", "text", text.trim()));
    }

    public void kick(String memberId) { if (isHost) sendRaw(json("t", "kick", "id", memberId)); }

    public void endRoom() { if (isHost) sendRaw(json("t", "end")); }

    /** Self mic toggle. Ignored (bounces back as onServerError("muted_by_host")) while the host has muted you. */
    public void setMic(boolean on) { sendRaw(json("t", "voice-state", "mic", on)); }

    public void muteMember(String memberId) { if (isHost) sendRaw(json("t", "mute", "id", memberId)); }

    public void unmuteMember(String memberId) { if (isHost) sendRaw(json("t", "unmute", "id", memberId)); }

    /** Relays an opaque WebRTC offer/answer/ice-candidate payload to [memberId] via the signaling server. */
    public void sendSignal(String memberId, JSONObject data) {
        JSONObject o = json("t", "signal", "to", memberId);
        try { o.put("data", data); } catch (JSONException ignored) { }
        sendRaw(o);
    }

    private void sendRaw(JSONObject o) {
        WebSocket s = socket;
        if (s != null && connected) s.send(o.toString());
    }

    private static JSONObject json(Object... kv) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]); } catch (JSONException ignored) { }
        return o;
    }
}
