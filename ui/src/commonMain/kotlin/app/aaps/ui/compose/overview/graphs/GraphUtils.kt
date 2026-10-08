package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.aaps.core.graph.vico.Linear
import app.aaps.core.interfaces.aps.MealHypothesisCoreState
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.ChartStyle
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import app.aaps.core.interfaces.overview.graph.ChartTbrSegment
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Shared utilities for Vico graphs in AAPS.
 *
 * CRITICAL: All graphs MUST use the same x-coordinate system to ensure proper alignment.
 * This uses whole minutes from minTimestamp to avoid label repetition and precision errors.
 *
 * **Graph Alignment (3 pillars):**
 * All graphs MUST have identical x-axis configuration for pixel-based scroll/zoom sync:
 * 1. `rangeProvider = CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)` — same X range
 * 2. `getXStep = { _, _, _ -> 1.0 }` — same xStep (1 minute per unit)
 * 3. Normalizer line ([createNormalizerLine] + [NORMALIZER_X]/[NORMALIZER_Y] dummy series) —
 *    ensures identical maxPointSize across all charts → same xSpacing and unscalable padding
 *
 * **Scroll/Zoom Synchronization:**
 * - BG graph (primary): scrollEnabled = true, zoomEnabled = true
 * - Secondary graphs: scrollEnabled = false, zoomEnabled = false
 * - Pixel-based sync: snapshotFlow { bgScrollState.value to bgZoomState.value }
 * - See OverviewGraphsSection for full implementation
 *
 * **Point Connectors:**
 * - Adaptive step graphs (COB): Use `AdaptiveStep` - steps for steep angles (>45°), lines for gradual
 * - Fixed step graphs (IOB, AbsIOB): Use `Square` PointConnector from core.graph.vico
 * - Smooth graphs (Activity, BGI, Ratio): Use default connector (no pointConnector parameter)
 */

/**
 * Convert timestamp to x-value (whole minutes from minTimestamp).
 *
 * CRITICAL: This is the standard x-coordinate calculation for ALL graphs.
 * - Uses whole minutes (not milliseconds or fractional hours)
 * - Prevents label repetition (Vico increments by 1)
 * - Avoids precision errors with decimals
 *
 * @param timestamp The data point timestamp in milliseconds
 * @param minTimestamp The reference timestamp (start of graph time range)
 * @return X-value in whole minutes from minTimestamp
 */
fun timestampToX(timestamp: Long, minTimestamp: Long): Double =
    ((timestamp - minTimestamp) / 60000).toDouble()

/**
 * Creates a time formatter for X-axis labels showing hours (HH format).
 *
 * @param minTimestamp The reference timestamp for x-value calculation
 * @return CartesianValueFormatter that converts x-values back to time labels
 */
@Composable
fun rememberTimeFormatter(minTimestamp: Long): CartesianValueFormatter {
    return remember(minTimestamp) {
        CartesianValueFormatter { _, value, _ ->
            val timestamp = minTimestamp + (value * 60000).toLong()
            // Always 24 hour, as SimpleDateFormat("HH") was: on an axis a bare 12 hour label would
            // not say which half of the day it belongs to.
            Instant.fromEpochMilliseconds(timestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .hour.toString().padStart(2, '0')
        }
    }
}

/**
 * Creates an item placer for X-axis that shows labels at whole hour intervals.
 *
 * Calculates offset from minTimestamp to align labels with whole hours (e.g., 12:00, 13:00).
 *
 * @param minTimestamp The reference timestamp for calculating hour alignment
 * @return HorizontalAxis.ItemPlacer with 60-minute spacing aligned to whole hours
 */
@OptIn(ExperimentalTime::class)
@Composable
fun rememberBottomAxisItemPlacer(minTimestamp: Long): HorizontalAxis.ItemPlacer {
    return remember(minTimestamp) {
        val instant = Instant.fromEpochMilliseconds(minTimestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val minutesIntoHour = localDateTime.minute
        val offsetToNextHour = if (minutesIntoHour == 0) 0 else 60 - minutesIntoHour

        HorizontalAxis.ItemPlacer.aligned(
            spacing = { 60 },  // 60 minutes between labels
            offset = { offsetToNextHour }
        )
    }
}

/**
 * Default zoom level for graphs - shows 6 hours of data (360 minutes).
 */
const val DEFAULT_GRAPH_ZOOM_MINUTES = 360.0

/**
 * When predictions are shown, auto-scroll places **now + this offset** at the right edge of the viewport
 * so the scenario tail (~4 h) stays readable on the dashboard / overview graph.
 * Keep in sync with [app.aaps.core.data.configuration.Constants.PREDICTION_VIEWPORT_FUTURE_BIAS_MINUTES].
 */
const val PREDICTION_VIEWPORT_FUTURE_BIAS_MINUTES = 180.0

/**
 * Maximum zoom-in level — never show fewer than this many minutes.
 * Prevents Vico's internal label/constraint math from overflowing at extreme zoom
 * (Compose `Constraints` can't represent the resulting data-label widths → crash).
 */
const val MIN_GRAPH_ZOOM_MINUTES = 30.0

/**
 * Grace period after the last user pan/zoom during which auto-scroll on new BG is suppressed,
 * so the viewport doesn't snap back while the user is examining the graph.
 */
const val INTERACTION_GRACE_MS = 60_000L

/**
 * Fraction of the graph height occupied by the basal overlay.
 *
 * The basal layer lives on its own (end) axis; the rest of the height is left for the primary data.
 * Two coupled formulas derive from this single value and MUST stay in sync — keep them expressed in
 * terms of this constant rather than hard-coded numbers:
 * - Basal axis max  = `maxBasal / BASAL_HEIGHT_FRACTION` → maxBasal plots at this fraction of the height.
 * - Primary axis max = `dataMax / (1 - BASAL_HEIGHT_FRACTION)` → primary (IOB) data fills the remaining
 *   height, reserving the basal fraction at the edge. Used by SecondaryGraphCompose (basal at top);
 *   BgGraphCompose overlays basal at the bottom and has no primary reservation.
 *
 * e.g. 0.5 → basal occupies half the height, the primary data the other half.
 */
const val BASAL_HEIGHT_FRACTION = 0.5

/**
 * Filters data points to only include those within the valid x-axis range.
 *
 * Use this when you have data that might extend beyond the visible time range
 * and you want to exclude out-of-range points from rendering.
 *
 * @param dataPoints List of (x, y) coordinate pairs
 * @param minX Minimum X value for the graph range
 * @param maxX Maximum X value for the graph range
 * @return Filtered and sorted list of (x, y) pairs within [minX, maxX]
 */
fun filterToRange(
    dataPoints: List<Pair<Double, Double>>,
    minX: Double,
    maxX: Double
): List<Pair<Double, Double>> {
    return dataPoints
        .filter { (x, _) -> x in minX..maxX }
        .sortedBy { (x, _) -> x }  // CRITICAL: Sort by x-value for Vico
}

/**
 * Target point size for layout normalization across all synchronized graphs.
 * Must be >= the largest actual point size used in any graph (currently 22dp from IOB SMB/bolus markers).
 *
 * Every graph includes an invisible normalizer line with this point size (via [createNormalizerLine]).
 * This ensures all charts have the same maxPointSize, which makes Vico compute identical:
 * - `xSpacing` (maxPointSize + pointSpacing) → same content width → pixel scroll sync works
 * - `unscalableStartPadding` (maxPointSize / 2) → same content offset → no start alignment shift
 *
 * Without this, each chart's different point sizes cause different layout, breaking pixel-based sync.
 */
val NORMALIZER_POINT_SIZE: Dp = 22.dp

/**
 * Creates an invisible line with [NORMALIZER_POINT_SIZE] transparent points.
 * Include this in every chart's lines list to normalize layout across synchronized graphs.
 */
fun createNormalizerLine(): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        areaFill = null,
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                component = ShapeComponent(fill = Fill(Color.Transparent), shape = CircleShape),
                size = NORMALIZER_POINT_SIZE
            )
        )
    )

