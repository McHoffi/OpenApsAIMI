package app.aaps.ui.compose.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.MealCarbsField
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.ThreeButtonDialog
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.aboutDialog.AboutAlertDialog
import app.aaps.ui.compose.aboutDialog.AboutDialogData
import app.aaps.ui.compose.loopSheet.LoopActionViewModel
import app.aaps.ui.compose.scenesSheet.ScenesBottomSheet
import app.aaps.ui.compose.scenesSheet.ScenesViewModel
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.MaintenanceDialogs
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageSheetState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.OverviewScreen
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchToolbar
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import app.aaps.ui.compose.treatmentsSheet.TreatmentBottomSheet
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchResults
import app.aaps.ui.search.SearchUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    aboutDialogData: AboutDialogData?,
    manageSheetState: ManageSheetState,
    manageViewModel: ManageViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    statusViewModel: StatusViewModel,
    treatmentViewModel: TreatmentViewModel,
    scenesViewModel: ScenesViewModel,
    loopActionViewModel: LoopActionViewModel,
    // Search
    searchUiState: SearchUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchResultClick: (SearchIndexEntry) -> Unit,
    onSearchPluginToggle: (PluginBase) -> Unit,
    onConfirmSearchPluginSwitch: () -> Unit,
    onDismissSearchPluginSwitch: () -> Unit,
    onConfirmSearchHardwarePump: () -> Unit,
    onDismissSearchHardwarePump: () -> Unit,
    // Menu/navigation
    onMenuClick: () -> Unit,
    onUserManualClick: () -> Unit = {},
    onNavigate: (NavigationRequest) -> Unit,
    onDrawerClosed: () -> Unit,
    onAboutDialogDismiss: () -> Unit,
    /** Null hides the button - only Android has the problem it links to. */
    onOpenBatteryHelp: (() -> Unit)?,
    onMaintenanceSheetDismiss: () -> Unit,
    onDirectoryClick: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    // Notifications
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    // Pump setup
    pumpSetupPlugin: PluginBase? = null,
    // BG source shortcut
    bgSetupPlugin: PluginBase? = null,
    bgQualityBadgeIcon: ImageVector? = null,
    bgQualityBadgeTint: Color = Color.Unspecified,
    bgQualityBadgeDescription: String? = null,
    // Objectives progress
    objectivesSetupPlugin: PluginBase? = null,
    objectivesProgressText: String? = null,
    // Permissions
    permissionsMissing: Boolean = false,
    onPermissionsClick: () -> Unit = {},
    // Toolbar
    quickLaunchItems: List<ResolvedQuickLaunchItem> = emptyList(),
    onQuickLaunchActionClick: (QuickLaunchAction) -> Unit = {},
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    treatmentButtonsDef: PreferenceSubScreenDef,
    // Pump activity
    bolusState: BolusProgressState? = null,
    pumpStatusText: String = "",
    queueStatusText: AnnotatedString? = null,
    isPumpCommunicating: Boolean = false,
    onStopBolus: () -> Unit = {},
    /**
     * When non-null, replaces [OverviewScreen] with an embedded dashboard supplied by the app
     * module. That view tree does not dispatch Compose nested scroll, so the skin gets tap-to-reveal
     * and the idle hide only. Scroll auto-hide needs the Compose overview.
     */
    dashboardOverview: (@Composable (PaddingValues, Dp) -> Unit)? = null,
    /** True only for the GLASS dashboard skin — swaps in [GlassNavigationBar] instead of the default
     *  [MainNavigationBar]. Does not affect OVERVIEW, which keeps using MainNavigationBar. */
    isGlassSkin: Boolean = false,
    modifier: Modifier = Modifier
) {
    LocalDateUtil.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showTreatmentSheet by remember { mutableStateOf(false) }
    var showAutomationSheet by remember { mutableStateOf(false) }
    var showLoopActionSheet by remember { mutableStateOf(false) }
    val automationState by scenesViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    // Sync automation state (otherwise missed after start)
    LaunchedEffect(Unit) {
        scenesViewModel.refreshState()
    }

    // One-shot messages from confirm dialogs (for example the optional meal carbs)
    LaunchedEffect(Unit) {
        mainViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Meal-carb feedback from the Modes buttons in the graphs panel
    LaunchedEffect(graphViewModel) {
        graphViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Sync drawer state with ui state
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed && uiState.isDrawerOpen) {
            onDrawerClosed()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawer(
                versionName = mainViewModel.versionName,
                onNavigate = { request ->
                    scope.launch { drawerState.close() }
                    onDrawerClosed()
                    onNavigate(request)
                },
                isTreatmentsEnabled = uiState.isProfileLoaded,
                showExit = mainViewModel.showExit
            )
        },
        gesturesEnabled = true,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            // Short windows (landscape phones) start with the bars hidden so the graph gets the
            // full height. Taller windows start with the bars on screen.
            val chrome = rememberChromeAutoHide(
                initiallyVisible = maxHeight >= START_HIDDEN_MAX_HEIGHT
            )
            val showChrome = chrome.isVisible

            // Latch measured bar heights. Never write 0 back — a transient 0 during
            // AnimatedVisibility exit can schedule a remeasure on a node that is losing its
            // owner and crash in dispatchDraw.
            var topBarHeightPx by remember { mutableIntStateOf(0) }
            var bottomBarHeightPx by remember { mutableIntStateOf(0) }

            // Pin while the user must see the top bar to get out of search, the drawer or Glass.
            // Glass content lives in its own ComposeView inside an AndroidView, so its scroll
            // never reaches chromeAutoHideScroll and its clickables eat the reveal tap. Without
            // this pin the bars hide after 3 s and the user cannot reach the skin setting.
            val chromePinned = searchUiState.isSearchActive || !drawerState.isClosed || isGlassSkin
            LaunchedEffect(chromePinned) {
                chrome.pin(chromePinned)
            }

            // Idle hide — only while shown and not pinned. Restarts whenever the bars are shown
            // again or a pull at the top extends the peek.
            LaunchedEffect(chrome.isVisible, chrome.showToken, chrome.isPinned) {
                if (!chrome.isVisible || chrome.isPinned) return@LaunchedEffect
                delay(AUTO_HIDE_DELAY_MS)
                chrome.onIdleTimeout()
            }

            Scaffold { scaffoldPadding ->
                // Glass has its own quick-shortcut pills and its own bottom nav, so the general-purpose
                // Quick Launch toolbar would just float on top of them.
                val hasToolbar = quickLaunchItems.isNotEmpty() && !isGlassSkin

                // Content expands into the freed space when the bars hide.
                val topBarHeight = with(density) { topBarHeightPx.toDp() }
                val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
                val topBarPad by animateDpAsState(
                    targetValue = if (showChrome) topBarHeight else 0.dp,
                    label = "topBarPad",
                )
                val bottomBarPad by animateDpAsState(
                    targetValue = if (showChrome) bottomBarHeight else 0.dp,
                    label = "bottomBarPad",
                )
                val contentPadding = PaddingValues(
                    top = scaffoldPadding.calculateTopPadding() + topBarPad,
                    bottom = scaffoldPadding.calculateBottomPadding() + bottomBarPad
                )

                val activeSceneState by mainViewModel.activeSceneState.collectAsStateWithLifecycle()
                val sceneExpired by mainViewModel.sceneExpired.collectAsStateWithLifecycle()
                val activeSceneChainTargetName by mainViewModel.activeSceneChainTargetName.collectAsStateWithLifecycle()
                val masterReachable by mainViewModel.masterReachable.collectAsStateWithLifecycle()
                // Stable pairing signal — hides the mutating nav buttons on an unpaired client.
                val masterOrPairedClient by mainViewModel.masterOrPairedClient.collectAsStateWithLifecycle()
                // (Probe-while-offline is now global — see ComposeMainActivity. This screen still reads
                // masterReachable for its own gating.)
                val fabBottomOffset = if (hasToolbar && showChrome) 56.dp else 0.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .chromeAutoHideScroll(chrome.controller)
                        .then(
                            if (!showChrome) {
                                // A modifier on THIS ancestor Box, not a separate full-screen sibling Box
                                // drawn on top of the content: a stacked sibling claimed the whole gesture
                                // stream and blocked scrolling in the dashboard below it. On the ancestor,
                                // detectTapGestures backs off once a descendant scrollable consumes the
                                // drag, so scrolling still works and only a stationary tap reveals the bars.
                                Modifier.pointerInput(showChrome) {
                                    detectTapGestures(onTap = { chrome.onRevealTap() })
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Main content — the embedded dashboard takes over when the selected skin asks for it
                    if (dashboardOverview != null) {
                        dashboardOverview(contentPadding, fabBottomOffset)
                    } else OverviewScreen(
                        tempTargetText = uiState.tempTargetText,
                        tempTargetState = uiState.tempTargetState,
                        tempTargetProgress = uiState.tempTargetProgress,
                        tempTargetReason = uiState.tempTargetReason,
                        tempTargetRecordId = uiState.tempTargetRecordId,
                        runningMode = uiState.runningMode,
                        runningModeText = uiState.runningModeText,
                        runningModeRemaining = uiState.runningModeRemaining,
                        runningModeProgress = uiState.runningModeProgress,
                        runningModeRecordId = uiState.runningModeRecordId,
                        smbEnabled = uiState.smbEnabled,
                        isSimpleMode = uiState.isSimpleMode,
                        calcProgress = calcProgress,
                        graphViewModel = graphViewModel,
                        chipsViewModel = chipsViewModel,
                        manageViewModel = manageViewModel,
                        statusViewModel = statusViewModel,
                        statusLightsDef = statusLightsDef,
                        onNavigate = onNavigate,
                        notifications = notifications,
                        onDismissNotification = onDismissNotification,
                        onNotificationActionClick = onNotificationActionClick,
                        autoShowNotificationSheet = autoShowNotificationSheet,
                        onAutoShowConsumed = onAutoShowConsumed,
                        activeSceneState = activeSceneState,
                        sceneExpired = sceneExpired,
                        activeSceneChainTargetName = activeSceneChainTargetName,
                        onEndScene = { mainViewModel.requestSceneDeactivation() },
                        onDismissScene = { mainViewModel.dismissExpiredScene() },
                        endSceneEnabled = masterReachable,
                        commandsAllowed = masterOrPairedClient,
                        formatDuration = mainViewModel::formatDuration,
                        paddingValues = contentPadding,
                        fabBottomOffset = fabBottomOffset,
                        bolusState = bolusState,
                        pumpStatusText = pumpStatusText,
                        queueStatusText = queueStatusText,
                        isPumpCommunicating = isPumpCommunicating,
                        onStopBolus = onStopBolus
                    )

                    // Search results overlay
                    if (searchUiState.isSearchActive) {
                        SearchResults(
                            results = searchUiState.results,
                            wikiResults = searchUiState.wikiResults,
                            isSearching = searchUiState.isSearching,
                            isSearchingWiki = searchUiState.isSearchingWiki,
                            wikiOffline = searchUiState.wikiOffline,
                            revision = searchUiState.revision,
                            onResultClick = onSearchResultClick,
                            onPluginToggle = onSearchPluginToggle,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding)
                        )
                    }

                    // Plugin enable/disable confirmations raised from search results (same dialogs as Config Builder)
                    searchUiState.pluginSwitchConfirmation?.let { confirmation ->
                        OkCancelDialog(
                            title = stringResource(CoreUiStrings.configbuilder_switch_confirmation_title),
                            message = stringResource(
                                CoreUiStrings.configbuilder_switch_confirmation,
                                confirmation.fromName,
                                confirmation.toName
                            ),
                            onConfirm = onConfirmSearchPluginSwitch,
                            onDismiss = onDismissSearchPluginSwitch
                        )
                    }
                    searchUiState.hardwarePumpConfirmation?.let { confirmation ->
                        OkCancelDialog(
                            title = stringResource(CoreUiStrings.confirmation),
                            message = confirmation.message,
                            onConfirm = onConfirmSearchHardwarePump,
                            onDismiss = onDismissSearchHardwarePump
                        )
                    }

                    // Status bar protection scrim — keeps system icons legible
                    // against the floating search bar / graph content
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(MaterialTheme.colorScheme.surface)
                    )

                        // Navigation bar protection scrim
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                .background(MaterialTheme.colorScheme.surface)
                        )

                    // Top bar overlay
                    AnimatedVisibility(
                        visible = showChrome,
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = scaffoldPadding.calculateTopPadding())
                    ) {
                        MainTopBar(
                            searchUiState = searchUiState,
                            onMenuClick = {
                                scope.launch {
                                    drawerState.open()
                                    onMenuClick()
                                }
                            },
                            onUserManualClick = onUserManualClick,
                            onPreferencesClick = { onNavigate(NavigationRequest.Element(ElementType.SETTINGS)) },
                            onSearchQueryChange = onSearchQueryChange,
                            onSearchClear = onSearchClear,
                            onSearchActiveChange = onSearchActiveChange,
                            // Guard against transient 0 heights during AnimatedVisibility exit:
                            // the resulting contentPadding invalidation can schedule a remeasure
                            // on a node that's losing its owner — crashes in dispatchDraw.
                            graphViewModel = graphViewModel,
                            modifier = Modifier.onSizeChanged {
                                if (it.height > 0 && it.height != topBarHeightPx) topBarHeightPx = it.height
                            }
                        )
                    }

                    // Bottom bar overlay
                    AnimatedVisibility(
                        visible = showChrome,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = scaffoldPadding.calculateBottomPadding())
                    ) {
                        val loopActionState = loopActionViewModel.uiState.collectAsStateWithLifecycle().value
                        if (isGlassSkin) {
                            GlassNavigationBar(
                                masterOrPairedClient = masterOrPairedClient,
                                onTreatmentClick = {
                                    treatmentViewModel.refreshState()
                                    showTreatmentSheet = true
                                },
                                onScenariosClick = {
                                    scenesViewModel.refreshState()
                                    showAutomationSheet = true
                                },
                                onManagementClick = { manageSheetState.show() },
                                onNavigate = onNavigate,
                                loopActionAvailable = loopActionState.actionAvailable,
                                onLoopActionClick = { showLoopActionSheet = true },
                                modifier = Modifier.onSizeChanged {
                                    if (it.height > 0 && it.height != bottomBarHeightPx) bottomBarHeightPx = it.height
                                },
                            )
                        } else MainNavigationBar(
                            onManageClick = { manageSheetState.show() },
                            onTreatmentClick = {
                                treatmentViewModel.refreshState()
                                showTreatmentSheet = true
                            },
                            masterOrPairedClient = masterOrPairedClient,
                            quickWizardCount = uiState.quickWizardItems.size,
                            onAutomationClick = {
                                scenesViewModel.refreshState()
                                showAutomationSheet = true
                            },
                            // Total drives nav-button visibility (button stays visible whenever
                            // scenes/automation exist, even if currently un-activatable).
                            // Count drives the badge — only items the user can act on right now.
                            automationTotal = automationState.items.size + automationState.sceneItems.size,
                            automationCount = automationState.items.count { it.activationReason == null } +
                                automationState.sceneItems.count { it.activationReason == null },
                            pumpSetupPlugin = pumpSetupPlugin,
                            bgSetupPlugin = bgSetupPlugin,
                            bgQualityBadgeIcon = bgQualityBadgeIcon,
                            bgQualityBadgeTint = bgQualityBadgeTint,
                            bgQualityBadgeDescription = bgQualityBadgeDescription,
                            objectivesSetupPlugin = objectivesSetupPlugin,
                            objectivesProgressText = objectivesProgressText,
                            onNavigate = onNavigate,
                            permissionsMissing = permissionsMissing,
                            onPermissionsClick = onPermissionsClick,
                            loopActionAvailable = loopActionState.actionAvailable,
                            onLoopActionClick = { showLoopActionSheet = true },
                            modifier = Modifier.onSizeChanged {
                                if (it.height > 0 && it.height != bottomBarHeightPx) bottomBarHeightPx = it.height
                            }
                        )
                    }

                    // Quick launch toolbar overlay
                    AnimatedVisibility(
                        visible = hasToolbar && showChrome,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = scaffoldPadding.calculateBottomPadding() +
                                    with(density) { bottomBarHeightPx.toDp() } + 8.dp
                            )
                    ) {
                        QuickLaunchToolbar(
                            items = quickLaunchItems,
                            onActionClick = onQuickLaunchActionClick,
                        )
                    }

                }
            }
        }
    }

        // Treatment bottom sheet
        if (showTreatmentSheet) {
            val treatmentState by treatmentViewModel.uiState.collectAsStateWithLifecycle()
            TreatmentBottomSheet(
                onDismiss = { showTreatmentSheet = false },
                showCgm = treatmentState.showCgm,
                showCalibration = treatmentState.showCalibration,
                showTreatment = treatmentState.showTreatment,
                showInsulin = treatmentState.showInsulin,
                showAfrezza = treatmentState.showAfrezza,
                showCarbs = treatmentState.showCarbs,
                showCalculator = treatmentState.showCalculator,
                isDexcomSource = treatmentState.isDexcomSource,
                showSettingsIcon = treatmentState.showSettingsIcon,
                quickWizardItems = treatmentState.quickWizardItems,
                onNavigate = onNavigate,
                treatmentButtonsDef = treatmentButtonsDef,
            )
        }

    // Automation bottom sheet
    if (showAutomationSheet) {
        val sceneState by scenesViewModel.uiState.collectAsStateWithLifecycle()
        ScenesBottomSheet(
            onDismiss = { showAutomationSheet = false },
            automationItems = sceneState.items,
            onItemClick = { item -> mainViewModel.requestAutomationConfirmation(item.eventId) },
            sceneItems = sceneState.sceneItems,
            onSceneClick = { sceneId -> mainViewModel.requestSceneConfirmation(sceneId) }
        )
    }

    // Loop accept action bottom sheet
    if (showLoopActionSheet) {
        val loopState by loopActionViewModel.uiState.collectAsStateWithLifecycle()
        app.aaps.ui.compose.loopSheet.LoopActionBottomSheet(
            state = loopState,
            onPerform = { mainViewModel.performLoopAccept() },
            onDismiss = { showLoopActionSheet = false }
        )
    }

    // Shared confirmation dialog (automation actions, TT presets, scene end — from toolbar or
    // bottom sheets). When the confirmation carries a secondary action (e.g., scene chain skip),
    // render a 3-button dialog; otherwise the standard 2-button OK/Cancel.
    val actionConfirmation by mainViewModel.actionConfirmation.collectAsStateWithLifecycle()
    actionConfirmation?.let { confirmation ->
        var carbsText by remember(confirmation) { mutableStateOf("") }
        val secondaryAction = confirmation.secondaryAction
        val secondaryLabel = confirmation.secondaryLabel
        if (secondaryAction != null && secondaryLabel != null) {
            ThreeButtonDialog(
                title = confirmation.title,
                message = confirmation.message,
                icon = confirmation.icon,
                primaryLabel = confirmation.confirmLabel ?: stringResource(CoreUiStrings.ok),
                onPrimary = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                secondaryLabel = secondaryLabel,
                onSecondary = { mainViewModel.executeConfirmableAction(secondaryAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        } else {
            OkCancelDialog(
                title = confirmation.title,
                message = confirmation.message,
                icon = confirmation.icon,
                extraContent = if (confirmation.showCarbField) {
                    {
                        MealCarbsField(
                            value = carbsText,
                            onValueChange = { carbsText = it }
                        )
                    }
                } else {
                    null
                },
                onConfirm = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction, carbsText) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        }
    }

    // Maintenance dialogs (sheets, confirmations, export chain)
    MaintenanceDialogs(
        maintenanceViewModel = maintenanceViewModel,
        showMaintenanceSheet = uiState.showMaintenanceSheet,
        onMaintenanceSheetDismiss = onMaintenanceSheetDismiss,
        onDirectoryClick = onDirectoryClick,
        onImportSettingsNavigate = onImportSettingsNavigate,
        onRecreateActivity = onRecreateActivity,
        onLaunchBrowser = onLaunchBrowser,
        onBringToForeground = onBringToForeground,
        onSnackbar = { snackbarHostState.showSnackbar(it) }
    )

    // About dialog
    if (uiState.showAboutDialog && aboutDialogData != null) {
        AboutAlertDialog(
            data = aboutDialogData,
            onDismiss = onAboutDialogDismiss,
            onOpenBatteryHelp = onOpenBatteryHelp
        )
    }
}

/** Windows shorter than this start with the bars hidden so the graph gets the full height. */
private val START_HIDDEN_MAX_HEIGHT: Dp = 500.dp
private const val AUTO_HIDE_DELAY_MS = 3000L
