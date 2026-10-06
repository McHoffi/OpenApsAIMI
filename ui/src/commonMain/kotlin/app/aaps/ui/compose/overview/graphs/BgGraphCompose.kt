package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BasalGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.ChartSmbMarker
import app.aaps.core.interfaces.overview.graph.ChartTbrSegment
import app.aaps.core.interfaces.overview.graph.EpsGraphPoint
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.overview.graph.TargetLineData
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.interfaces.resources.formatTemplate
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.overview.LocalOverviewGlass
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlinx.coroutines.delay

/** Series identifiers */
/** Basal on BG graph — deprecated, now shown as flipped overlay on IOB graph. Set to true to restore. */
@Deprecated("Basal moved to IOB graph as flipped overlay")
private const val showBasalOnBgGraph = false
/** Series identifiers */

private const val SERIES_REGULAR = "regular"
private const val SERIES_BUCKETED = "bucketed"
private const val SERIES_PRED_IOB = "pred_iob"
private const val SERIES_PRED_COB = "pred_cob"
private const val SERIES_PRED_ACOB = "pred_acob"
private const val SERIES_PRED_UAM = "pred_uam"
private const val SERIES_PRED_ZT = "pred_zt"
private const val SERIES_DASHBOARD_SMB = "dashboard_smb"

/** All prediction series identifiers */
private val PREDICTION_SERIES = listOf(SERIES_PRED_IOB, SERIES_PRED_COB, SERIES_PRED_ACOB, SERIES_PRED_UAM, SERIES_PRED_ZT)

/**
 * CartesianChartModelProducer.update() skips notifying receivers (and thus skips recomputing axis
 * ranges) when a transaction's partials AND extraStore are both unchanged from the last one. Since
 * scrolling/zooming re-submits identical series data (only the visible window changed), stashing
 * the visible window here forces the extraStore to differ, so Vico actually reprocesses the
 * transaction instead of silently dropping it. Same mechanism as SecondaryGraphCompose.kt.
 */
private val BG_VISIBLE_RANGE_KEY = ExtraStore.Key<Pair<Long?, Long?>>()

/**
 * A [CartesianLayerRangeProvider] backed by plain mutable fields instead of an immutable value
 * object. [CartesianLayerRangeProvider.fixed] returns a NEW instance whenever bounds change,
 * which forces Vico to rebuild the [com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer]
 * and mint a new CartesianChart id — that in turn triggers Vico's internal chart re-registration,
 * which was found to destabilize BG's live pinch-zoom/scroll gesture handling (BG is the only
 * graph with an interactive chart). This object's IDENTITY never changes across scroll/zoom; only
 * its field values do, read fresh whenever Vico next processes a modelProducer transaction — so
 * the chart itself is never rebuilt mid-gesture. Private to this file: BG is the only graph that
 * needs this (secondary graphs are non-interactive, so `.fixed(...)` churn never affected them).
 */
private class MutableYRangeProvider(
    @Volatile var maxX: Double,
    @Volatile var minY: Double,
    @Volatile var maxY: Double,
    @Volatile var yStep: Double = 0.0
) : CartesianLayerRangeProvider {
    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = 0.0
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = this.maxX
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = this.minY
    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = this.maxY
}

/**
 * BG Graph using Vico — dual-layer chart.
 *
 * Layer 0 (start axis): BG readings — subtle neutral dots plus a continuous curve coloured by range
 * Layer 1 (end axis, hidden): Basal — profile (dashed step) + actual delivered (solid step + area fill)
 *
 * **BG start-axis Y** follows legacy [app.aaps.core.graph.data.GlucoseValueDataPoint] / GraphView:
 * display units from General → Units (mg/dL or mmol/L). Cache [BgDataPoint.value] stays mg/dL; conversion
 * happens when building Vico series so ticks and data share one coordinate space.
 *
 * Basal Y-axis is scaled so maxBasal occupies [BASAL_HEIGHT_FRACTION] of the chart height (maxY = maxBasal / BASAL_HEIGHT_FRACTION).
 *
 * Scroll/Zoom:
 * - Accepts external scroll/zoom states for synchronization with secondary graphs
 * - This is the primary interactive graph - user controls scroll/zoom here
 *
 * Tap on SMB triangle to show bolus details dialog.
 */