/**
 * Y data for the normalizer dummy series. Always add this to every chart's lineModel block.
 * Two points at y=0, invisible, just to occupy a series slot for the normalizer line.
 */
val NORMALIZER_Y = listOf(0.0, 0.0)

/**
 * X data for the normalizer dummy series spanning the full chart range.
 * Must reach [maxX] so Vico computes the same scrollable content width across all charts.
 * Without this, charts without prediction data (IOB, COB) have shorter scroll extent
 * than the BG chart, causing them to stop following when scrolling into the forecast area.
 */
fun normalizerX(maxX: Double): List<Double> = listOf(0.0, maxX)

/**
 * Vico data-label guardrail.
 *
 * Very large X ranges can make Vico compute oversized text constraints while drawing point labels
 * (bolus/carbs/ext-bolus), leading to:
 * "Can't represent a width ... in Constraints".
 *
 * We keep point markers visible, but disable text labels when the chart span gets too large.
 */
private const val MAX_SAFE_DATA_LABEL_X_SPAN_MINUTES = 10_000.0

fun shouldRenderPointDataLabels(maxX: Double): Boolean =
    maxX.isFinite() && maxX in 0.0..MAX_SAFE_DATA_LABEL_X_SPAN_MINUTES

/**
 * Triangle shape pointing upward (apex at top center, flat base at bottom).
 *
 * Used for rendering SMB markers on graphs. The triangle sits on the X axis
 * with the point facing up, making it visually distinct from circle dots.
 */
val TriangleShape: Shape = GenericShape { size, _ ->
    val baseHalf = size.width * 0.3f         // Narrow base for sharper triangle
    val cx = size.width / 2f
    moveTo(cx, 0f)                           // Top center (apex)
    lineTo(cx + baseHalf, size.height / 2f)  // Right base
    lineTo(cx - baseHalf, size.height / 2f)  // Left base
    close()
}

/**
 * Inverted triangle shape pointing downward (flat base at top, apex at bottom).
 *
 * Used for rendering bolus markers on graphs. The base sits at the data point's
 * y-coordinate with the apex pointing down.
 */
val InvertedTriangleShape: Shape = GenericShape { size, _ ->
    val baseHalf = size.width * 0.3f         // Narrow base for sharper triangle
    val cx = size.width / 2f
    moveTo(cx - baseHalf, 0f)               // Top left (base)
    lineTo(cx + baseHalf, 0f)               // Top right (base)
    lineTo(cx, size.height / 2f)            // Bottom center (apex)
    close()
}

/**
 * Maps a chart x-value (whole minutes from minTimestamp) to canvas pixels.
 *
 * Same transform as [NowLine] and the TBR lane: `layerBounds.left + startPadding +
 * xSpacing * ((x - minX) / xStep) - scroll`. Kept in one place so a prediction fade
 * stays glued to the data when the user scrolls or zooms.
 */
fun CartesianDrawingContext.dataXToCanvasX(dataX: Double): Float {
    val xStep = ranges.xStep
    if (xStep == 0.0) return layerBounds.left
    return layerBounds.left +
        layerDimensions.startPadding +
        layerDimensions.xSpacing * ((dataX - ranges.minX) / xStep).toFloat() -
        scroll
}

/**
 * Alpha for a point at [x] on a line that fades from [startDataX] to [endDataX].
 * Outside that window the alpha is clamped to the nearer end. Pure — easy to unit-test.
 */
fun predictionFadeAlpha(
    x: Double,
    startDataX: Double,
    endDataX: Double,
    startAlpha: Float,
    endAlpha: Float,
): Float {
    if (endDataX <= startDataX) return startAlpha
    val t = ((x - startDataX) / (endDataX - startDataX)).toFloat().coerceIn(0f, 1f)
    return startAlpha + (endAlpha - startAlpha) * t
}

/**
 * Line fill that colours the stroke with a horizontal fade in **data space**.
 *
 * Vico's built-in brush fills are sized to the visible layer, so a plain
 * `Brush.horizontalGradient` would fade toward the screen edge and slide when scrolling.
 * This fill maps [startDataX] / [endDataX] to canvas x at draw time instead.
 */
class FadeLineFill(
    private val color: Color,
    private val startAlpha: Float,
    private val endAlpha: Float,
    private val startDataX: Double,
    private val endDataX: Double,
) : LineCartesianLayer.LineFill {
    private val paint = Paint()

    override fun draw(
        context: CartesianDrawingContext,
        halfLineThickness: Float,
        verticalAxisPosition: Axis.Position.Vertical?,
    ) {
        with(context) {
            val top = layerBounds.top - halfLineThickness
            val bottom = layerBounds.bottom + halfLineThickness
            val startX = dataXToCanvasX(startDataX)
            val endX = dataXToCanvasX(endDataX).coerceAtLeast(startX + 1f)
            val brush = Brush.horizontalGradient(
                colors = listOf(
                    color.copy(alpha = startAlpha),
                    color.copy(alpha = endAlpha),
                ),
                startX = startX,
                endX = endX,
            )
            brush.applyTo(size = Size(layerBounds.width, bottom - top), p = paint, alpha = 1f)
            canvas.drawRect(layerBounds.left, top, layerBounds.right, bottom, paint)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FadeLineFill &&
            color == other.color &&
            startAlpha == other.startAlpha &&
            endAlpha == other.endAlpha &&
            startDataX == other.startDataX &&
            endDataX == other.endDataX

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + startAlpha.hashCode()
        result = 31 * result + endAlpha.hashCode()
        result = 31 * result + startDataX.hashCode()
        result = 31 * result + endDataX.hashCode()
        return result
    }
}

/**
 * Prediction dots whose alpha fades along the same data-space window as [FadeLineFill],
 * so a series dissolves into the future instead of ending with a hard last dot.
 */
class FadePointProvider(
    private val color: Color,
    private val size: Dp,
    private val startAlpha: Float,
    private val endAlpha: Float,
    private val startDataX: Double,
    private val endDataX: Double,
) : LineCartesianLayer.PointProvider {

    override fun getPoint(
        entry: LineCartesianLayerModel.Entry,
        extraStore: ExtraStore,
    ): LineCartesianLayer.Point {
        val alpha = predictionFadeAlpha(entry.x, startDataX, endDataX, startAlpha, endAlpha)
        return pointWithAlpha(alpha)
    }

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point =
        pointWithAlpha(startAlpha)

    private fun pointWithAlpha(alpha: Float): LineCartesianLayer.Point =
        LineCartesianLayer.Point(
            component = ShapeComponent(
                fill = Fill(color.copy(alpha = alpha)),
                shape = CircleShape,
            ),
            size = size,
        )
}

/**
 * Creates a line for BG prediction series.
 * Continuous connector and small filled circle points in the given color, both fading
 * out toward the end of the prediction window (the far future).
 * Each prediction type (IOB, COB, UAM, ZT, aCOB) uses a different color.
 *
 * @param fadeWindow chart x range `(start, end)` over which the fade runs — start is near
 *   "now" (full alpha), end is the far future (lowest alpha). `null` keeps full alpha.
 */
fun createPredictionLine(
    color: Color,
    chartStyle: ChartStyle,
    fadeWindow: Pair<Double, Double>?,
): LineCartesianLayer.Line {
    val fadeStartX = fadeWindow?.first ?: 0.0
    val fadeEndX = fadeWindow?.second ?: 1.0
    val lineStartAlpha = if (fadeWindow == null) 1f else chartStyle.predictionLineStartAlpha
    val lineEndAlpha = if (fadeWindow == null) 1f else chartStyle.predictionLineEndAlpha
    val pointStartAlpha = if (fadeWindow == null) 1f else chartStyle.predictionPointStartAlpha
    val pointEndAlpha = if (fadeWindow == null) 1f else chartStyle.predictionPointEndAlpha
    return LineCartesianLayer.Line(
        fill = FadeLineFill(
            color = color,
            startAlpha = lineStartAlpha,
            endAlpha = lineEndAlpha,
            startDataX = fadeStartX,
            endDataX = fadeEndX,
        ),
        stroke = LineCartesianLayer.LineStroke.Continuous(
            thickness = chartStyle.predictionLineStrokeWidth,
            cap = StrokeCap.Round,
        ),
        areaFill = null,
        pointProvider = FadePointProvider(
            color = color,
            size = chartStyle.predictionPointSize,
            startAlpha = pointStartAlpha,
            endAlpha = pointEndAlpha,
            startDataX = fadeStartX,
            endDataX = fadeEndX,
        ),
    )
}

/**
 * Softer BG prediction series: faint connector + smaller pastel dots (dashboard calm mode).
 */
fun createSoftPredictionLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color.copy(alpha = 0.14f))),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.85.dp, cap = StrokeCap.Round),
        areaFill = null,
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                component = ShapeComponent(
                    fill = Fill(color.copy(alpha = 0.72f)),
                    shape = CircleShape
                ),
                size = 3.5.dp
            )
        )
    )

