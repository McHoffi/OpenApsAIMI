package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.resources.formatTemplate
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalDecimalFormatter
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.LocalScreenOpener
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.overview.LocalOverviewGlass
import app.aaps.ui.compose.overview.OverviewGlassPanel
import app.aaps.ui.compose.overview.OverviewGlassSideInset
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce

/**
 * Overview graphs section using Vico charts.
 *
 * Pattern: Observe Primary + Sync to Secondary
 * - Each graph has its OWN VicoScrollState and VicoZoomState
 * - BG graph: Interactive - user can scroll/zoom
 * - Secondary graphs: Non-interactive - scroll/zoom disabled
 * - LaunchedEffect observes BG graph's state changes and syncs to secondary
 *
 * Synchronization Implementation:
 * - snapshotFlow observes scroll/zoom values from BG graph
 * - debounce(30) waits for gesture to settle
 * - zoomState.zoom() and scrollState.scroll() copy state to secondary graphs
 *
 * Secondary graphs are config-driven via [GraphConfig.secondaryGraphs].
 * Scroll/Zoom states are pre-allocated (up to [GraphConfig.MAX_SECONDARY_GRAPHS])
 * to avoid dynamic composable state issues with Vico's remember-based states.
 *
 * Long-press any graph to edit its series/height (hidden in simple mode).
 *
 * MODES uses [PointerEventPass.Initial] to intercept long press before
 * OutlinedButtons consume the gesture.
 * PULSE uses [combinedClickable] replacing Card's onClick.
 */
private val SIMPLE_MODE_CONFIG = GraphConfig(
    bgOverlays = emptyList(),
    iobOverlays = emptyList(),
    secondaryGraphs = listOf(SecondaryGraph(listOf(SeriesType.COB)))
)

/**
 * How many recent readings the spoken description of the BG graph lists.
 *
 * Five is about twenty-five minutes, which is enough to hear where the glucose is heading without
 * turning the description into a list nobody will sit through.
 */
private const val RECENT_VALUES_SPOKEN = 5

/** Series types available as BG graph overlays */
private val BG_OVERLAY_SERIES = listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS, SeriesType.BOLUS, SeriesType.BASAL )

/** AutoISF-specific series — only shown in the picker when AutoISF is the active APS plugin */
private val AUTOISF_SERIES = setOf(
    SeriesType.IOB_THRESHOLD,
    SeriesType.FINAL_ISF,
    SeriesType.ACCE_ISF,
    SeriesType.BG_ISF,
    SeriesType.PP_ISF,
    SeriesType.DURA_ISF,
    SeriesType.MODES
)

private val AIMI_SERIES = setOf(
    SeriesType.MODES,
)

/** Base series types for user-configurable secondary graphs (IOB + UI-only overlays excluded) */
private val BASE_CONFIGURABLE_SERIES = SeriesType.entries.filter {
    it != SeriesType.IOB && it != SeriesType.PREDICTIONS && it !in AUTOISF_SERIES && it !in AIMI_SERIES
}