@Composable
fun BgGraphCompose(
    viewModel: GraphViewModel,
    bgOverlays: List<SeriesType>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    useMaterial3DashboardStyle: Boolean = false,
    /** Dashboard-only SMB markers as a Vico line series (triangles), empty on overview. */
    dashboardSmbMarkers: List<ChartSmbMarker> = emptyList(),
    dashboardTbrSegments: List<ChartTbrSegment> = emptyList(),
    dashboardTbrMarkerEpochMs: List<Long> = emptyList(),
    /**
     * When true, the BG start axis uses a fixed **0 … max** vertical range so decorations (TBR) align with the
     * glycémie = 0 grid line (dashboard). Overview keeps the default auto Y range when false.
     */
    lockStartAxisYFromZero: Boolean = false,
    /**
     * Fallback TBR vertical layout when [lockStartAxisYFromZero] is false: distance from layer bottom (pixels).
     */
    dashboardTbrLegacyBottomReservePx: Float = 0f,
    /**
     * Dashboard: SMB/TBR, « now » line and BG point outlines use a **muted** palette (no alarm red for SMB)
     * and lighter weights to keep therapy cues readable without a punitive feel.
     */
    dashboardSoftTherapyVisuals: Boolean = false,
    /**
     * Dashboard AIMI scenario projection: show only floor (IOB) + best (UAM) with high-contrast styling,
     * risk envelope band, and terminal marker. PKPD reference curves (COB/ZT/aCOB) are hidden.
     */
    dashboardScenarioProjectionVisuals: Boolean = false,
    /**
     * When true with [SeriesType.ACTIVITY] in [bgOverlays], activity is drawn in [DashboardActivityStripChart]
     * below this chart (own Y scale) instead of being scaled into mg/dL space.
     */
    dashboardSplitActivityToStrip: Boolean = false,
    /**
     * Dashboard-only: called when the user taps an SMB triangle on this chart.
     * Uses Vico marker hit-testing (scroll/zoom aware). Overview leaves this null.
     */
    onDashboardSmbMarkerTap: @Composable ((ChartSmbMarker) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    // Collect flows independently - each triggers recomposition only when it changes
    val bgReadings by viewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val bucketedData by viewModel.bucketedDataFlow.collectAsStateWithLifecycle()
    val showPredictions = SeriesType.PREDICTIONS in bgOverlays
    val rawPredictions by viewModel.predictionsFlow.collectAsStateWithLifecycle()
    val predictions = if (showPredictions) rawPredictions else emptyList()
    val rawBasalData by viewModel.basalGraphFlow.collectAsStateWithLifecycle()
    val targetData by viewModel.targetLineFlow.collectAsStateWithLifecycle()
    val showBasal = SeriesType.BASAL in bgOverlays
    val basalData = if (showBasal) rawBasalData else BasalGraphData(emptyList(), emptyList(), 0.0)
    val epsPoints by viewModel.epsGraphFlow.collectAsStateWithLifecycle()
    val showActivityOnMainChart = SeriesType.ACTIVITY in bgOverlays && !dashboardSplitActivityToStrip
    val activityData by viewModel.activityGraphFlow.collectAsStateWithLifecycle()
    val showBolus = SeriesType.BOLUS in bgOverlays
    val treatmentData by viewModel.treatmentGraphFlow.collectAsStateWithLifecycle()
    val smbColor = AapsTheme.elementColors.smbMarker
    val chartConfig by viewModel.chartConfigFlow.collectAsStateWithLifecycle()
    val generalUnits by viewModel.generalUnits.collectAsStateWithLifecycle()
    val vicoChartLook by viewModel.vicoChartLookFlow.collectAsStateWithLifecycle()
    val glass = LocalOverviewGlass.current
    val chartStyle = AapsTheme.chartStyle
    // Glass draws the chart over a translucent gradient. The classic guideline color is too faint
    // there, so use the stronger outline color to keep the grid visible.
    val guidelineColor =
        if (glass.enabled) MaterialTheme.colorScheme.outline.copy(alpha = chartStyle.glassGridAlpha)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = chartStyle.gridAlpha)
    val axisLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = chartStyle.axisLabelAlpha)

    // Y-axis matches legacy GraphView: display units (mg/dL or mmol/L per General → Units).
    // [BgDataPoint.value] / target line / DB remain mg/dL; convert at Vico series build.
    val startAxisMaxY = remember(bgReadings, bucketedData, predictions, chartConfig, targetData, generalUnits) {
        val bgDisplay = (bgReadings + bucketedData).map { viewModel.glucoseMgdlToChartY(it.value) }
        val fromBg = if (bgDisplay.isNotEmpty()) bgDisplay.max() else chartConfig.highMark
        val predMax = predictions.maxOfOrNull { viewModel.glucoseMgdlToChartY(it.value) }
        val targetMax =
            targetData.targets.maxOfOrNull { viewModel.glucoseMgdlToChartY(it.value) }
                ?: Double.NEGATIVE_INFINITY
        maxOf(fromBg, predMax ?: fromBg, chartConfig.highMark, targetMax)
    }

    // Use derived time range or fall back to default (last GRAPH_TIME_RANGE_HOURS hours)
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = dateUtil.now()
        val dayAgo = now - Constants.GRAPH_TIME_RANGE_HOURS * 60 * 60 * 1000L
        dayAgo to now
    }

    // Single model producer shared by all layers
    val modelProducer = remember { CartesianChartModelProducer() }

    // Series registry - tracks current data for each series
    val seriesRegistry = remember { mutableStateMapOf<String, List<BgDataPoint>>() }

    // Colors from theme + optional Vico tint preference (discrete BG dots only)
    val scheme = MaterialTheme.colorScheme
    val regularColorBase = if (useMaterial3DashboardStyle) {
        scheme.primary
    } else {
        AapsTheme.generalColors.originalBgValue
    }
    val regularColor = remember(vicoChartLook.bgReadingTintKey, regularColorBase, scheme) {
        bgReadingTintColor(vicoChartLook.bgReadingTintKey, regularColorBase, scheme)
    }
    val lowColor = AapsTheme.generalColors.bgLow
    val inRangeColor = AapsTheme.generalColors.bgInRange
    val highColor = AapsTheme.generalColors.bgHigh
    val basalColor = AapsTheme.elementColors.tempBasal
    val targetLineColor = AapsTheme.elementColors.tempTarget
    val activityColor = AapsTheme.elementColors.activity

    // Predictions: keep AAPS palette (overview parity). Dashboard only overrides BG dot stroke above.
    val iobPredColor = AapsTheme.generalColors.iobPrediction
    val cobPredColor = AapsTheme.generalColors.cobPrediction
    val aCobPredColor = AapsTheme.generalColors.aCobPrediction
    val uamPredColor = AapsTheme.generalColors.uamPrediction
    val ztPredColor = AapsTheme.generalColors.ztPrediction

    // Shared fade window for prediction lines: full alpha at the first prediction point,
    // lowest alpha at the last. All series use the same window so they fade together.
    val predictionFadeWindow = remember(predictions, minTimestamp) {
        if (predictions.isEmpty()) {
            null
        } else {
            val xs = predictions.map { timestampToX(it.timestamp, minTimestamp) }
            xs.min() to xs.max()
        }
    }

    // Calculate x-axis range (must match COB graph for alignment)
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }

    // // Stable range-provider instance for the start (BG) axis — created once, mutated in place by
    // // the LaunchedEffect below rather than recreated (see MutableYRangeProvider).
    // val startAxisRangeProvider = remember {
    //     val initialScale = niceScale(chartConfig.lowMark, chartConfig.highMark)
    //     MutableYRangeProvider(maxX = maxX, minY = initialScale.min, maxY = initialScale.max, yStep = initialScale.step)
    // }

    // Track which BG-layer lines to emit (area fill, band runs, dots, SMB, predictions)
    val bgLinePlanState = remember { mutableStateOf(BgLinePlan()) }

    // Stable time range - only changes when timestamps change by more than 1 minute
    val stableTimeRange = remember(minTimestamp / 60000, maxTimestamp / 60000) {
        minTimestamp to maxTimestamp
    }

    // =========================================================================
    // SMB tap detection state
    // =========================================================================
    var selectedSmb by remember { mutableStateOf<BolusGraphPoint?>(null) }

    suspend fun rebuildChart(
        currentBasalData: BasalGraphData,
        currentTargetData: TargetLineData,
        currentEpsPoints: List<EpsGraphPoint>,
        currentActivityData: ActivityGraphData,
        currentMinBgY: Double,
        currentMaxBgY: Double,
        currentTreatmentData: TreatmentGraphData,
        showBolusMarkers: Boolean,
        currentLowMark: Double,
        currentHighMark: Double
    ) {
        val regularPoints = seriesRegistry[SERIES_REGULAR] ?: emptyList()
        val bucketedPoints = seriesRegistry[SERIES_BUCKETED] ?: emptyList()
        val smbPoints = seriesRegistry[SERIES_DASHBOARD_SMB] ?: emptyList()

        // Note: do NOT early-return when there are no BG points. With a clean DB the chart must
        // still build its frame (axes, now-line, in-range belt) via the normalizer + dummy layers,
        // matching the COB graph. The per-layer logic below already handles empty series.

        modelProducer.runTransaction {
            // Block 1 → BG layer (layer 0, start axis)
            lineModel {
                var hasAreaFill = false
                val bandRuns = mutableListOf<BgBand>()
                var hasRegularDots = false
                var hasBucketedDots = false
                var hasSmb = false
                val predictionIds = mutableListOf<String>()

                fun toSortedPoints(source: List<BgDataPoint>): List<Pair<Double, Double>> =
                    source
                        .map { timestampToX(it.timestamp, minTimestamp) to viewModel.glucoseMgdlToChartY(it.value) }
                        .sortedBy { it.first }

                // Continuous BG curve: bucketed (smoothed) when present, else raw readings.
                val lineSource = toSortedPoints(
                    if (bucketedPoints.isNotEmpty()) bucketedPoints else regularPoints
                )
                val veryHighY = viewModel.glucoseMgdlToChartY(VERY_HIGH_BG_MGDL)

                if (lineSource.size >= 2) {
                    // Soft area under the whole curve (not split by band).
                    series(x = lineSource.map { it.first }, y = lineSource.map { it.second })
                    hasAreaFill = true

                    // Coloured curve, one series per band run so the colour changes at the thresholds.
                    for (run in splitBgLineByBand(lineSource, currentLowMark, currentHighMark, veryHighY)) {
                        series(x = run.points.map { it.first }, y = run.points.map { it.second })
                        bandRuns.add(run.band)
                    }
                }

                if (regularPoints.isNotEmpty()) {
                    val dataPoints = toSortedPoints(regularPoints)
                    series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                    hasRegularDots = true
                }

                if (bucketedPoints.isNotEmpty()) {
                    val dataPoints = toSortedPoints(bucketedPoints)
                    series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                    hasBucketedDots = true
                }

                if (smbPoints.isNotEmpty()) {
                    val dataPoints = toSortedPoints(smbPoints)
                    series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                    hasSmb = true
                }

                // Prediction series - each type as a separate line
                for (predSeries in PREDICTION_SERIES) {
                    val predPoints = seriesRegistry[predSeries]
                    if (predPoints != null && predPoints.isNotEmpty()) {
                        val dataPoints = toSortedPoints(predPoints)
                        series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                        predictionIds.add(predSeries)
                    }
                }

                // Normalizer series
                series(x = normalizerX(maxX), y = NORMALIZER_Y)

                bgLinePlanState.value = BgLinePlan(
                    hasAreaFill = hasAreaFill,
                    bandRuns = bandRuns.toList(),
                    hasRegularDots = hasRegularDots,
                    hasBucketedDots = hasBucketedDots,
                    hasSmb = hasSmb,
                    predictionIds = predictionIds.toList(),
                )
            }

            // Block 2 → Basal layer (layer 1, end axis)
            lineModel {
                if (currentBasalData.profileBasal.size >= 2) {
                    val pts = currentBasalData.profileBasal
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }

                if (currentBasalData.actualBasal.size >= 2) {
                    val pts = currentBasalData.actualBasal
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 3 → Target line layer (layer 2, start axis)
            // Only the live target value line. The low/high range is a soft band decoration.
            lineModel {
                if (currentTargetData.targets.size >= 2) {
                    val pts = currentTargetData.targets
                        .map { timestampToX(it.timestamp, minTimestamp) to viewModel.glucoseMgdlToChartY(it.value) }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 4 → EPS layer (layer 3, start axis — Y based on profile %, scaled into BG coordinate space)
            // Same principle as legacy (originalPercentage/100 * baseline); baseline = 75% of the BG axis
            // height. Anchored at currentMinBgY (not 0) — since the axis floor is no longer fixed at 0,
            // a 0%-profile point must sit at the axis' actual bottom, not fall below it and disappear.
            lineModel {
                if (currentEpsPoints.isNotEmpty()) {
                    val epsBaseline = (currentMaxBgY - currentMinBgY) * 0.75
                    val pts = currentEpsPoints
                        .map { eps -> timestampToX(eps.timestamp, minTimestamp) to (currentMinBgY + eps.originalPercentage / 100.0 * epsBaseline) }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 5 → Activity layer (layer 4, start axis — Y-values normalized to BG coordinate space)
            // Scale so maxActivity maps to 80% of maxBgY (same as legacy: maxY * 0.8 / maxIAValue)
            lineModel {
                val maxAct = currentActivityData.maxActivity
                if (!showActivityOnMainChart || maxAct <= 0.0 || currentActivityData.activity.size < 2) {
                    // Activity disabled or no data — emit dummy series (history + prediction)
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    return@lineModel
                }
                val scaleFactor = (currentMaxBgY - currentMinBgY) * 0.8 / maxAct

                val pts = currentActivityData.activity
                    .map { timestampToX(it.timestamp, minTimestamp) to (currentMinBgY + it.value * scaleFactor) }
                    .sortedBy { it.first }
                series(x = pts.map { it.first }, y = pts.map { it.second })

                if (currentActivityData.activityPrediction.size >= 2) {
                    val predPts = currentActivityData.activityPrediction
                        .map { timestampToX(it.timestamp, minTimestamp) to (currentMinBgY + it.value * scaleFactor) }
                        .sortedBy { it.first }
                    series(x = predPts.map { it.first }, y = predPts.map { it.second })
                } else {
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 6 → SMB markers (layer 5, start axis — fixed at bottom of BG chart)
            lineSeries {
                val smbX = if (showBolusMarkers) {
                    currentTreatmentData.boluses
                        .filter { it.isValid && it.bolusType == BolusType.SMB }
                        .map { timestampToX(it.timestamp, minTimestamp) }
                        .filter { it in 0.0..maxX }
                } else emptyList()
                if (smbX.isNotEmpty()) {
                    series(x = smbX, y = List(smbX.size) { currentLowMark })
                } else {
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }
        }
    }

    // Split predictions by type into registry
    val predictionsByType = remember(predictions, dashboardScenarioProjectionVisuals) {
        if (dashboardScenarioProjectionVisuals) {
            mapOf(
                SERIES_PRED_IOB to predictions.filter { it.type == BgType.IOB_PREDICTION },
                SERIES_PRED_COB to emptyList(),
                SERIES_PRED_ACOB to emptyList(),
                SERIES_PRED_UAM to predictions.filter { it.type == BgType.UAM_PREDICTION },
                SERIES_PRED_ZT to emptyList(),
            )
        } else {
            mapOf(
                SERIES_PRED_IOB to predictions.filter { it.type == BgType.IOB_PREDICTION },
                SERIES_PRED_COB to predictions.filter { it.type == BgType.COB_PREDICTION },
                SERIES_PRED_ACOB to predictions.filter { it.type == BgType.A_COB_PREDICTION },
                SERIES_PRED_UAM to predictions.filter { it.type == BgType.UAM_PREDICTION },
                SERIES_PRED_ZT to predictions.filter { it.type == BgType.ZT_PREDICTION },
            )
        }
    }

    // Single LaunchedEffect for all data - ensures atomic updates
    LaunchedEffect(
        bgReadings,
        bucketedData,
        predictionsByType,
        basalData,
        targetData,
        epsPoints,
        activityData,
        showActivityOnMainChart,
        chartConfig,
        generalUnits,
        stableTimeRange,
        dashboardSmbMarkers,
        dashboardSplitActivityToStrip,
        dashboardScenarioProjectionVisuals,
    ) {
        seriesRegistry[SERIES_REGULAR] = bgReadings
        seriesRegistry[SERIES_BUCKETED] = bucketedData
        for ((key, points) in predictionsByType) {
            seriesRegistry[key] = points
        }
        // maxBgY clamped against highMark (same as legacy GraphData.maxY logic)
        val allBgValues = (bgReadings + bucketedData).map { it.value }
        var maxBgY = if (allBgValues.isNotEmpty()) maxOf(allBgValues.max(), chartConfig.highMark) else chartConfig.highMark
        val minBgY = if (allBgValues.isNotEmpty()) minOf(allBgValues.min(), chartConfig.lowMark) else chartConfig.lowMark
        rebuildChart(basalData, targetData, epsPoints, activityData, minBgY, maxBgY, treatmentData, showBolus, chartConfig.lowMark, chartConfig.highMark)
        val sortedBgAsc = bgReadings.sortedBy { it.timestamp }
        val fallbackSmbY = viewModel.glucoseDisplayYToMgdl(
            (chartConfig.lowMark + chartConfig.highMark) / 2.0,
        )
        seriesRegistry[SERIES_DASHBOARD_SMB] =
            if (dashboardSplitActivityToStrip) {
                emptyList()
            } else {
                dashboardSmbMarkers.map { m ->
                    val y = interpolateBgForDashboardMarker(
                        epochMs = m.timestampEpochMs,
                        sortedAsc = sortedBgAsc,
                        fallbackY = fallbackSmbY,
                    )
                    BgDataPoint(
                        timestamp = m.timestampEpochMs,
                        value = y,
                        range = BgRange.IN_RANGE,
                        type = BgType.REGULAR,
                    )
                }
            }
        // Activity layer shares BG axis — scale in display units (legacy GraphData maxY).
        val bgDisplay = (bgReadings + bucketedData).map { viewModel.glucoseMgdlToChartY(it.value) }
        val targetPeakDisplay =
            targetData.targets.maxOfOrNull { viewModel.glucoseMgdlToChartY(it.value) }
                ?: Double.NEGATIVE_INFINITY
        maxBgY = if (bgDisplay.isNotEmpty()) {
            maxOf(bgDisplay.max(), chartConfig.highMark, targetPeakDisplay)
        } else {
            maxOf(chartConfig.highMark, targetPeakDisplay)
        }
        rebuildChart(basalData, targetData, epsPoints, activityData,minBgY, maxBgY, treatmentData, showBolus, chartConfig.lowMark, chartConfig.highMark)
    }

    // Time formatter and axis configuration
    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)

    // =========================================================================
    // BG layer lines (layer 0)
    // =========================================================================
    val regularColorChart = if (dashboardSoftTherapyVisuals) regularColor.copy(alpha = 0.88f) else regularColor
    val customTintDots = vicoChartLook.bgReadingTintKey != VicoChartAppearance.TINT_THEME
    val regularDotFillAlpha = if (customTintDots) {
        if (dashboardSoftTherapyVisuals) 0.38f else chartStyle.bgPointFillAlpha
    } else {
        chartStyle.bgPointFillAlpha
    }

    // Subtle neutral dots. Range colouring lives on the continuous line only.
    val regularLine = remember(
        regularColorChart,
        chartStyle.bgPointStrokeAlpha,
        regularDotFillAlpha,
        chartStyle.bgPointSize,
    ) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(
                        fill = Fill(regularColorChart.copy(alpha = regularDotFillAlpha)),
                        shape = CircleShape,
                        strokeFill = Fill(regularColorChart.copy(alpha = chartStyle.bgPointStrokeAlpha)),
                        strokeThickness = 1.dp
                    ),
                    size = chartStyle.bgPointSize
                )
            )
        )
    }

    // Continuous BG curve: one coloured run per band, plus a soft fading area under the whole line.
    // Low and very high share the red band colour ("tief = rot", "sehr hoch = rot").
    val bgAreaFillLine = remember(inRangeColor, chartStyle.bgLineFillTopAlpha, chartStyle.bgLineFillBottomAlpha) {
        createBgAreaFillLine(
            wash = inRangeColor,
            topAlpha = chartStyle.bgLineFillTopAlpha,
            bottomAlpha = chartStyle.bgLineFillBottomAlpha,
        )
    }
    val bgBandLineLow = remember(lowColor, chartStyle.bgLineStrokeWidth) {
        createBgBandLine(lowColor, chartStyle.bgLineStrokeWidth)
    }
    val bgBandLineTarget = remember(inRangeColor, chartStyle.bgLineStrokeWidth) {
        createBgBandLine(inRangeColor, chartStyle.bgLineStrokeWidth)
    }
    val bgBandLineHigh = remember(highColor, chartStyle.bgLineStrokeWidth) {
        createBgBandLine(highColor, chartStyle.bgLineStrokeWidth)
    }
    fun bgBandLineFor(band: BgBand): LineCartesianLayer.Line = when (band) {
        BgBand.LOW       -> bgBandLineLow
        BgBand.TARGET    -> bgBandLineTarget
        BgBand.HIGH      -> bgBandLineHigh
        BgBand.VERY_HIGH -> bgBandLineLow
    }

    val smbFillResolved = if (dashboardSoftTherapyVisuals) {
        scheme.secondaryContainer.copy(alpha = 0.52f)
    } else {
        scheme.error.copy(alpha = 0.42f)
    }
    val smbStrokeResolved = if (dashboardSoftTherapyVisuals) {
        scheme.outline.copy(alpha = 0.48f)
    } else {
        scheme.error.copy(alpha = 0.88f)
    }
    val smbPointSize = if (dashboardSoftTherapyVisuals) 9.dp else 11.dp
    val smbStrokeThickness = if (dashboardSoftTherapyVisuals) 1.dp else 2.dp
    // Hit target only: the visible triangles are drawn by [SmbMarkersDecoration] over the layers.
    val smbDashboardLine = remember {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(Color.Transparent), shape = TriangleShape),
                    size = 0.dp
                )
            )
        )
    }
    val normalizerLine = remember { createNormalizerLine() }

    val surfaceForBlend = scheme.surface
    val useScenarioProjectionLines = dashboardScenarioProjectionVisuals && showPredictions
    val scenarioPointHalo = scheme.onSurface.copy(alpha = 0.94f)
    val scenarioFloorColor = iobPredColor
    val scenarioBestColor = remember(uamPredColor, scheme.tertiary) {
        // UAM palette (#C9BD60) is muted on dark dashboard — lift toward tertiary for contrast.
        lerp(uamPredColor, scheme.tertiary, 0.45f)
    }
    val iobPredLine = remember(
        scenarioFloorColor,
        scenarioPointHalo,
        dashboardSoftTherapyVisuals,
        surfaceForBlend,
        useScenarioProjectionLines,
        chartStyle,
        predictionFadeWindow,
    ) {
        when {
            useScenarioProjectionLines -> createScenarioFloorLine(scenarioFloorColor, scenarioPointHalo)
            dashboardSoftTherapyVisuals -> createSoftPredictionLine(softenChartColor(iobPredColor, surfaceForBlend))
            else -> createPredictionLine(iobPredColor, chartStyle, predictionFadeWindow)
        }
    }
    val cobPredLine = remember(cobPredColor, dashboardSoftTherapyVisuals, surfaceForBlend, chartStyle, predictionFadeWindow) {
        val c = if (dashboardSoftTherapyVisuals) softenChartColor(cobPredColor, surfaceForBlend) else cobPredColor
        if (dashboardSoftTherapyVisuals) createSoftPredictionLine(c) else createPredictionLine(c, chartStyle, predictionFadeWindow)
    }
    val aCobPredLine = remember(aCobPredColor, dashboardSoftTherapyVisuals, surfaceForBlend, chartStyle, predictionFadeWindow) {
        val c = if (dashboardSoftTherapyVisuals) softenChartColor(aCobPredColor, surfaceForBlend) else aCobPredColor
        if (dashboardSoftTherapyVisuals) createSoftPredictionLine(c) else createPredictionLine(c, chartStyle, predictionFadeWindow)
    }
    val uamPredLine = remember(
        scenarioBestColor,
        scenarioPointHalo,
        dashboardSoftTherapyVisuals,
        surfaceForBlend,
        useScenarioProjectionLines,
        chartStyle,
        predictionFadeWindow,
    ) {
        when {
            useScenarioProjectionLines -> createScenarioBestLine(scenarioBestColor, scenarioPointHalo)
            dashboardSoftTherapyVisuals -> createSoftPredictionLine(softenChartColor(uamPredColor, surfaceForBlend))
            else -> createPredictionLine(uamPredColor, chartStyle, predictionFadeWindow)
        }
    }
    val ztPredLine = remember(ztPredColor, dashboardSoftTherapyVisuals, surfaceForBlend, chartStyle, predictionFadeWindow) {
        val c = if (dashboardSoftTherapyVisuals) softenChartColor(ztPredColor, surfaceForBlend) else ztPredColor
        if (dashboardSoftTherapyVisuals) createSoftPredictionLine(c) else createPredictionLine(c, chartStyle, predictionFadeWindow)
    }
    val bgLinePlan by bgLinePlanState
    val bgLines = remember(
        bgLinePlan,
        bgAreaFillLine,
        bgBandLineLow,
        bgBandLineTarget,
        bgBandLineHigh,
        regularLine,
        smbDashboardLine,
        iobPredLine,
        cobPredLine,
        aCobPredLine,
        uamPredLine,
        ztPredLine,
        normalizerLine,
    ) {
        val plan = bgLinePlan
        buildList {
            if (plan.hasAreaFill) add(bgAreaFillLine)
            for (band in plan.bandRuns) add(bgBandLineFor(band))
            if (plan.hasRegularDots) add(regularLine)
            if (plan.hasBucketedDots) add(regularLine)
            if (plan.hasSmb) add(smbDashboardLine)
            if (SERIES_PRED_IOB in plan.predictionIds) add(iobPredLine)
            if (SERIES_PRED_COB in plan.predictionIds) add(cobPredLine)
            if (SERIES_PRED_ACOB in plan.predictionIds) add(aCobPredLine)
            if (SERIES_PRED_UAM in plan.predictionIds) add(uamPredLine)
            if (SERIES_PRED_ZT in plan.predictionIds) add(ztPredLine)
            add(normalizerLine)
        }
    }

    // =========================================================================
    // Basal layer lines (layer 1) — always 2 lines: [profileLine, actualLine]
    // =========================================================================

    // Profile basal: dashed line, no fill, step connector
    val profileBasalLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Dashed(
                thickness = 1.dp,
                cap = StrokeCap.Round,
                dashLength = 1.dp,
                gapLength = 2.dp
            ),
            areaFill = null,
            interpolator = Square
        )
    }

    // Actual delivered basal: solid line with semi-transparent area fill, step connector
    val actualBasalLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp),
            areaFill = LineCartesianLayer.AreaFill.single(Fill(basalColor.copy(alpha = 0.3f))),
            interpolator = Square
        )
    }

    val basalLines = remember(profileBasalLine, actualBasalLine) {
        listOf(profileBasalLine, actualBasalLine)
    }

    // =========================================================================
    // Target line (layer 2) — single line on start (BG) axis
    // =========================================================================

    val targetLineColorUse = if (dashboardSoftTherapyVisuals) targetLineColor.copy(alpha = 0.48f) else targetLineColor
    val targetLine = remember(targetLineColorUse) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(targetLineColorUse)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp),
            areaFill = null,
            interpolator = Square
        )
    }

    val targetLines = remember(targetLine) { listOf(targetLine) }

    // =========================================================================
    // EPS layer lines (layer 3) — profile icon points
    // =========================================================================

    val profileSwitchColor = AapsTheme.elementColors.profileSwitch
    val profilePainter = rememberVectorPainter(IcProfile)

    val epsLine = remember(profileSwitchColor, profilePainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(profilePainter, tint = profileSwitchColor),
                    size = 16.dp
                )
            )
        )
    }

    val epsLines = remember(epsLine) { listOf(epsLine) }

    // =========================================================================
    // Activity layer lines (layer 4) — solid historical + dashed prediction
    // =========================================================================

    val activityColorUse = if (dashboardSoftTherapyVisuals) activityColor.copy(alpha = 0.42f) else activityColor
    val activityHistStroke = if (dashboardSoftTherapyVisuals) 1.dp else 1.5.dp
    val activityPredStroke = if (dashboardSoftTherapyVisuals) 1.dp else 1.5.dp
    val activityHistLine = remember(activityColorUse, activityHistStroke) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(activityColorUse)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = activityHistStroke),
            areaFill = null
        )
    }

    val activityPredLine = remember(activityColorUse, activityPredStroke) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(activityColorUse)),
            stroke = LineCartesianLayer.LineStroke.Dashed(
                thickness = activityPredStroke,
                cap = StrokeCap.Round,
                dashLength = 4.dp,
                gapLength = 4.dp
            ),
            areaFill = null
        )
    }

    val activityLines = remember(activityHistLine, activityPredLine) {
        listOf(activityHistLine, activityPredLine)
    }

    // Outline keeps the marker readable on top of the basal wash and the BG curve.
    val markerOutlineColor = MaterialTheme.colorScheme.onSurface
    // Hit target only: the visible triangles are drawn by [SmbMarkersDecoration] over the layers.
    val smbLine = remember {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(Color.Transparent), shape = TriangleShape),
                    size = 0.dp
                )
            )
        )
    }
    val smbLines = remember(smbLine) { listOf(smbLine) }

    // Basal Y-axis range: maxBasal * 4 so basal occupies ~25% of chart height
    // EPS layer shares End axis with basal, so both must use the same Y-range (basalMaxY)
    val basalMaxY = remember(basalData.maxBasal) {
        if (basalData.maxBasal > 0.0) basalData.maxBasal / BASAL_HEIGHT_FRACTION else 1.0
    }

    // =========================================================================
    // Decorations
    // =========================================================================

    val nowLineColor = if (dashboardSoftTherapyVisuals) {
        scheme.onSurface.copy(alpha = 0.36f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor, chartStyle)

    // Highlight the current (latest) BG reading: halo + ring, coloured by its BG band.
    val currentBgPoint = remember(bgReadings, bucketedData, minTimestamp) {
        val source = if (bucketedData.isNotEmpty()) bucketedData else bgReadings
        val latest = source.maxByOrNull { it.timestamp } ?: return@remember null
        val chartY = viewModel.glucoseMgdlToChartY(latest.value)
        timestampToX(latest.timestamp, minTimestamp) to chartY
    }
    val currentBgHighlightColor = remember(currentBgPoint, chartConfig, lowColor, inRangeColor, highColor) {
        val point = currentBgPoint ?: return@remember lowColor
        val veryHighY = viewModel.glucoseMgdlToChartY(VERY_HIGH_BG_MGDL)
        bgBandColor(
            band = bgBandOf(point.second, chartConfig.lowMark, chartConfig.highMark, veryHighY),
            low = lowColor,
            target = inRangeColor,
            high = highColor,
        )
    }
    val currentBgHighlight = rememberCurrentBgHighlight(
        point = currentBgPoint,
        glowColor = currentBgHighlightColor,
        glowRadius = chartStyle.currentBgGlowRadius,
        glowAlpha = chartStyle.currentBgGlowAlpha,
        ringColor = currentBgHighlightColor,
        ringRadius = chartStyle.currentBgRingRadius,
        ringWidth = chartStyle.currentBgRingWidth,
    )
    // Visible SMB triangles live in the over-layers so they sit above basal, the BG curve and the grid.
    // The matching line series stays in the model as a tap hit target (invisible points).
    // Drawn triangle positions are recorded here so taps can hit the shape the user sees.
    val overviewSmbHits = remember { SmbHitPositionHolder() }
    val dashboardSmbHits = remember { SmbHitPositionHolder() }
    val overviewSmbPoints = remember(treatmentData, showBolus, minTimestamp, maxX, chartConfig) {
        if (!showBolus) {
            emptyList()
        } else {
            treatmentData.boluses
                .filter { it.isValid && it.bolusType == BolusType.SMB }
                .map { it.timestamp to timestampToX(it.timestamp, minTimestamp) }
                .filter { (_, x) -> x in 0.0..maxX }
                .map { (timestamp, x) ->
                    SmbMarkerPoint(timestampEpochMs = timestamp, x = x, y = chartConfig.lowMark)
                }
        }
    }
    val overviewSmbDecoration = rememberSmbMarkers(
        points = overviewSmbPoints,
        color = smbColor,
        outlineColor = markerOutlineColor,
        size = chartStyle.smbMarkerSize,
        strokeWidth = chartStyle.smbMarkerStrokeWidth,
        hitHolder = overviewSmbHits,
    )
    val dashboardSmbPoints = remember(
        dashboardSmbMarkers,
        bgReadings,
        minTimestamp,
        dashboardSplitActivityToStrip,
        chartConfig,
        generalUnits,
    ) {
        if (dashboardSplitActivityToStrip) {
            emptyList()
        } else {
            val sortedBgAsc = bgReadings.sortedBy { it.timestamp }
            val fallbackMgdl = viewModel.glucoseDisplayYToMgdl(
                (chartConfig.lowMark + chartConfig.highMark) / 2.0,
            )
            dashboardSmbMarkers.map { m ->
                val mgdl = interpolateBgForDashboardMarker(m.timestampEpochMs, sortedBgAsc, fallbackMgdl)
                SmbMarkerPoint(
                    timestampEpochMs = m.timestampEpochMs,
                    x = timestampToX(m.timestampEpochMs, minTimestamp),
                    y = viewModel.glucoseMgdlToChartY(mgdl),
                )
            }
        }
    }
    val dashboardSmbDecoration = rememberSmbMarkers(
        points = dashboardSmbPoints,
        color = smbFillResolved,
        outlineColor = smbStrokeResolved,
        size = smbPointSize,
        strokeWidth = smbStrokeThickness,
        hitHolder = dashboardSmbHits,
    )
    val tbrLaneWash = if (dashboardSoftTherapyVisuals) scheme.surfaceContainerHighest else scheme.tertiary.copy(alpha = 0.78f)
    val tbrBarHalo = if (dashboardSoftTherapyVisuals) scheme.outlineVariant else scheme.tertiary.copy(alpha = 0.78f)
    val tbrBarCore = if (dashboardSoftTherapyVisuals) scheme.secondary else scheme.tertiary.copy(alpha = 0.78f)
    val tbrMarkerLine = if (dashboardSoftTherapyVisuals) scheme.outline else scheme.tertiary.copy(alpha = 0.78f)
    val tbrDecoration = rememberDashboardTbrLaneDecoration(
        minTimestamp = minTimestamp,
        segments = if (dashboardSplitActivityToStrip) emptyList() else dashboardTbrSegments,
        markerEpochMs = if (dashboardSplitActivityToStrip) emptyList() else dashboardTbrMarkerEpochMs,
        bgAxisYMin = if (lockStartAxisYFromZero) 0.0 else null,
        bgAxisYMax = if (lockStartAxisYFromZero) startAxisMaxY else null,
        legacyBottomReservePx = dashboardTbrLegacyBottomReservePx,
        laneBackground = tbrLaneWash,
        barFillSoft = tbrBarHalo,
        barFillStrong = tbrBarCore,
        markerLineColor = tbrMarkerLine,
        softStyle = dashboardSoftTherapyVisuals,
    )
    // Soft tinted target-range band (low mark .. high mark). Replaces the old hard dashed marks.
    val targetRangeBandDecoration = rememberTargetRangeBandDecoration(
        yRange = if (chartConfig.lowMark < chartConfig.highMark) {
            chartConfig.lowMark to chartConfig.highMark
        } else {
            null
        },
        fillColor = AapsTheme.generalColors.bgTargetRangeArea,
        cornerRadius = chartStyle.targetBandCorner,
    )
    val scenarioGeometry = remember(
        predictions,
        dashboardScenarioProjectionVisuals,
        showPredictions,
        minTimestamp,
        generalUnits,
    ) {
        if (!dashboardScenarioProjectionVisuals || !showPredictions) {
            null
        } else {
            buildScenarioProjectionChartGeometry(predictions, minTimestamp, viewModel::glucoseMgdlToChartY)
        }
    }
    val scenarioDecorations = rememberScenarioProjectionDecorations(
        geometry = scenarioGeometry,
        bgAxisMinY = 0.0,
        bgAxisMaxY = startAxisMaxY,
        envelopeFillColor = scenarioBestColor,
        terminalMarkerColor = scenarioBestColor,
        terminalGlowColor = scenarioBestColor,
    )

    val decorations = remember(
        targetRangeBandDecoration,
        scenarioDecorations,
        tbrDecoration,
        currentBgHighlight,
        overviewSmbDecoration,
        dashboardSmbDecoration,
        nowLine,
    ) {
        buildList {
            targetRangeBandDecoration?.let { add(it) }
            currentBgHighlight?.let { add(it) }
            addAll(scenarioDecorations)
            tbrDecoration?.let { add(it) }
            add(nowLine)
            overviewSmbDecoration?.let { add(it) }
            dashboardSmbDecoration?.let { add(it) }
        }
    }

    val defaultMarkerController = CartesianMarkerController.rememberShowOnPress()
    val smbTouchRadiusPx = with(LocalDensity.current) { chartStyle.smbMarkerTouchRadius.toPx() }
    val dashboardSmbTapController =
        remember(
            treatmentData,
            dashboardSmbMarkers,
            minTimestamp,
            dashboardSplitActivityToStrip,
            overviewSmbHits,
            dashboardSmbHits,
            smbTouchRadiusPx,
        ) {
            if (dashboardSplitActivityToStrip) return@remember null
            val treatmentSmbBoluses = treatmentData.boluses.filter { it.isValid && it.bolusType == BolusType.SMB }
            if (dashboardSmbMarkers.isEmpty() && treatmentSmbBoluses.isEmpty()) return@remember null
            DashboardSmbTapMarkerController(
                smbs = dashboardSmbMarkers,
                smbBoluses = treatmentSmbBoluses,
                minTimestamp = minTimestamp,
                hitHolders = listOf(overviewSmbHits, dashboardSmbHits),
                touchRadiusPx = smbTouchRadiusPx,
                onSmbTap = { smb ->
                    //Log.d("SMB_TAP", "Vico marker matched: ${smb.amountLabel}")
                    selectedSmb = treatmentData.boluses.firstOrNull {
                        it.timestamp == smb.timestampEpochMs && it.bolusType == BolusType.SMB && it.isValid
                    }
                },
            )
        }
    val valueFormatter = remember {
        DefaultCartesianMarker.ValueFormatter { _, _ -> "" }
    }
    val dashboardSmbTapMarker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(),
        valueFormatter = valueFormatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
        indicator = null,
        guideline = null,
    )
    val sortedBgAscForMarker = remember(bgReadings) { bgReadings.sortedBy { it.timestamp } }
    val bgValueMarker = rememberBgValueMarker(
        minTimestamp = minTimestamp,
        sortedBg = sortedBgAscForMarker,
        formatChartYValue = viewModel::formatBgChartAxisTick,
        mgdlToChartY = viewModel::glucoseMgdlToChartY,
    )

    // =========================================================================
    // Range providers — hoisted out of rememberCartesianChart so keys are re-evaluated on recomposition
    // =========================================================================

    val startAxisRangeProvider = remember(maxX, lockStartAxisYFromZero, startAxisMaxY) {
        if (lockStartAxisYFromZero) {
            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = startAxisMaxY)
        } else {
            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)
        }
    }
    val endAxisRangeProvider = remember(maxX, basalMaxY) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = basalMaxY)
    }

    // =========================================================================
    // SMB tap detection via Vico marker pipeline
    // =========================================================================

    CartesianChartHost(
        chart = rememberCartesianChart(
            // Layer 0: BG (start axis, visible)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(bgLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 1: Basal (end axis, hidden — no endAxis parameter)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(basalLines),
                rangeProvider = endAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.End
            ),
            // Layer 2: Target line (start axis — shares BG Y-axis range)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(targetLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 3: EPS (start axis — Y based on profile %, same range as BG layer)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(epsLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 4: Activity (start axis — shares BG Y-axis range, values normalized in rebuildChart)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(activityLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 5: SMB markers (start axis — triangle points fixed at lowMark, no line)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(smbLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            startAxis = VerticalAxis.rememberStart(
                itemPlacer = VerticalAxis.ItemPlacer.step({ 1.0 }),
                label = rememberTextComponent(
                    style = TextStyle(color = axisLabelColor),
                    minWidth = TextComponent.MinWidth.fixed(30.dp)
                ),
                guideline = LineComponent(fill = Fill(guidelineColor))
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = timeFormatter,
                itemPlacer = bottomAxisItemPlacer,
                label = rememberTextComponent(
                    style = TextStyle(color = axisLabelColor)
                ),
                guideline = LineComponent(fill = Fill(guidelineColor))
            ),
            decorations = decorations,
            marker = if (dashboardSmbTapController != null) dashboardSmbTapMarker else null,
            markerController = dashboardSmbTapController ?: defaultMarkerController,
            getXStep = { _, _, _ -> 1.0 }
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth(),
        scrollState = scrollState,
        zoomState = zoomState
    )

    // SMB tap popup — anchored at top-center when selectedSmb is non-null
    selectedSmb?.let { smb ->
        val timeStr = remember(smb.timestamp) { dateUtil.timeString(smb.timestamp) }
        Popup(
            alignment = Alignment.TopCenter,
            offset = IntOffset(0, 8),
            onDismissRequest = { selectedSmb = null },
            properties = PopupProperties(focusable = true),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(max = 200.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = smb.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = AapsTheme.elementColors.smbMarker,
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            LaunchedEffect(smb) {
                delay(3000L)
                selectedSmb = null
            }
        }
    }
}