/**
 * AIMI scenario **clinical floor** (maps to legacy IOB prediction series on the graph).
 *
 * Vico draws the connector from [LineCartesianLayer.Line.fill]; keep it **opaque** — low-alpha fill
 * made the dashed prediction nearly invisible on the dark dashboard (regression vs Canvas renderer).
 */
fun createScenarioFloorLine(
    color: Color,
    pointHaloColor: Color = Color.White,
): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Dashed(
            thickness = 2.5.dp,
            cap = StrokeCap.Round,
            dashLength = 6.dp,
            gapLength = 4.dp,
        ),
        areaFill = null,
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                component = ShapeComponent(
                    fill = Fill(color),
                    shape = CircleShape,
                    strokeFill = Fill(pointHaloColor.copy(alpha = 0.72f)),
                    strokeThickness = 1.dp,
                ),
                size = 5.dp,
            )
        ),
    )

/**
 * AIMI scenario **best path** (maps to legacy UAM prediction series on the graph).
 * Stronger weight than [createScenarioFloorLine] so the authoritative curve reads first.
 */
fun createScenarioBestLine(
    color: Color,
    pointHaloColor: Color = Color.White,
): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Dashed(
            thickness = 2.75.dp,
            cap = StrokeCap.Round,
            dashLength = 7.dp,
            gapLength = 4.dp,
        ),
        areaFill = null,
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                component = ShapeComponent(
                    fill = Fill(color),
                    shape = CircleShape,
                    strokeFill = Fill(pointHaloColor.copy(alpha = 0.78f)),
                    strokeThickness = 1.dp,
                ),
                size = 5.5.dp,
            )
        ),
    )

/** Blend prediction / accent colors toward surface for a less alarming palette. */
fun softenChartColor(accent: Color, surface: Color, amount: Float = 0.22f): Color =
    lerp(accent, surface, amount)

/**
 * Presentation-only "very high" BG threshold (Dexcom-style, mg/dL).
 * Not a user preference. Above this the BG curve uses the same red as the low band.
 */
const val VERY_HIGH_BG_MGDL: Double = 250.0

/** Colour bands of the BG curve. Thresholds are in chart Y units (display units). */
enum class BgBand { LOW, TARGET, HIGH, VERY_HIGH }

/** One continuous run of the BG polyline that stays inside a single [BgBand]. */
data class BgBandRun(
    val band: BgBand,
    val points: List<Pair<Double, Double>>,
)

/** Which BG-layer lines to emit into the Vico model, in model order. */
data class BgLinePlan(
    val hasAreaFill: Boolean = false,
    val bandRuns: List<BgBand> = emptyList(),
    val hasRegularDots: Boolean = false,
    val hasBucketedDots: Boolean = false,
    val hasSmb: Boolean = false,
    val predictionIds: List<String> = emptyList(),
)

/** Band of a single Y value. At an exact threshold the value counts as the upper band. */
fun bgBandOf(y: Double, lowMark: Double, highMark: Double, veryHigh: Double): BgBand = when {
    y < lowMark  -> BgBand.LOW
    y < highMark -> BgBand.TARGET
    y < veryHigh -> BgBand.HIGH
    else         -> BgBand.VERY_HIGH
}

/**
 * Colour for a [BgBand]. Low and very high share the red colour ("tief = rot", "sehr hoch = rot"),
 * same mapping as the BG curve bands.
 */
fun bgBandColor(
    band: BgBand,
    low: Color,
    target: Color,
    high: Color,
): Color = when (band) {
    BgBand.LOW,
    BgBand.VERY_HIGH -> low
    BgBand.TARGET    -> target
    BgBand.HIGH      -> high
}

/**
 * Split a sorted (x, y) polyline into colour runs by [BgBand].
 *
 * Inserts a synthetic point at each threshold crossing so the colour changes exactly at the
 * limit. Adjacent runs share that crossing point, so the line stays continuous. Each run's
 * band is taken from the midpoint of its first and last Y, which is always strictly inside
 * one band.
 *
 * Pure function — no chart or theme dependency.
 */
fun splitBgLineByBand(
    points: List<Pair<Double, Double>>,
    lowMark: Double,
    highMark: Double,
    veryHigh: Double,
): List<BgBandRun> {
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) {
        return listOf(BgBandRun(bgBandOf(points[0].second, lowMark, highMark, veryHigh), points))
    }
    val thresholds = listOf(lowMark, highMark, veryHigh)
    val runs = mutableListOf<List<Pair<Double, Double>>>()
    var current = mutableListOf(points[0])

    fun closeRun() {
        if (current.size >= 2) runs.add(current)
        current = mutableListOf()
    }

    for (i in 1 until points.size) {
        val p1 = points[i - 1]
        val p2 = points[i]
        val y1 = p1.second
        val y2 = p2.second
        val crossings = if (y1 == y2) {
            emptyList()
        } else {
            val minY = minOf(y1, y2)
            val maxY = maxOf(y1, y2)
            thresholds.mapNotNull { t ->
                // Strictly between the endpoints: a point already on a threshold is not a crossing.
                if (t <= minY || t >= maxY) {
                    null
                } else {
                    val frac = (t - y1) / (y2 - y1)
                    (p1.first + frac * (p2.first - p1.first)) to t
                }
            }.sortedBy { it.first }
        }
        if (crossings.isEmpty()) {
            current.add(p2)
        } else {
            for ((cx, cy) in crossings) {
                current.add(cx to cy)
                closeRun()
                current = mutableListOf(cx to cy)
            }
            current.add(p2)
        }
    }
    closeRun()

    return runs.map { pts ->
        val bandY = (pts.first().second + pts.last().second) / 2.0
        BgBandRun(bgBandOf(bandY, lowMark, highMark, veryHigh), pts)
    }
}

