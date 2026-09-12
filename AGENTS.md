# AGENTS.md — Fluency Coach (Open-Speech)

> Read this first. This is the shared context for every new agent working on this repo.

## 1. What this app is

**Fluency Coach** is an AI English speaking-coach Android app (native Jetpack Compose) + a static marketing/preview site.

Core loop today: **Dashboard → Timed Recording (30s/60s/90s/2m/Open) → AI Analyzing → Fluency Results**.
Results show: overall score, CEFR label, WPM / pauses / fillers / accuracy, transcription, pronunciation + accent scores, phonetic tips with IPA, feedback list, and milestone badges.

- Live site: `https://sunnydev07.github.io/Open-Speech/`
- APK: GitHub Releases (`preview/app-debug.apk` is the copy deployed to Pages)
- Package: `com.aistudio.fluencycoach.kxmlzp`, `minSdk 24`, `target/compileSdk 36`, JDK 17, Android Studio Koala+.

## 2. Repo layout

```
app/src/main/java/com/example/
  MainActivity.kt                  # AppState machine + ViewModel + all 4 screens (Dashboard/Recording/Analyzing/Result)
  ai/GeminiPronunciationService.kt # Gemini REST call (OkHttp + JSONObject), fallback generator, JSON parser
  audio/AudioRecorderManager.kt    # MediaRecorder → .m4a in cacheDir + amplitude StateFlow
  ui/components/
    SpeakingTimerComponent.kt      # TimerPreset enum, countdown/stopwatch ring, preset selector
    PronunciationAccentCard.kt     # Dual score meters + phonetic tip expandables
    DailyGoalComponent.kt          # DailyGoalCard + DailyGoalDialog + presets
    MilestoneBadgeComponent.kt     # MilestoneBadge model + carousel + detail dialog + reward banner
  ui/effects/EffectComponents.kt   # SimpleAudioVisualizer + subtleClick modifier
  ui/theme/ (Color.kt, Theme.kt, Type.kt)
  util/HapticFeedbackHelper.kt     # Compose haptics + Vibrator wrapper
app/src/main/res/                  # strings, themes, launcher icons
preview/                           # GitHub Pages source: index.html, privacy.html, screenshots/, app-debug.apk, qr-download.png
.github/workflows/pages.yml        # Deploys preview/ to Pages on push to main
.env.example                       # GEMINI_API_KEY=MY_GEMINI_API_KEY (Secrets Gradle plugin reads .env)
```

Key config: `app/build.gradle.kts` (Compose BOM, Navigation, Room, Retrofit/Moshi/OkHttp, Firebase AI + AppCheck, KSP, Roborazzi). Room + Retrofit are declared but **Room/Firestore/Auth/DataStore are currently unused/commented out** — persistence is in-memory only.

## 3. Architecture (current)

- Single-`Activity`, no Navigation component in use. Navigation = `enum AppState { Dashboard, Recording, Analyzing, Result }` + `Crossfade` in `FluencyApp()` (`MainActivity.kt:58`).
- Single `FluencyViewModel` holds **all** state: timer, audio manager, Gemini service, metrics, daily goal, badges/streak (all `MutableStateFlow`).
- Audio path: `AudioRecorderManager.startRecording()` (AAC/MPEG-4, 128kbps, 44.1kHz) → `stopRecording(): File?` → base64 `inlineData audio/mp4` → `POST gemini-2.5-flash:generateContent` → strict-JSON prompt → `parseFeedbackJson()` → `FluencyMetrics`.
- No-API-key path: `generateDiagnosticFallback()` returns **randomized** scores + templated transcription. `isRealAiGenerated=false` flags it in UI.
- Secrets: Secrets-Gradle-Plugin maps `.env` → `BuildConfig.GEMINI_API_KEY`, fallback `.env.example`.

## 4. Build / run / test

```bash
# App (open folder in Android Studio, or CLI)
./gradlew assembleDebug                    # APK → app/build/outputs/apk/debug/
adb install preview/app-debug.apk          # prebuilt APK (~23 MB)

# Tests
./gradlew testDebugUnitTest                # JUnit + Robolectric + Roborazzi screenshot tests
./gradlew connectedDebugAndroidTest        # on-device Compose tests

# Website preview
cd preview
py -m http.server 8001                     # open http://localhost:8001/index.html
```