private fun interpolateBgForDashboardMarker(
    epochMs: Long,
    sortedAsc: List<BgDataPoint>,
    fallbackY: Double,
): Double {
    if (sortedAsc.isEmpty()) return fallbackY
    if (sortedAsc.size == 1) return sortedAsc.first().value
    if (epochMs <= sortedAsc.first().timestamp) return sortedAsc.first().value
    if (epochMs >= sortedAsc.last().timestamp) return sortedAsc.last().value
    for (i in 0 until sortedAsc.lastIndex) {
        val a = sortedAsc[i]
        val b = sortedAsc[i + 1]
        if (epochMs < a.timestamp || epochMs > b.timestamp) continue
        val span = (b.timestamp - a.timestamp).toDouble().coerceAtLeast(1.0)
        val t = ((epochMs - a.timestamp).toDouble() / span).coerceIn(0.0, 1.0)
        return a.value + t * (b.value - a.value)
    }
    return sortedAsc.last().value
}

/**
 * Marker shown when tapping any point on the BG line: the value at that time (in the user's display
 * unit, via [formatChartYValue] — the same formatter the Y-axis ticks already use) and the time of day.
 */
@Composable
private fun rememberBgValueMarker(
    minTimestamp: Long,
    sortedBg: List<BgDataPoint>,
    formatChartYValue: (Double) -> CharSequence,
    mgdlToChartY: (Double) -> Double,
): DefaultCartesianMarker {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline
    val dateUtil = LocalDateUtil.current
    val valueTimeTemplate = stringResource(UiStrings.graph_point_value_time)

    val labelBackground = remember(surfaceColor, outlineColor) {
        ShapeComponent(
            fill = Fill(surfaceColor),
            shape = RoundedCornerShape(4.dp),
            strokeFill = Fill(outlineColor),
            strokeThickness = 1.dp
        )
    }
    val markerLabel = rememberTextComponent(
        style = TextStyle(color = onSurfaceColor, fontSize = 15.sp),
        padding = Insets(horizontal = 10.dp, vertical = 5.dp),
        background = labelBackground
    )
    val valueFormatter = remember(minTimestamp, sortedBg, formatChartYValue, mgdlToChartY, valueTimeTemplate) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            if (sortedBg.isEmpty()) return@ValueFormatter ""
            val x = targets.firstOrNull()?.x ?: return@ValueFormatter ""
            val epochMs = minTimestamp + (x * 60000).toLong()
            val bgMgdl = interpolateBgForDashboardMarker(epochMs, sortedBg, fallbackY = sortedBg.last().value)
            val valueText = formatChartYValue(mgdlToChartY(bgMgdl))
            val timeText = dateUtil.timeString(epochMs)
            formatTemplate(valueTimeTemplate, listOf(valueText, timeText))
        }
    }

    return rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = valueFormatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
        indicator = null,
        guideline = null,
    )
}

