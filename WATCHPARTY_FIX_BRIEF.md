# Watch Party - engineering brief (the prompt this release was built against)

Role: senior Android/WebRTC engineer. Fix root causes, not symptoms. Every change must be
explained by an observed symptom, be defensive against the failure modes below, and leave the
phone in the state it found it (audio mode, route, volume) when the room ends.

## Observed symptoms -> root causes -> fixes

| # | Symptom | Root cause | Fix (file) |
|---|---------|-----------|-----------|
| 1 | Room creator's voice never heard | Answering side attached the mic to a transceiver libwebrtc never negotiates (addTransceiver() ones are not reused for an incoming offer; the offer's own transceiver is recvonly) | Only the offerer pre-creates a transceiver; the answerer flips the offer's transceiver to SEND_RECV and attaches the mic before createAnswer (VoiceChatManager) |
| 2 | Movie volume low / inconsistent / stuck after mic off | Ducking followed libwebrtc playout callbacks, which stay "active" for the whole call | Duck only from server-confirmed mic state of members; restore instantly (RoomController.refreshVoiceDuck) |
| 3 | Movie audio comes out of the earpiece when voice is on | Voice playout declared VOICE_COMMUNICATION + focus request as a call -> the OS moves the whole output to the earpiece | Voice plays as USAGE_MEDIA (reflection, safe if the setter is missing); focus requested as MEDIA/MOVIE; AudioSessionGuard forces the loudspeaker via setCommunicationDevice (API 31+) / setSpeakerphoneOn, steps aside when headphones are connected, re-checks on device changes, and restores everything on exit; hardware volume keys pinned to STREAM_MUSIC (VoiceChatManager, PlayerActivity) |
| 4 | Guest video freezes after joining / on slow networks | Guest hard-seeked on >2.5 s drift every 3 s; each seek rebuffers, which re-creates the drift: a seek loop | Soft correction by playback speed (+-10 %, with hysteresis); hard seek only for >4 s drift, at most every 8 s, never while buffering; follows the host's playback rate (PlayerActivity.applyRemotePlayback) |
| 5 | Participant randomly thrown out / room "ends" | A bare WS close 1000 from a proxy was read as "room ended"; a single HTTP 400/404 on upgrade was read as "room not found" and closed the player; reconnect gave up after ~100 s | 1000 -> reconnect (server answers 4004 if the room is really gone); 404/400 needs 3 consecutive confirmations; up to ~15 min of retries; immediate retry when the network returns; stale socket cancelled before a new one opens (RoomClient) |
| 6 | Random errors / crashes | Any exception inside message handling or a listener killed the app | Message handling wrapped; ICE candidates queued until the remote description exists; SDP callbacks hop to the main thread; peers rebuilt after reconnect; bounded ICE restarts (RoomClient, VoiceChatManager) |
| 7 | Crash on room creation (release build) | R8 renamed/merged the Gson DTO TurnCredentialsResponse | keep rules + defensive callback (proguard-rules.pro, RoomController) |
| 8 | Broken accented Spanish letters | Bundled "Arial CE" has wrong glyph outlines for U+0080-U+00FF | Those cmap entries removed so Android falls back to a system font (res/font/stream_arial.ttf) |
| 9 | Signaling messages silently dropped | Server token buckets too small for offer+answer+ICE bursts | 200/100 per socket, 400/150 for signals (server, optional) |

## Acceptance checklist (run on two different phones, release build)
1. Create room on A, join on B. Open A's mic -> B hears A. Open B's mic -> A hears B. Both mics -> both hear each other.
2. Movie plays from the loudspeaker (bottom/back), never the earpiece, with mics on and off; hardware volume keys change the movie volume.
3. Movie is at 100 % when no mic is open, ~50 % only while someone's mic is open.
4. Plug in headphones during the call: sound moves to them; unplug: back to the loudspeaker. Leave the room: normal phone audio.
5. B joins mid-movie on mobile data: no freeze loop; B converges to A within ~30 s without repeated seeks.
6. Toggle airplane mode on B for 20 s, then off: B returns to the room and the voice reconnects by itself.
7. Host pauses / seeks / changes speed: B follows.
8. Chat in Spanish ("Que estas haciendo aqui?" with accents) renders correctly in an English UI.

## Not verifiable offline (needs a real device)
Everything above was reviewed against libwebrtc / Android audio-routing behaviour and syntax-checked,
but not executed. If a phone still misbehaves, capture `adb logcat -s VoiceChat RoomClient` and the phone model.