Pages deploy: push to `main` → `pages.yml` publishes `preview/`. First-time repo setup: Settings → Pages → Source: **GitHub Actions**.

## 5. Conventions for agents

- Kotlin + Material3 + Compose only. Keep screens in `MainActivity.kt` small — prefer extracting to `ui/components/` or `ui/screens/` (create `ui/screens/` when a screen exceeds ~200 lines).
- State: `MutableStateFlow` in ViewModel + `collectAsState()` in Compose. No `var` state in composables except UI-local (e.g. expanded index).
- Every interactive element needs a `testTag` (existing pattern: `start_practice_button`, `stop_recording_button`, `daily_goal_*`, `badge_*`, `gemini_pronunciation_accent_card`).
- Feedback copy: supportive, actionable, 2–3 sentences per section. Phonetic tips always include `word + IPA + issue + tip`.
- Never commit `.env`, `*.jks`, `google-services.json`, or real API keys. `.env.example` keeps placeholder `MY_GEMINI_API_KEY`.
- Keep `preview/` in sync when UX changes: update `index.html` simulator + `screenshots/` + rebuilt APK.
- Don't add heavy new deps without asking — Room, DataStore, Coil, Credential Manager entries already exist (commented) and should be preferred.

## 6. Code review — known bugs / tech debt (fix first)

1. **Double Gemini analysis (wasted + billed 2x).** `stopRecording()` (`MainActivity.kt:306`) calls `performGeminiAnalysis()`, then `AnalyzingScreen` (`MainActivity.kt:899`) fires `onAnalysisComplete → finishAnalysis() → performGeminiAnalysis()` again after 2.4s. Fix: single entry — ViewModel drives `AppState.Analyzing → Result`; Analyzing screen must be passive (no callback), or gate with a job/flag.
2. **All progress is fake/hardcoded.** Streak (7), today (6 min), total (60 min), 4 unlocked badges are literals (`MainActivity.kt:142-240`). Nothing survives process death. Room is on classpath but unused; DataStore is commented out. Fix: persist `dailyGoal`, session log, streak with Room (sessions table) + DataStore (goal, onboarding). Compute streak from real dates.
3. **Fallback scores are random `(85..92).random()`** (`GeminiPronunciationService.kt:266`). Users can't distinguish real vs. noise except a small badge. Fix: label clearly "Demo mode — connect API key", stop randomizing (return deterministic `null-score` state), add retry UI.
4. **Hardcoded irrelevant grounding text.** `searchGroundingSummary` about "crash-free 99.5%–99.9%" is a literal in two places (`MainActivity.kt:77,342`), not from Search Grounding. Fix: either wire `googleSearch` tool + render real citations, or delete the card.
5. **CEFR is a one-line threshold** (`>=90 → C1 else B2`). Fix: derive from rubric (score + accuracy + WPM + pauses) or return model-graded CEFR with justification.
6. **Recording timer leak / weak lifecycle.** `RecordingScreen` uses `LaunchedEffect(Unit) { while(true){delay(1000); onTick()} }` — survives pause correctly only by inner check, and isn't tied to lifecycle. Fix: `LaunchedEffect(isPaused)` + `DisposableEffect`/lifecycle-aware ticker, cancel amplitude collection on stop (currently a new `collect` launches every `startRecording` without cancelling the old one).
7. **`AudioRecorderManager.startRecording()` calls `stopRecording()` first** and discards the result — can delete the just-recorded file reference. Fix: explicit `release()`/guard `if (isRecording) return`.
8. **No error / offline / retry UX.** Network failure silently falls back to fake data. Fix: sealed `AnalysisUiState (Loading/Success/Error)`, snackbar + Retry, keep last real result.
9. **Single hardcoded prompt** (`currentPrompt`). No prompt rotation, categories, or difficulty levels.
10. **Privacy page + permissions:** mic rationale exists inline but no first-run onboarding; `VIBRATE` + `RECORD_AUDIO` declared correctly, but no `POST_NOTIFICATIONS` plan for streak reminders (decide before adding).

