# fluency_roadmap.md — What Actually Makes a Person Fluent

> Source of truth: `AGENTS.md §7`. Prerequisite: `fixing_plan.md` F1–F2 (single analysis, real persistence) before starting P0.
> Principle: the app **scores** today but doesn't **teach**. Every phase below must close the loop: attempt → feedback → targeted retry → measurable gain.
> Stack: Kotlin + Compose Material3, Room + DataStore, MediaPlayer/ExoPlayer + Android TTS, Gemini 2.5 Flash (+ Search Grounding / Live only where noted).

## Phase order (do top-down)
P0 teaches → P1 adds real skills → P2 retention → P3 trust/platform. Don't skip P0.

---

### P0 — Close the learning loop (highest fluency per effort)

#### P0.1 Playback + transcript display
**Why:** can't fix what you can't hear.
**Build:**
- [ ] `ResultScreen`: MediaPlayer playback for saved `.m4a` (`playТestTag="playback_button"`, seek bar, duration).
- [ ] Persist audio path in Room `SessionEntity.audioPath`; handle file-missing gracefully.
- [ ] Show transcript paragraph under player (word-timestamps later in P1.4).
**Accept:** record → result → play own audio end-to-end; survives rotation.

#### P0.2 Listen-and-repeat / shadowing mode
**Why:** #1 pronunciation driver.
**Build:**
- [ ] New `ShadowingScreen`: TTS plays model sentence (slow/normal toggle) → user records repeat → Gemini word-diff → highlight insert/delete/substitute.
- [ ] `testTag`s: `shadow_play_button`, `shadow_record_button`, `shadow_retry_button`.
- [ ] 10 starter sentences across A2–B2 in `PromptLibrary`.
**Accept:** complete one shadow round, see word-level diff + retry improves or re-scores.

#### P0.3 Targeted micro-drills from phonetic tips
**Why:** generic tips don't stick; 30s word drills do.
**Build:**
- [ ] Each `PhoneticTip` card gets "Drill this word" button → `DrillScreen(word, IPA)`: slow TTS → record → word-only re-score.
- [ ] Spaced-repetition queue: Room `WeakWord(word, failCount, nextDue)`; Dashboard "Practice weak words (n)".
**Accept:** weak word drilled twice → `failCount`/`nextDue` updates, word leaves queue when passed.

#### P0.4 Session history + progress graphs
**Why:** Dashboard "132 WPM avg" is a literal; motivation needs proof.
**Build:**
- [ ] `HistoryScreen` (Room → list of sessions: date, score, WPM, duration) + simple line/bar chart (no new chart dep — Compose Canvas).
- [ ] Dashboard computes real `avgWPM(last 7)`, `avgScore`, `totalMinutes` from DB.
**Accept:** 3 sessions → history lists 3, dashboard averages match DB.

#### P0.5 Placement test + adaptive levels
**Why:** fixed prompt kills growth.
**Build:**
- [ ] Onboarding test: 1 read-aloud + 60s free speech + 5 vocab items → Gemini grades → sets `userLevel` (A2–C1) in DataStore.
- [ ] `PromptLibrary` filters by level; level-up rule (e.g. 3 sessions ≥85 → offer level-up).
**Accept:** fresh install → placement → dashboard prompts match level; level-up offered after criteria.

### P1 — Real speaking skills (fluency ≠ good audio)

#### P1.1 Conversation mode (turn-taking)
- [ ] Gemini Live / streaming: AI asks follow-up, user answers 3–5 turns, interruption handling, final coherence score. New `ConversationScreen`.

#### P1.2 IELTS/TOEFL tracks
- [ ] Part 1/2/3 timed flows + 4-criterion rubric (fluency/coherence, lexical, grammar, pronunciation) instead of single 0–100.

#### P1.3 Grammar-in-speech corrections
- [ ] Show own sentence → corrected diff + 1-line rule + "say it again" retry. Replaces generic `recommendations` strings.

#### P1.4 Filler / pause coach
- [ ] Detect um/uh/like + pause map on transcript timeline; per-session filler goal + optional haptic nudge while recording.

#### P1.5 Vocabulary upgrades (CEFR+1)
- [ ] 3 swaps per transcript ("good → compelling") with example + say-it drill button.

### P2 — Retention (fluency needs daily reps)
- [ ] **Real streak:** date-based streak + streak-freeze + WorkManager daily reminder (decide `POST_NOTIFICATIONS` here, not earlier).
- [ ] **Weak-sound profile:** aggregate /θ/ vs /ð/, stress, intonation → Dashboard "Top 3 sounds to fix".
- [ ] **Offline-first:** cache prompts, queue uploads, on-device `SpeechRecognizer` for instant WPM/fillers without key.
- [ ] **Share progress:** score-card image share + weekly summary.

### P3 — Trust / platform
- [ ] Auth + Firestore sync (uncomment existing deps).
- [ ] Migrate raw `JSONObject` Gemini call → `firebase-ai` SDK + streaming + schema validation.
- [ ] Unit coverage: `parseFeedbackJson`, `CefrMapper`, streak calc, WPM estimator (today only `addition_isCorrect`).
- [ ] Accessibility + dark-theme pass (content descriptions, 48dp targets, contrast).

---

## Prompts for any agent (copy-paste)

**Whole P0 (one phase at a time, in order):**
```text
You are working on Fluency Coach (Kotlin + Jetpack Compose).
Read AGENTS.md, fixing_plan.md, then fluency_roadmap.md.
Prereq: F1+F2 from fixing_plan.md must be done — verify first.

Implement phase [PHASE, e.g. P0.1]: follow its Build checklist exactly.
Constraints: smallest diff for Accept criteria; MutableStateFlow + collectAsState();
Material3 only; testTag on every new button; Room+DataStore for persistence
(no in-memory literals); never commit secrets.
Run ./gradlew assembleDebug + ./gradlew testDebugUnitTest.
Update AGENTS.md §7 checkbox + preview/ if UX changed.
Return: files changed, verification, follow-ups.
```

**Single drill/feature (e.g. shadowing only):**
```text
Implement fluency_roadmap.md item P0.2 (shadowing mode) in Fluency Coach.
Read AGENTS.md first. Reuse PromptLibrary (F9) and SessionEntity (F2) if present;
create them minimally if missing. TTS via Android TextToSpeech, playback via
MediaPlayer. Include word-diff UI + retry + testTags listed in the roadmap.
Verify with assembleDebug + unit tests. Keep the diff small.
```
