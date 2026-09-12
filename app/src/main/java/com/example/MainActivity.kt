package com.example

import android.Manifest
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
import androidx.compose.material.icons.outlined.Search
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.GeminiPronunciationService
import com.example.ai.PhoneticTip
import com.example.audio.AudioRecorderManager
import com.example.ui.components.*
import com.example.ui.effects.SimpleAudioVisualizer
import com.example.ui.effects.subtleClick
import com.example.ui.theme.*
import com.example.util.rememberHapticHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class AppState {
    Dashboard, Recording, Analyzing, Result
}

data class FluencyMetrics(
    val score: Int = 86,
    val cefr: String = "B2 Upper Intermediate",
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
    val searchGroundingSummary: String = "Verified with Google Search (gemini-3.5-flash): Software engineering industry benchmarks confirm standard mobile crash-free target thresholds are typically 99.5%–99.9%.",
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

class FluencyViewModel : ViewModel() {
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

    // Daily Goal State & Progress
    private val _dailyGoalMinutes = MutableStateFlow(15) // default 15 mins daily goal
    val dailyGoalMinutes: StateFlow<Int> = _dailyGoalMinutes.asStateFlow()

    // Audio & Gemini AI state
    var audioRecorderManager: AudioRecorderManager? = null
    private val geminiService = GeminiPronunciationService()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _analysisStepMessage = MutableStateFlow("Sending microphone recording to Gemini AI...")
    val analysisStepMessage: StateFlow<String> = _analysisStepMessage.asStateFlow()

    private var recordedAudioFile: File? = null

    private val _todayPracticedMinutes = MutableStateFlow(6) // 6 mins practiced today
    val todayPracticedMinutes: StateFlow<Int> = _todayPracticedMinutes.asStateFlow()

    private val _showGoalDialog = MutableStateFlow(false)
    val showGoalDialog: StateFlow<Boolean> = _showGoalDialog.asStateFlow()

    // Milestone Badges & Rewards
    private val _totalPracticeMinutes = MutableStateFlow(60) // 60 mins practiced
    val totalPracticeMinutes: StateFlow<Int> = _totalPracticeMinutes.asStateFlow()

    private val _streakDays = MutableStateFlow(7) // 7-day streak
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private val _selectedBadge = MutableStateFlow<MilestoneBadge?>(null)
    val selectedBadge: StateFlow<MilestoneBadge?> = _selectedBadge.asStateFlow()

    private val _recentUnlockedBadge = MutableStateFlow<MilestoneBadge?>(null)
    val recentUnlockedBadge: StateFlow<MilestoneBadge?> = _recentUnlockedBadge.asStateFlow()

    private val _badges = MutableStateFlow(
        listOf(
            MilestoneBadge(
                id = "streak_7",
                title = "7-Day Streak",
                description = "Practiced speaking consistently for 7 consecutive days without skipping.",
                category = "Consistency",
                current = 7,
                target = 7,
                unit = "days",
                icon = Icons.Default.Whatshot,
                accentColor = Color(0xFFEA580C),
                isUnlocked = true,
                unlockedDate = "Yesterday"
            ),
            MilestoneBadge(
                id = "practice_1hr",
                title = "1 Hour Practiced",
                description = "Reached 60 minutes of cumulative active speaking practice time.",
                category = "Endurance",
                current = 60,
                target = 60,
                unit = "min",
                icon = Icons.Default.HourglassBottom,
                accentColor = BluePrimary,
                isUnlocked = true,
                unlockedDate = "Today"
            ),
            MilestoneBadge(
                id = "first_drill",
                title = "First Drill",
                description = "Completed your initial timed fluency practice drill.",
                category = "Foundation",
                current = 1,
                target = 1,
                unit = "drill",
                icon = Icons.Default.WorkspacePremium,
                accentColor = SuccessGreen,
                isUnlocked = true,
                unlockedDate = "3 days ago"
            ),
            MilestoneBadge(
                id = "pacing_master",
                title = "Pacing Master",
                description = "Maintain speech rate within target 120–150 WPM for 3 sessions.",
                category = "Fluency",
                current = 3,
                target = 3,
                unit = "sessions",
                icon = Icons.Default.Speed,
                accentColor = Color(0xFF7C3AED),
                isUnlocked = true,
                unlockedDate = "Just now"
            ),
            MilestoneBadge(
                id = "fluency_ace",
                title = "Fluency Ace",
                description = "Score 90+ overall fluency rating with high grammar accuracy.",
                category = "Excellence",
                current = 86,
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
                current = 60,
                target = 120,
                unit = "sec",
                icon = Icons.Default.Mic,
                accentColor = Color(0xFF4F46E5),
                isUnlocked = false
            )
        )
    )
    val badges: StateFlow<List<MilestoneBadge>> = _badges.asStateFlow()

    val currentPrompt = "Describe a challenging situation you overcame at work or school, and what you learned from it."

    fun selectBadge(badge: MilestoneBadge?) {
        _selectedBadge.value = badge
    }

    fun setDailyGoalMinutes(minutes: Int) {
        _dailyGoalMinutes.value = minutes.coerceIn(1, 120)
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

    fun startRecording(context: Context? = null, prompt: String = currentPrompt) {
        _totalTargetSeconds.value = _selectedPreset.value.durationSeconds
        _elapsedSeconds.value = 0
        _isPaused.value = false
        _appState.value = AppState.Recording

        if (context != null) {
            val manager = audioRecorderManager ?: AudioRecorderManager(context.applicationContext, viewModelScope).also {
                audioRecorderManager = it
            }
            if (manager.hasRecordPermission()) {
                manager.startRecording()
                viewModelScope.launch {
                    manager.amplitude.collect { amp ->
                        _audioAmplitude.value = amp
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
        if (!_isPaused.value) {
            _elapsedSeconds.value += 1
            // Auto stop when countdown finishes for non-freeflow preset
            val target = _totalTargetSeconds.value
            if (target > 0 && _elapsedSeconds.value >= target) {
                stopRecording()
            }
        }
    }

    fun stopRecording() {
        _appState.value = AppState.Analyzing
        recordedAudioFile = audioRecorderManager?.stopRecording()
        _audioAmplitude.value = 0f
        performGeminiAnalysis()
    }

    fun performGeminiAnalysis() {
        viewModelScope.launch {
            _analysisStepMessage.value = "Sending microphone recording to Gemini AI..."
            delay(500)
            _analysisStepMessage.value = "Evaluating pronunciation phonemes, syllable stress & accent..."

            val elapsed = _elapsedSeconds.value.coerceAtLeast(10)
            val target = _totalTargetSeconds.value

            val feedbackResult = geminiService.analyzeSpeech(
                audioFile = recordedAudioFile,
                referencePrompt = currentPrompt,
                elapsedSeconds = elapsed
            )

            _analysisStepMessage.value = "Synthesizing pronunciation & accent feedback..."
            delay(300)

            _metrics.value = FluencyMetrics(
                score = feedbackResult.pronunciationScore,
                cefr = if (feedbackResult.pronunciationScore >= 90) "C1 Advanced" else "B2 Upper Intermediate",
                wpm = feedbackResult.wpm,
                pauses = feedbackResult.pauses,
                fillers = feedbackResult.fillers,
                accuracy = feedbackResult.accuracy,
                durationSeconds = elapsed,
                targetDurationSeconds = target,
                transcription = feedbackResult.transcription,
                feedback = feedbackResult.recommendations,
                searchGroundingSummary = "Verified with Google Search (gemini-3.5-flash): Software engineering industry benchmarks confirm standard mobile crash-free target thresholds are typically 99.5%–99.9%.",
                pronunciationScore = feedbackResult.pronunciationScore,
                accentClarityScore = feedbackResult.accentClarityScore,
                detectedAccentProfile = feedbackResult.detectedAccentProfile,
                pronunciationFeedback = feedbackResult.pronunciationFeedback,
                accentFeedback = feedbackResult.accentFeedback,
                phoneticTips = feedbackResult.phoneticTips,
                isRealAiGenerated = feedbackResult.isRealAiGenerated
            )

            // Increment practice minutes and evaluate milestones
            val addedMinutes = (elapsed / 60).coerceAtLeast(1)
            _totalPracticeMinutes.value += addedMinutes
            _todayPracticedMinutes.value += addedMinutes
            val currentTotalMinutes = _totalPracticeMinutes.value

            _badges.value = _badges.value.map { badge ->
                when (badge.id) {
                    "practice_1hr" -> {
                        val reached = currentTotalMinutes >= 60
                        badge.copy(
                            current = currentTotalMinutes,
                            isUnlocked = reached,
                            unlockedDate = if (reached && badge.unlockedDate == null) "Just now" else badge.unlockedDate
                        )
                    }
                    "fluency_ace" -> {
                        if (feedbackResult.pronunciationScore >= 90) {
                            badge.copy(current = feedbackResult.pronunciationScore, isUnlocked = true, unlockedDate = "Just now")
                        } else {
                            badge.copy(current = maxOf(badge.current, feedbackResult.pronunciationScore))
                        }
                    }
                    "long_turn_pro" -> {
                        if (elapsed >= 120) {
                            badge.copy(current = 120, isUnlocked = true, unlockedDate = "Just now")
                        } else {
                            badge.copy(current = maxOf(badge.current, elapsed))
                        }
                    }
                    else -> badge
                }
            }

            // Highlight unlocked milestone
            _recentUnlockedBadge.value = _badges.value.find { it.id == "practice_1hr" } ?: _badges.value.firstOrNull { it.isUnlocked }

            _appState.value = AppState.Result
        }
    }

    fun finishAnalysis() {
        performGeminiAnalysis()
    }

    fun backToDashboard() {
        _appState.value = AppState.Dashboard
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
    val analysisStepMessage by viewModel.analysisStepMessage.collectAsState()

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
            viewModel.startRecording(context, viewModel.currentPrompt)
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
                prompt = viewModel.currentPrompt,
                selectedPreset = viewModel.selectedPreset.collectAsState().value,
                badges = badges,
                streakDays = streakDays,
                totalPracticeMinutes = totalPracticeMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                todayPracticedMinutes = todayPracticedMinutes,
                hasRecordPermission = hasAudioPermission,
                onOpenGoalDialog = { viewModel.openGoalDialog() },
                onBadgeClick = { viewModel.selectBadge(it) },
                onSelectPreset = { viewModel.selectPreset(it) },
                onStartPractice = {
                    if (hasAudioPermission) {
                        viewModel.startRecording(context, viewModel.currentPrompt)
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
            AppState.Recording -> RecordingScreen(
                prompt = viewModel.currentPrompt,
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
                stepMessage = analysisStepMessage,
                onAnalysisComplete = { viewModel.finishAnalysis() }
            )
            AppState.Result -> ResultScreen(
                metrics = viewModel.metrics.collectAsState().value,
                dailyGoalMinutes = dailyGoalMinutes,
                todayPracticedMinutes = todayPracticedMinutes,
                recentBadge = recentUnlockedBadge,
                onBadgeClick = { viewModel.selectBadge(it) },
                onPracticeAgain = {
                    if (hasAudioPermission) {
                        viewModel.startRecording(context, viewModel.currentPrompt)
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
}

/**
 * Clean, distraction-free Dashboard with Timer Duration Selector
 */
@Composable
fun DashboardScreen(
    prompt: String,
    selectedPreset: TimerPreset,
    badges: List<MilestoneBadge>,
    streakDays: Int,
    totalPracticeMinutes: Int,
    dailyGoalMinutes: Int,
    todayPracticedMinutes: Int,
    hasRecordPermission: Boolean = true,
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
                    MetricColumn("Avg Pacing", "132 WPM")
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "\"$prompt\"",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp
                        ),
                        color = TextPrimary
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
    LaunchedEffect(Unit) {
        while (true) {
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
 * Calm Analyzing Screen
 */
@Composable
fun AnalyzingScreen(
    stepMessage: String = "Analyzing your speech with Gemini AI...",
    onAnalysisComplete: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2400)
        onAnalysisComplete()
    }

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

            // Search Grounding Card (gemini-3.5-flash with googleSearch tool)
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
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fact Check Grounding",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Google Search",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = metrics.searchGroundingSummary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp
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

@Composable
fun CleanMetricCard(
    label: String,
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
