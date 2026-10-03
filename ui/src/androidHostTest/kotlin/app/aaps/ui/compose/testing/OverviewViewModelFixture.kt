package app.aaps.ui.compose.testing

import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.MealHypothesisHistorySource
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.AuditorDisplayState
import app.aaps.core.interfaces.overview.AuditorStateProvider
import app.aaps.core.interfaces.overview.SensitivityOverview
import app.aaps.core.interfaces.overview.SensitivityOverviewData
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.ui.compose.manageSheet.FakePumpPlugin
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Builds the four **real** view models the three overview layouts share
 * (`OverviewScreenStacked`, `OverviewScreenSplit`, `OverviewScreenTablet`).
 *
 * All three take the same view model set, so one builder covers all three and the layouts are
 * compared against identical data - which is the only way a difference the test finds can be
 * attributed to the layout rather than to the fixture.
 *
 * Shares [AapsScreenFixture.preferences], `dateUtil` and `profileUtil` with the screen environment
 * so what a view model reads and what the composables read cannot drift apart.
 *
 * The data source is [FakeOverviewDataCache], a real in-memory object rather than a mock: the view
 * models read about twenty flows off it while they are being constructed, and one missing `whenever`
 * lands as an NPE inside a `combine`, nowhere near the gap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OverviewViewModelFixture(private val screen: AapsScreenFixture) {

    val cache = FakeOverviewDataCache()

    val rh: ResourceHelper = mock()
    val aapsLogger: AAPSLogger = mock()
    val rxBus: RxBus = mock()
    val persistenceLayer: PersistenceLayer = mock()
    val activePlugin: ActivePlugin = mock()
    val profileFunction: ProfileFunction = mock()
    val loop: Loop = mock()

    /** Shared with the composables through `LocalDecimalFormatter`, so the two cannot format differently. */
    val decimalFormatter: DecimalFormatter = screen.decimalFormatter
    val processedDeviceStatusData: ProcessedDeviceStatusData = mock()
    val iobCobCalculator: IobCobCalculator = mock()
    val constraintChecker: ConstraintsChecker = mock()
    // A fake, not a mock: the chip collects this on every screen, and an unstubbed mock hands
    // back null for the data class
    val sensitivityOverview: SensitivityOverview = object : SensitivityOverview {
        override suspend fun build() = SensitivityOverviewData()
    }
    val graphConfigRepository: GraphConfigRepository = mock()
    val nsClient: NsClient = mock()
    val visibilityContext: VisibilityContext = mock()
    val batchExecutor: BatchExecutor = mock()
    val processedTbrEbData: ProcessedTbrEbData = mock()
    val bgSource: BgSource = mock()
    val tddCalculator: TddCalculator = mock()
    val automation: Automation = mock()
    val auditorStateProvider: AuditorStateProvider = mock()
    val mealHypothesisHistorySource: MealHypothesisHistorySource = mock()
    val ch: ConcentrationHelper = mock()

    /**
     * `activePump` and `activePumpInternal` are two different types on [ActivePlugin]
     * (`PumpWithConcentration` and `Pump`), and the second one is cast to `PluginBase` at
     * [ManageViewModel] field init - so the fixture needs both shapes, backed by one description.
     */
    val activePump: PumpWithConcentration = mock()
    val pumpPlugin: FakePumpPlugin = mock()
    val pumpDescription = PumpDescription()

    init {
        stubResourcesFromRobolectric(rh)

        // ----- shared ambient -----
        whenever(screen.dateUtil.now()).thenReturn(NOW)
        whenever(screen.dateUtil.timeString()).thenReturn(CLOCK_TIME)
        // Only the BG reading's own timestamp produces an age. Any other timestamp - including the
        // one a mis-wired clock would pass - reads as no age at all, so a test asserting on the
        // clock text fails on the assertion rather than on a missing stub.
        whenever(screen.dateUtil.minAgoShort(anyOrNull())).thenReturn("")
        whenever(screen.dateUtil.minAgoShort(BG_TIMESTAMP)).thenReturn(CLOCK_AGO)
        whenever(screen.dateUtil.minAgo(any(), anyOrNull())).thenReturn(TIME_AGO)
        // The status card writes the last bolus of the day as a time of day. The mock pump reports
        // no bolus, but a zero timestamp still falls inside the local day in most time zones, so the
        // formatting call is reached - and a mock answers null for a non-null String, which the
        // card's data class refuses at construction. Fixed text keeps the test independent of the
        // machine's time zone.
        whenever(screen.dateUtil.formatHHMM(any())).thenReturn(SMB_TIME)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        // The graph view model hands this flow straight to the layout. A mock would answer null and
        // the screen would fail on a missing flow rather than on what the test is about.
        whenever(auditorStateProvider.displayStateFlow).thenReturn(MutableStateFlow(AuditorDisplayState.IDLE))
        runBlocking {
            // No running profile: the reservoir has no concentration to convert with, and the
            // sensitivity chip takes its "no variable ISF" branch. Both are the empty-data cases.
            whenever(profileFunction.getProfile()).thenReturn(null)
            whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        }

        // ----- GraphViewModel -----
        whenever(screen.preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(180.0)
        whenever(screen.preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(72.0)
        whenever(screen.preferences.observe(UnitDoubleKey.OverviewHighMark)).thenReturn(MutableStateFlow(180.0))
        whenever(screen.preferences.observe(UnitDoubleKey.OverviewLowMark)).thenReturn(MutableStateFlow(72.0))
        // The graph look and the display units: two keys feed a non-null field initializer and two
        // are observed in `init`, so an unstubbed mock would throw inside the constructor.
        whenever(screen.preferences.get(StringKey.OverviewVicoBgReadingTint)).thenReturn("theme")
        whenever(screen.preferences.get(StringKey.OverviewVicoChartBackdrop)).thenReturn("theme")
        whenever(screen.preferences.observe(StringKey.OverviewVicoBgReadingTint)).thenReturn(MutableStateFlow("theme"))
        whenever(screen.preferences.observe(StringKey.OverviewVicoChartBackdrop)).thenReturn(MutableStateFlow("theme"))
        whenever(screen.preferences.observe(StringKey.GeneralUnits)).thenReturn(MutableStateFlow("mg/dl"))
        whenever(graphConfigRepository.graphConfigFlow).thenReturn(MutableStateFlow(GraphConfig()))
        // Every 30 s tick, the graph view model rebuilds its panels from these two. The tick fires as
        // soon as a layout collects the flow, so an unstubbed mock would fail inside the flow, where
        // the stack trace points at the panel builder and not at the missing stub.
        whenever(automation.events).thenReturn(MutableStateFlow<List<AutomationEvent>>(emptyList()))
        runBlocking {
            whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(emptyList())
        }

        // ----- ChipsViewModel: the three chip flows evaluate as soon as a composable collects them,
        // and an unstubbed non-null return would fail inside the flow, not at the call site. -----
        runBlocking {
            whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(NOW))
            whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(NOW))
            whenever(iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(NOW, null, 0.0))
        }
        whenever(iobCobCalculator.ads).thenReturn(mock<AutosensDataStore>())
        val autosensEnabled: Constraint<Boolean> = mock()
        whenever(autosensEnabled.value()).thenReturn(true)
        whenever(constraintChecker.isAutosensModeEnabled()).thenReturn(autosensEnabled)

        // ----- StatusViewModel / ManageViewModel -----
        whenever(activePlugin.activePump).thenReturn(activePump)
        whenever(activePlugin.activePumpInternal).thenReturn(pumpPlugin)
        whenever(activePlugin.activeBgSource).thenReturn(bgSource)
        whenever(bgSource.sensorBatteryLevel).thenReturn(-1)
        whenever(activePump.pumpDescription).thenReturn(pumpDescription)
        whenever(activePump.batteryLevel).thenReturn(MutableStateFlow<Int?>(null))
        // The status panel shows the last bolus of the day. A mock answers null for a flow, and the
        // screen reads it on every emission, so the gap shows inside a `map`, not at the call site.
        whenever(activePump.lastBolusTime).thenReturn(MutableStateFlow(null))
        whenever(activePump.lastBolusAmount).thenReturn(MutableStateFlow(null))
        runBlocking {
            // Suspending, so it needs a coroutine to be stubbed at all. The status panel asks for the
            // steps of the day and maps over the answer, so a null would fail there, not here.
            whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(emptyList())
        }
        whenever(pumpPlugin.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpPlugin.batteryLevel).thenReturn(MutableStateFlow<Int?>(null))
        whenever(rxBus.toFlow(EventInitializationChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventPumpStatusChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventNsClientStatusUpdated::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventCustomActionsChanged::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(EB::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TB::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.databaseClearedFlow()).thenReturn(emptyFlow())
        whenever(nsClient.masterOrPairedClientFlow).thenReturn(MutableStateFlow(false))
    }

    val graphViewModel: GraphViewModel by lazy {
        GraphViewModel(
            cache = cache, fullWindow = false, graphConfigRepository = graphConfigRepository,
            aapsLogger = aapsLogger, preferences = screen.preferences, dateUtil = screen.dateUtil, rh = rh,
            iobCobCalculator = iobCobCalculator, decimalFormatter = decimalFormatter, loop = loop,
            config = screen.config, persistenceLayer = persistenceLayer, constraintChecker = constraintChecker,
            profileFunction = profileFunction, processedDeviceStatusData = processedDeviceStatusData,
            profileUtil = screen.profileUtil, activePlugin = activePlugin, automation = automation,
            processedTbrEbData = processedTbrEbData, auditorStateProvider = auditorStateProvider,
            mealHypothesisHistorySource = mealHypothesisHistorySource, ch = ch
        )
    }

    val chipsViewModel: ChipsViewModel by lazy {
        ChipsViewModel(
            cache, iobCobCalculator, loop, screen.config, persistenceLayer, sensitivityOverview,
            activePlugin, aapsLogger, rh, decimalFormatter, rxBus
        )
    }

    val statusViewModel: StatusViewModel by lazy {
        StatusViewModel(
            rh, activePlugin, profileFunction, screen.config, persistenceLayer, screen.dateUtil, rxBus,
            screen.preferences, tddCalculator, decimalFormatter, processedDeviceStatusData
        )
    }

    val manageViewModel: ManageViewModel by lazy {
        ManageViewModel(
            rh, activePlugin, profileFunction, loop, screen.config, processedTbrEbData, persistenceLayer,
            rxBus, screen.dateUtil, screen.preferences, batchExecutor, nsClient, visibilityContext,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    /** Publishes a BG reading, so the BG circle has something to draw. */
    fun withBg(
        bgText: String = BG_TEXT,
        deltaText: String? = DELTA_TEXT,
        timestamp: Long = BG_TIMESTAMP
    ) {
        cache.bgInfoFlow.value = BgInfoData(
            bgValue = 120.0, bgText = bgText, bgRange = BgRange.IN_RANGE,
            isOutdated = false, timestamp = timestamp, trendArrow = TrendArrow.FLAT,
            trendDescription = "Flat", delta = 2.0, deltaText = deltaText,
            shortAvgDelta = null, shortAvgDeltaText = null, longAvgDelta = null, longAvgDeltaText = null
        )
    }

    /**
     * The clock the view model coroutines run on.
     *
     * `Dispatchers.Main` is a `TestMainDispatcher`, which is not itself a `TestDispatcher`, so its
     * scheduler cannot be read from there. A test dispatcher made without an explicit scheduler
     * takes the one already installed as Main - the same clock the view model timers run on. In a
     * test that never called `Dispatchers.setMain`, this is a clock of its own with nothing on it,
     * and then there is nothing to pump.
     */
    private val mainScheduler: TestCoroutineScheduler = StandardTestDispatcher().scheduler

    /**
     * Waits until [StatusViewModel] has finished its first refresh.
     *
     * Three things stand between construction and the first status item:
     *  - the view model coalesces refresh requests, so the first one waits out a short `debounce`,
     *    and that timer runs on the Main test dispatcher, whose clock only moves when a test asks;
     *  - the refresh then hops to the IO dispatcher, which is a real one and needs a moment;
     *  - `OverviewStatusSection` draws nothing while the status is still missing.
     *
     * So the wait moves the test clock on a little and then gives the IO side a moment, over and
     * over, and stops as soon as the status is there. Blocking here means a test asserting on the
     * status card is not racing any of that.
     */
    fun awaitStatusItems(): StatusViewModel = statusViewModel.also { vm ->
        runBlocking {
            withTimeout(5_000) {
                while (vm.uiState.value.sensorStatus == null) {
                    // A small step, not advanceUntilIdle: the same view model also runs a repeating
                    // ticker, so its queue is never empty and advanceUntilIdle would never return.
                    mainScheduler.advanceTimeBy(150)
                    delay(20)
                }
            }
        }
    }

    companion object {

        const val NOW = 1_700_000_000_000L
        const val BG_TIMESTAMP = NOW - 300_000L

        /** Distinct on purpose: the BG circle's "time ago" and the clock's "time ago" are different widgets. */
        const val TIME_AGO = "5 min"
        /** Distinct from [CLOCK_TIME] on purpose: the status card's bolus time is a third widget. */
        const val SMB_TIME = "07:15"
        const val CLOCK_TIME = "21:33"
        const val CLOCK_AGO = " 5'"
        const val BG_TEXT = "120"
        const val DELTA_TEXT = "+2"
    }
}
