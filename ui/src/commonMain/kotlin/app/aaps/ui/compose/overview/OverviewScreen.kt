package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.keys.BooleanKey
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.glass.glassScreenBackgroundBrush
import app.aaps.core.ui.compose.glass.isGlassDarkMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.ui.compose.isLandscape
import app.aaps.core.ui.compose.smallestScreenWidthDp
import app.aaps.core.ui.compose.TABLET_MIN_SW_DP
import app.aaps.core.ui.compose.navigation.NavigationRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.pump.PumpActivityDialog
import app.aaps.core.ui.compose.pump.PumpActivityFab
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.notificationsSheet.NotificationBottomSheet
import app.aaps.ui.compose.notificationsSheet.NotificationFab
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import kotlinx.coroutines.delay

private val SPLIT_LAYOUT_MIN_WIDTH: Dp = 720.dp

@Composable
fun OverviewScreen(
    profilePsId: Long = 0,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetRecordId: Long = 0,
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeRecordId: Long = 0,
    smbEnabled: Boolean,
    isSimpleMode: Boolean,
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    endSceneEnabled: Boolean = true,
    // Disables the command chips' click (running mode / profile / temp target) on an unpaired client — same gate as nav/Manage.
    commandsAllowed: Boolean = true,
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    paddingValues: PaddingValues,
    fabBottomOffset: Dp = 0.dp,
    bolusState: BolusProgressState? = null,
    pumpStatusText: String = "",
    queueStatusText: AnnotatedString? = null,
    isPumpCommunicating: Boolean = false,
    onStopBolus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showNotificationSheet by remember { mutableStateOf(false) }
    var showPumpActivityDialog by remember { mutableStateOf(false) }
    val showPumpFab = isPumpCommunicating || (bolusState != null && bolusState.isSMB)

    // Three seconds is enough to glance at the card, but not to hear it read out. Ask the platform
    // how long this content needs instead: with a screen reader on it stretches the timeout, and
    // with one off calculateRecommendedTimeoutMillis hands back the original 3 seconds unchanged.
    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(showPumpFab) {
        if (!showPumpFab && showPumpActivityDialog) {
            delay(
                accessibilityManager?.calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis = 3_000,
                    containsIcons = true,
                    containsText = true,
                    containsControls = true
                ) ?: 3_000
            )
            showPumpActivityDialog = false
        }
    }

    LaunchedEffect(autoShowNotificationSheet) {
        if (autoShowNotificationSheet) {
            showNotificationSheet = true
            onAutoShowConsumed()
        }
    }

    val runningModeSceneManaged = activeSceneState?.scopedRecords?.rmId
        ?.let { it == runningModeRecordId && it > 0 } == true
    val tempTargetSceneManaged = activeSceneState?.scopedRecords?.ttId
        ?.let { it == tempTargetRecordId && it > 0 } == true
    val profileSceneManaged = activeSceneState?.scopedRecords?.psId
        ?.let { it == profilePsId && it > 0 } == true

    val isLandscape = isLandscape()
    val isTablet = smallestScreenWidthDp() >= TABLET_MIN_SW_DP && isLandscape

    // BOOST V5 state chip — appears automatically when BOOST is active APS
    val boostChip by chipsViewModel.boostChipState.collectAsStateWithLifecycle()

    val glassLook by LocalPreferences.current.observe(BooleanKey.OverviewGlassLook).collectAsStateWithLifecycle()
    val glass = OverviewGlassChrome(enabled = glassLook, isDark = isGlassDarkMode())

    CompositionLocalProvider(LocalOverviewGlass provides glass) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (glass.enabled) Modifier.background(glassScreenBackgroundBrush(glass.isDark)) else Modifier)
    ) {
        if (isTablet) {
            OverviewScreenTablet(
                tempTargetText = tempTargetText,
                tempTargetState = tempTargetState,
                tempTargetProgress = tempTargetProgress,
                tempTargetReason = tempTargetReason,
                tempTargetSceneManaged = tempTargetSceneManaged,
                runningMode = runningMode,
                runningModeText = runningModeText,
                runningModeRemaining = runningModeRemaining,
                runningModeProgress = runningModeProgress,
                runningModeSceneManaged = runningModeSceneManaged,
                smbEnabled = smbEnabled,
                isSimpleMode = isSimpleMode,
                graphViewModel = graphViewModel,
                chipsViewModel = chipsViewModel,
                manageViewModel = manageViewModel,
                statusViewModel = statusViewModel,
                statusLightsDef = statusLightsDef,
                onNavigate = onNavigate,
                paddingValues = paddingValues,
                activeSceneState = activeSceneState,
                sceneExpired = sceneExpired,
                onEndScene = onEndScene,
                onDismissScene = onDismissScene,
                endSceneEnabled = endSceneEnabled,
                commandsAllowed = commandsAllowed,
                formatDuration = formatDuration
            )
        } else BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (isLandscape && maxWidth >= SPLIT_LAYOUT_MIN_WIDTH) {
                OverviewScreenSplit(
                    tempTargetText = tempTargetText,
                    tempTargetState = tempTargetState,
                    tempTargetProgress = tempTargetProgress,
                    tempTargetReason = tempTargetReason,
                    tempTargetSceneManaged = tempTargetSceneManaged,
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeRemaining = runningModeRemaining,
                    runningModeProgress = runningModeProgress,
                    runningModeSceneManaged = runningModeSceneManaged,
                    smbEnabled = smbEnabled,
                    isSimpleMode = isSimpleMode,
                    graphViewModel = graphViewModel,
                    chipsViewModel = chipsViewModel,
                    manageViewModel = manageViewModel,
                    statusViewModel = statusViewModel,
                    statusLightsDef = statusLightsDef,
                    onNavigate = onNavigate,
                    paddingValues = paddingValues,
                    activeSceneState = activeSceneState,
                    sceneExpired = sceneExpired,
                    onEndScene = onEndScene,
                    onDismissScene = onDismissScene,
                    endSceneEnabled = endSceneEnabled,
                    commandsAllowed = commandsAllowed,
                    formatDuration = formatDuration
                )
            } else {
                OverviewScreenStacked(
                    tempTargetText = tempTargetText,
                    tempTargetState = tempTargetState,
                    tempTargetProgress = tempTargetProgress,
                    tempTargetReason = tempTargetReason,
                    tempTargetSceneManaged = tempTargetSceneManaged,
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeRemaining = runningModeRemaining,
                    runningModeProgress = runningModeProgress,
                    runningModeSceneManaged = runningModeSceneManaged,
                    isSimpleMode = isSimpleMode,
                    smbEnabled = smbEnabled,
                    graphViewModel = graphViewModel,
                    chipsViewModel = chipsViewModel,
                    manageViewModel = manageViewModel,
                    statusViewModel = statusViewModel,
                    statusLightsDef = statusLightsDef,
                    onNavigate = onNavigate,
                    paddingValues = paddingValues,
                    activeSceneState = activeSceneState,
                    sceneExpired = sceneExpired,
                    onEndScene = onEndScene,
                    onDismissScene = onDismissScene,
                    endSceneEnabled = endSceneEnabled,
                    commandsAllowed = commandsAllowed,
                    formatDuration = formatDuration
                )
            }
        }

        // Calculation progress (IOB / graph data). Overlaid on top of content so it never reflows
        // the layout — previously a flow child of the content Column which caused the screen to jump.
        AnimatedVisibility(
            visible = calcProgress < 100,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(paddingValues)
                .fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { calcProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }

        PumpActivityFab(
            visible = showPumpFab,
            bolusState = bolusState,
            onClick = { showPumpActivityDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(paddingValues)
                .padding(end = 16.dp, bottom = 128.dp + fabBottomOffset)
        )

        NotificationFab(
            notificationCount = notifications.size,
            highestLevel = notifications.minByOrNull { it.level.ordinal }?.level,
            onClick = { showNotificationSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(paddingValues)
                .padding(end = 16.dp, bottom = 72.dp + fabBottomOffset)
        )

        // BOOST V5 state banner — on top of everything, auto-appears when BOOST is active
        if (boostChip.isBoost && boostChip.state.isNotEmpty()) {
            val c = Color(boostChip.color.toInt())
            Box(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(c.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    if (boostChip.detail.isNotEmpty()) "${boostChip.state} ${boostChip.detail}"
                    else if (boostChip.tier.isNotEmpty()) boostChip.tier else boostChip.state,
                    style = MaterialTheme.typography.labelSmall, color = c, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    if (showPumpActivityDialog) {
        PumpActivityDialog(
            bolusState = bolusState,
            pumpStatus = pumpStatusText,
            queueStatus = queueStatusText,
            isModal = false,
            onStop = onStopBolus,
            onDismiss = { showPumpActivityDialog = false }
        )
    }

    if (showNotificationSheet && notifications.isNotEmpty()) {
        NotificationBottomSheet(
            notifications = notifications,
            onDismissSheet = { showNotificationSheet = false },
            onDismissNotification = onDismissNotification,
            onNotificationActionClick = onNotificationActionClick
        )
    }
    }
}