/**
 * Tapping an SMB triangle fires [onSmbTap] (the popup). Any other tap falls through to the normal
 * value/time tooltip.
 *
 * Hit testing is two-step:
 * 1. **2D circle around the drawn triangle** (positions recorded by `SmbMarkersDecoration`) with
 *    radius [touchRadiusPx] — this is the main path, so the touch window matches what the user sees.
 * 2. **Fallback** on Vico's nearest model-X targets, for a tap near an SMB that misses the shape.
 *
 * Per the Vico contract for [shouldAcceptInteraction], returning `false` only skips updating marker
 * visibility for that interaction — it does not hide an already-visible marker.
 */
private class DashboardSmbTapMarkerController(
    private val smbs: List<ChartSmbMarker>,
    private val smbBoluses: List<BolusGraphPoint>,
    private val minTimestamp: Long,
    private val hitHolders: List<SmbHitPositionHolder>,
    private val touchRadiusPx: Float,
    private val onSmbTap: (ChartSmbMarker) -> Unit,
) : CartesianMarkerController {

    override val acceptsLongPress: Boolean get() = false

    // Vico only calls shouldShowMarker after shouldAcceptInteraction returns true for the SAME
    // interaction, and both run on the UI thread during pointer-input handling, so a plain var is
    // enough to pass "was this tap an SMB hit" across the two calls.
    private var lastTapWasSmb = false

    override fun shouldAcceptInteraction(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean {
        if (interaction !is Interaction.Tap) return true
        val tapX = interaction.point.x
        val tapY = interaction.point.y

        // 1) 2D hit on the drawn triangles (works even when Vico snaps to a neighbouring BG point).
        var bestSmb: ChartSmbMarker? = null
        val canvasHit = findSmbCanvasHit(
            hits = hitHolders.flatMap { it.points },
            tapX = tapX,
            tapY = tapY,
            radiusPx = touchRadiusPx,
        )
        if (canvasHit != null) {
            bestSmb = markerForTimestamp(canvasHit.timestampEpochMs)
        }

        // 2) Fallback: nearest model-X targets (old path).
        if (bestSmb == null && targets.isNotEmpty()) {
            var bestDist = Float.POSITIVE_INFINITY
            for (t in targets) {
                if (t !is LineCartesianLayerMarkerTarget) continue
                val smb = smbs.minByOrNull { smb ->
                    abs(timestampToX(smb.timestampEpochMs, minTimestamp) - t.x)
                }?.takeIf { abs(timestampToX(it.timestampEpochMs, minTimestamp) - t.x) < MODEL_X_MATCH_EPS }
                if (smb != null) {
                    val d = abs(t.canvasX - tapX)
                    if (d <= SMB_TAP_MAX_CANVAS_X_DIST_PX && d < bestDist) {
                        bestDist = d
                        bestSmb = smb
                    }
                    continue
                }
                // Also match against treatment SMB boluses (the actual data source for Layer 5 triangles)
                val bolus = smbBoluses.minByOrNull { b ->
                    abs(timestampToX(b.timestamp, minTimestamp) - t.x)
                }?.takeIf { abs(timestampToX(it.timestamp, minTimestamp) - t.x) < MODEL_X_MATCH_EPS }
                if (bolus != null) {
                    val d = abs(t.canvasX - tapX)
                    if (d <= SMB_TAP_MAX_CANVAS_X_DIST_PX && d < bestDist) {
                        bestDist = d
                        bestSmb = ChartSmbMarker(
                            timestampEpochMs = bolus.timestamp,
                            amountLabel = bolus.label,
                        )
                    }
                }
            }
        }

        lastTapWasSmb = bestSmb != null
        if (bestSmb != null) {
            onSmbTap(bestSmb)
        }
        // Always true: an SMB hit must still reach shouldShowMarker so it can actively hide any
        // stale value/time tooltip left over from an earlier tap, instead of leaving it on screen.
        return true
    }

    override fun shouldShowMarker(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean =
        !lastTapWasSmb

    private fun markerForTimestamp(timestampEpochMs: Long): ChartSmbMarker? {
        smbs.firstOrNull { it.timestampEpochMs == timestampEpochMs }?.let { return it }
        val bolus = smbBoluses.firstOrNull { it.timestamp == timestampEpochMs } ?: return null
        return ChartSmbMarker(
            timestampEpochMs = bolus.timestamp,
            amountLabel = bolus.label,
        )
    }

    private companion object {
        private const val MODEL_X_MATCH_EPS = 0.02
        private const val SMB_TAP_MAX_CANVAS_X_DIST_PX = 56f
    }
}