// =========================================================================
// Long press interceptor using PointerEventPass.Initial
//
// detectTapGestures / combinedClickable use Main pass — child composables
// (OutlinedButton) consume events first and the parent never sees long press.
//
// Initial pass fires BEFORE children. We eavesdrop:
//   finger down + timeout → long press → invoke callback
//   finger up before timeout → normal tap → do nothing, children handle it
//
// The gesture is NOT keyed on the callback. pointerInput throws its coroutine
// away whenever a key changes, and a lambda written at the call site is a new
// object on every recomposition — keyed on it, a recomposition in the middle of
// a press restarted the gesture and the long press never fired. The timeout is
// a plain Long and holds still, and the callback is read through a state, so the
// running gesture always calls the newest one.
//
// internal rather than private so a test can drive the gesture directly.
// =========================================================================
@Composable
internal fun Modifier.interceptLongPress(
    enabled: Boolean,
    onLongPress: () -> Unit
): Modifier {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    if (!enabled) return this
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    return this.pointerInput(longPressTimeout) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            val isLongPress = try {
                withTimeout(longPressTimeout) {
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                }
                false
            } catch (_: PointerEventTimeoutCancellationException) {
                true
            }
            if (isLongPress) {
                currentOnLongPress()
                // Consume finger-up so button onClick doesn't fire afterwards
                var event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach { it.consume() }
                while (event.changes.any { it.pressed }) {
                    event = awaitPointerEvent(PointerEventPass.Main)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

/**
 * Moves the content up and shrinks its layout height by the same amount.
 *
 * Classic stitches the BG chart to the treatment belt with `Modifier.offset`, which moves the
 * drawing but keeps the layout size. That is fine when the graphs touch — the leftover layout
 * space is covered by the next strip. The glass look shows the card edge, so that leftover space
 * reads as empty glass under the chart. This modifier does the same move and also reclaims the
 * height, so the chart's top inset (the room for its topmost Y label) sits over the belt and the
 * card ends at the chart's bottom.
 */
private fun Modifier.pullUpOverPrevious(pullUp: Dp): Modifier =
    layout { measurable, constraints ->
        val px = pullUp.roundToPx()
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) {
            placeable.place(0, -px)
        }
    }

@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun GraphsSection(
    graphViewModel: GraphViewModel,
    isSimpleMode: Boolean,
    modifier: Modifier = Modifier,
    fitWholeWindow: Boolean = false,
    /**
     * Insulin and carbs on board, already formatted, for the spoken description of the BG graph.
     * The overview passes them; the history browser must NOT, because these are CURRENT values and
     * announcing them beside a past day would state something untrue about insulin.
     */
    iobText: String? = null,
    cobText: String? = null
) {
    val savedGraphConfig by graphViewModel.graphConfigFlow.collectAsStateWithLifecycle()
    // In simple mode: fixed layout (BG, IOB+BAS, COB — no overlays, no editing)
    val graphConfig = if (isSimpleMode) SIMPLE_MODE_CONFIG else savedGraphConfig

    // Include AutoISF/AIMI series in the picker only when the respective plugin is active
    val isAutoIsfActive by graphViewModel.isAutoIsfActiveFlow.collectAsStateWithLifecycle()
    val isAIMIActive by graphViewModel.isAIMIActiveFlow.collectAsStateWithLifecycle()
    // MHB subgraph renders nothing when BOOST is not the active algorithm (same gate as the BOOST panel)
    val isBoostActive by graphViewModel.isBoostActiveFlow.collectAsStateWithLifecycle()
    val configurableSeries = remember(isAutoIsfActive, isAIMIActive) {
        when {
            isAutoIsfActive -> BASE_CONFIGURABLE_SERIES + AUTOISF_SERIES.toList()
            isAIMIActive    -> BASE_CONFIGURABLE_SERIES + AIMI_SERIES.toList()
            else            -> BASE_CONFIGURABLE_SERIES
        }
    }


    // rememberVicoZoomState's underlying rememberSaveable is keyed on initialZoom/minZoom/maxZoom
    // by reference, so passing a freshly-allocated Zoom on every recomposition of this composable
    // tears down and recreates the VicoZoomState — resetting the zoom level back to initialZoom.
    // Memoize these once so all zoom states below stay stable across recompositions.
    val defaultZoom = remember { Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES) }
    val bgMinZoom = remember { Zoom.x(Constants.GRAPH_TIME_RANGE_HOURS * 60.0) }
    val bgMaxZoom = remember { Zoom.x(MIN_GRAPH_ZOOM_MINUTES) }

    // Where the graphs start. The overview is a live view, so the last six hours anchored at the
    // right edge is what the user wants. The history browser shows one chosen day, and its window
    // ends at that day's midnight - the same six hours would be the late evening only, and for
    // today it would be hours that have not happened yet, which reads as an empty graph. There it
    // starts fully zoomed out, which is exactly the day, because the view model spans the axis over
    // the whole window in that mode. Both pick one of the zoom objects above rather than allocating
    // a new one - see the note above on why a fresh Zoom instance tears the state down.
    val startZoom = if (fitWholeWindow) bgMinZoom else defaultZoom

    // BG graph - primary interactive.
    // Wrapped in key(bgViewportResetTrigger) so bumping the trigger tears down and recreates both
    // states from scratch, snapping them back to their initial values (Scroll.Absolute.End /
    // startZoom). This is deliberate: VicoZoomState.zoom(Zoom) is pinch-gesture-oriented
    // (it applies a ratio anchored on the current canvas center via an async pendingScroll flow)
    // and produced a wrong end state when used to "reset to a fixed default" — recreating the
    // state objects is simpler and matches the exact positioning Vico itself already uses on first
    // composition. Only reset on real inactivity (rare, never mid-gesture), so this doesn't
    // reintroduce the "churn during an active gesture" issues found elsewhere in this file.
    // IMPORTANT: every effect below that reads bgScrollState/bgZoomState.value MUST be keyed on
    // them (not just Unit) — otherwise it keeps a stale reference to the abandoned pre-reset
    // objects forever, comparing live secondary-graph state against a frozen stale value and
    // firing wrong corrections (this caused the secondary graphs to visibly "dance"/flash on the
    // first attempt at this).
    var bgViewportResetTrigger by remember { mutableIntStateOf(0) }
    val (bgScrollState, bgZoomState) = key(bgViewportResetTrigger) {
        rememberVicoScrollState(
            scrollEnabled = true,
            initialScroll = Scroll.Absolute.End
        ) to rememberVicoZoomState(
            zoomEnabled = true,
            initialZoom = startZoom,
            minZoom = bgMinZoom,
            maxZoom = bgMaxZoom
        )
    }
    // Pre-allocate secondary graph scroll/zoom states (up to MAX_SECONDARY_GRAPHS)
    // These are always created to keep Compose's remember slots stable
    val sec0scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec0zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)
    val sec1scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec1zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)
    val sec2scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec2zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)
    val sec3scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec3zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)
    val sec4scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec4zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)

    // Collect nowTimestamp ONCE so all graphs use the same value (avoids separate recompositions every 30s)
    val nowTimestamp by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()

    // Collect time range ONCE so all graphs use the exact same values in the same frame.
    // Without this, each graph independently collects derivedTimeRange via
    // collectAsStateWithLifecycle(), which can recompose in different frames —
    // causing minTimestamp divergence and scroll misalignment (pixel position
    // maps to different time when x-axis ranges differ).
    val derivedTimeRange by graphViewModel.derivedTimeRange.collectAsStateWithLifecycle()

    // BG's own visible window, sourced from the fixed IOB graph's already-computed visible range
    // (its scroll/zoom are synced to BG's, see the sync LaunchedEffect below) — not from a
    // decoration on BG's own chart, since that was tried and found to break BG's pinch-zoom
    // gesture handling (BG is the only graph with live scrollEnabled/zoomEnabled = true).
    var iobVisibleRange by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Settle further before feeding into BG specifically: unlike the secondary graphs (which are
    // non-interactive and unaffected by frequent updates), BG has live pinch-zoom. Updating its
    // axis range on every ~80ms tick during an active gesture kept changing the Y-axis label
    // width/layerDimensions mid-gesture and broke pinch-zoom. Only update once the window has
    // been stable for a bit, i.e. after the gesture ends.
    var iobVisibleRangeSettled by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { iobVisibleRange }
            .debounce(400)
            .collect { iobVisibleRangeSettled = it }
    }

    val bgVisibleTimeRange = derivedTimeRange?.first?.let { minTs ->
        iobVisibleRangeSettled?.let { (minXv, maxXv) ->
            (minTs + (minXv * 60_000).toLong()) to (minTs + (maxXv * 60_000).toLong())
        }
    }

    // Treatment belt graph - non-interactive, synced from BG
    val beltScrollState = rememberVicoScrollState(
        scrollEnabled = false,
        initialScroll = Scroll.Absolute.End
    )
    val beltZoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = startZoom
    )

    // Fixed IOB graph - non-interactive, synced from BG
    val iobScrollState = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val iobZoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)

    // Active graph count — rememberUpdatedState so coroutines always read the latest value
    // without writing to state during composition. Unattached states are no-ops for
    // .zoom()/.scroll(), but we skip them to avoid redundant calls.
    val activeCount by rememberUpdatedState(
        graphConfig.secondaryGraphs.size.coerceAtMost(GraphConfig.MAX_SECONDARY_GRAPHS)
    )

    // All secondary scroll/zoom states in arrays for indexed access (keyed to rebuild if state identity changes)
    val secScrollStates = remember(sec0scroll, sec1scroll, sec2scroll, sec3scroll, sec4scroll) {
        arrayOf(sec0scroll, sec1scroll, sec2scroll, sec3scroll, sec4scroll)
    }
    val secZoomStates = remember(sec0zoom, sec1zoom, sec2zoom, sec3zoom, sec4zoom) {
        arrayOf(sec0zoom, sec1zoom, sec2zoom, sec3zoom, sec4zoom)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe BG graph scroll/zoom and sync to belt + active secondary graphs
    // Keys include ALL state objects — identical pattern to the original working sync
    LaunchedEffect(
        bgScrollState, bgZoomState, beltScrollState, beltZoomState,
        iobScrollState, iobZoomState,
        sec0scroll, sec0zoom, sec1scroll, sec1zoom,
        sec2scroll, sec2zoom, sec3scroll, sec3zoom,
        sec4scroll, sec4zoom,
        lifecycleOwner
    ) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var initialValue = true
            snapshotFlow { bgScrollState.value to bgZoomState.value }
                .debounce(30) // Wait for gesture to settle
                .collect { (scroll, zoom) ->
                    if (initialValue) {
                        initialValue = false
                    } else {
                        graphViewModel.onGraphInteraction()
                    }
                    val count = activeCount
                    // Sync zoom first, then scroll (order matters for proper positioning)
                    beltZoomState.zoom(Zoom.fixed(zoom))
                    iobZoomState.zoom(Zoom.fixed(zoom))
                    for (i in 0 until count) secZoomStates[i].zoom(Zoom.fixed(zoom))
                    delay(10)
                    beltScrollState.scroll(Scroll.Absolute.pixels(scroll))
                    iobScrollState.scroll(Scroll.Absolute.pixels(scroll))
                    for (i in 0 until count) secScrollStates[i].scroll(Scroll.Absolute.pixels(scroll))
                }
        }
    }

    // Custom panel states — collected here (not inside the loop) to satisfy Compose composition rules
    val modesState by graphViewModel.modesFlow.collectAsStateWithLifecycle()
    val pulseState by graphViewModel.pulseFlow.collectAsStateWithLifecycle()
    val tirState by graphViewModel.tirFlow.collectAsStateWithLifecycle()
    // Auto-scroll when new BG value arrives
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    val dateUtil = LocalDateUtil.current
    var lastBgTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(bgInfoState.bgInfo?.timestamp, lifecycleOwner) {
        val newTimestamp = bgInfoState.bgInfo?.timestamp ?: return@LaunchedEffect
        val showPredictions = SeriesType.PREDICTIONS in graphConfig.bgOverlays
        if (lastBgTimestamp != 0L && newTimestamp > lastBgTimestamp) {
            // Skip auto-reset while user is interacting with the graph
            val sinceInteraction = dateUtil.now() - graphViewModel.lastInteractionMs
            if (sinceInteraction < INTERACTION_GRACE_MS) {
                lastBgTimestamp = newTimestamp
                return@LaunchedEffect
            }
            val timeRange = derivedTimeRange
            if (showPredictions && predictions.isNotEmpty() && timeRange != null) {
                // Scroll so "now + 3h" is at the right edge of viewport
                val (minTimestamp, _) = timeRange
                val nowX = timestampToX(dateUtil.now(), minTimestamp)
                bgScrollState.animateScroll(Scroll.Absolute.x(nowX + PREDICTION_VIEWPORT_FUTURE_BIAS_MINUTES, bias = 1f))
            } else {
                // No predictions - scroll to end
                bgScrollState.animateScroll(Scroll.Absolute.End)
            }
            // Reset scroll+zoom to their defaults by recreating the state objects (see
            // bgViewportResetTrigger doc above) instead of driving VicoZoomState.zoom(Zoom) to an
            // absolute target, which was found to land on the wrong zoom/scroll combination.
            bgViewportResetTrigger++
        }
        lastBgTimestamp = newTimestamp
    }

    // Correct secondary graph scroll drift — Vico may internally adjust scroll
    // when model producers fire. Watch for any divergence and re-sync to BG.
    // No isSyncing guard needed: primary sync only reads BG state, so writing to
    // secondary states here cannot trigger primary sync (no feedback loop).
    // Keyed on bgScrollState/bgZoomState (not Unit) — MUST restart when they're recreated by a
    // reset, otherwise this keeps comparing secondary graphs against a stale, abandoned pre-reset
    // reference forever and fires wrong corrections (the actual cause of the "dancing" regression
    // in the first attempt at this feature).
    LaunchedEffect(bgScrollState, bgZoomState) {
        snapshotFlow {
            // Only read states that are attached to a chart (belt + IOB fixed + active secondary)
            val count = activeCount
            buildList {
                add(beltScrollState.value to beltZoomState.value)
                add(iobScrollState.value to iobZoomState.value)
                for (i in 0 until count) {
                    add(secScrollStates[i].value to secZoomStates[i].value)
                }
            }
        }
            .debounce(100) // Let Vico settle after model update
            .collect { states ->
                val bgScroll = bgScrollState.value
                val bgZoom = bgZoomState.value
                val threshold = 1f
                val needsSync = states.any { (scroll, zoom) ->
                    abs(scroll - bgScroll) > threshold || abs(zoom - bgZoom) > 0.001f
                }
                if (needsSync) {
                    val count = activeCount
                    beltZoomState.zoom(Zoom.fixed(bgZoom))
                    iobZoomState.zoom(Zoom.fixed(bgZoom))
                    for (i in 0 until count) secZoomStates[i].zoom(Zoom.fixed(bgZoom))
                    delay(10)
                    beltScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    iobScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    for (i in 0 until count) secScrollStates[i].scroll(Scroll.Absolute.pixels(bgScroll))
                }
            }
    }


    // With the glass look the main graph (belt + BG chart) is one glass card and each extra
    // strip below it gets its own. Without it the graphs keep today's stitched layout.
    val glass = LocalOverviewGlass.current
    // Classic pulls the BG chart up 16.dp over the belt so the belt's invisible axis band is
    // covered and the charts share one time axis. Glass shows the card edge, so the same move
    // has to reclaim the height as well — a bare offset would leave empty glass under the chart.
    val stitchBg = if (glass.enabled) {
        Modifier.pullUpOverPrevious(16.dp)
    } else {
        Modifier.offset(y = (-16).dp)
    }
    val stitchStrip = if (glass.enabled) Modifier else Modifier.offset(y = (-8).dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = OverviewGlassSideInset,
                end = OverviewGlassSideInset,
                // Glass keeps a small gap above the main graph so its border does not touch the
                // status cards. Classic keeps the old 8.dp on both sides.
                top = if (glass.enabled) AapsSpacing.small else 8.dp,
                bottom = 8.dp
            ),
        verticalArrangement = if (glass.enabled) Arrangement.spacedBy(AapsSpacing.medium) else Arrangement.Top
    ) {
        // Main graph card: treatment belt + BG chart share the time axis and stay one unit.
        // When the glass look is on the card edge is visible, so the belt gets no extra top
        // padding and its axis gutters shrink — the colored strip starts right under the edge.
        var editingBgOverlays by remember { mutableStateOf(false) }
        // The graph is drawn, so a screen reader gets nothing from it at all - a chart contributes
        // no semantics node of its own. Describe it instead: the recent readings newest first,
        // because the current value is what matters most and a listener may stop there, plus what
        // is on board. The description goes on the graph itself rather than the Box, so the edit
        // button beside it stays a separate control.
        val bgReadings by graphViewModel.bgReadingsFlow.collectAsStateWithLifecycle()
        val profileUtil = LocalProfileUtil.current
        val decimalFormatter = LocalDecimalFormatter.current
        val recentValues = remember(bgReadings, profileUtil.units) {
            // BgDataPoint.value is ALREADY in the user's units, so it must not be converted again -
            // only formatted. The unit decides the decimals; the "detect" helpers guess that from
            // the magnitude and would read a very high mmol/l reading as mg/dl.
            bgReadings.takeLast(RECENT_VALUES_SPOKEN)
                .asReversed()
                .joinToString(", ") {
                    if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(it.value)
                    else decimalFormatter.to1Decimal(it.value)
                }
        }
        val graphDescription = when {
            recentValues.isEmpty()                 -> null
            iobText != null && cobText != null     -> stringResource(
                UiStrings.a11y_bg_graph_summary,
                recentValues,
                stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.iob), iobText),
                stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.cob), cobText)
            )

            else                                   -> stringResource(UiStrings.a11y_bg_graph_summary_values_only, recentValues)
        }

        OverviewGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = if (glass.enabled) {
                PaddingValues(start = AapsSpacing.small, end = AapsSpacing.small, top = 0.dp, bottom = AapsSpacing.small)
            } else {
                PaddingValues(AapsSpacing.small)
            }
        ) { panelModifier ->
            Column(modifier = panelModifier.fillMaxWidth()) {
                TreatmentBeltGraphCompose(
                    viewModel = graphViewModel,
                    scrollState = beltScrollState,
                    zoomState = beltZoomState,
                    derivedTimeRange = derivedTimeRange,
                    nowTimestamp = nowTimestamp,
                    compactFill = glass.enabled,
                    modifier = Modifier.fillMaxWidth()
                )
                // Outer box carries the stitch (offset or pull-up). The clickable sits on an
                // inner box so its hit area follows the chart when the pull-up places it higher.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(stitchBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!isSimpleMode) Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { editingBgOverlays = true }
                                ) else Modifier
                            )
                    ) {
                        BgGraphCompose(
                            viewModel = graphViewModel,
                            bgOverlays = graphConfig.bgOverlays,
                            scrollState = bgScrollState,
                            zoomState = bgZoomState,
                            derivedTimeRange = derivedTimeRange,
                            nowTimestamp = nowTimestamp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(graphConfig.bgHeight.dp)
                                .then(
                                    if (graphDescription != null) Modifier.semantics { contentDescription = graphDescription }
                                    else Modifier
                                )
                        )
                    }
                }
            }
        }
        if (editingBgOverlays) {
            GraphSeriesBottomSheet(
                title = stringResource(CoreUiStrings.graph_bg),
                selectedSeries = graphConfig.bgOverlays,
                availableSeries = BG_OVERLAY_SERIES,
                height = graphConfig.bgHeight,
                onHeightChange = { h ->
                    graphViewModel.updateGraphConfig(graphConfig.copy(bgHeight = h))
                },
                onToggle = { type ->
                    val current = graphConfig.bgOverlays.toMutableList()
                    if (type in current) current.remove(type) else current.add(type)
                    graphViewModel.updateGraphConfig(graphConfig.copy(bgOverlays = current))
                },
                onDismiss = { editingBgOverlays = false }
            )
        }
        // Fixed IOB graph (Graph 1) with optional Activity overlay
        var editingIobOverlays by remember { mutableStateOf(false) }
        if (graphConfig.showIobGraph) {
            OverviewGlassPanel(modifier = Modifier.fillMaxWidth().then(stitchStrip)) { panelModifier ->
            Box(
                modifier = panelModifier
                    .fillMaxWidth()
                    .then(
                        if (!isSimpleMode) Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { editingIobOverlays = true }
                        ) else Modifier
                    )
            ) {
                SecondaryGraphCompose(
                    viewModel = graphViewModel,
                    seriesTypes = listOf(SeriesType.IOB),
                    scrollState = iobScrollState,
                    zoomState = iobZoomState,
                    derivedTimeRange = derivedTimeRange,
                    nowTimestamp = nowTimestamp,
                    activityOverlay = SeriesType.ACTIVITY in graphConfig.iobOverlays,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(graphConfig.iobHeight.dp)
                )
                val iobHeaderColors = rememberSeriesColors()
                val iobHeaderSep = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                val iobHeaderBasalColor = AapsTheme.elementColors.tempBasal
                val iobHeaderParts = buildList {
                    add(stringResource(CoreUiStrings.iob) to iobHeaderColors.iob)
                    add(stringResource(CoreUiStrings.basal_shortname) to iobHeaderBasalColor)
                    if (SeriesType.ACTIVITY in graphConfig.iobOverlays) {
                        add(stringResource(CoreUiStrings.activity_shortname) to iobHeaderColors.activity)
                    }
                }
                Text(
                   text = coloredSeriesLabel(iobHeaderParts, iobHeaderSep),
                   style = MaterialTheme.typography.labelSmall,
                   modifier = Modifier
                       .align(Alignment.TopStart)
                       .padding(start = 36.dp, top = 2.dp)
                )
            }
            }
            if (editingIobOverlays) {
                GraphSeriesBottomSheet(
                    title = stringResource(CoreUiStrings.iob) + " / " + stringResource(CoreUiStrings.basal_shortname),
                    selectedSeries = graphConfig.iobOverlays,
                    availableSeries = listOf(SeriesType.ACTIVITY),
                    height = graphConfig.iobHeight,
                    onHeightChange = { h ->
                        graphViewModel.updateGraphConfig(graphConfig.copy(iobHeight = h))
                    },
                    onToggle = { type ->
                        val current = graphConfig.iobOverlays.toMutableList()
                        if (type in current) current.remove(type) else current.add(type)
                        graphViewModel.updateGraphConfig(graphConfig.copy(iobOverlays = current))
                    },
                    onRemoveGraph = {
                        graphViewModel.updateGraphConfig(graphConfig.copy(showIobGraph = false))
                        editingIobOverlays = false
                    },
                    onDismiss = { editingIobOverlays = false }
                )
            }
        }
        // Secondary graphs — config-driven (labels start at "Graph 2")
        var editingGraphIndex by remember { mutableIntStateOf(-1) }
        for (i in 0 until activeCount) {
            val secondary = graphConfig.secondaryGraphs[i]
            val customType = secondary.series.firstOrNull {
                it in AIMI_SERIES || it == SeriesType.PULSE || it == SeriesType.BOOST
            }
            // MHS and BOOST render nothing when BOOST is not the active algorithm.
            // Skip the glass frame too, so an empty slot never paints an empty card.
            val isEmptySlot =
                (customType == null && secondary.series == listOf(SeriesType.MHS) && !isBoostActive) ||
                    (customType == SeriesType.BOOST && !isBoostActive)
            if (isEmptySlot) continue
            OverviewGlassPanel(modifier = Modifier.fillMaxWidth().then(stitchStrip)) { panelModifier ->
            Box(modifier = panelModifier.fillMaxWidth()) {
                when (customType) {
                    SeriesType.MODES -> {
                        // FIX: pass onLongPress → interceptLongPress uses Initial pass
                        // so it fires BEFORE OutlinedButtons consume the gesture
                        ModesPanel(
                            events = modesState.events,
                            onRunEvent = { eventId -> graphViewModel.runAutomationEvent(eventId) },
                            onLongPress = if (!isSimpleMode) ({ editingGraphIndex = i }) else null,
                            modifier = Modifier.fillMaxWidth().height(secondary.height.dp)
                        )
                    }

                    SeriesType.PULSE -> {
                        // FIX: pass onLongPress → combinedClickable on Card handles both
                        PulsePanel(
                            state = pulseState,
                            onLongPress = if (!isSimpleMode) ({ editingGraphIndex = i }) else null,
                            modifier = Modifier.fillMaxWidth().height(secondary.height.dp)
                        )
                    }

                    SeriesType.TIR -> {
                        TirPanel(
                            state = tirState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(secondary.height.dp)
                                .then(
                                    if (!isSimpleMode) Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { editingGraphIndex = i }
                                    ) else Modifier
                                )
                        )
                    }

                    SeriesType.BOOST -> {
                        val boostState by graphViewModel.boostPanelFlow.collectAsStateWithLifecycle()
                        BoostDataCard(
                            state = boostState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(secondary.height.dp)
                                .then(
                                    if (!isSimpleMode) Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { editingGraphIndex = i }
                                    ) else Modifier
                                )
                        )
                    }

                    else             -> {
                        SecondaryGraphCompose(
                                viewModel = graphViewModel,
                                seriesTypes = secondary.series,
                                scrollState = secScrollStates[i],
                                zoomState = secZoomStates[i],
                                derivedTimeRange = derivedTimeRange,
                                nowTimestamp = nowTimestamp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(secondary.height.dp)
                                    .then(
                                        if (!isSimpleMode) Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = { editingGraphIndex = i }
                                        ) else Modifier
                                    )
                            )
                            val secHeaderColors = rememberSeriesColors()
                            val secHeaderSep = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            val secHeaderParts = secondary.series.map { s ->
                                stringResource(seriesShortNameId(s)) to secHeaderColors.colorFor(s)
                            }
                            Text(
                                text = coloredSeriesLabel(secHeaderParts, secHeaderSep),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 36.dp, top = 2.dp)
                            )
                    }
                }
            }
            }
        }
        if (editingGraphIndex >= 0 && editingGraphIndex < activeCount) {
            val editing = graphConfig.secondaryGraphs[editingGraphIndex]
            GraphSeriesBottomSheet(
                title = stringResource(CoreUiStrings.graph_number, editingGraphIndex + 2),
                selectedSeries = editing.series,
                availableSeries = configurableSeries,
                height = editing.height,
                onHeightChange = { h ->
                    val graphs = graphConfig.secondaryGraphs.toMutableList()
                    graphs[editingGraphIndex] = graphs[editingGraphIndex].copy(height = h)
                    graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                },
                onToggle = { type ->
                    val graphs = graphConfig.secondaryGraphs.toMutableList()
                    val current = graphs[editingGraphIndex].series.toMutableList()
                    if (type in current) {
                        current.remove(type)
                    } else {
                        current.add(type)
                        if (current.size > 5) current.removeAt(0) // FIFO: drop oldest
                    }
                    if (current.isEmpty()) {
                        // Auto-remove graph when all series deselected
                        graphs.removeAt(editingGraphIndex)
                        editingGraphIndex = -1
                    } else {
                        graphs[editingGraphIndex] = graphs[editingGraphIndex].copy(series = current)
                    }
                    graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                },
                onRemoveGraph = {
                    val graphs = graphConfig.secondaryGraphs.toMutableList()
                    graphs.removeAt(editingGraphIndex)
                    editingGraphIndex = -1
                    graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                },
                onDismiss = { editingGraphIndex = -1 }
            )
        }
        // Add graph button (hidden in simple mode)
        if (!isSimpleMode && activeCount < GraphConfig.MAX_SECONDARY_GRAPHS) {
            var showAddSheet by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showAddSheet = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(CoreUiStrings.graph_add), style = MaterialTheme.typography.labelMedium)
            }
            if (showAddSheet) {
                var newGraphSeries by remember { mutableStateOf(emptyList<SeriesType>()) }
                var newGraphHeight by remember { mutableIntStateOf(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP) }
                GraphSeriesBottomSheet(
                    title = stringResource(CoreUiStrings.graph_new),
                    selectedSeries = newGraphSeries,
                    availableSeries = configurableSeries,
                    height = newGraphHeight,
                    onHeightChange = { newGraphHeight = it },
                    onToggle = { type ->
                        val current = newGraphSeries.toMutableList()
                        if (type in current) {
                            current.remove(type)
                        } else {
                            current.add(type)
                            if (current.size > 5) current.removeAt(0)
                        }
                        newGraphSeries = current
                    },
                    onDismiss = {
                        if (newGraphSeries.isNotEmpty()) {
                            val graphs = graphConfig.secondaryGraphs.toMutableList()
                            graphs.add(SecondaryGraph(newGraphSeries, newGraphHeight))
                            graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                        }
                        newGraphSeries = emptyList()
                        newGraphHeight = GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                        showAddSheet = false
                    }
                )
            }
        }
        // Spacer so the last graph / Add button isn't covered by QuickLaunch toolbar
        Spacer(Modifier.height(48.dp))
    }
}

