package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.AnalysisException
import com.example.ai.CefrLevel
import com.example.ai.CefrMapper
import com.example.ai.GeminiPronunciationService
import com.example.ai.PhoneticTip
import com.example.ai.PracticePrompt
import com.example.ai.PromptLibrary
import com.example.audio.AudioRecorderManager
import com.example.data.FluencyDatabase
import com.example.data.SessionEntity
import com.example.data.StreakCalculator
import com.example.data.UserPrefs
import com.example.ui.components.*
import com.example.ui.effects.SimpleAudioVisualizer
import com.example.ui.effects.subtleClick
import com.example.ui.theme.*
import com.example.util.rememberHapticHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

enum class AppState {
    Dashboard, Recording, Analyzing, Result
}

/** Analysis status for the Analyzing screen and error/retry UX (F8). */
sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Loading(val step: String) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

data class FluencyMetrics(
    val score: Int = 86,
    val cefr: String = "B2 Upper Intermediate",
    val cefrJustification: String = "",
    val wpm: Int = 132,
    val pauses: Int = 2,
    val fillers: Int = 1,
    val accuracy: Int = 94,
    val durationSeconds: Int = 58,
    val targetDurationSeconds: Int = 60,
    val transcription: String = "In my previous project, we faced a tight deadline when delivering our mobile app. The primary bottleneck was unexpected performance lag on entry-level devices. I organized an emergency triage session with the engineering team, where we systematically profiled GPU render times and identified memory leaks in our list recycling. Consequently, we refactored the image loading pipeline and successfully launched on schedule with a 99.8% crash-free rate.",
    val feedback: List<String> = listOf(
        "Strong natural pacing (132 WPM) well within the target 120–150 range.",
        "Smooth transition using 'consequently' to connect cause and effect.",
        "Consider using 'under tight deadlines' instead of 'in a tight deadline' for idiomatic precision."
    ),
    val pronunciationScore: Int = 88,
    val accentClarityScore: Int = 84,
    val detectedAccentProfile: String = "General American cadence with clear consonant articulation",
    val pronunciationFeedback: String = "Clear syllable stress on main vocabulary words. Final plosive consonants were articulated crisply.",
    val accentFeedback: String = "Natural intonation contour with expressive pitch shifts on emphasis words.",
    val phoneticTips: List<PhoneticTip> = listOf(
        PhoneticTip(
            word = "consequently",
            phonetic = "/ˈkɒn.sɪ.kwənt.li/",
            issue = "Primary stress placed on 2nd syllable",
            tip = "Emphasize the first syllable 'CON-', keeping '-se-quent-ly' light."
        ),
        PhoneticTip(
            word = "bottleneck",
            phonetic = "/ˈbɒt.əl.nek/",
            issue = "Glottal stop softened on 'tt'",
            tip = "Articulate a crisp flap 't' for standard American clarity."
        ),
        PhoneticTip(
            word = "prioritized",
            phonetic = "/praɪˈɔːr.ɪ.taɪzd/",
            issue = "Vowel shortening on initial diphthong",
            tip = "Elongate the /aɪ/ diphthong in 'PRY' before transitioning."
        )
    ),
    val isRealAiGenerated: Boolean = true
)

class FluencyViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPrefs(application.applicationContext)
    private val sessionDao = FluencyDatabase.get(application.applicationContext).sessionDao()

    private val _appState = MutableStateFlow(AppState.Dashboard)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    // Timer states
    private val _selectedPreset = MutableStateFlow(TimerPreset.SIXTY_SEC)
    val selectedPreset: StateFlow<TimerPreset> = _selectedPreset.asStateFlow()

    private val _totalTargetSeconds = MutableStateFlow(60)
    val totalTargetSeconds: StateFlow<Int> = _totalTargetSeconds.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _metrics = MutableStateFlow(FluencyMetrics())
    val metrics: StateFlow<FluencyMetrics> = _metrics.asStateFlow()

    // Daily Goal State & Progress (F2: loaded from DataStore + Room, never hardcoded)
    private val _dailyGoalMinutes = MutableStateFlow(15) // default until DataStore loads
    val dailyGoalMinutes: StateFlow<Int> = _dailyGoalMinutes.asStateFlow()

    // Audio & Gemini AI state
    var audioRecorderManager: AudioRecorderManager? = null
    private val geminiService = GeminiPronunciationService()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    // F8: single source of truth for analysis status (replaces the fixed-delay callback).
    private val _analysisUiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisUiState: StateFlow<AnalysisUiState> = _analysisUiState.asStateFlow()

    // F1: single in-flight analysis job; F6: single amplitude collector.
    private var analysisJob: Job? = null
    private var amplitudeCollectJob: Job? = null

    private var recordedAudioFile: File? = null

    private val _todayPracticedMinutes = MutableStateFlow(0)
    val todayPracticedMinutes: StateFlow<Int> = _todayPracticedMinutes.asStateFlow()

    private val _showGoalDialog = MutableStateFlow(false)
    val showGoalDialog: StateFlow<Boolean> = _showGoalDialog.asStateFlow()

    // Milestone Badges & Rewards (F2: computed from the Room session log)
    private val _totalPracticeMinutes = MutableStateFlow(0)
    val totalPracticeMinutes: StateFlow<Int> = _totalPracticeMinutes.asStateFlow()

    private val _streakDays = MutableStateFlow(0)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private val _avgWpm = MutableStateFlow(0)
    val avgWpm: StateFlow<Int> = _avgWpm.asStateFlow()

    private val _selectedBadge = MutableStateFlow<MilestoneBadge?>(null)
    val selectedBadge: StateFlow<MilestoneBadge?> = _selectedBadge.asStateFlow()

    private val _recentUnlockedBadge = MutableStateFlow<MilestoneBadge?>(null)
    val recentUnlockedBadge: StateFlow<MilestoneBadge?> = _recentUnlockedBadge.asStateFlow()

    private val _badges = MutableStateFlow(baseBadges())
    val badges: StateFlow<List<MilestoneBadge>> = _badges.asStateFlow()

    // F9: prompt library + rotation; F10: user level + onboarding.
    private val _userLevel = MutableStateFlow(CefrLevel.B1)
    val userLevel: StateFlow<CefrLevel> = _userLevel.asStateFlow()

    private val _currentPrompt = MutableStateFlow(
        PromptLibrary.prompts.first { it.id == "work_b1_1" }
    )
    val currentPrompt: StateFlow<PracticePrompt> = _currentPrompt.asStateFlow()
    private val recentPromptIds = ArrayDeque<String>(6)

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            _dailyGoalMinutes.value = prefs.dailyGoalMinutes.first()
            _userLevel.value = prefs.userLevel.first()
            _showOnboarding.value = !prefs.onboardingDone.first()
            // Start follow-up collectors so later changes (e.g. goal edits) stick.
            launch { prefs.dailyGoalMinutes.collect { _dailyGoalMinutes.value = it } }
            launch { prefs.userLevel.collect { _userLevel.value = it } }
            refreshProgress()
        }
    }

    /** Recomputes all progress numbers from the Room session log (F2). */
    private suspend fun refreshProgress() {
        val today = currentEpochDay()
        _totalPracticeMinutes.value = (sessionDao.totalDurationSec() / 60).toInt()
        _todayPracticedMinutes.value = (sessionDao.durationSecOn(today) / 60).toInt()
        _streakDays.value = StreakCalculator.computeStreak(sessionDao.activeEpochDays(), today)
        _avgWpm.value = sessionDao.avgWpmReal().toInt()

        val sessionCount = sessionDao.count()
        val totalMinutes = _totalPracticeMinutes.value
        val bestScore = sessionDao.bestScoreReal()
        val pacingSessions = sessionDao.pacingSessionCount()
        val longestSec = sessionDao.longestSessionSec()
        val streak = _streakDays.value

        val previouslyUnlocked = _badges.value.filter { it.isUnlocked }.map { it.id }.toSet()
        _badges.value = _badges.value.map { badge ->
            when (badge.id) {
                "streak_7" -> badge.copy(current = streak, isUnlocked = streak >= 7)
                "practice_1hr" -> badge.copy(current = totalMinutes, isUnlocked = totalMinutes >= 60)
                "first_drill" -> badge.copy(current = minOf(sessionCount, 1), isUnlocked = sessionCount >= 1)
                "pacing_master" -> badge.copy(current = minOf(pacingSessions, 3), isUnlocked = pacingSessions >= 3)
                "fluency_ace" -> badge.copy(current = bestScore, isUnlocked = bestScore >= 90)
                "long_turn_pro" -> badge.copy(current = minOf(longestSec, 120), isUnlocked = longestSec >= 120)
                else -> badge
            }
        }
        // Celebration banner only for badges unlocked by the latest session (F2).
        _recentUnlockedBadge.value =
            _badges.value.firstOrNull { it.isUnlocked && it.id !in previouslyUnlocked }
    }

    private fun currentEpochDay(): Long = System.currentTimeMillis() / 86_400_000L

    fun selectBadge(badge: MilestoneBadge?) {
        _selectedBadge.value = badge
    }

    fun setDailyGoalMinutes(minutes: Int) {
        val coerced = minutes.coerceIn(1, 120)
        _dailyGoalMinutes.value = coerced
        viewModelScope.launch { prefs.setDailyGoalMinutes(coerced) }
    }

    fun completeOnboarding() {
        _showOnboarding.value = false
        viewModelScope.launch { prefs.setOnboardingDone() }
    }

    fun openGoalDialog() {
        _showGoalDialog.value = true
    }

    fun closeGoalDialog() {
        _showGoalDialog.value = false
    }

    fun selectPreset(preset: TimerPreset) {
        _selectedPreset.value = preset
        _totalTargetSeconds.value = preset.durationSeconds
    }

    /** F9: rotates to the next prompt for the user's level, avoiding recent repeats. */
    fun requestNewPrompt() {
        recentPromptIds.addLast(_currentPrompt.value.id)
        while (recentPromptIds.size > 5) recentPromptIds.removeFirst()
        _currentPrompt.value = PromptLibrary.next(recentPromptIds.toSet(), _userLevel.value)
    }

    fun startRecording(context: Context? = null) {
        // F1: never start a new session while analysis is in flight.
        if (_appState.value == AppState.Analyzing) return
        analysisJob?.cancel()
        _analysisUiState.value = AnalysisUiState.Idle
        _totalTargetSeconds.value = _selectedPreset.value.durationSeconds
        _elapsedSeconds.value = 0
        _isPaused.value = false
        _appState.value = AppState.Recording

        if (context != null) {
            val manager = audioRecorderManager ?: AudioRecorderManager(context.applicationContext, viewModelScope).also {
                audioRecorderManager = it
            }
            if (manager.hasRecordPermission()) {
                // F7: startRecording now fails fast when already recording.
                if (manager.startRecording().isSuccess) {
                    // F6: single amplitude collector — cancel the previous one first.
                    amplitudeCollectJob?.cancel()
                    amplitudeCollectJob = viewModelScope.launch {
                        manager.amplitude.collect { amp ->
                            _audioAmplitude.value = amp
                        }
                    }
                }
            }
        }
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun addSeconds(extraSeconds: Int) {
        _totalTargetSeconds.value += extraSeconds
    }

    fun tickTimer() {
        if (_appState.value != AppState.Recording || _isPaused.value) return
        _elapsedSeconds.value += 1
        // Auto stop when countdown finishes for non-freeflow preset
        val target = _totalTargetSeconds.value
        if (target > 0 && _elapsedSeconds.value >= target) {
            stopRecording()
        }
    }

    fun stopRecording() {
        // F1: single entry into analysis — ignore double taps / timer race.
        if (_appState.value != AppState.Recording) return
        _appState.value = AppState.Analyzing
        recordedAudioFile = audioRecorderManager?.stopRecording()
        amplitudeCollectJob?.cancel()
        amplitudeCollectJob = null
        _audioAmplitude.value = 0f
        launchAnalysis()
    }

    /** F8: retry after an error, keeping the recorded file and timer values. */
    fun retryAnalysis() {
        if (_appState.value == AppState.Analyzing || recordedAudioFile == null) return
        _appState.value = AppState.Analyzing
        launchAnalysis()
    }

    fun clearAnalysisError() {
        if (_analysisUiState.value is AnalysisUiState.Error) {
            _analysisUiState.value = AnalysisUiState.Idle
        }
    }

    private fun setLoadingStep(step: String) {
        _analysisUiState.value = AnalysisUiState.Loading(step)
    }

    /**
     * F1: the ONLY place that runs Gemini analysis. Guarded by [analysisJob] so
     * at most one analysis runs per session — the Analyzing screen is passive.
     */
    private fun launchAnalysis() {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            setLoadingStep("Sending microphone recording to Gemini AI...")
            delay(500)
            setLoadingStep("Evaluating pronunciation phonemes, syllable stress & accent...")

            val elapsed = _elapsedSeconds.value.coerceAtLeast(10)
            val target = _totalTargetSeconds.value
            val prompt = _currentPrompt.value

            try {
                val feedbackResult = geminiService.analyzeSpeech(
                    audioFile = recordedAudioFile,
                    referencePrompt = prompt.text,
                    elapsedSeconds = elapsed
                )

                setLoadingStep("Synthesizing pronunciation & accent feedback...")
                delay(300)

                // F5: prefer the model-graded CEFR when real, else the local rubric.
                val cefrLabel: String
                val cefrJustification: String
                if (feedbackResult.isRealAiGenerated && feedbackResult.cefr != null) {
                    cefrLabel = feedbackResult.cefr
                    cefrJustification = feedbackResult.cefrJustification
                } else {
                    val level = CefrMapper.mapToCefr(
                        score = feedbackResult.pronunciationScore,
                        accuracy = feedbackResult.accuracy,
                        wpm = feedbackResult.wpm,
                        pauses = feedbackResult.pauses
                    )
                    cefrLabel = level.label
                    cefrJustification = ""
                }

                _metrics.value = FluencyMetrics(
                    score = feedbackResult.pronunciationScore,
                    cefr = cefrLabel,
                    cefrJustification = cefrJustification,
                    wpm = feedbackResult.wpm,
                    pauses = feedbackResult.pauses,
                    fillers = feedbackResult.fillers,
                    accuracy = feedbackResult.accuracy,
                    durationSeconds = elapsed,
                    targetDurationSeconds = target,
                    transcription = feedbackResult.transcription,
                    feedback = feedbackResult.recommendations,
                    pronunciationScore = feedbackResult.pronunciationScore,
                    accentClarityScore = feedbackResult.accentClarityScore,
                    detectedAccentProfile = feedbackResult.detectedAccentProfile,
                    pronunciationFeedback = feedbackResult.pronunciationFeedback,
                    accentFeedback = feedbackResult.accentFeedback,
                    phoneticTips = feedbackResult.phoneticTips,
                    isRealAiGenerated = feedbackResult.isRealAiGenerated
                )

                // F2: persist the session, then recompute all progress from the DB.
                sessionDao.insert(
                    SessionEntity(
                        epochDay = currentEpochDay(),
                        timestamp = System.currentTimeMillis(),
                        durationSec = elapsed,
                        score = feedbackResult.pronunciationScore,
                        wpm = feedbackResult.wpm,
                        pauses = feedbackResult.pauses,
                        fillers = feedbackResult.fillers,
                        accuracy = feedbackResult.accuracy,
                        cefr = cefrLabel,
                        promptId = prompt.id,
                        isDemo = !feedbackResult.isRealAiGenerated
                    )
                )
                refreshProgress()

                _analysisUiState.value = AnalysisUiState.Idle
                _appState.value = AppState.Result
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnalysisException) {
                // F8: surface the error — never silently substitute fake scores.
                _analysisUiState.value =
                    AnalysisUiState.Error(e.message ?: "Analysis failed. Retry when online.")
                _appState.value = AppState.Dashboard
            } catch (e: Exception) {
                _analysisUiState.value =
                    AnalysisUiState.Error("Something went wrong (${e.message}). Retry the analysis.")
                _appState.value = AppState.Dashboard
            }
        }
    }

    fun backToDashboard() {
        _appState.value = AppState.Dashboard
    }

    override fun onCleared() {
        analysisJob?.cancel()
        amplitudeCollectJob?.cancel()
        audioRecorderManager?.release()
        super.onCleared()
    }

    companion object {
        /** Static badge definitions; progress comes from [refreshProgress] (F2). */
        fun baseBadges(): List<MilestoneBadge> = listOf(
            MilestoneBadge(
                id = "streak_7",
                title = "7-Day Streak",
                description = "Practiced speaking consistently for 7 consecutive days without skipping.",
                category = "Consistency",
                current = 0,
                target = 7,
                unit = "days",
                icon = Icons.Default.Whatshot,
                accentColor = Color(0xFFEA580C),
                isUnlocked = false
            ),
            MilestoneBadge(
                id = "practice_1hr",
                title = "1 Hour Practiced",
                description = "Reached 60 minutes of cumulative active speaking practice time.",
                category = "Endurance",
                current = 0,
                target = 60,
                unit = "min",
                icon = Icons.Default.HourglassBottom,
                accentColor = BluePrimary,
                isUnlocked = false
            ),
            MilestoneBadge(
                id = "first_drill",
                title = "First Drill",
                description = "Completed your initial timed fluency practice drill.",
                category = "Foundation",
                current = 0,
                target = 1,
                unit = "drill",
                icon = Icons.Default.WorkspacePremium,
                accentColor = SuccessGreen,
                isUnlocked = false
            ),
            MilestoneBadge(
                id = "pacing_master",
                title = "Pacing Master",
                description = "Maintain speech rate within target 120–150 WPM for 3 sessions.",
                category = "Fluency",
                current = 0,
                target = 3,
                unit = "sessions",
                icon = Icons.Default.Speed,
                accentColor = Color(0xFF7C3AED),
                isUnlocked = false
            ),
            MilestoneBadge(
                id = "fluency_ace",
                title = "Fluency Ace",
                description = "Score 90+ overall fluency rating with high grammar accuracy.",
                category = "Excellence",
                current = 0,
                target = 90,
                unit = "score",
                icon = Icons.Default.EmojiEvents,
                accentColor = WarningAmber,
                isUnlocked = false
            ),
            MilestoneBadge(
                id = "long_turn_pro",
                title = "Long-Turn Pro",
                description = "Complete a demanding 2-minute IELTS long-turn speaking drill.",
                category = "Challenge",
                current = 0,
                target = 120,
                unit = "sec",
                icon = Icons.Default.Mic,
                accentColor = Color(0xFF4F46E5),
                isUnlocked = false
            )
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluencyCoachTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BackgroundLight,
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    FluencyApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun FluencyApp(modifier: Modifier = Modifier, viewModel: FluencyViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.appState.collectAsState()
    val badges by viewModel.badges.collectAsState()
    val selectedBadge by viewModel.selectedBadge.collectAsState()
    val streakDays by viewModel.streakDays.collectAsState()
    val totalPracticeMinutes by viewModel.totalPracticeMinutes.collectAsState()
    val recentUnlockedBadge by viewModel.recentUnlockedBadge.collectAsState()
    val dailyGoalMinutes by viewModel.dailyGoalMinutes.collectAsState()
    val todayPracticedMinutes by viewModel.todayPracticedMinutes.collectAsState()
    val showGoalDialog by viewModel.showGoalDialog.collectAsState()
    val audioAmplitude by viewModel.audioAmplitude.collectAsState()
    val analysisUiState by viewModel.analysisUiState.collectAsState()
    val currentPrompt by viewModel.currentPrompt.collectAsState()
    val userLevel by viewModel.userLevel.collectAsState()
    val avgWpm by viewModel.avgWpm.collectAsState()
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    val analysisStepMessage = (analysisUiState as? AnalysisUiState.Loading)?.step
        ?: "Analyzing your speech with Gemini AI..."
    val analysisError = (analysisUiState as? AnalysisUiState.Error)?.message

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasAudioPermission = granted
            viewModel.startRecording(context)
        }
    )

    Crossfade(
        targetState = state,
        label = "AppStateCrossfade",
        animationSpec = tween(250),
        modifier = modifier.fillMaxSize()
    ) { currentState ->
        when (currentState) {
            AppState.Dashboard -> DashboardScreen(
                prompt = currentPrompt,
                userLevel = userLevel,
                selectedPreset = viewModel.selectedPreset.collectAsState().value,
                badges = badges,
                streakDays = streakDays,
                totalPracticeMinutes = totalPracticeMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                todayPracticedMinutes = todayPracticedMinutes,
                avgWpm = avgWpm,
                hasRecordPermission = hasAudioPermission,
                analysisError = analysisError,
                onRetryAnalysis = { viewModel.retryAnalysis() },
                onDismissError = { viewModel.clearAnalysisError() },
                onNewPrompt = { viewModel.requestNewPrompt() },
                onOpenGoalDialog = { viewModel.openGoalDialog() },
                onBadgeClick = { viewModel.selectBadge(it) },
                onSelectPreset = { viewModel.selectPreset(it) },
                onStartPractice = {
                    if (hasAudioPermission) {
                        viewModel.startRecording(context)
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
            AppState.Recording -> RecordingScreen(
                prompt = currentPrompt.text,
                totalDuration = viewModel.totalTargetSeconds.collectAsState().value,
                elapsedSeconds = viewModel.elapsedSeconds.collectAsState().value,
                isPaused = viewModel.isPaused.collectAsState().value,
                amplitude = audioAmplitude,
                hasRecordPermission = hasAudioPermission,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onTogglePause = { viewModel.togglePause() },
                onAddSeconds = { viewModel.addSeconds(it) },
                onTick = { viewModel.tickTimer() },
                onStop = { viewModel.stopRecording() }
            )
            AppState.Analyzing -> AnalyzingScreen(
                stepMessage = analysisStepMessage
            )
            AppState.Result -> ResultScreen(
                metrics = viewModel.metrics.collectAsState().value,
                dailyGoalMinutes = dailyGoalMinutes,
                todayPracticedMinutes = todayPracticedMinutes,
                recentBadge = recentUnlockedBadge,
                onBadgeClick = { viewModel.selectBadge(it) },
                onPracticeAgain = {
                    viewModel.requestNewPrompt()
                    if (hasAudioPermission) {
                        viewModel.startRecording(context)
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onBack = { viewModel.backToDashboard() }
            )
        }
    }

    if (showGoalDialog) {
        DailyGoalDialog(
            currentGoalMinutes = dailyGoalMinutes,
            onSaveGoal = { newGoal ->
                viewModel.setDailyGoalMinutes(newGoal)
                viewModel.closeGoalDialog()
            },
            onDismiss = { viewModel.closeGoalDialog() }
        )
    }

    if (selectedBadge != null) {
        MilestoneDetailDialog(
            badge = selectedBadge,
            onDismiss = { viewModel.selectBadge(null) }
        )
    }

    // F10: first-run onboarding explaining mic use + demo vs AI mode.
    if (showOnboarding) {
        OnboardingDialog(
            onContinue = { viewModel.completeOnboarding() }
        )
    }
}

/**
 * Clean, distraction-free Dashboard with Timer Duration Selector
 */
@Composable
fun DashboardScreen(
    prompt: PracticePrompt,
    userLevel: CefrLevel,
    selectedPreset: TimerPreset,
    badges: List<MilestoneBadge>,
    streakDays: Int,
    totalPracticeMinutes: Int,
    dailyGoalMinutes: Int,
    todayPracticedMinutes: Int,
    avgWpm: Int = 0,
    hasRecordPermission: Boolean = true,
    analysisError: String? = null,
    onRetryAnalysis: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onNewPrompt: () -> Unit = {},
    onOpenGoalDialog: () -> Unit,
    onBadgeClick: (MilestoneBadge) -> Unit,
    onSelectPreset: (TimerPreset) -> Unit,
    onStartPractice: () -> Unit
) {
    val hapticHelper = rememberHapticHelper()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 36.dp)
    ) {
        item {
            // F8: visible error + retry when analysis fails (never silent fake scores).
            if (analysisError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("analysis_error_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Analysis failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DangerRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = analysisError,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRetryAnalysis,
                                modifier = Modifier.testTag("analysis_retry_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                            ) {
                                Text("Retry")
                            }
                            OutlinedButton(onClick = onDismissError) {
                                Text("Dismiss", color = TextSecondary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "Fluency Coach",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Timed speaking sessions with AI fluency feedback",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricColumn("Streak", "$streakDays days 🔥")
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(BorderLight)
                    )
                    MetricColumn("Today's Goal", "$todayPracticedMinutes / ${dailyGoalMinutes}m")
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(BorderLight)
                    )
                    // F2: real average from the session log ("—" until the first scored session).
                    MetricColumn("Avg Pacing", if (avgWpm > 0) "$avgWpm WPM" else "—")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Daily Speaking Goal & Progress Bar Component
            DailyGoalCard(
                practicedMinutes = todayPracticedMinutes,
                goalMinutes = dailyGoalMinutes,
                onEditGoal = onOpenGoalDialog
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Milestone Badges & Reward System
            MilestoneRewardsSection(
                badges = badges,
                onBadgeClick = onBadgeClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Practice Duration Preset Selector (Timer Feature)
            TimerPresetSelector(
                selectedPreset = selectedPreset,
                onSelectPreset = onSelectPreset
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Today's Speaking Prompt Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPEAKING DRILL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedPreset.formatDuration(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // F9: level + category chip with shuffle for a new prompt.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BluePrimary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "${prompt.category.label} • ${prompt.level.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BluePrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("prompt_level_chip")
                            )
                        }
                        TextButton(
                            onClick = onNewPrompt,
                            modifier = Modifier.testTag("new_prompt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "New prompt",
                                tint = BluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New prompt", color = BluePrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "\"${prompt.text}\"",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp
                        ),
                        color = TextPrimary
                    )

                    Text(
                        text = "Your level: ${userLevel.label} — prompts adapt to it",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Record Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(BluePrimary)
                    .subtleClick {
                        hapticHelper.onStartSession()
                        onStartPractice()
                    }
                    .testTag("start_practice_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Start Speaking",
                    modifier = Modifier.size(38.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Tap to begin ${selectedPreset.formatDuration()} session",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasRecordPermission) "Mic enabled • Gemini AI Pronunciation & Accent" else "Tap to grant mic permission for Gemini AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = BluePrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

/**
 * Focused Recording Screen with Dedicated Speaking Timer Component
 */
@Composable
fun RecordingScreen(
    prompt: String,
    totalDuration: Int,
    elapsedSeconds: Int,
    isPaused: Boolean,
    amplitude: Float = 0f,
    hasRecordPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onTogglePause: () -> Unit,
    onAddSeconds: (Int) -> Unit,
    onTick: () -> Unit,
    onStop: () -> Unit
) {
    // F6: lifecycle-aware ticker — pauses stop the loop, leaving the screen cancels it.
    LaunchedEffect(isPaused) {
        while (!isPaused) {
            delay(1000)
            onTick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Prompt Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Microphone access notice if not granted
        if (!hasRecordPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = BorderStroke(1.dp, Color(0xFFFCD34D))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MicOff, contentDescription = null, tint = WarningAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Microphone Access Needed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Grant mic access to send speech audio to Gemini AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    TextButton(onClick = onRequestPermission) {
                        Text("Allow", color = BluePrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dedicated Speaking Timer Component
        SpeakingTimerComponent(
            totalDuration = totalDuration,
            elapsedSeconds = elapsedSeconds,
            isPaused = isPaused,
            onTogglePause = onTogglePause,
            onAddSeconds = onAddSeconds,
            onStop = onStop
        )

        // Bottom audio visualizer & live microphone status indicator
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SimpleAudioVisualizer(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(28.dp),
                isRecording = !isPaused,
                barColor = if (isPaused) WarningAmber else BluePrimary,
                amplitude = amplitude
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isPaused) WarningAmber else Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPaused) "Recording paused • Tap resume" else "Microphone Live • Gemini AI Pronunciation & Accent",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Calm Analyzing Screen. Passive by design (F1): the ViewModel owns the
 * analysis job and drives Analyzing → Result, so this screen never fires
 * callbacks or timers that could trigger a second analysis.
 */
@Composable
fun AnalyzingScreen(
    stepMessage: String = "Analyzing your speech with Gemini AI..."
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(50.dp),
            color = BluePrimary,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Analyzing your speech...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stepMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = BluePrimary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Gemini AI is evaluating pronunciation phonemes, syllable stress, intonation & accent clarity",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Clear, structured Results Screen displaying Practice Duration and Transcripts
 */
@Composable
fun ResultScreen(
    metrics: FluencyMetrics,
    dailyGoalMinutes: Int = 15,
    todayPracticedMinutes: Int = 0,
    recentBadge: MilestoneBadge? = null,
    onBadgeClick: (MilestoneBadge) -> Unit = {},
    onPracticeAgain: () -> Unit,
    onBack: () -> Unit
) {
    val hapticHelper = rememberHapticHelper()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 36.dp)
    ) {
        item {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = SuccessGreen
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Session Complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // F3: demo-mode banner — zero scores are never shown as a real evaluation.
            if (!metrics.isRealAiGenerated) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("demo_mode_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Demo mode — connect API key",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add GEMINI_API_KEY to your .env file (see .env.example) to get real AI transcription and scoring. Scores below are placeholders, not an evaluation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Duration Tracking Summary Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Duration Tracked:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                    val targetStr = if (metrics.targetDurationSeconds > 0) "${metrics.targetDurationSeconds}s target" else "open drill"
                    Text(
                        text = "${metrics.durationSeconds}s (${targetStr})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Daily Goal Progress Snippet
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_daily_goal_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = null,
                            tint = if (todayPracticedMinutes >= dailyGoalMinutes) SuccessGreen else BluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily Goal:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                    val pct = if (dailyGoalMinutes > 0) ((todayPracticedMinutes.toFloat() / dailyGoalMinutes.toFloat()) * 100).toInt() else 0
                    Text(
                        text = "$todayPracticedMinutes / ${dailyGoalMinutes} min ($pct%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (todayPracticedMinutes >= dailyGoalMinutes) SuccessGreen else BluePrimary
                    )
                }
            }

            // Milestone Reward Celebration Banner
            if (recentBadge != null) {
                Spacer(modifier = Modifier.height(14.dp))
                MilestoneRewardBanner(
                    badge = recentBadge,
                    onClick = { onBadgeClick(recentBadge) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Overall Score Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Fluency Score",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${metrics.score} / 100",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Level: ${metrics.cefr}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    // F5: model-graded justification when available.
                    if (metrics.cefrJustification.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = metrics.cefrJustification,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dedicated Gemini AI Pronunciation & Accent Card
            PronunciationAccentCard(
                pronunciationScore = metrics.pronunciationScore,
                accentScore = metrics.accentClarityScore,
                detectedAccent = metrics.detectedAccentProfile,
                pronunciationFeedback = metrics.pronunciationFeedback,
                accentFeedback = metrics.accentFeedback,
                phoneticTips = metrics.phoneticTips,
                isRealAiGenerated = metrics.isRealAiGenerated
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Clean Metric Blocks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CleanMetricCard(
                    label = "Speech Rate",
                    value = "${metrics.wpm}",
                    unit = "WPM",
                    modifier = Modifier.weight(1f)
                )
                CleanMetricCard(
                    label = "Pauses",
                    value = "${metrics.pauses}",
                    unit = ">1s pauses",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CleanMetricCard(
                    label = "Filler Words",
                    value = "${metrics.fillers}",
                    unit = "um / uh",
                    modifier = Modifier.weight(1f)
                )
                CleanMetricCard(
                    label = "Accuracy",
                    value = "${metrics.accuracy}%",
                    unit = "grammar",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speech Audio Transcription Card (gemini-3.5-transcribe)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Article,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Speech Transcription",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "gemini-3.5-transcribe",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "\"${metrics.transcription}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp
                        ),
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Feedback & Recommendations Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Feedback & Recommendations",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    metrics.feedback.forEach { tip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = "•", color = BluePrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Button(
                onClick = {
                    hapticHelper.onStartSession()
                    onPracticeAgain()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("practice_again_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Practice Again", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    hapticHelper.onToggleOrAdjust()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("back_to_dashboard_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Text("Back to Home", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * F10: one-time first-run onboarding. Explains microphone use, demo vs AI mode,
 * and the daily goal — shown once, persisted via DataStore.
 */
@Composable
fun OnboardingDialog(
    onContinue: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Welcome to Fluency Coach",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Tap the mic to start a timed speaking drill. Your audio is sent to Gemini AI for pronunciation and fluency feedback.\n\n• Without an API key the app runs in Demo mode with placeholder scores — add GEMINI_API_KEY in .env for real scoring.\n\n• Set a daily goal to build a streak. Progress is saved on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_continue_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Got it — start speaking", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun CleanMetricCard(    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}