/**
 * Continuous BG curve segment for one colour band.
 * No points — the dots live on their own series. Linear connector so the drawn path matches
 * the synthetic threshold crossings from [splitBgLineByBand].
 */
fun createBgBandLine(color: Color, strokeWidth: Dp): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = strokeWidth, cap = StrokeCap.Round),
        areaFill = null,
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                component = ShapeComponent(fill = Fill(Color.Transparent), shape = CircleShape),
                size = 0.dp,
            )
        ),
        interpolator = Linear,
    )

/**
 * Soft area fill under the full BG curve. Same fading-downward pattern as the IOB/COB graphs.
 * Stroke is invisible — the coloured line lives on the band series.
 */
fun createBgAreaFillLine(
    wash: Color,
    topAlpha: Float,
    bottomAlpha: Float,
): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
        areaFill = LineCartesianLayer.AreaFill.single(
            Fill(
                Brush.verticalGradient(
                    listOf(
                        wash.copy(alpha = topAlpha),
                        wash.copy(alpha = bottomAlpha),
                    )
                )
            )
        ),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                component = ShapeComponent(fill = Fill(Color.Transparent), shape = CircleShape),
                size = 0.dp,
            )
        ),
        interpolator = Linear,
    )

/**
 * Soft tinted horizontal band between [yLow] and [yHigh] (chart Y units).
 *
 * Maps Y through the live chart ranges (same as Vico's `HorizontalBox`), so it works with both
 * a fixed Y axis and Overview's auto Y axis. Drawn **under** the layers so the BG curve and
 * dots stay crisp on top of the wash.
 */