// =========================================================================
// Graph label generation
// =========================================================================

/** Build a per-segment colored label so each shortname matches its line color in the graph. */
private fun coloredSeriesLabel(
    parts: List<Pair<String, Color>>,
    separatorColor: Color
): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { i, (text, color) ->
        if (i > 0) withStyle(SpanStyle(color = separatorColor)) { append(" / ") }
        withStyle(SpanStyle(color = color)) { append(text) }
    }
}

/** String resource ID for the short name of a series type */
private fun seriesShortNameId(type: SeriesType): TextRef = when (type) {
    SeriesType.IOB             -> CoreUiStrings.iob
    SeriesType.ABS_IOB         -> CoreUiStrings.abs_insulin_shortname
    SeriesType.COB             -> CoreUiStrings.cob
    SeriesType.BGI             -> CoreUiStrings.bgi_shortname
    SeriesType.DEVIATIONS      -> CoreUiStrings.deviation_shortname
    SeriesType.SENSITIVITY     -> CoreUiStrings.sensitivity_shortname
    SeriesType.VAR_SENSITIVITY -> CoreUiStrings.variable_sensitivity_shortname
    SeriesType.DEV_SLOPE       -> CoreUiStrings.devslope_shortname
    SeriesType.HEART_RATE      -> CoreUiStrings.heartRate_shortname
    SeriesType.STEPS           -> CoreUiStrings.steps_shortname
    SeriesType.ACTIVITY        -> CoreUiStrings.activity_shortname
    SeriesType.PREDICTIONS     -> CoreUiStrings.predictions_shortname
    SeriesType.BASAL           -> CoreUiStrings.basal_shortname
    SeriesType.MODES           -> CoreUiStrings.modes_series_shortname
    SeriesType.PULSE           -> CoreUiStrings.pulse_series_shortname
    SeriesType.TIR             -> CoreUiStrings.tir_series_shortname
    SeriesType.BOLUS           -> CoreUiStrings.graph_series_smb
    SeriesType.IOB_THRESHOLD   -> CoreUiStrings.iob_threshold_shortname
    SeriesType.FINAL_ISF       -> CoreUiStrings.final_isf_shortname
    SeriesType.ACCE_ISF        -> CoreUiStrings.acce_isf_shortname
    SeriesType.BG_ISF          -> CoreUiStrings.bg_isf_shortname
    SeriesType.PP_ISF          -> CoreUiStrings.pp_isf_shortname
    SeriesType.DURA_ISF        -> CoreUiStrings.dura_isf_shortname
    SeriesType.BOOST           -> CoreUiStrings.boost
    SeriesType.MHS             -> CoreUiStrings.mhs
}

