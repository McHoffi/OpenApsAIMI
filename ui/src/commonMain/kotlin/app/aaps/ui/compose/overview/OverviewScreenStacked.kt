package app.aaps.ui.compose.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.AuditorDisplayState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalScreenOpener
import app.aaps.core.ui.compose.TonalIcon
import app.aaps.core.ui.compose.icons.IcAuditMonitor
import app.aaps.core.ui.compose.icons.IcDroplet
import app.aaps.core.ui.compose.icons.IcHeartRate
import app.aaps.core.ui.compose.icons.IcShoe
import app.aaps.core.ui.compose.icons.IcSyringe
import app.aaps.core.ui.compose.icons.IcWave
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.chips.BoostChipState
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.graphs.StatusPanelUiState
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.scenes.ActiveSceneBanner


@Composable
fun OverviewScreenStacked(
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    smbEnabled: Boolean,
    isSimpleMode: Boolean,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    paddingValues: PaddingValues,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    endSceneEnabled: Boolean = true,
    commandsAllowed: Boolean = true,
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    modifier: Modifier = Modifier
) {
    val config = LocalConfig.current
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val sensitivityUiState by chipsViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
    val statusPanelState by graphViewModel.statusPanelFlow.collectAsStateWithLifecycle()
    val auditorState by graphViewModel.auditorStateFlow.collectAsStateWithLifecycle()
    val screenOpener = LocalScreenOpener.current
    val glass = LocalOverviewGlass.current

    var statusExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
    ) {
        ActiveSceneBanner(
            activeState = activeSceneState,
            expired = sceneExpired,
            onEndClick = onEndScene,
            onDismiss = onDismissScene,
            endEnabled = endSceneEnabled,
            formatDuration = formatDuration
        )

        // State sammeln
        val tirState by graphViewModel.tirFlow.collectAsStateWithLifecycle()
        val isAIMIActive by graphViewModel.isAIMIActiveFlow.collectAsStateWithLifecycle()
        val isAutoISFActive by graphViewModel.isAutoIsfActiveFlow.collectAsStateWithLifecycle()

        // With the glass look the top block and the cards below share one side inset, and the
        // four items in this row sit 4.dp apart. Classic keeps its old mixed spacing.
        val topSide = if (glass.enabled) OverviewGlassSideInset else 8.dp
        val cardSide = if (glass.enabled) OverviewGlassSideInset else 4.dp
        val midGap = if (glass.enabled) AapsSpacing.small else 8.dp
        val tirGap = if (glass.enabled) 0.dp else 2.dp
        // Glass shrinks the side blocks so the pills keep enough width for readable text.
        val bgMax = if (glass.enabled) 145.dp else 154.dp
        val tirWidth = if (glass.enabled) 32.dp else 36.dp
        val tilesWidth = if (glass.enabled) 48.dp else 52.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = topSide, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box (modifier = Modifier.widthIn(max = bgMax)) {
                // One glass card around ring + autosense values
                OverviewGlassPanel(
                    modifier = Modifier,
                    contentPadding = PaddingValues(AapsSpacing.extraSmall)
                ) { panelModifier ->
                    Box(modifier = panelModifier) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BgInfoSection(
                                bgInfo = bgInfoState.bgInfo,
                                timeAgoText = bgInfoState.timeAgoText
                            )
                            SensitivityChipBlock(state = sensitivityUiState)
                        }
                        if (isAIMIActive && screenOpener.isAvailable) {
                            AuditorIconButton(
                                state = auditorState,
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                screenOpener.open("app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorVerdictActivity")
                            }
                        }
                    }
                }
            }

            // Middle: chips + status panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = midGap),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                OverviewChipsColumn(
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeRemaining = runningModeRemaining,
                    runningModeProgress = runningModeProgress,
                    runningModeSceneManaged = runningModeSceneManaged,
                    smbEnabled = smbEnabled,
                    tempTargetText = tempTargetText,
                    tempTargetState = tempTargetState,
                    tempTargetProgress = tempTargetProgress,
                    tempTargetReason = tempTargetReason,
                    tempTargetSceneManaged = tempTargetSceneManaged,
                    sensitivityUiState = sensitivityUiState,
                    onNavigate = onNavigate,
                    commandsAllowed = commandsAllowed
                )
                // HR, Steps,LastSMB, Basal, IOB
                OverviewStatusPanel(
                    state = statusPanelState,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            VerticalTirPanel(
                state = tirState,
                modifier = Modifier
                    .padding(horizontal = tirGap)
                    .width(tirWidth)
                    .heightIn(min = 100.dp, max = 200.dp)
            )
            // Right: AIMI quick action tiles
            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(tilesWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if ( isAutoISFActive && screenOpener.isAvailable) {
                    AimiQuickTile(
                        elementType = ElementType.PROFILE_HELPER,
                        label = stringResource( app.aaps.core.ui.CoreUiStrings.autoisf_btn_advisor),
                    ) {
                        screenOpener.open("app.aaps.plugins.aps.openAPSAutoISF.advisor.AutoIsfProfileAdvisorActivity")
                    }
                }
                if ( isAIMIActive && screenOpener.isAvailable) {
                    AimiQuickTile(
                        elementType = ElementType.PROFILE_HELPER,
                        label = stringResource(app.aaps.core.ui.CoreUiStrings.aimi_btn_advisor),
                    ) {
                        screenOpener.open("app.aaps.plugins.aps.openAPSAIMI.advisor.AimiProfileAdvisorActivity")
                    }
                    AimiQuickTile(
                        elementType = ElementType.QUICK_WIZARD_MANAGEMENT,
                        label = stringResource(app.aaps.core.ui.CoreUiStrings.aimi_btn_meal),
                    ) {
                        screenOpener.open("app.aaps.plugins.aps.openAPSAIMI.advisor.meal.MealAdvisorActivity")
                    }
                    AimiQuickTile(
                        elementType = ElementType.USER_ENTRY,
                        label = stringResource(app.aaps.core.ui.CoreUiStrings.aimi_btn_context),
                    ) {
                        screenOpener.open("app.aaps.plugins.aps.openAPSAIMI.context.ui.ContextActivity")
                    }
                }
            }
        }

        OverviewStatusSection(
            sensorStatus = statusState.sensorStatus,
            insulinStatus = statusState.insulinStatus,
            cannulaStatus = statusState.cannulaStatus,
            batteryStatus = statusState.batteryStatus,
            showFill = statusState.showFill,
            showPumpBatteryChange = statusState.showPumpBatteryChange,
            commandsAllowed = commandsAllowed,
            onNavigate = onNavigate,
            statusLightsDef = statusLightsDef,
            expanded = statusExpanded,
            onExpandedChange = { statusExpanded = it },
            sidePadding = cardSide
        )

        if (config.AAPSCLIENT) {
            val nsClientStatus by graphViewModel.nsClientStatusFlow.collectAsStateWithLifecycle()
            val flavorTint = when {
                config.AAPSCLIENT3 -> AapsTheme.generalColors.flavorClient3Tint
                config.AAPSCLIENT2 -> AapsTheme.generalColors.flavorClient2Tint
                else               -> AapsTheme.generalColors.flavorClient1Tint
            }
            AapsClientStatusCard(
                statusData = nsClientStatus,
                flavorTint = flavorTint,
                sidePadding = cardSide
            )
        }

        GraphsSection(graphViewModel = graphViewModel, isSimpleMode = isSimpleMode, iobText = iobUiState.text, cobText = cobUiState.text)
    }
}

