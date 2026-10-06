package app.aaps.core.ui.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Chart presentation metrics for the Vico graphs (stroke widths, point sizes, alphas).
 *
 * **Usage:**
 * - Overview BG graph
 * - Dashboard BG graph (same composable)
 * - Any chart that wants the shared "modern graph" look
 *
 * Colours are NOT stored here — band and series colours come from [GeneralColors] and
 * [app.aaps.core.ui.compose.navigation.ElementColors]. This type only holds sizes and alphas,
 * so Light and Dark can diverge without touching the call sites.
 *
 * @property bgLineStrokeWidth Stroke width of the continuous BG curve
 * @property bgLineFillTopAlpha Alpha of the area fill at the curve (fades downward)
 * @property bgLineFillBottomAlpha Alpha of the area fill at the bottom (0f = fades to nothing)
 * @property bgPointSize Diameter of the individual BG reading dots
 * @property bgPointFillAlpha Fill alpha of the BG reading dots
 * @property bgPointStrokeAlpha Outline alpha of the BG reading dots
 * @property targetBandCorner Corner radius of the soft target-range band
 * @property predictionLineStrokeWidth Stroke width of a prediction connector line
 * @property predictionLineStartAlpha Line alpha at the start of the prediction (near "now")
 * @property predictionLineEndAlpha Line alpha at the end of the prediction (far future)
 * @property predictionPointSize Diameter of the prediction dots
 * @property predictionPointStartAlpha Dot alpha at the start of the prediction
 * @property predictionPointEndAlpha Dot alpha at the end of the prediction
 * @property seriesAreaFillTopAlpha Alpha of the IOB/COB area fill at the curve (fades downward)
 * @property seriesAreaFillBottomAlpha Alpha of that area fill at the bottom (0f = fades to nothing)
 * @property gridAlpha Alpha of the axis gridlines on a normal background
 * @property glassGridAlpha Alpha of the axis gridlines over the glass (overview) background
 * @property axisLabelAlpha Alpha of the axis tick labels
 * @property smbMarkerSizeSmall Diameter of a small SMB triangle (dose below 0.5 U)
 * @property smbMarkerSizeMedium Diameter of a medium SMB triangle (dose 0.5 U to 2.5 U)
 * @property smbMarkerSizeLarge Diameter of a large SMB triangle (dose above 2.5 U)
 * @property smbMarkerStrokeWidth Stroke width of the outline around SMB / bolus markers
 * @property smbMarkerTouchRadius Radius of the tap hit box around an SMB marker (bigger = easier to tap)
 * @property nowLineStrokeWidth Stroke width of the core "now" dashed line
 * @property nowLineGlowWidth Stroke width of the soft glow behind the "now" line
 * @property nowLineGlowAlpha Glow alpha, as a multiplier on the line colour's alpha
 * @property currentBgGlowRadius Radius of the soft halo around the current BG point
 * @property currentBgGlowAlpha Alpha of that halo
 * @property currentBgRingRadius Radius of the ring drawn around the current BG point
 * @property currentBgRingWidth Stroke width of that ring
 */
data class ChartStyle(
    val bgLineStrokeWidth: Dp,
    val bgLineFillTopAlpha: Float,
    val bgLineFillBottomAlpha: Float,
    val bgPointSize: Dp,
    val bgPointFillAlpha: Float,
    val bgPointStrokeAlpha: Float,
    val targetBandCorner: Dp,
    val predictionLineStrokeWidth: Dp,
    val predictionLineStartAlpha: Float,
    val predictionLineEndAlpha: Float,
    val predictionPointSize: Dp,
    val predictionPointStartAlpha: Float,
    val predictionPointEndAlpha: Float,
    val seriesAreaFillTopAlpha: Float,
    val seriesAreaFillBottomAlpha: Float,
    val gridAlpha: Float,
    val glassGridAlpha: Float,
    val axisLabelAlpha: Float,
    val smbMarkerSizeSmall: Dp,
    val smbMarkerSizeMedium: Dp,
    val smbMarkerSizeLarge: Dp,
    val smbMarkerStrokeWidth: Dp,
    val smbMarkerTouchRadius: Dp,
    val nowLineStrokeWidth: Dp,
    val nowLineGlowWidth: Dp,
    val nowLineGlowAlpha: Float,
    val currentBgGlowRadius: Dp,
    val currentBgGlowAlpha: Float,
    val currentBgRingRadius: Dp,
    val currentBgRingWidth: Dp,
)

/** Chart metrics for light theme. */
val LightChartStyle = ChartStyle(
    bgLineStrokeWidth = 2.dp,
    bgLineFillTopAlpha = 0.22f,
    bgLineFillBottomAlpha = 0f,
    bgPointSize = 3.5.dp,
    bgPointFillAlpha = 0.55f,
    bgPointStrokeAlpha = 0.25f,
    targetBandCorner = 8.dp,
    predictionLineStrokeWidth = 1.5.dp,
    predictionLineStartAlpha = 0.9f,
    predictionLineEndAlpha = 0f,
    predictionPointSize = 4.dp,
    predictionPointStartAlpha = 1f,
    predictionPointEndAlpha = 0.1f,
    seriesAreaFillTopAlpha = 0.55f,
    seriesAreaFillBottomAlpha = 0f,
    gridAlpha = 0.20f,
    glassGridAlpha = 0.25f,
    axisLabelAlpha = 0.65f,
    smbMarkerSizeSmall = 10.dp,
    smbMarkerSizeMedium = 16.dp,
    smbMarkerSizeLarge = 22.dp,
    smbMarkerStrokeWidth = 1.5.dp,
    smbMarkerTouchRadius = 28.dp,
    nowLineStrokeWidth = 1.2.dp,
    nowLineGlowWidth = 7.dp,
    nowLineGlowAlpha = 0.12f,
    currentBgGlowRadius = 12.dp,
    currentBgGlowAlpha = 0.30f,
    currentBgRingRadius = 6.dp,
    currentBgRingWidth = 1.5.dp,
)

/** Chart metrics for dark theme. */
val DarkChartStyle = ChartStyle(
    bgLineStrokeWidth = 2.dp,
    bgLineFillTopAlpha = 0.22f,
    bgLineFillBottomAlpha = 0f,
    bgPointSize = 3.5.dp,
    bgPointFillAlpha = 0.55f,
    bgPointStrokeAlpha = 0.25f,
    targetBandCorner = 8.dp,
    predictionLineStrokeWidth = 1.5.dp,
    predictionLineStartAlpha = 0.9f,
    predictionLineEndAlpha = 0f,
    predictionPointSize = 4.dp,
    predictionPointStartAlpha = 1f,
    predictionPointEndAlpha = 0.1f,
    seriesAreaFillTopAlpha = 0.55f,
    seriesAreaFillBottomAlpha = 0f,
    gridAlpha = 0.20f,
    glassGridAlpha = 0.25f,
    axisLabelAlpha = 0.65f,
    smbMarkerSizeSmall = 10.dp,
    smbMarkerSizeMedium = 16.dp,
    smbMarkerSizeLarge = 22.dp,
    smbMarkerStrokeWidth = 1.5.dp,
    smbMarkerTouchRadius = 28.dp,
    nowLineStrokeWidth = 1.2.dp,
    nowLineGlowWidth = 7.dp,
    nowLineGlowAlpha = 0.16f,
    currentBgGlowRadius = 12.dp,
    currentBgGlowAlpha = 0.35f,
    currentBgRingRadius = 6.dp,
    currentBgRingWidth = 1.5.dp,
)

internal val LocalChartStyle = compositionLocalOf { LightChartStyle }