// =========================================================================
// Custom panel composables (MODES, PULSE, TIR)
//
// MODES and TIR are internal rather than private: the panel height test lays
// them out directly, at the sizes the height row offers.
// =========================================================================

@Composable
internal fun ModesPanel(
    events: List<AutomationEventData>,
    onRunEvent: (String) -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // FIX: was missing — pendingEvent must be declared here
    var pendingEvent by remember { mutableStateOf<AutomationEventData?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            // FIX: replaced detectTapGestures (Main pass, blocked by buttons)
            // with interceptLongPress (Initial pass, fires before buttons consume)
            .interceptLongPress(enabled = onLongPress != null, onLongPress = onLongPress ?: {})
    ) {
        if (events.isEmpty()) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        } else {
            // Rows of five share the panel height, so a taller panel gives taller buttons
            // instead of empty space under them. The FlowRow this replaces kept every button
            // at 32 dp, and a flow row cannot stretch its rows to fill the panel.
            for (row in events.take(10).chunked(5)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    for (event in row) {
                        OutlinedButton(
                            onClick = { pendingEvent = event },
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    pendingEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingEvent = null },
            title = { Text(event.title) },
            text = { Text(stringResource(CoreUiStrings.run_event_question, event.title)) },
            confirmButton = {
                Button(onClick = {
                    onRunEvent(event.id)
                    pendingEvent = null
                }) { Text(stringResource(CoreUiStrings.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingEvent = null }) {
                    Text(stringResource(CoreUiStrings.cancel))
                }
            }
        )
    }
}

@Composable
private fun PulsePanel(
    state: PulseUiState,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val screenOpener = LocalScreenOpener.current
    // FIX: was missing — hypoColor must be declared here
    val hypoColor = Color(0xFFD50000)

    Card(
        // FIX: Card has no onClick → combinedClickable on modifier handles both
        // tap (navigate to detail) and long press (edit graph)
        modifier = modifier
            .padding(bottom = 4.dp)
            .combinedClickable(
                // The card keeps showing the pulse numbers on every target. Only the tap that opens
                // the detail screen is Android-only, and on a target without it the tap does nothing.
                onClick = { screenOpener.open("app.aaps.plugins.aps.openAPSAIMI.advisor.pulse.AimiPulseDetailActivity") },
                onLongClick = { onLongPress?.invoke() }
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (state.titleText.isNotEmpty()) {
                Text(
                    text = state.titleText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isHypoRisk) hypoColor else MaterialTheme.colorScheme.onSurface
                )
            }
            if (state.summaryText.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.summaryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4
                )
            }
            if (state.metaText.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = state.metaText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (state.hintText.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.hintText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
internal fun TirPanel(
    state: TirUiState,
    modifier: Modifier = Modifier
) {
    val colorVeryLow  = Color(0xFFD50000)
    val colorLow      = Color(0xFFFF6D00)
    val colorInRange  = Color(0xFF00C853)
    val colorHigh     = Color(0xFFFFD600)
    val colorVeryHigh = Color(0xFFD50000)

    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(CoreUiStrings.tir_series_shortname),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))
        if (state.readingCount == 0) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = TextUnit(9f, TextUnitType.Sp)
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        } else {
            val segments = listOf(
                state.veryLow  to colorVeryLow,
                state.low      to colorLow,
                state.inRange  to colorInRange,
                state.high     to colorHigh,
                state.veryHigh to colorVeryHigh
            )
            // Color bar — takes the height the panel has left, so a taller panel shows a taller
            // bar instead of empty space, and the labels stay under it.
            TirColorBar(
                segments = segments,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Spacer(Modifier.height(2.dp))
            // Percentage labels
            Row(modifier = Modifier.fillMaxWidth()) {
                for ((fraction, color) in segments) {
                    if (fraction > 0f) {
                        Box(modifier = Modifier.weight(fraction)) {
                            val pct = (fraction).toInt()
                            if (fraction >= 0.05f) {
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = TextUnit(9f, TextUnitType.Sp)
                                    ),
                                    color = color,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatTemplate("Avg %.0f mg/dL · A1C %.1f%%", listOf(state.avgMgDl, state.a1c)),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = TextUnit(9f, TextUnitType.Sp)
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * The coloured TIR bar.
 *
 * It draws at the height it is given, so the caller decides how tall the bar is - the panel hands
 * it the height no other row needs. Each segment is as wide as its share of the readings.
 *
 * @param segments each range with its share of the readings (0..100) and its colour
 */
@Composable
internal fun TirColorBar(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        for ((fraction, color) in segments) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

// =========================================================================
// Graph series bottom sheet
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GraphSeriesBottomSheet(
    title: String,
    selectedSeries: List<SeriesType>,
    availableSeries: List<SeriesType>,
    height: Int,
    onHeightChange: (Int) -> Unit,
    onToggle: (SeriesType) -> Unit,
    onDismiss: () -> Unit,
    onRemoveGraph: (() -> Unit)? = null
) {
    // Buffer height locally — committing on every keystroke crashed the parent
    // composition (Vico SubcomposeLayout + IME insets re-measurement raced with
    // the live height change). Commit only on dismiss.
    var pendingHeight by remember(height) { mutableIntStateOf(height) }
    val flushAndDismiss = {
        if (pendingHeight != height) onHeightChange(pendingHeight)
        onDismiss()
    }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = flushAndDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (onRemoveGraph != null) {
                    TextButton(onClick = onRemoveGraph) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(CoreUiStrings.remove))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            NumberInputRow(
                labelRef = CoreUiStrings.graph_height,
                value = pendingHeight.toDouble(),
                onValueChange = { pendingHeight = it.toInt() },
                valueRange = GraphConfig.MIN_GRAPH_HEIGHT_DP.toDouble()..GraphConfig.MAX_GRAPH_HEIGHT_DP.toDouble(),
                step = 10.0
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                for (type in availableSeries) {
                    FilterChip(
                        selected = type in selectedSeries,
                        onClick = { onToggle(type) },
                        label = { Text(stringResource(seriesShortNameId(type))) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}


@Composable
private fun BoostDataCard(state: BoostPanelState, modifier: Modifier) {
    if (!state.enabled) return
    Card(modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // ── Row 1: numeric stats (Fast Carb | DynISF | TDD | Activity ) ──
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.fastCarbProtection) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFF9800).copy(alpha = 0.18f)) {
                        Text("Fast Carb", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFF9800))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DynISF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.dynIsfLabel.isNotEmpty()) {
                        Text(state.dynIsfLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    else
                    {
                        Text(state.dynIsf, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TDD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.tdd, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Activity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.activityLabel, style = MaterialTheme.typography.labelSmall, color = Color(state.activityColor.toInt()))
                }
                // Surface(shape = RoundedCornerShape(12.dp), color = Color(state.statusColor.toInt()).copy(alpha = 0.15f)) {
                //     Text(state.status, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                //         style = MaterialTheme.typography.labelMedium, color = Color(state.statusColor.toInt()))
                // }
            }

            // ── V5 state strip (only when V5 meal-hypothesis is active) ──
            if (state.v5Active) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(6.dp))

                // Row 2: state label + dose/budget
                if (state.v5StateLabel.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            state.v5StateLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(state.statusColor.toInt())
                        )
                        if (state.v5DoseBudget.isNotEmpty()) {
                            Text(
                                state.v5DoseBudget,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Row 3: score bar
                if (state.v5Score > 0f) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SCORE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { state.v5Score },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = Color(state.statusColor.toInt()),
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatTemplate("%.2f", listOf(state.v5Score)),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Row 4: active brakes
                if (state.v5Brakes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.v5Brakes,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFFFB923C)
                    )
                }
            }
        }
    }
}
