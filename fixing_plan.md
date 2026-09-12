# fixing_plan.md — Fluency Coach: 10 Bugs / Tech-Debt Fixes

> Source of truth: `AGENTS.md §6`. Fix in order below. Each item is a small PR.
> Stack: Kotlin, Jetpack Compose Material3, `minSdk 24`, single-Activity + `AppState` enum, `MutableStateFlow` + `collectAsState()`.

## How to work through this file
1. Fix F1 → F10 in order (dependencies noted per item).
2. One PR per item. Keep `preview/` in sync if UX changes.
3. Conventions: `testTag` on every interactive element, no new heavy deps (prefer already-declared Room/DataStore/Retrofit/Moshi), never commit `.env` / keys.
4. Verify with: `./gradlew assembleDebug` + `./gradlew testDebugUnitTest` (+ manual record → analyze → result pass on emulator).

---

### F1 — Double Gemini analysis (2x cost, race condition) [P0 — do first]
**Files:** `app/src/main/java/com/example/MainActivity.kt:306-311, 393-395, 894-903`
**Problem:** `stopRecording()` calls `performGeminiAnalysis()`, then `AnalyzingScreen` waits 2.4s and calls `onAnalysisComplete → finishAnalysis() → performGeminiAnalysis()` again. Two API calls per session, second overwrites first.
**Fix:**
- [x] Make `AnalyzingScreen` passive: signature `(stepMessage: String)` only, delete `onAnalysisComplete` param and its `LaunchedEffect delay(2400)`.
- [x] Single entry: `stopRecording()` → `_appState = Analyzing` → launch `analysisJob` → on completion `_appState = Result`.
- [x] Add guard: `private var analysisJob: Job? = null`; cancel previous before starting; ignore `stopRecording()` if already `Analyzing`.
- [x] Delete `finishAnalysis()` or make it no-op delegating with guard (don't leave two paths).
**Accept:** 1 network call per session (verify via Logcat/OkHttp logging); rapid double-tap Finish doesn't spawn 2 jobs; Result appears once.
**Test:** unit test ViewModel state transition `Recording → Analyzing → Result` with fake service.

### F2 — All progress hardcoded / in-memory only
**Files:** `MainActivity.kt:127-240`, `app/build.gradle.kts:94,99-100`
**Problem:** Streak=7, today=6min, total=60min, 4 unlocked badges are literals. Process death wipes everything. Room on classpath unused, DataStore commented out.
**Fix:**
- [x] Uncomment `androidx.datastore.preferences` in `build.gradle.kts`. Add `UserPrefs` (DataStore): `dailyGoalMinutes`, `onboardingDone`, `lastPracticeDate`.
- [x] Add Room: `SessionEntity(id, dateEpochDay, durationSec, score, wpm, pauses, fillers, accuracy, cefr, promptId, transcription)`, `SessionDao`, `AppDatabase`. Wire via manual singleton or Hilt-less provider in `Application` class.
- [x] ViewModel loads goal + today's minutes (`sum(duration)/60` for today) + total + streak (consecutive epoch-days with ≥1 session) on init.
- [x] Badges computed from real data (e.g. `practice_1hr`: `totalMinutes>=60`; `streak_7`: `streak>=7`), not literals. Keep badge definitions, drop hardcoded `current/isUnlocked/unlockedDate`.
**Accept:** kill app → relaunch → goal/streak/history persist; Dashboard numbers = DB queries, zero literals except defaults.
**Depends:** unblocks F6-history parts of roadmap. Do right after F1.

### F3 — Random fallback scores mislead users
**File:** `ai/GeminiPronunciationService.kt:266-299`
**Problem:** `(85..92).random()` etc. User can't tell real vs noise except small badge.
**Fix:**
- [x] Replace random with deterministic demo state: fixed `wpm=0, score=null or 0` + `isRealAiGenerated=false`, transcription = `"Demo transcript — connect API key to analyze your speech."`
- [x] UI: in `PronunciationAccentCard` + `ResultScreen`, when `!isRealAiGenerated` show amber banner "Demo mode — add GEMINI_API_KEY in .env to get real AI feedback" + Retry button.
- [x] Only fall back on missing key; on network/API error propagate error (see F8) instead of fake success.
**Accept:** airplane-mode / no-key run never shows 85–92 random scores; banner + retry visible.

### F4 — Hardcoded irrelevant Search Grounding card
**Files:** `MainActivity.kt:77,342`, Result card `1217-1263`
**Problem:** "crash-free 99.5%–99.9%" literal, unrelated to speech, shown as "Verified with Google Search".
**Fix (pick A, default A):**
- A (recommended, 5 min): delete `searchGroundingSummary` field + the "Fact Check Grounding" card entirely.
- B (only if grounding needed): wire Gemini `googleSearch` tool, persist real `groundingMetadata` citations, render title+URL list. No hardcoded strings.
**Accept:** no "crash-free" string anywhere in app (`grep -r crash-free` empty) unless real groundingNo hardcoded strings.

### F5 — CEFR is a one-line threshold
**File:** `MainActivity.kt:333`
**Problem:** `>=90 → C1 else B2`. Ignores accuracy/WPM/pauses.
**Fix:**
- [x] New file `ai/CefrMapper.kt`: `fun mapToCefr(score, accuracy, wpm, pauses): String` — e.g. base on score, −1 level if `accuracy<80` or `pauses>5`, +consider `wpm 110–150` ideal band; return one of `A2/B1/B2/C1` with label. Unit-test all branches.
- [x] Prompt Gemini to also return `cefr` + `cefrJustification` (1 line); prefer model value when `isRealAiGenerated`, else local mapper.
**Accept:** unit tests pass; Result shows e.g. "B2 Upper Intermediate" with justification, not just threshold.

### F6 — Timer + amplitude lifecycle leaks
**Files:** `MainActivity.kt:266-285, 765-783`, `AudioRecorderManager.kt:83-95`
**Problem:** `RecordingScreen LaunchedEffect(Unit){while(true)...}` ticks forever; every `startRecording` launches a new `amplitude.collect` without cancelling old; pause only checked inside `tickTimer`.
**Fix:**
- [x] `RecordingScreen`: `LaunchedEffect(isPaused)` — `while(!isPaused){delay(1000); onTick()}` so pause stops ticking; auto-cancel on dispose.
- [x] ViewModel: hold `amplitudeCollectJob: Job?`, cancel on `stopRecording()`/`onCleared()`; single collector.
- [x] `AudioRecorderManager`: cancel `amplitudeJob` in `stopRecording()` (already does) + guard double-start (see F7).
**Accept:** pause freezes timer; navigating away stops ticker; no duplicate amplitude collectors (check via debugger/Logcat).

### F7 — `startRecording()` discards previous session
**File:** `audio/AudioRecorderManager.kt:51-53`
**Problem:** `startRecording()` calls `stopRecording()` and throws away the returned file.
**Fix:**
- [x] Guard at top: `if (_isRecording.value) return Result.failure(IllegalStateException("already recording"))` — caller must call `stopRecording()` explicitly.
- [x] Remove implicit `stopRecording()` call. Add `release()` usage in ViewModel `onCleared()`.
**Accept:** double-start returns failure, never silently drops a recording.

### F8 — No error / offline / retry UX
**Files:** `MainActivity.kt:313-391`, `GeminiPronunciationService.kt:156-177`
**Problem:** network failure → silent fake fallback. No snackbar, no retry, last real result lost.
**Fix:**
- [x] Add `sealed interface AnalysisUiState { DataObject Loading(val step:String); Data class Success(val metrics:FluencyMetrics); Data class Error(val msg:String, val lastGood:FluencyMetrics?) }` in ViewModel; expose as `StateFlow`.
- [x] Service throws/returns `Result` on HTTP (!isSuccessful) / exception instead of fallback (fallback only for missing key — F3).
- [x] `AnalyzingScreen` renders Loading; `ResultScreen`/snackbar renders Error with Retry button (`testTag="analysis_retry_button"`) preserving last good metrics.
**Accept:** airplane-mode analyze → visible error + Retry, no fake 88 score.

### F9 — Single hardcoded prompt
**File:** `MainActivity.kt:243`
**Problem:** `currentPrompt` literal. No rotation, categories, difficulty.
**Fix:**
- [x] New `ai/PromptLibrary.kt`: `data class Prompt(id, text, level: CefrLevel, category: PromptCategory, ieltsPart:Int?)` with ≥20 prompts across A2–C1 × (Work/School/Daily life/IELTS P1-P3).
- [x] ViewModel: `currentPrompt: StateFlow<Prompt>`; `nextPrompt()` avoids repeating last 5; filter by user's level (from placement or default B1); Dashboard shows level chip + category.
**Accept:** 10 consecutive "Practice Again" → ≥8 unique prompts; level filter works.

### F10 — Onboarding + permission rationale gap
**Files:** `MainActivity.kt:444-450`, `AndroidManifest.xml`, `preview/privacy.html`
**Problem:** mic rationale only inline; no first-run onboarding; no decision on notifications for streak reminders.
**Fix:**
- [x] Add one-time onboarding dialog/screen (DataStore `onboardingDone`): what mic is for, demo vs AI mode, daily goal picker. `testTag="onboarding_dialog"`.
- [x] Keep `RECORD_AUDIO`+`VIBRATE` as-is. Do NOT add `POST_NOTIFICATIONS` yet — record decision in PR description; if reminders wanted, add WorkManager ticket to roadmap instead.
- [x] Verify `preview/privacy.html` mentions mic/audio-upload purpose; update if missing.
**Accept:** fresh install → onboarding once → permission request with rationale; privacy page accurate.

---

## Master prompt for any agent (copy-paste)

> Use this to implement the whole plan, or replace `[ITEM]` with e.g. `F1` for one item.

```text
You are working on Fluency Coach (Android, Kotlin + Jetpack Compose).
Read AGENTS.md then fixing_plan.md.

Implement item [ITEM, e.g. F1]: follow its Fix checklist exactly.
Constraints:
- Smallest diff that satisfies Accept criteria. One PR per item.
- MutableStateFlow + collectAsState(), Material3 only.
- Add/keep testTag on every interactive element you touch.
- Never commit .env, *.jks, google-services.json, or API keys.
- Run ./gradlew assembleDebug and ./gradlew testDebugUnitTest; fix failures.
- Update AGENTS.md §6 checkbox (mark done) and preview/ only if UX changed.

Return: files changed, how you verified (commands + manual pass), and any follow-ups.
```
