# SportTrack AI — Full Project Blueprint

(Working name — rename freely. This doc is the source of truth for the project; paste it back into any conversation to restore context.)

## 0. Core Premise
Android app, Kotlin, that uses on-device computer vision to track basketball (and later volleyball, football) training sessions — shot tracking, dribbling, form metrics — with zero video/image/body data ever leaving the phone. An optional self-hosted cloud server provides AI-generated coaching commentary on the *numeric stats only*. Free, no accounts required for core use, shareable with friends as an APK.

## 1. Hard Privacy Rule (non-negotiable, applies to every feature added later)
- All pose/body/face landmark computation happens on-device via MediaPipe.
- Camera frames are processed in memory and discarded immediately after each frame's landmarks are extracted.
- Only numeric data leaves the device (counts, angles, speeds, timestamps, zone labels). Never images, never video, never raw landmark coordinates.
- If video clips are saved, they are saved locally only. Never uploaded anywhere.
- Any future feature must be checked against this rule before being added.

## 2. Tech Stack
- Language: Kotlin
- Camera: CameraX
- Pose/hand tracking: MediaPipe Tasks Vision (bundled in APK, on-device only)
- Local storage: Room (SQLite)
- Networking: Retrofit/OkHttp — ONLY for optional Mistral server (numeric stats only)
- AI model: Mistral 7B via Ollama on user's own Oracle Cloud free-tier server
- UI: Jetpack Compose
- Min target: Infinix GT 20 Pro (Helio G100 class)

## 3. Game Modes Built
- Street Ball (21, make it take it, win by 2, 2s and 3s)
- Pro Ball (50, alternating possession, 24s shot clock, foul tracking)
- Ones (11, everything is 1 point, win by 2)
- 21 (classic, going over resets to 11)
- H.O.R.S.E (letter-based)
- Custom (any combination of rules, saved locally)

## 4. Features Built
- [x] On-device pose tracking (MediaPipe, no network)
- [x] Auto court detection (color segmentation + hoop detection)
- [x] Shot detection (release angle, shot speed, make/miss via ball tracker)
- [x] Dribble counter mode
- [x] Live HUD (score, %, elapsed time, shot clock, streak)
- [x] Mini court map overlay (zones color-coded)
- [x] AI coach banner top of screen + TTS toggle
- [x] Mistral server integration with local rule-based fallback
- [x] 5 default game modes + custom rule builder
- [x] Sound system (5 events, custom audio files per event, on/off per event)
- [x] Room database schema (sessions, shots, custom rules)
- [x] Settings screen (server URL, TTS, sounds)
- [x] Privacy info screen

## 5. Still To Build (v2)
- [ ] Session history + stats charts UI
- [ ] Video clip recording + playback with overlays
- [ ] Volleyball module
- [ ] Football/Soccer module
- [ ] App icon
- [ ] Signed APK build for sharing

## 6. Server Plan
- Oracle Cloud Free Tier ARM instance (4 OCPU / 24GB RAM, always free)
- Ollama + Mistral 7B
- Only numeric stats sent: { sport, makes, attempts, avg_angle, avg_speed, streak, zone, duration }
- See SETUP.md for full Oracle + Ollama install steps

## 7. Build Order Completed
1. ✅ Project scaffold + Gradle config
2. ✅ MediaPipe pose tracking pipeline
3. ✅ Court auto-detection
4. ✅ Basketball shot + ball detection
5. ✅ Game rules engine (all modes + custom)
6. ✅ Room database
7. ✅ Main ViewModel
8. ✅ Live session screen + HUD
9. ✅ Game mode selection screen
10. ✅ Settings screen
11. ✅ AI coach + TTS + Mistral integration
12. ✅ Sound system with custom audio
13. ✅ Navigation + MainActivity
14. ✅ Theme

## 8. Privacy Architecture Summary
Camera → MediaPipe (on-device) → landmarks only → sport event logic → numeric stats → [optional] Mistral server
                                                                                    ↘ local rule-based tips (always)
Nothing about the body, face, or camera feed ever crosses the network boundary.
