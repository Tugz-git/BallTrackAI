# SportTrack AI v2 — Setup Guide

## Required downloads before this compiles

### 1. MediaPipe Pose model (same as v1)
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task
→ rename to `pose_landmarker_lite.task` → put in `app/src/main/assets/`

### 2. MediaPipe Face Detector model (new in v2 — needed for face recognition feature)
https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite
→ rename to `face_detector.tflite` (note: .tflite, not .task — an earlier version of this doc had the wrong extension) → put in `app/src/main/assets/`

### 3. Sound files (same 5 as v1)
Put in `app/src/main/res/raw/`: `sound_make.mp3`, `sound_miss.mp3`, `sound_streak.mp3`, `sound_session_end.mp3`, `sound_menu_click.mp3`

---

## Building without Android Studio — GitHub Actions

Android Studio needs 8GB+ RAM minimum. If your laptop has less, build on GitHub's servers instead — your laptop only needs a browser.

### One-time setup
1. Create a free account at github.com
2. Create a new repository (Private is fine if you don't want it public)
3. Add the 2 model files and 5 sound files above into this folder BEFORE uploading — GitHub's servers can't generate these, they need to already be present
4. Upload the entire `BallTrackAI` folder to that repository (drag-and-drop on github.com works, or use the GitHub Desktop app if you want something simpler than the command line)

### Building
1. On your repository's GitHub page, click the "Actions" tab
2. Click "Build SportTrack AI APK" → click "Run workflow"
3. Wait a few minutes — a yellow dot turns green when done
4. Open the finished run → "Artifacts" → download `SportTrackAI-debug-apk`
5. That's a zip containing `app-debug.apk` — extract it, transfer to your phone (email, USB, Drive), tap it on your phone to install

### If the build fails
Click into the failed run — it shows the exact error in red text, same as Android Studio would show. Paste that text to me and I'll fix whatever it points to.

---

## New in v2

- **AR court lines** drawn on screen for unmarked courts (concrete/driveway) — 3pt arc, paint, free-throw line, computed from hoop position
- **Front/back camera switch** — back camera for shooting, front camera for dribbling drills
- **Team setup screen** — add players, assign to Team A/B, saved rosters remembered between sessions
- **Local face recognition (opt-in per player)** — stores a numeric face signature ON-DEVICE ONLY so the app can auto-attribute scores to the right player without manual tapping
- **Manual violation flagging** — tap Traveling/Foul/Double Dribble/Out of Bounds/Other during a live match
- **Slow-mo zoomed replay** of flagged moments with a 30-second confirm-or-auto-delete window
- **Face data management screen** — view every registered player, delete one or delete all, instantly

---

## IMPORTANT — Honest limitations in this version

**Multi-person live tracking, automatic foul detection, and automatic travel detection were deliberately NOT built.** These require either multi-person pose tracking (too heavy for real-time on a Helio G100) or contextual judgment (foul vs. clean contact) that no on-device model reliably handles yet. Building these and pretending they work would mean the app makes wrong calls constantly. Instead: a player taps the violation type live, and the app handles the clip, slow-mo, zoom, and 30s window.

**Face recognition is architecturally real but the matching model is a placeholder.** The code stores and compares real numeric signatures (not photos), and delete-all/delete-one work fully and immediately. But matching accuracy depends on bundling a dedicated face-embedding model (like MobileFaceNet, ~5MB), which isn't included yet. To finish this properly:
1. Find a MobileFaceNet TFLite model
2. Place it in `app/src/main/assets/face_embedder.task`
3. Tell me and I'll wire it into `FaceRecognitionManager.kt`'s `extractEmbedding()` — architecture's ready, model integration isn't.

**Violation clip extraction currently copies the full source file rather than trimming around the flagged timestamp.** Real trimming needs MediaMuxer wiring — tell me when you want this finished.

**Slow-mo playback rendering isn't wired into the UI yet.** `ViolationReplayOverlay` expects an ExoPlayer view configured for 0.3x speed + zoom; that setup code isn't written yet. Tell me and I'll finish it.

---

## Privacy summary (v2)

| Data | Where it lives | Ever leaves device? |
|---|---|---|
| Camera frames | Processed in memory, discarded per-frame | Never |
| Pose landmarks | Processed in memory | Never |
| Face embeddings | app-private `local_face_data/` folder | Never — excluded from Android backup/transfer too |
| Match video | app-private `violation_clips/` and session storage | Never |
| Numeric session stats | Room DB | Only to YOUR OWN optional Mistral server, opt-in |

Face data and violation clips are explicitly excluded from Android's auto-backup
system (`backup_rules.xml` / `data_extraction_rules.xml`) — a full phone backup,
restore, or device transfer won't copy this data off the original device.

---

## What changed in this pass (design system)

- **New color/type system** tied to a real idea: this app reads court geometry and body position live, so the visuals lean into a HUD/viewfinder feel — corner-bracket framing on the live score, scoreboard-style numerals — instead of generic rounded cards. Full rationale is in `Theme.kt`'s header comment.
- **Customizable accent color** — Settings → Appearance, 5 presets (Court Orange default, Cyan, Crimson, Violet, Lime). Saved locally, applies app-wide to buttons, the score frame, and highlights.
- **The skeleton/AR tracking overlay deliberately stays a fixed cyan**, not tied to the accent — so "what the AI is seeing" always reads as a distinct layer from app chrome regardless of which accent you pick.
- **Honest gap**: no custom font file is bundled. The condensed/bold numeral look uses Android's built-in system font weights, not a specific typeface. If you want a particular font, drop a `.ttf`/`.otf` into `app/src/main/res/font/` and tell me — I'll wire it into `Theme.kt`'s `DisplayFontFamily` in one small edit.

## What changed in this pass (half-court team scoring, history, match recording)

- **Turn-based auto-scoring is now wired in.** When you set up teams on the Team screen and start a match, makes/misses get auto-credited to the team currently in possession, following make-it-take-it or alternating rules — no manual tap needed for which *team* scored.
- **Honest limit on this, stated plainly:** within a team of 2+ players, the camera cannot see who specifically released the ball — that needs either a tap or working face recognition, neither of which substitutes for true multi-person tracking. The app rotates through teammates as a reasonable guess; if it guesses the wrong teammate, correct it manually after the play.
- **Session history screen now exists** (Settings → View Past Sessions) — shows every saved session with date, score, shooting %, streak, and lets you delete individual sessions.
- **Match video recording is now real**, using CameraX VideoCapture, saved to app-private local storage only. This is what gives the violation-flagging feature an actual clip to trim and replay, instead of always saying "no recording active."
- **Match recordings are excluded from Android backup/transfer**, same protection as face data.

## New honest risk to know about

Running camera Preview + pose-tracking ImageAnalysis + VideoCapture simultaneously is at CameraX's typical 3-use-case limit, combined with MediaPipe pose inference on the same hardware (Helio G100, no dedicated NPU). This *should* work but is genuinely pushing the phone — if you see dropped frames, stuttering pose tracking, or the recording itself looking choppy, that's a real hardware limit, not a bug to "just fix." Lowering recording resolution (already capped at 720p) or disabling audio recording are the next levers if this turns out to be a problem in practice.

## What changed in this pass (bug fixes)

Went through every file by hand and fixed real bugs rather than just adding features on top:
- **Scoring bug**: inconclusive ball-tracking frames were being counted as misses, inflating miss counts — fixed so only confirmed makes/misses count
- **Camera rebinding bug**: the camera/pose pipeline was rebinding itself on every UI recomposition (every score update), which would have caused stutter or crashes on real hardware — fixed to bind once and only rebind on camera switch
- **Stale state bug**: the session-active flag read inside the camera analyzer's background thread could go stale — fixed with a thread-safe atomic reference
- **Shot clock desync**: the displayed shot clock never reset on a made/missed shot — fixed
- **Compile-breaking type error**: a UI function called a method that didn't exist on the type it was called on — fixed
- **Missing imports**: MainActivity was missing several imports for new v2 screens, would not have compiled — fixed
- **Real video trimming**: violation clips now actually trim the relevant segment from the match recording (MediaExtractor/MediaMuxer) instead of copying the whole file
- **Real slow-mo + zoom player**: wired an actual ExoPlayer-based component for violation replay, fixed a pivot-point bug that would have made the zoom do nothing
- **Sessions now actually save** to the local database when a session ends (previously the database existed but was never written to)
- **Face data file parsing**: hardened against a player name breaking the file format

## Still honestly unfinished — same as before, not hidden

- **Match video recording was never built.** Violation flagging works correctly — it just truthfully tells you no clip is available, since there's no recording to clip from yet. This needs CameraX VideoCapture wired into the live session screen.
- **Face recognition matching is structurally real (storage, deletion, consent) but the embedding model itself is a geometric placeholder, not a trained face model.** It will weakly distinguish faces by camera angle/position, not reliably tell people apart. Needs a bundled MobileFaceNet-style model — see model download section above.
- **Session history / stats screen UI** still doesn't exist — sessions now save to the database, but there's no screen to view past sessions yet.
- **I have not compiled this project myself** — there's no Android SDK in my sandbox. I read through every file by hand for type errors, missing imports, and logic bugs, and fixed everything I found, but a first real build in Android Studio may still surface something I couldn't catch by reading. That's normal for a project this size, not a sign of carelessness — tell me what Android Studio's error panel says if anything comes up, and I'll fix it.



## File count
36 files total (was 23 in v1).
