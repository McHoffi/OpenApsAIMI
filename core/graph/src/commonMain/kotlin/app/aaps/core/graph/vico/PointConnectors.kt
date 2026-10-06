package app.aaps.core.graph.vico

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer

/**
 * Custom Vico Interpolator that creates a stepped (staircase) line appearance.
 *
 * Draws horizontal-then-vertical segments between data points instead of diagonal
 * lines, producing right-angle corners ("square" / stepped look). Useful for profile
 * data that changes at discrete time blocks (basal rates, IC, ISF, target BG).
 *
 * Between point (x1, y1) and point (x2, y2):
 *   1. horizontal line from (x1, y1) to (x2, y1)
 *   2. vertical line from (x2, y1) to (x2, y2)
 *
 * Replaces the older `PointConnector`-based implementation (Vico 2.x API),
 * which is now deprecated in favor of `Interpolator`.
 */
val Square: LineCartesianLayer.Interpolator = object : LineCartesianLayer.Interpolator {
    override fun interpolate(
        context: CartesianDrawingContext,
        path: Path,
        points: List<Offset>,
        visibleIndexRange: IntRange
    ) {
        for (index in visibleIndexRange) {
            val point = points[index]
            if (index == visibleIndexRange.first) {
                path.moveTo(point.x, point.y)
            } else {
                val prev = points[index - 1]
                path.lineTo(point.x, prev.y)
                path.lineTo(point.x, point.y)
            }
        }
    }
}

/**
 * Straight line between data points (no smoothing).
 *
 * Used for the BG curve so colour-band splits at the threshold crossings sit exactly on the
 * drawn path. A smoothed interpolator would bow away from those synthetic points and the
 * colour would change slightly off the threshold.
 */
val Linear: LineCartesianLayer.Interpolator = object : LineCartesianLayer.Interpolator {
    override fun interpolate(
        context: CartesianDrawingContext,
        path: Path,
        points: List<Offset>,
        visibleIndexRange: IntRange
    ) {
        for (index in visibleIndexRange) {
            val point = points[index]
            if (index == visibleIndexRange.first) {
                path.moveTo(point.x, point.y)
            } else {
                path.lineTo(point.x, point.y)
            }
        }
    }
}
