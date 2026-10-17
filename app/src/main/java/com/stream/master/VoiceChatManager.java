package com.stream.master;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Audio-only WebRTC mesh for Watch Party voice chat.
 *
 * One PeerConnection per remote participant. Signaling (offer / answer / ICE candidates) travels
 * over the existing room WebSocket via RoomClient.sendSignal(...) / RoomClient.Listener.onSignal(...);
 * this class never opens a connection of its own. To avoid glare when two peers would each try to
 * open a connection to the other at once, the participant whose id sorts first (String.compareTo)
 * always creates the offer - the other side only answers.
 *
 * A remote track needs no extra wiring to be heard: once it is attached to a PeerConnection it plays
 * through the device's normal audio output via WebRTC's own audio device module.
 *
 * ---------------------------------------------------------------------------------------------
 * WHY THIS FILE ALSO MANAGES AudioManager / audio focus (read before touching this class again)
 * ---------------------------------------------------------------------------------------------
 * WebRTC is, by design, a VoIP stack: the moment its native audio device module actually starts
 * capturing or playing out audio, it switches AudioManager into MODE_IN_COMMUNICATION. This is
 * inherent to libwebrtc, not something this app opts into - see the WebRTC team's own reply on
 * this exact question ("Given the nature of WebRTC (it is a VoIP stack), it is required to use
 * the MODE_IN_COMMUNICATION mode" - https://groups.google.com/g/discuss-webrtc/c/Pqag6R7QV2c).
 *
 * PlayerActivity's ExoPlayer is built with Media3's defaults (no setAudioAttributes /
 * handleAudioFocus override), which means handleAudioFocus=true: ExoPlayer automatically ducks
 * its own volume when it loses audio focus, and automatically restores it when it regains focus.
 * Nothing in this app used to ever hand focus back, so once WebRTC's audio pipeline engaged even
 * once, the movie stayed ducked forever - matching exactly the two symptoms this class was
 * rewritten to fix:
 *   1. Movie volume dropped just from being in a room (see "lazy local track" below for why).
 *   2. Movie volume never recovered after every mic in the room went quiet (no focus was ever
 *      abandoned, so ExoPlayer never received the AUDIOFOCUS_GAIN callback that tells it to
 *      undo its own ducking).
 *
 * AudioSessionGuard below fixes both: it is driven ONLY by JavaAudioDeviceModule's own
 * AudioRecordStateCallback / AudioTrackStateCallback - i.e. by WebRTC's native audio thread
 * actually starting or stopping capture/playout, not by guessing from UI state like "mic button
 * is on". The moment the last such session ends, it explicitly restores AudioManager's previous
 * mode and abandons the audio focus request, which is what lets ExoPlayer's own focus listener
 * un-duck automatically (this is standard Media3 behavior, not something this app has to redo
 * itself).
 *
 * WHY THE LOCAL TRACK IS CREATED LAZILY, AND ATTACHED VIA A TRANSCEIVER'S SENDER, NOT addTrack()
 * ---------------------------------------------------------------------------------------------
 * The previous version created the local AudioSource in the constructor (i.e. the moment
 * VoiceChatManager is built in RoomController.onWelcome() - as soon as you enter a room) and
 * called pc.addTrack(localAudioTrack, ...) for every peer as soon as a connection was opened,
 * regardless of whether the user had ever tapped the mic button. Because WebRTC ties native
 * capture/playout start to there being an active, negotiated audio m-line using the track (not
 * to AudioTrack.setEnabled()), this meant the native audio session - and therefore
 * MODE_IN_COMMUNICATION - engaged the instant you joined a room with anyone else in it, and
 * AudioTrack.setEnabled(false) (muting) never actually stopped the underlying capture, so it
 * never disengaged either.
 *
 * Fix: every PeerConnection is still created eagerly (so you can always receive audio from
 * anyone who does have their mic on), but via addTransceiver(..., SEND_RECV) with NO track
 * attached - this negotiates a sendrecv-capable audio m-line up front (so a future track can be
 * attached without renegotiating) while sending nothing and capturing nothing. The local
 * AudioSource/AudioTrack itself is only created the first time the user enables their mic
 * (createLocalTrackIfNeeded()), and is only ever attached to a peer's sender
 * (RtpSender.setTrack(...)) while the mic is actually on - detached from every sender the moment
 * it's turned off. That is what makes the native start/stop callbacks above fire exactly when
 * real speech might be flowing, instead of for the entire time you're simply sitting in a room.
 *
 * Threading: all public methods are expected to be called from the main thread, matching RoomClient.
 */
final class VoiceChatManager {

    private static PeerConnectionFactory factory;
    private static AudioDeviceModule audioDeviceModule;
    private static boolean factoryInitAttempted;
    private static AudioSessionGuard sessionGuard;

    private final RoomClient client;
    private final String myId;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<PeerConnection.IceServer> iceServers = new ArrayList<>();

    private AudioSource localAudioSource;
    private AudioTrack localAudioTrack;
    private boolean micEnabled;
    private boolean released;

    private final Map<String, PeerConnection> peers = new HashMap<>();
    private final Map<String, RtpSender> senders = new HashMap<>(); // this peer's slot for OUR track

    VoiceChatManager(Context context, RoomClient client, String myId) {
        this.client = client;
        this.myId = myId;
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        ensureFactory(context.getApplicationContext());
        // NOTE: no local track is created here on purpose - see class doc above. Entering a room
        // must never by itself start capturing audio or touch AudioManager's mode.
    }

    /** Initialized once per process; never released, so re-entering a room is cheap and never re-inits mid-call. */
    private static void ensureFactory(Context appContext) {
        if (factory != null || factoryInitAttempted) return;
        factoryInitAttempted = true;
        try {
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                            .setEnableInternalTracer(false)
                            .createInitializationOptions());

            sessionGuard = new AudioSessionGuard(appContext);

            audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .setAudioRecordStateCallback(new JavaAudioDeviceModule.AudioRecordStateCallback() {
                        @Override public void onWebRtcAudioRecordStart() { sessionGuard.onNativeSessionStart(); }
                        @Override public void onWebRtcAudioRecordStop() { sessionGuard.onNativeSessionStop(); }
                    })
                    .setAudioTrackStateCallback(new JavaAudioDeviceModule.AudioTrackStateCallback() {
                        @Override public void onWebRtcAudioTrackStart() { sessionGuard.onNativeSessionStart(); }
                        @Override public void onWebRtcAudioTrackStop() { sessionGuard.onNativeSessionStop(); }
                    })
                    .createAudioDeviceModule();

            factory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(audioDeviceModule)
                    .createPeerConnectionFactory();
        } catch (Throwable t) {
            factory = null; // caller must check isAvailable() before relying on voice chat
        }
    }

    boolean isAvailable() { return factory != null; }

    private void createLocalTrackIfNeeded() {
        if (factory == null || localAudioTrack != null) return;
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        try {
            localAudioSource = factory.createAudioSource(audioConstraints);
            localAudioTrack = factory.createAudioTrack("watchparty-mic-" + myId, localAudioSource);
            localAudioTrack.setEnabled(true); // the sender attach/detach below is what actually gates capture
        } catch (Throwable t) {
            localAudioTrack = null;
            localAudioSource = null;
        }
    }

    /**
     * Turns local mic capture on/off. The caller is also responsible for RoomClient.setMic(on);
     * this only touches the local WebRTC/audio side, driven by the server's confirmed voice-state
     * (see RoomController.onVoiceState), not called optimistically on tap.
     */
    void setMicEnabled(boolean on) {
        if (on) {
            createLocalTrackIfNeeded();
            micEnabled = localAudioTrack != null;
            if (!micEnabled) return;
            for (Map.Entry<String, RtpSender> e : senders.entrySet()) {
                try { e.getValue().setTrack(localAudioTrack, false); } catch (Exception ignored) { }
            }
        } else {
            micEnabled = false;
            // Detach from every sender - not just AudioTrack.setEnabled(false) - because a track
            // still attached to an active sender is still "in use" as far as the native audio
            // pipeline is concerned, which is exactly the bug this rewrite fixes (see class doc).
            for (Map.Entry<String, RtpSender> e : senders.entrySet()) {
                try { e.getValue().setTrack(null, false); } catch (Exception ignored) { }
            }
        }
    }

    boolean isMicEnabled() { return micEnabled; }

    /** Called whenever the room's member list changes: opens connections to new members, closes stale ones. */
    void syncMembers(List<RoomClient.Member> members) {
        if (factory == null || released) return;
        Set<String> live = new HashSet<>();
        for (RoomClient.Member m : members) {
            if (m.id.equals(myId)) continue;
            live.add(m.id);
            if (!peers.containsKey(m.id)) connectTo(m.id);
        }
        for (String id : new ArrayList<>(peers.keySet())) {
            if (!live.contains(id)) closePeer(id);
        }
    }

    private void connectTo(String remoteId) {
        PeerConnection pc = createPeerConnection(remoteId);
        if (pc == null) return;
        peers.put(remoteId, pc);
        // Deterministic offerer: the lower id always initiates, so both sides never send an offer at once.
        if (myId.compareTo(remoteId) < 0) createOffer(remoteId, pc);
    }

    private PeerConnection createPeerConnection(String remoteId) {
        if (factory == null) return null;
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        PeerConnection pc = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override public void onIceCandidate(IceCandidate candidate) {
                JSONObject data = new JSONObject();
                try {
                    data.put("kind", "ice");
                    data.put("sdpMid", candidate.sdpMid);
                    data.put("sdpMLineIndex", candidate.sdpMLineIndex);
                    data.put("candidate", candidate.sdp);
                } catch (JSONException ignored) { }
                main.post(() -> { if (!released) client.sendSignal(remoteId, data); });
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
            @Override public void onAddStream(MediaStream stream) { /* audio plays automatically via the device audio module */ }
            @Override public void onRemoveStream(MediaStream stream) { }
            @Override public void onDataChannel(DataChannel channel) { }
            @Override public void onRenegotiationNeeded() { }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) { }
            @Override public void onIceConnectionReceivingChange(boolean receiving) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
            @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) { }
        });
        if (pc == null) return null;

        // sendrecv up front, track attached later (or never) - see class doc for why. This lets
        // us always receive the remote side's audio without ever sending/capturing anything
        // ourselves unless and until the local mic is actually turned on.
        RtpTransceiver.RtpTransceiverInit init =
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);
        RtpTransceiver transceiver = pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, init);
        if (transceiver != null && transceiver.getSender() != null) {
            senders.put(remoteId, transceiver.getSender());
            // A peer that joins while I already have my mic on should hear me immediately.
            if (micEnabled && localAudioTrack != null) {
                try { transceiver.getSender().setTrack(localAudioTrack, false); } catch (Exception ignored) { }
            }
        }
        return pc;
    }

    private void createOffer(String remoteId, PeerConnection pc) {
        pc.createOffer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                pc.setLocalDescription(new SimpleSdpObserver(), sdp);
                JSONObject data = new JSONObject();
                try { data.put("kind", "offer"); data.put("sdp", sdp.description); } catch (JSONException ignored) { }
                main.post(() -> { if (!released) client.sendSignal(remoteId, data); });
            }
        }, new MediaConstraints());
    }

    /** Routes an incoming offer / answer / ice payload from RoomClient.Listener.onSignal(fromId, data). */
    void onSignal(String fromId, JSONObject data) {
        if (factory == null || released) return;
        String kind = data.optString("kind");
        PeerConnection pc = peers.get(fromId);
        if (pc == null && "offer".equals(kind)) {
            pc = createPeerConnection(fromId);
            if (pc == null) return;
            peers.put(fromId, pc);
        }
        if (pc == null) return;
        final PeerConnection fpc = pc;

        switch (kind) {
            case "offer": {
                SessionDescription remote = new SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp"));
                fpc.setRemoteDescription(new SimpleSdpObserver() {
                    @Override public void onSetSuccess() {
                        fpc.createAnswer(new SimpleSdpObserver() {
                            @Override public void onCreateSuccess(SessionDescription answer) {
                                fpc.setLocalDescription(new SimpleSdpObserver(), answer);
                                JSONObject out = new JSONObject();
                                try { out.put("kind", "answer"); out.put("sdp", answer.description); } catch (JSONException ignored) { }
                                main.post(() -> { if (!released) client.sendSignal(fromId, out); });
                            }
                        }, new MediaConstraints());
                    }
                }, remote);
                break;
            }
            case "answer": {
                SessionDescription remote = new SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp"));
                fpc.setRemoteDescription(new SimpleSdpObserver(), remote);
                break;
            }
            case "ice": {
                try {
                    fpc.addIceCandidate(new IceCandidate(data.optString("sdpMid"), data.optInt("sdpMLineIndex"), data.optString("candidate")));
                } catch (Exception ignored) { }
                break;
            }
            default: break;
        }
    }

    private void closePeer(String id) {
        senders.remove(id);
        PeerConnection pc = peers.remove(id);
        if (pc != null) {
            try { pc.close(); } catch (Exception ignored) { }
        }
    }

    /** Full teardown: room leave/close, voice disabled, activity destruction, or app backgrounded for good. */
    void release() {
        if (released) return;
        released = true;
        for (String id : new ArrayList<>(peers.keySet())) closePeer(id);
        peers.clear();
        senders.clear();
        if (localAudioTrack != null) {
            try { localAudioTrack.setEnabled(false); localAudioTrack.dispose(); } catch (Exception ignored) { }
            localAudioTrack = null;
        }
        if (localAudioSource != null) {
            try { localAudioSource.dispose(); } catch (Exception ignored) { }
            localAudioSource = null;
        }
        micEnabled = false;
        // Deliberately NOT releasing the static factory/audioDeviceModule/sessionGuard here -
        // see ensureFactory()'s doc: they are process-lifetime singletons. AudioSessionGuard's
        // own reference count (driven by native start/stop callbacks, not by this instance)
        // already guarantees AudioManager gets restored once this was the last active session -
        // closePeer() above stops sending/receiving, which stops the native audio pipeline,
        // which fires the stop callbacks on its own.
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String s) { }
        @Override public void onSetFailure(String s) { }
    }

    /**
     * Restores AudioManager to exactly how the app found it once every native WebRTC audio
     * session (record or playout) has stopped. Reference-counted because multiple things can
     * start/stop independently (our own mic, plus however many remote peers are talking), and
     * only the 0 -> 1 and 1 -> 0 transitions matter. See the class-level doc for why this exists.
     */
    private static final class AudioSessionGuard {
        private final Context appContext;
        private final Handler main;

        private int activeSessions = 0;
        private boolean modeSaved = false;
        private int savedMode = AudioManager.MODE_NORMAL;
        private boolean focusHeld = false;
        private AudioFocusRequest focusRequest; // API 26+ only; null and unused below that
        private AudioManager.OnAudioFocusChangeListener focusListener;

        AudioSessionGuard(Context appContext) {
            this.appContext = appContext;
            this.main = new Handler(Looper.getMainLooper());
            // We don't need to react to focus changes ourselves (ExoPlayer has its own listener
            // and reacts to gain/loss on its own); this listener only needs to exist because the
            // focus-request APIs require one.
            this.focusListener = change -> { };
        }

        /** Called from JavaAudioDeviceModule's native audio thread - hop to main before touching AudioManager. */
        void onNativeSessionStart() {
            main.post(() -> {
                activeSessions++;
                if (activeSessions == 1) engage();
            });
        }

        void onNativeSessionStop() {
            main.post(() -> {
                activeSessions = Math.max(0, activeSessions - 1);
                if (activeSessions == 0) release();
            });
        }

        private void engage() {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;

            // WebRTC's native ADM sets MODE_IN_COMMUNICATION itself the moment it actually starts
            // capturing/playing (that part is inherent to libwebrtc - see class doc). Saving the
            // mode here, before that happens, is what lets release() below put it back exactly as
            // found, rather than assuming it was MODE_NORMAL.
            if (!modeSaved) {
                savedMode = am.getMode();
                modeSaved = true;
            }

            if (!focusHeld) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(attrs)
                            .setOnAudioFocusChangeListener(focusListener, main)
                            .build();
                    focusHeld = am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                } else {
                    //noinspection deprecation
                    focusHeld = am.requestAudioFocus(focusListener, AudioManager.STREAM_VOICE_CALL,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                }
            }
        }

        private void release() {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;

            // This abandon() is the actual fix for "movie stays quiet forever": it is what
            // delivers AUDIOFOCUS_GAIN back to ExoPlayer's own focus listener, which is what
            // makes Media3's default handleAudioFocus=true behavior undo its own ducking. Without
            // this call, nothing ever tells ExoPlayer the competing focus holder is gone.
            if (focusHeld) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    am.abandonAudioFocusRequest(focusRequest);
                } else {
                    //noinspection deprecation
                    am.abandonAudioFocus(focusListener);
                }
                focusHeld = false;
            }

            if (modeSaved) {
                am.setMode(savedMode);
                modeSaved = false;
            }
        }
    }
}