## 7. Feature roadmap — what actually makes a person fluent

Ordered by fluency-impact per effort. Pick top-down.

### P0 — Close the learning loop (app doesn't teach today, it only scores)
- [ ] **Playback + transcript sync.** Play own `.m4a` back, tap word → seek. Without hearing yourself, pronunciation tips don't stick.
- [ ] **Listen-and-repeat / shadowing mode.** TTS model sentence → user repeats → word-level diff (insert/delete/substitute highlighting). This is the #1 pronunciation driver.
- [ ] **Targeted drill-downs from phonetic tips.** Each tip ("consequently /ˈkɒn.../") gets a 30s micro-drill: slow TTS → record → re-score only that word. Spaced repetition queue for weak words.
- [ ] **Session history + progress graphs.** Store every session (Room): score/WPM/pauses/fillers over time, weak-sound trends. Dashboard "Avg Pacing 132 WPM" is currently a literal — compute it.
- [ ] **Placement test + adaptive levels.** 3-minute onboarding (read-aloud + 60s free speech + 5 vocab items) → set starting CEFR + prompt difficulty. Prompts must adapt; fixed prompt kills growth.

### P1 — Real speaking skills (fluency ≠ good audio)
- [ ] **Conversation mode (turn-taking).** Gemini Live / streaming audio: AI asks follow-up, user answers, interruption handling. Monologue drills alone don't build interactional fluency.
- [ ] **IELTS/TOEFL Part 1/2/3 tracks.** Timed parts, band-descriptor rubric (fluency/coherence, lexical resource, grammar range, pronunciation) instead of one 0–100 number.
- [ ] **Grammar-in-speech corrections.** Show *own sentence → corrected sentence* diff + 1-line rule + "say it again" retry. Current `recommendations` are generic strings.
- [ ] **Filler/pause coach.** Detect um/uh/like + pause map on transcript timeline; per-session "filler count" goal with haptic nudge in practice.
- [ ] **Vocabulary upgrade suggestions.** For each transcript, propose 3 CEFR+1 swaps (e.g. "good → compelling") with example sentence + say-it drill.

### P2 — Retention (fluency needs daily reps)
- [ ] **Streak that means something.** Real date-based streak + streak-freeze + daily reminder (WorkManager). Current 7-day streak is fake.
- [ ] **Personal weak-sound profile.** Aggregate /θ/ vs /ð/, word stress, intonation over sessions; dashboard shows "Top 3 sounds to fix" with drills.
- [ ] **Offline-first.** Cache prompts + queue uploads; on-device `SpeechRecognizer` for instant WPM/filler counts even without API key.
- [ ] **Export/share progress.** Shareable score card, weekly report — drives consistency.

### P3 — Trust / platform
- [ ] Auth + cloud sync (uncomment Firebase Auth/Firestore lines, add login), multi-device history.
- [ ] Replace raw `JSONObject` Gemini call with `firebase-ai` SDK + streaming + proper `responseMimeType`/schema validation.
- [ ] Real unit coverage: `parseFeedbackJson`, CEFR mapper, streak calculator, WPM estimator (today only `addition_isCorrect` exists).
- [ ] Accessibility pass (content descriptions on all icon-only buttons, 48dp targets), dark-theme contrast check.

## 8. Suggested next tickets (copy-paste ready)

1. `fix: single Gemini analysis per session` — remove callback loop in AnalyzingScreen, ViewModel owns transition.
2. `feat: persist sessions + goals (Room + DataStore)` — kill all hardcoded progress literals.
3. `feat: prompt library (20+ prompts × A2–C1 × IELTS parts)` + rotation, replaces `currentPrompt` literal.
4. `feat: playback + shadowing drill` — ExoPlayer/MediaPlayer for `.m4a` + TTS reference.
5. `feat: history screen + progress chart` — real avg WPM/score from DB.

---
*Teams: prefer small PRs per ticket above. Update this file when architecture changes (new screens dir, DB schema, or AI model swap).*