// =========================================================================
// AIMI panel composables — internal so OverviewScreenSplit can use them too
// =========================================================================

private val StatusChipStepsColor = Color(0xFF26A69A)  // teal
private val StatusChipHrColor    = Color(0xFFEF5350)  // red
private val StatusChipSmbColor   = Color(0xFFFF7043)  // orange
private val StatusChipBasalColor = Color(0xFF42A5F5)  // light blue
private val StatusChipIobColor   = Color(0xFF7E57C2)  // purple

@Composable
internal fun OverviewStatusPanel(
    state: StatusPanelUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        StatusChip(
            icon = IcShoe,
            text = state.stepsText,
            chipColor = StatusChipStepsColor
        )
        StatusChip(
            icon = IcHeartRate,
            text = state.hrText,
            chipColor = StatusChipHrColor
        )
        StatusChip(
            icon = IcDroplet,
            text = "${state.lastSmbTime} · ${state.lastSmbAmount}",
            chipColor = StatusChipSmbColor
        )
        StatusChip(
            icon = IcWave,
            text = if ( state.basalPctText == state.basalRateText )
            {
                state.basalRateText
            }
            else{
                "${state.basalPctText} · ${state.basalRateText}"
            },
            chipColor = StatusChipBasalColor
        )
        StatusChip(
            icon = IcSyringe,
            text = state.iobText,
            chipColor = StatusChipIobColor
        )
    }
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    text: String,
    chipColor: Color,
    modifier: Modifier = Modifier
) {
    val glass = LocalOverviewGlass.current
    val shape = RoundedCornerShape(AapsSpacing.chipCornerRadius)
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = AapsSpacing.small, vertical = 0.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = chipColor,
                modifier = Modifier.size(AapsSpacing.large)
            )
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelMedium,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                    stepSize = 0.5.sp
                ),
                modifier = Modifier
                    .padding(start = AapsSpacing.small)
                    .weight(1f)
            )
        }
    }
    if (glass.enabled) {
        OverviewGlassChipFrame(
            modifier = modifier
                .fillMaxWidth()
                .height(AapsSpacing.chipHeight),
            shape = shape
        ) {
            content()
        }
    } else {
        Surface(
            shape = shape,
            modifier = modifier
                .fillMaxWidth()
                .height(AapsSpacing.chipHeight)
        ) {
            content()
        }
    }
}

@Composable
internal fun AuditorIconButton(
    state: AuditorDisplayState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = when (state) {
        AuditorDisplayState.IDLE       -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        AuditorDisplayState.PROCESSING -> AapsTheme.generalColors.ttActivity
        AuditorDisplayState.READY      -> AapsTheme.generalColors.statusNormal
        AuditorDisplayState.WARNING    -> AapsTheme.generalColors.statusWarning
        AuditorDisplayState.ERROR      -> AapsTheme.generalColors.statusCritical
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp)
    ) {
        Icon(
            imageVector = IcAuditMonitor,
            contentDescription = "Auditor",
            tint = tint,
            modifier = Modifier.size(AapsSpacing.auditorIconSize)
        )
    }
}

@Composable
internal fun AimiQuickTile(
    elementType: ElementType,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = elementType.color()
    val content: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            TonalIcon(
                icon = elementType.icon(),
                color = accent,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8
                ),
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val glass = LocalOverviewGlass.current
    if (glass.enabled) {
        OverviewGlassChipFrame(
            modifier = modifier,
            shape = RoundedCornerShape(13.dp),
            onClick = onClick
        ) { content() }
    } else {
        Column(
            modifier = modifier.clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

@Composable
internal fun AimiActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = AapsSpacing.small, vertical = 0.dp),
        modifier = Modifier
            .height(26.dp)
            .widthIn(min = 56.dp)
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