class TargetRangeBandDecoration(
    private val yLow: Double,
    private val yHigh: Double,
    private val fillColor: Color,
    private val cornerRadius: Dp,
) : Decoration {

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        val lo = minOf(yLow, yHigh)
        val hi = maxOf(yLow, yHigh)
        if (hi <= lo) return
        with(context) {
            val yRange = ranges.getYRange(Axis.Position.Vertical.Start)
            if (yRange.length <= 0.0) return
            fun glucoseYToCanvas(y: Double): Float =
                layerBounds.bottom - ((y - yRange.minY) / yRange.length).toFloat() * layerBounds.height
            val topY = glucoseYToCanvas(hi)
            val bottomY = glucoseYToCanvas(lo)
            val rectTop = minOf(topY, bottomY).coerceIn(layerBounds.top, layerBounds.bottom)
            val rectBottom = maxOf(topY, bottomY).coerceIn(layerBounds.top, layerBounds.bottom)
            val h = rectBottom - rectTop
            if (h <= 0f) return
            val radiusPx = cornerRadius.pixels
            with(mutableDrawScope) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(layerBounds.left, rectTop),
                    size = Size((layerBounds.right - layerBounds.left).coerceAtLeast(1f), h),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is TargetRangeBandDecoration &&
            yLow == other.yLow &&
            yHigh == other.yHigh &&
            fillColor == other.fillColor &&
            cornerRadius == other.cornerRadius

    override fun hashCode(): Int {
        var result = yLow.hashCode()
        result = 31 * result + yHigh.hashCode()
        result = 31 * result + fillColor.hashCode()
        result = 31 * result + cornerRadius.hashCode()
        return result
    }
}

@Composable
fun rememberTargetRangeBandDecoration(
    yRange: Pair<Double, Double>?,
    fillColor: Color,
    cornerRadius: Dp,
): TargetRangeBandDecoration? {
    return remember(yRange, fillColor, cornerRadius) {
        if (yRange == null || yRange.second <= yRange.first) {
            null
        } else {
            TargetRangeBandDecoration(
                yLow = yRange.first,
                yHigh = yRange.second,
                fillColor = fillColor,
                cornerRadius = cornerRadius,
            )
        }
    }
}

/**
 * Size tier for an SMB triangle. Same rule on the BG chart and the IOB chart.
 */
enum class SmbSizeTier { SMALL, MEDIUM, LARGE }

/** Doses below this are drawn as a small triangle (U). */
const val SMB_SIZE_SMALL_MAX_U: Double = 0.5

/** Doses above this are drawn as a large triangle (U). A dose of exactly 2.5 U stays medium. */
const val SMB_SIZE_LARGE_MIN_U: Double = 2.5

/**
 * Map an insulin dose in units to its [SmbSizeTier]:
 * below [SMB_SIZE_SMALL_MAX_U] → small, above [SMB_SIZE_LARGE_MIN_U] → large, else medium.
 */
fun smbSizeTierOf(amountUnits: Double): SmbSizeTier = when {
    amountUnits < SMB_SIZE_SMALL_MAX_U -> SmbSizeTier.SMALL
    amountUnits > SMB_SIZE_LARGE_MIN_U -> SmbSizeTier.LARGE
    else                               -> SmbSizeTier.MEDIUM
}

/**
 * One SMB marker in chart space, with the source timestamp so a tap can map back to the bolus.
 * [amountUnits] picks the triangle size tier.
 */
data class SmbMarkerPoint(
    val timestampEpochMs: Long,
    val x: Double,
    val y: Double,
    val amountUnits: Double,
)

/**
 * Canvas position of a drawn SMB triangle, plus its timestamp. Written from
 * [SmbMarkersDecoration] on every draw pass and read on tap for hit testing.
 */
data class SmbCanvasHit(
    val canvasX: Float,
    val canvasY: Float,
    val timestampEpochMs: Long,
)

/**
 * Plain holder for the latest drawn SMB canvas positions. Same rules as [VisibleRangeHolder]:
 * not Compose state, written from the draw phase, polled on tap.
 */
class SmbHitPositionHolder {
    @Volatile
    var points: List<SmbCanvasHit> = emptyList()
}

/**
 * Nearest drawn SMB triangle within [radiusPx] of the tap, or null.
 * Uses squared distance — no sqrt. Pure — easy to unit-test.
 */
fun findSmbCanvasHit(
    hits: List<SmbCanvasHit>,
    tapX: Float,
    tapY: Float,
    radiusPx: Float,
): SmbCanvasHit? {
    if (hits.isEmpty() || radiusPx <= 0f) return null
    val maxDistSq = radiusPx * radiusPx
    var best: SmbCanvasHit? = null
    var bestDistSq = Float.POSITIVE_INFINITY
    for (hit in hits) {
        val dx = hit.canvasX - tapX
        val dy = hit.canvasY - tapY
        val distSq = dx * dx + dy * dy
        if (distSq <= maxDistSq && distSq < bestDistSq) {
            bestDistSq = distSq
            best = hit
        }
    }
    return best
}

/**
 * SMB markers drawn in the over-layers so they sit above basal, the BG curve and the grid.
 *
 * The matching line series stays in the chart model as a hit target only (invisible points).
 * The triangle matches [TriangleShape]: apex at the top of the size box, base at the data point's y.
 * Each drawn triangle's canvas position is recorded into [hitHolder] so taps can hit the shape
 * the user sees, not only Vico's nearest model-X key.
 */
class SmbMarkersDecoration(
    private val points: List<SmbMarkerPoint>,
    private val color: Color,
    private val outlineColor: Color,
    private val smallSize: Dp,
    private val mediumSize: Dp,
    private val largeSize: Dp,
    private val strokeWidth: Dp,
    private val hitHolder: SmbHitPositionHolder? = null,
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        if (points.isEmpty()) {
            hitHolder?.points = emptyList()
            return
        }
        val yRange = context.ranges.getYRange(Axis.Position.Vertical.Start)
        if (yRange.length <= 0.0) {
            hitHolder?.points = emptyList()
            return
        }
        val smallPx = with(context) { smallSize.pixels }
        val mediumPx = with(context) { mediumSize.pixels }
        val largePx = with(context) { largeSize.pixels }
        val strokePx = with(context) { strokeWidth.pixels }
        val maxHalf = maxOf(smallPx, mediumPx, largePx) / 2f
        val hits = if (hitHolder != null) ArrayList<SmbCanvasHit>(points.size) else null
        with(context.mutableDrawScope) {
            for (point in points) {
                val sizePx = when (smbSizeTierOf(point.amountUnits)) {
                    SmbSizeTier.SMALL  -> smallPx
                    SmbSizeTier.MEDIUM -> mediumPx
                    SmbSizeTier.LARGE  -> largePx
                }
                val half = sizePx / 2f
                val baseHalf = sizePx * 0.3f
                val canvasX = context.dataXToCanvasX(point.x)
                if (canvasX < context.layerBounds.left - maxHalf || canvasX > context.layerBounds.right + maxHalf) continue
                val canvasY =
                    context.layerBounds.bottom -
                        ((point.y - yRange.minY) / yRange.length).toFloat() * context.layerBounds.height
                // Hit centre is the middle of the triangle box (apex at canvasY - half, base at canvasY).
                hits?.add(
                    SmbCanvasHit(
                        canvasX = canvasX,
                        canvasY = canvasY - half / 2f,
                        timestampEpochMs = point.timestampEpochMs,
                    )
                )
                val path = Path().apply {
                    moveTo(canvasX, canvasY - half)
                    lineTo(canvasX + baseHalf, canvasY)
                    lineTo(canvasX - baseHalf, canvasY)
                    close()
                }
                drawPath(path, color = color)
                if (strokePx > 0f && outlineColor.alpha > 0f) {
                    drawPath(path, color = outlineColor, style = Stroke(width = strokePx))
                }
            }
        }
        hitHolder?.points = hits.orEmpty()
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is SmbMarkersDecoration &&
            points == other.points &&
            color == other.color &&
            outlineColor == other.outlineColor &&
            smallSize == other.smallSize &&
            mediumSize == other.mediumSize &&
            largeSize == other.largeSize &&
            strokeWidth == other.strokeWidth &&
            hitHolder === other.hitHolder

    override fun hashCode(): Int {
        var result = points.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + outlineColor.hashCode()
        result = 31 * result + smallSize.hashCode()
        result = 31 * result + mediumSize.hashCode()
        result = 31 * result + largeSize.hashCode()
        result = 31 * result + strokeWidth.hashCode()
        result = 31 * result + (hitHolder?.hashCode() ?: 0)
        return result
    }
}

/**
 * Remember a [SmbMarkersDecoration] for the given chart-space points.
 * @param points SMB markers; empty draws nothing
 * @param hitHolder receives drawn canvas positions for tap hit testing (optional)
 */
@Composable
fun rememberSmbMarkers(
    points: List<SmbMarkerPoint>,
    color: Color,
    outlineColor: Color,
    smallSize: Dp,
    mediumSize: Dp,
    largeSize: Dp,
    strokeWidth: Dp,
    hitHolder: SmbHitPositionHolder? = null,
): SmbMarkersDecoration? {
    return remember(points, color, outlineColor, smallSize, mediumSize, largeSize, strokeWidth, hitHolder) {
        if (points.isEmpty()) {
            // Decoration is dropped, so clear the hit list here — otherwise a tap could hit a
            // triangle that is no longer on screen.
            hitHolder?.points = emptyList()
            null
        } else {
            SmbMarkersDecoration(points, color, outlineColor, smallSize, mediumSize, largeSize, strokeWidth, hitHolder)
        }
    }
}

/**
 * Soft highlight for the current (latest) BG reading: a translucent halo under the point
 * and a thin ring around it, so "now" stands out from the historical dots.
 *
 * The halo sits under the layers (so the curve still reads on top); the ring sits over them.
 * Y is mapped through the live start-axis range, so it tracks Overview auto-Y and the
 * dashboard fixed range alike.
 */
class CurrentBgHighlightDecoration(
    private val x: Double,
    private val y: Double,
    private val glowColor: Color,
    private val glowRadius: Dp,
    private val glowAlpha: Float,
    private val ringColor: Color,
    private val ringRadius: Dp,
    private val ringWidth: Dp,
) : Decoration {

    private fun CartesianDrawingContext.center(): Offset? {
        val yRange = ranges.getYRange(Axis.Position.Vertical.Start)
        if (yRange.length <= 0.0) return null
        val canvasX = dataXToCanvasX(x)
        if (canvasX < layerBounds.left - 8f || canvasX > layerBounds.right + 8f) return null
        val canvasY =
            layerBounds.bottom - ((y - yRange.minY) / yRange.length).toFloat() * layerBounds.height
        return Offset(canvasX, canvasY)
    }

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        if (glowAlpha <= 0f || glowRadius <= 0.dp) return
        val center = with(context) { center() } ?: return
        val radiusPx = with(context) { glowRadius.pixels }
        with(context.mutableDrawScope) {
            drawCircle(
                color = glowColor.copy(alpha = glowAlpha),
                radius = radiusPx,
                center = center,
            )
        }
    }

    override fun drawOverLayers(context: CartesianDrawingContext) {
        if (ringRadius <= 0.dp || ringWidth <= 0.dp) return
        val center = with(context) { center() } ?: return
        val radiusPx = with(context) { ringRadius.pixels }
        val widthPx = with(context) { ringWidth.pixels }
        with(context.mutableDrawScope) {
            drawCircle(
                color = ringColor,
                radius = radiusPx,
                center = center,
                style = Stroke(width = widthPx),
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CurrentBgHighlightDecoration &&
            x == other.x &&
            y == other.y &&
            glowColor == other.glowColor &&
            glowRadius == other.glowRadius &&
            glowAlpha == other.glowAlpha &&
            ringColor == other.ringColor &&
            ringRadius == other.ringRadius &&
            ringWidth == other.ringWidth

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + glowColor.hashCode()
        result = 31 * result + glowRadius.hashCode()
        result = 31 * result + glowAlpha.hashCode()
        result = 31 * result + ringColor.hashCode()
        result = 31 * result + ringRadius.hashCode()
        result = 31 * result + ringWidth.hashCode()
        return result
    }
}

/**
 * Remember a [CurrentBgHighlightDecoration] for the latest BG reading.
 * @param point chart-space `(x, y)` of the current reading, or null to draw nothing
 */
@Composable
fun rememberCurrentBgHighlight(
    point: Pair<Double, Double>?,
    glowColor: Color,
    glowRadius: Dp,
    glowAlpha: Float,
    ringColor: Color,
    ringRadius: Dp,
    ringWidth: Dp,
): CurrentBgHighlightDecoration? {
    return remember(point, glowColor, glowRadius, glowAlpha, ringColor, ringRadius, ringWidth) {
        point?.let { (x, y) ->
            CurrentBgHighlightDecoration(
                x = x,
                y = y,
                glowColor = glowColor,
                glowRadius = glowRadius,
                glowAlpha = glowAlpha,
                ringColor = ringColor,
                ringRadius = ringRadius,
                ringWidth = ringWidth,
            )
        }
    }
}

/**
 * "Now" vertical dotted line decoration for Vico charts.
 * Draws a dotted vertical line at the current time position across the full chart height,
 * with a soft glow behind it. Shared across all graphs (BG, IOB, COB, Treatment Belt) for
 * consistent "now" indication.
 *
 * The glow is two wide translucent strokes under the dashed core. That is the portable way to
 * get a soft halo in Compose canvas — no blur filter needed.
 *
 * @param nowX The x-value for "now" (minutes from minTimestamp, via [timestampToX])
 * @param color The line color
 * @param strokeWidth Stroke width of the dashed core
 * @param dashLength Dash segment length
 * @param gapLength Gap between dashes
 * @param glowWidth Stroke width of the soft glow (0 = no glow)
 * @param glowAlpha Glow alpha, as a multiplier on [color]'s own alpha
 */
class NowLine(
    private val nowX: Double,
    private val color: Color,
    private val strokeWidth: Dp = 2.dp,
    private val dashLength: Dp = 6.dp,
    private val gapLength: Dp = 4.dp,
    private val glowWidth: Dp = 0.dp,
    private val glowAlpha: Float = 0f,
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val xStep = ranges.xStep
            if (xStep == 0.0) return

            // Convert x-value to canvas coordinate (mirrors Vico's internal getDrawX logic)
            val canvasX = dataXToCanvasX(nowX)

            // Skip if outside visible area
            if (canvasX < layerBounds.left || canvasX > layerBounds.right) return

            val strokeWidthPx = strokeWidth.pixels
            val dashLengthPx = dashLength.pixels
            val gapLengthPx = gapLength.pixels
            val glowWidthPx = glowWidth.pixels

            with(mutableDrawScope) {
                if (glowWidthPx > 0f && glowAlpha > 0f) {
                    val baseAlpha = color.alpha * glowAlpha
                    // Outer then inner: a cheap two-step falloff that reads as a soft halo.
                    drawLine(
                        color = color.copy(alpha = baseAlpha),
                        start = Offset(canvasX, layerBounds.top),
                        end = Offset(canvasX, layerBounds.bottom),
                        strokeWidth = glowWidthPx,
                    )
                    drawLine(
                        color = color.copy(alpha = (baseAlpha * 1.8f).coerceAtMost(1f)),
                        start = Offset(canvasX, layerBounds.top),
                        end = Offset(canvasX, layerBounds.bottom),
                        strokeWidth = glowWidthPx * 0.45f,
                    )
                }
                drawLine(
                    color = this@NowLine.color,
                    start = Offset(canvasX, layerBounds.top),
                    end = Offset(canvasX, layerBounds.bottom),
                    strokeWidth = strokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(dashLengthPx, gapLengthPx), 0f
                    )
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is NowLine &&
            nowX == other.nowX &&
            color == other.color &&
            strokeWidth == other.strokeWidth &&
            glowWidth == other.glowWidth &&
            glowAlpha == other.glowAlpha

    override fun hashCode(): Int {
        var result = nowX.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + strokeWidth.hashCode()
        result = 31 * result + glowWidth.hashCode()
        result = 31 * result + glowAlpha.hashCode()
        return result
    }
}

/**
 * Remember a [NowLine] decoration for the current time.
 * Stroke and glow metrics come from [chartStyle] so Light and Dark can diverge.
 * @param nowTimestamp current time in millis — pass a ticker value so the line updates periodically
 */
@Composable
fun rememberNowLine(
    minTimestamp: Long,
    nowTimestamp: Long,
    color: Color,
    chartStyle: ChartStyle = AapsTheme.chartStyle,
): NowLine {
    return remember(minTimestamp, nowTimestamp, color, chartStyle) {
        NowLine(
            nowX = timestampToX(nowTimestamp, minTimestamp),
            color = color,
            strokeWidth = chartStyle.nowLineStrokeWidth,
            glowWidth = chartStyle.nowLineGlowWidth,
            glowAlpha = chartStyle.nowLineGlowAlpha,
        )
    }
}

/**
 * TBR lane + bars (or vertical marker lines) in dashboard coordinates, using the same X mapping as [NowLine].
 *
 * When [bgAxisYMin] and [bgAxisYMax] are non-null, the lane is anchored to **glycémie = 0** in data space
 * (same vertical scale as the BG layer). Otherwise falls back to [legacyBottomReservePx] above the layer bottom.
 */
class DashboardTbrLaneDecoration(
    private val minTimestamp: Long,
    private val segments: List<ChartTbrSegment>,
    private val markerEpochMs: List<Long>,
    private val bgAxisYMin: Double?,
    private val bgAxisYMax: Double?,
    private val legacyBottomReservePx: Float,
    private val laneBackground: Color,
    private val barFillSoft: Color,
    private val barFillStrong: Color,
    private val markerLineColor: Color,
    /** Lower contrast bars, thinner markers — therapy strip stays readable without alarm-like weight. */
    private val softStyle: Boolean = false,
    /**
     * When true (activity + SMB + TBR strip under the BG chart), the TBR lane uses a larger share of
     * height so temp basal bars stay readable on a short chart.
     */
    private val therapyStrip: Boolean = false,
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        if (segments.isEmpty() && markerEpochMs.isEmpty()) return
        with(context) {
            val xStep = ranges.xStep
            if (xStep == 0.0) return@with

            val plotLeft = layerBounds.left
            val plotRight = layerBounds.right
            val plotTop = layerBounds.top
            val plotBottom = layerBounds.bottom

            val minY = bgAxisYMin
            val maxY = bgAxisYMax
            val laneBottom = if (minY != null && maxY != null && maxY > minY) {
                val span = maxY - minY
                fun glucoseYToCanvas(y: Double): Float {
                    val t = ((y - minY) / span).toFloat().coerceIn(0f, 1f)
                    return plotBottom - t * layerBounds.height
                }
                kotlin.math.min(glucoseYToCanvas(0.0) - 1.5f, plotBottom - 4f)
            } else {
                val reserve = legacyBottomReservePx.coerceAtLeast(4f)
                plotBottom - reserve - 1.5f
            }

            val contentTop = plotTop
            val contentHeight = (laneBottom - contentTop).coerceAtLeast(1f)
            // Taller lane + bars than the first Vico port — temp basals are easier to read without stealing BG space.
            val laneH = if (therapyStrip) {
                (contentHeight * 0.34f).coerceIn(52f, 96f)
            } else {
                (contentHeight * 0.14f).coerceIn(24f, 56f)
            }
            val laneTop = laneBottom - laneH

            fun dataXToCanvasX(dataX: Double): Float =
                plotLeft +
                    layerDimensions.startPadding +
                    layerDimensions.xSpacing * ((dataX - ranges.minX) / xStep).toFloat() -
                    scroll

            val laneBgAlpha = if (softStyle) 0.06f else 0.12f
            val barHaloAlpha = if (softStyle) 0.12f else 0.28f
            val barCoreAlpha = if (softStyle) 0.38f else 0.72f
            val markOuterW = if (softStyle) 3f else 5f
            val markInnerW = if (softStyle) 1.2f else 2.8f
            val markOuterA = if (softStyle) 0.22f else 0.45f
            val markInnerA = if (softStyle) 0.42f else 1f
            val cornerLane = if (softStyle) CornerRadius(6f, 6f) else CornerRadius(4f, 4f)
            val cornerBar = if (softStyle) CornerRadius(5f, 5f) else CornerRadius(3f, 3f)

            with(mutableDrawScope) {
                drawRoundRect(
                    color = laneBackground.copy(alpha = laneBgAlpha),
                    topLeft = Offset(plotLeft, laneTop),
                    size = Size((plotRight - plotLeft).coerceAtLeast(1f), laneH),
                    cornerRadius = cornerLane,
                )

                if (segments.isNotEmpty()) {
                    for (seg in segments) {
                        var x1 = dataXToCanvasX(timestampToX(seg.startEpochMs, minTimestamp))
                        var x2 = dataXToCanvasX(timestampToX(seg.endEpochMs, minTimestamp))
                        if (x2 < x1) {
                            val tmp = x1
                            x1 = x2
                            x2 = tmp
                        }
                        x1 = x1.coerceIn(plotLeft, plotRight)
                        x2 = x2.coerceIn(plotLeft, plotRight)
                        val barW = (x2 - x1).coerceAtLeast(3f)
                        val barHMax = (laneH - (if (therapyStrip) 5f else 3f)).coerceAtLeast(if (therapyStrip) 18f else 12f)
                        val barH = (laneH * seg.intensity01).coerceIn(if (therapyStrip) 14f else 10f, barHMax)
                        val barTop = laneBottom - barH
                        drawRoundRect(
                            color = barFillSoft.copy(alpha = barHaloAlpha),
                            topLeft = Offset(x1 - 0.5f, barTop - 0.5f),
                            size = Size(barW + 1f, barH + 1f),
                            cornerRadius = cornerBar,
                        )
                        drawRoundRect(
                            color = barFillStrong.copy(alpha = barCoreAlpha),
                            topLeft = Offset(x1, barTop),
                            size = Size(barW, barH),
                            cornerRadius = cornerBar,
                        )
                    }
                } else {
                    for (t in markerEpochMs) {
                        val x = dataXToCanvasX(timestampToX(t, minTimestamp))
                        if (x < plotLeft || x > plotRight) continue
                        val yTop = contentTop + contentHeight * (if (therapyStrip) 0.52f else 0.68f)
                        drawLine(
                            color = markerLineColor.copy(alpha = markOuterA),
                            start = Offset(x, yTop),
                            end = Offset(x, laneTop),
                            strokeWidth = markOuterW,
                        )
                        drawLine(
                            color = markerLineColor.copy(alpha = markInnerA),
                            start = Offset(x, yTop),
                            end = Offset(x, laneTop),
                            strokeWidth = markInnerW,
                        )
                    }
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is DashboardTbrLaneDecoration &&
            minTimestamp == other.minTimestamp &&
            segments == other.segments &&
            markerEpochMs == other.markerEpochMs &&
            bgAxisYMin == other.bgAxisYMin &&
            bgAxisYMax == other.bgAxisYMax &&
            legacyBottomReservePx == other.legacyBottomReservePx &&
            laneBackground == other.laneBackground &&
            barFillSoft == other.barFillSoft &&
            barFillStrong == other.barFillStrong &&
            markerLineColor == other.markerLineColor &&
            softStyle == other.softStyle &&
            therapyStrip == other.therapyStrip

    override fun hashCode(): Int {
        var result = minTimestamp.hashCode()
        result = 31 * result + segments.hashCode()
        result = 31 * result + markerEpochMs.hashCode()
        result = 31 * result + (bgAxisYMin?.hashCode() ?: 0)
        result = 31 * result + (bgAxisYMax?.hashCode() ?: 0)
        result = 31 * result + legacyBottomReservePx.hashCode()
        result = 31 * result + laneBackground.hashCode()
        result = 31 * result + barFillSoft.hashCode()
        result = 31 * result + barFillStrong.hashCode()
        result = 31 * result + markerLineColor.hashCode()
        result = 31 * result + softStyle.hashCode()
        result = 31 * result + therapyStrip.hashCode()
        return result
    }
}

@Composable
fun rememberDashboardTbrLaneDecoration(
    minTimestamp: Long,
    segments: List<ChartTbrSegment>,
    markerEpochMs: List<Long>,
    bgAxisYMin: Double?,
    bgAxisYMax: Double?,
    legacyBottomReservePx: Float,
    laneBackground: Color,
    barFillSoft: Color,
    barFillStrong: Color,
    markerLineColor: Color,
    softStyle: Boolean = false,
    therapyStrip: Boolean = false,
): DashboardTbrLaneDecoration? {
    return remember(
        minTimestamp,
        segments,
        markerEpochMs,
        bgAxisYMin,
        bgAxisYMax,
        legacyBottomReservePx,
        laneBackground,
        barFillSoft,
        barFillStrong,
        markerLineColor,
        softStyle,
        therapyStrip,
    ) {
        if (segments.isEmpty() && markerEpochMs.isEmpty()) {
            null
        } else {
            DashboardTbrLaneDecoration(
                minTimestamp = minTimestamp,
                segments = segments,
                markerEpochMs = markerEpochMs,
                bgAxisYMin = bgAxisYMin,
                bgAxisYMax = bgAxisYMax,
                legacyBottomReservePx = legacyBottomReservePx,
                laneBackground = laneBackground,
                barFillSoft = barFillSoft,
                barFillStrong = barFillStrong,
                markerLineColor = markerLineColor,
                softStyle = softStyle,
                therapyStrip = therapyStrip,
            )
        }
    }
}

/**
 * Plain (non-Compose-state) holder for the latest visible x-range, written from
 * [VisibleRangeReporter.drawOverLayers] on every draw pass.
 *
 * Deliberately NOT a Compose `State`: writing Compose state synchronously from the draw phase
 * fights with Vico's own gesture-driven scroll/zoom mutations — the resulting invalidate-during-draw
 * starves pinch-zoom gesture recognition (observed: zoom became unresponsive). Callers must poll
 * [value] from a coroutine (e.g. a `LaunchedEffect` with a periodic `delay`) and only then write it
 * into real Compose state.
 */
class VisibleRangeHolder {
    @Volatile
    var value: Pair<Double, Double>? = null
}

/**
 * Reports the currently visible x-range (same "minutes from minTimestamp" unit as [timestampToX])
 * into [holder] on every draw pass. Purely observational — draws nothing, and never touches
 * Compose state directly (see [VisibleRangeHolder]).
 *
 * Inverse of [NowLine]'s x-value-to-canvas transform: canvasX = layerBounds.left + startPadding +
 * xSpacing * ((x - minX) / xStep) - scroll. Solving for x at the left/right edges of layerBounds
 * gives the visible x-range.
 */
class VisibleRangeReporter(
    private val holder: VisibleRangeHolder
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val xStep = ranges.xStep
            val xSpacing = layerDimensions.xSpacing
            if (xStep == 0.0 || xSpacing <= 0f) return

            val visibleMinX = ranges.minX + xStep * (scroll - layerDimensions.startPadding) / xSpacing
            val visibleWidth = xStep * layerBounds.width / xSpacing
            holder.value = visibleMinX to (visibleMinX + visibleWidth)
        }
    }

    // No equals/hashCode overrides on purpose. Vico compares decorations to decide what to redraw,
    // and this one must compare by identity: two reporters writing to different holders are not
    // interchangeable. A plain (non-data) class already gets exactly that from Any - reference
    // equality and an identity hash - on every platform. The previous explicit pair said the same
    // thing through System.identityHashCode, which does not exist outside the JVM.
}

/**
 * Remember a [VisibleRangeReporter] decoration that writes the visible x-range into [holder]
 * on every draw pass.
 */
@Composable
fun rememberVisibleRangeReporter(holder: VisibleRangeHolder): VisibleRangeReporter {
    return remember(holder) { VisibleRangeReporter(holder) }
}

/**
 * Color of a meal hypothesis state — the single mapping shared by the BOOST panel status strip
 * and the MHB graph step line, so the two can never diverge. CONFIRMED (deep orange) and
 * COMMITTED (red) are deliberately far apart: in the BOOST panel they once shared two similar
 * orange tones and were easy to confuse.
 */
fun mealHypothesisStateColor(state: MealHypothesisCoreState): Color = when (state) {
    MealHypothesisCoreState.IDLE       -> Color(0xFF4CAF50) // green
    MealHypothesisCoreState.OBSERVING  -> Color(0xFFFFC107) // amber
    MealHypothesisCoreState.CONFIRMED  -> Color(0xFFFF6E40) // deep orange
    MealHypothesisCoreState.COMMITTED  -> Color(0xFFF44336) // red
    MealHypothesisCoreState.RECOVERING -> Color(0xFF26C6DA) // cyan
}

// =========================================================================
// Nice-numbers axis scaling ("Nice Numbers for Graph Labels", Paul Heckbert)
// =========================================================================
//
// Vico's default Y-axis item placer divides a fixed [minY, maxY] range into evenly-spaced
// ticks with no rounding, so an arbitrary data-driven range (e.g. 12.39..57.39) produces ugly
// tick labels (12.39, 27.39, 42.39...). These helpers snap axis bounds and tick spacing to
// round numbers (1, 2, 5, 10, 20, 50... times a power of ten) instead.

/** A "nice" axis range: bounds and tick spacing all rounded to 1/2/5/10 x 10^n. */
data class NiceScale(val min: Double, val max: Double, val step: Double)

/**
 * Rounds [range] to a value of the form 1/2/2.5/5/10 x 10^n. [round] chooses the nearest such
 * value (used for tick spacing) vs. always rounding up (used for axis bounds, so the bound never
 * clips inside the data). [range] must be > 0.
 *
 * The round-up variant includes a 2.5 tier (a known variant of Heckbert's original {1,2,5,10}
 * set) to avoid an overly coarse jump between the 2 and 5 tiers — e.g. without it, a BG max of
 * 210 would round all the way up to 300 (skipping straight from a 50-step scale to a 100-step
 * one); with it, 210 rounds to 250 first, only reaching 300 once the value exceeds 250.
 */
private fun niceNum(range: Double, round: Boolean): Double {
    val exponent = floor(log10(range))
    val fraction = range / 10.0.pow(exponent)
    val niceFraction = if (round) {
        when {
            fraction < 1.5 -> 1.0
            fraction < 3.0 -> 2.0
            fraction < 7.0 -> 5.0
            else           -> 10.0
        }
    } else {
        when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 2.5 -> 2.5
            fraction <= 5.0 -> 5.0
            else            -> 10.0
        }
    }
    return niceFraction * 10.0.pow(exponent)
}

/**
 * Standard nice-ify: snaps [min, max] and the tick spacing to round numbers. Free-range, no
 * zero or pivot anchoring — used for series like VAR_SENSITIVITY and HEART_RATE.
 */
fun niceScale(min: Double, max: Double, maxTickCount: Int = 5): NiceScale {
    val safeMax = if (max > min) max else min + 1.0
    val range = niceNum(safeMax - min, round = false)
    val step = niceNum(range / (maxTickCount - 1), round = true)
    return NiceScale(floor(min / step) * step, ceil(safeMax / step) * step, step)
}

/** Rounds [value] up to the nearest nice number (1/2/5/10 x 10^n). Used for a lone axis bound. */
fun niceUp(value: Double): Double {
    if (value <= 0.0) return 0.0
    return niceNum(value, round = false)
}

/**
 * Rounds [value] (expected negative) further negative to the nearest nice magnitude — e.g.
 * -0.3 -> -0.5, -0.07 -> -0.1 (but -0.05 is already nice and is left unchanged). Used to give a
 * small negative excursion its own clean bound, decoupled from a much larger positive-side scale
 * (see [zeroFloorNiceRange]).
 */
fun niceNegativeSliver(value: Double): Double {
    if (value >= 0.0) return 0.0
    return -niceNum(-value, round = false)
}

/**
 * Minimum half-width for SENSITIVITY's pivot scale (see [niceScaleAroundPivot]'s `minDeviation`
 * param) — below this, snap to a fixed 95%/100%/105% scale instead of nice-ifying a near-zero
 * deviation (e.g. a SENS ratio sitting flat around 100%).
 */
const val SENS_MIN_DEVIATION = 5.0

/**
 * Nice-ify a symmetric deviation around [pivot] (e.g. SENSITIVITY around 100%, DEV_SLOPE
 * around 0), so [pivot] always lands exactly in the middle of the axis. Nice-ifying [min, max]
 * directly (via [niceScale]) would not preserve that centering.
 */
fun niceScaleAroundPivot(min: Double, max: Double, pivot: Double, maxTickCount: Int = 5, minDeviation: Double = 0.0): NiceScale {
    val rawDeviation = maxOf(abs(pivot - min), abs(max - pivot))
    // Below the floor (e.g. SENS sitting flat around 100%): snap to a fixed 3-tick scale
    // (pivot-minDeviation, pivot, pivot+minDeviation) instead of nice-ifying a near-zero range,
    // which would otherwise produce an overly tight, non-round scale (e.g. 99/99.5/100/100.5/101).
    if (minDeviation > 0.0 && rawDeviation <= minDeviation) return NiceScale(pivot - minDeviation, pivot + minDeviation, minDeviation)
    val deviation = if (rawDeviation > 0.0) rawDeviation else 1.0
    val niceDeviation = niceNum(deviation, round = false)
    val step = niceNum(2 * niceDeviation / (maxTickCount - 1), round = true)
    val steppedDeviation = ceil(niceDeviation / step) * step
    return NiceScale(pivot - steppedDeviation, pivot + steppedDeviation, step)
}

/**
 * Series that are 0-floored by default (see [zeroFloorNiceRange]): mostly-positive, allowing a
 * disparity-aware negative excursion without ever centering zero. IOB follows the same rule but
 * isn't included here — its basal-overlay combo graph has its own dedicated range computation
 * (SecondaryGraphCompose's `primaryYMaxResult`) that calls [zeroFloorNiceRange] directly.
 */
val ZERO_FLOOR_SERIES_TYPES = setOf(SeriesType.BGI, SeriesType.DEVIATIONS, SeriesType.ACTIVITY, SeriesType.STEPS, SeriesType.ABS_IOB)

/**
 * Zero-floor axis range, disparity-aware: when the negative excursion is tiny relative to the
 * positive side (ratio >= [disparityRatio]), one shared nice tick spacing across the whole
 * range would make the small negative part look arbitrary — so the two sides are nice-ified
 * independently instead (a small negative sliver just large enough to show the excursion, plus
 * a normal positive-side scale). When the two sides are comparable in magnitude, a single
 * unified nice scale spans the whole range, negative and positive sharing the same tick step.
 * Used for IOB and [ZERO_FLOOR_SERIES_TYPES].
 */
fun zeroFloorNiceRange(dataMin: Double, dataMax: Double, maxTickCount: Int = 5, disparityRatio: Double = 10.0): NiceScale {
    if (dataMin >= 0.0) return niceScale(0.0, dataMax, maxTickCount)
    val absMin = -dataMin
    val ratio = if (absMin > 0.0) dataMax / absMin else Double.MAX_VALUE
    return if (ratio >= disparityRatio) {
        val niceMax = niceUp(dataMax)
        val niceMin = niceNegativeSliver(dataMin)
        val step = niceNum(niceMax / (maxTickCount - 1), round = true)
        NiceScale(niceMin, niceMax, step)
    } else {
        niceScale(dataMin, dataMax, maxTickCount)
    }
}
