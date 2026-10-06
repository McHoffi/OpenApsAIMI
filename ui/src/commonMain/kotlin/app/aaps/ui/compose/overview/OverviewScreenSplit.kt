package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalScreenOpener
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.scenes.ActiveSceneBanner


@Composable
fun OverviewScreenSplit(
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

    var statusExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
        ) {
            // Left column — BG + chips + status + NS card, own scroll
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Same top-block spacing as the stacked layout: 4.dp between the four items
                // when the glass look is on, classic keeps its old mixed spacing.
                val midGap = if (glass.enabled) AapsSpacing.small else 8.dp
                val tirGap = if (glass.enabled) 0.dp else 2.dp
                // Glass shrinks the side blocks so the pills keep enough width for readable text.
                val bgMax = if (glass.enabled) 145.dp else 154.dp
                val tirWidth = if (glass.enabled) 32.dp else 36.dp
                val tilesWidth = if (glass.enabled) 48.dp else 52.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left: BG Info + sensitivity chip
                    Box (modifier = Modifier.widthIn(max = bgMax)) {
                        OverviewGlassPanel(
                            modifier = Modifier,
                            contentPadding = PaddingValues(AapsSpacing.extraSmall)
                        ) { panelModifier ->
                            Box(modifier = panelModifier) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                            commandsAllowed = commandsAllowed,
                            // Right of the chips: the clock with the age of the last BG reading.
                            // The stacked layout has no room for it, so this is what tells the two
                            // wide layouts apart - the tablet places the same clock up in its header.
                            trailingContent = {
                                LargeClock(
                                    bgTimestamp = bgInfoState.bgInfo?.timestamp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            )
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

                    if( isAIMIActive && screenOpener.isAvailable ) {
                        // Right: AIMI quick action tiles
                        Column(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .width(tilesWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
                                elementType = ElementType.STATISTICS,
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
                    onExpandedChange = { statusExpanded = it }
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
                        flavorTint = flavorTint
                    )
                }
            }

            // Right column — graphs, own scroll
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 4.dp)
            ) {
                GraphsSection(graphViewModel = graphViewModel, isSimpleMode = isSimpleMode, iobText = iobUiState.text, cobText = cobUiState.text)
            }
        }
    }
}
