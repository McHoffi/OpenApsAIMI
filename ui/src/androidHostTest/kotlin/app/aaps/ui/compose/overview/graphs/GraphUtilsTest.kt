package app.aaps.ui.compose.overview.graphs

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Regression coverage for the "nice numbers" axis-scaling math (Heckbert's algorithm and its
 * pivot/zero-floor variants), extracted during the Step 4 graph-scale refactor. Expected values
 * verified by direct calculation against the algorithm, not guessed — several were hand-verified
 * during that work and are pinned here so a future change can't silently re-break them.
 */
internal class GraphUtilsTest {

    @Nested
    inner class NiceScaleTest {

        @ParameterizedTest(name = "max just above {0} rounds to a clean ({1}, {2})")
        @CsvSource(
            "195.0, 200.0, 50.0",
            "210.0, 250.0, 50.0", // the 2.5-tier fix: without it this jumps straight to 300/100
            "250.0, 250.0, 50.0",
            "260.0, 300.0, 100.0",
            "310.0, 400.0, 100.0",
        )
        fun `BG max rounds to the expected clean ceiling and step`(input: Double, expectedMax: Double, expectedStep: Double) {
            val scale = niceScale(0.0, input)
            assertThat(scale.min).isEqualTo(0.0)
            assertThat(scale.max).isEqualTo(expectedMax)
            assertThat(scale.step).isEqualTo(expectedStep)
        }

        @Test
        fun `never clips the real data range`() {
            val scale = niceScale(12.39, 57.39)
            assertThat(scale.min).isEqualTo(10.0)
            assertThat(scale.max).isEqualTo(60.0)
            assertThat(scale.step).isEqualTo(10.0)
        }

        @Test
        fun `degenerate range (min equals max) still produces a usable non-empty scale`() {
            val scale = niceScale(5.0, 5.0)
            assertThat(scale.max).isGreaterThan(scale.min)
            assertThat(scale.step).isGreaterThan(0.0)
        }
    }

    @Nested
    inner class NiceUpTest {

        @Test
        fun `non-positive values return zero`() {
            assertThat(niceUp(0.0)).isEqualTo(0.0)
            assertThat(niceUp(-5.0)).isEqualTo(0.0)
        }

        @Test
        fun `rounds up, never down, and never below the input`() {
            assertThat(niceUp(82.0)).isEqualTo(100.0)
            assertThat(niceUp(82.0)).isAtLeast(82.0)
        }
    }

    @Nested
    inner class NiceNegativeSliverTest {

        @Test
        fun `non-negative values return zero`() {
            assertThat(niceNegativeSliver(0.0)).isEqualTo(0.0)
            assertThat(niceNegativeSliver(5.0)).isEqualTo(0.0)
        }

        @Test
        fun `rounds further negative, never toward zero`() {
            assertThat(niceNegativeSliver(-0.3)).isEqualTo(-0.5)
            assertThat(niceNegativeSliver(-0.07)).isEqualTo(-0.1)
        }

        @Test
        fun `a value already nice is left unchanged`() {
            // 0.05 is itself on the 1/2/2.5/5/10 ladder, so it must not get bumped to 0.1
            assertThat(niceNegativeSliver(-0.05)).isEqualTo(-0.05)
        }
    }

    @Nested
    inner class NiceScaleAroundPivotTest {

        @Test
        fun `pivot always sits exactly at the midpoint regardless of input asymmetry`() {
            val scale = niceScaleAroundPivot(min = 60.0, max = 108.0, pivot = 100.0)
            assertThat(scale.min).isEqualTo(40.0)
            assertThat(scale.max).isEqualTo(160.0)
            assertThat((scale.min + scale.max) / 2.0).isEqualTo(100.0)
        }

        @Test
        fun `never clips the real data range`() {
            val scale = niceScaleAroundPivot(min = 60.0, max = 108.0, pivot = 100.0)
            assertThat(scale.min).isAtMost(60.0)
            assertThat(scale.max).isAtLeast(108.0)
        }

        @Test
        fun `DEV_SLOPE-style pivot at zero centers correctly`() {
            val scale = niceScaleAroundPivot(min = -3.0, max = 1.0, pivot = 0.0)
            assertThat(scale.min).isEqualTo(-6.0)
            assertThat(scale.max).isEqualTo(6.0)
        }

        @Test
        fun `SENS sitting flat on its pivot snaps to a fixed 95-100-105 scale, not a tight near-zero one`() {
            // Regression for the reported bug: a constant SENS at 100% used to produce something
            // like 99/99.5/100/100.5/101 instead of a clean 95%/100%/105%.
            val scale = niceScaleAroundPivot(min = 100.0, max = 100.0, pivot = 100.0, minDeviation = 5.0)
            assertThat(scale.min).isEqualTo(95.0)
            assertThat(scale.max).isEqualTo(105.0)
            assertThat(scale.step).isEqualTo(5.0)
        }

        @Test
        fun `deviation above the minDeviation floor ignores the floor and nice-ifies normally`() {
            val scale = niceScaleAroundPivot(min = 40.0, max = 160.0, pivot = 100.0, minDeviation = 5.0)
            assertThat(scale.min).isEqualTo(0.0)
            assertThat(scale.max).isEqualTo(200.0)
        }

        @Test
        fun `requesting 3 ticks always produces exactly 3 (pivot guaranteed to be one of them)`() {
            // The SENS_PIVOT_TICK_COUNT fix: side-steps Vico's step-thinning entirely by never
            // requesting more ticks than always fit.
            val scale = niceScaleAroundPivot(min = 40.0, max = 160.0, pivot = 100.0, maxTickCount = 3)
            val tickCount = Math.round((scale.max - scale.min) / scale.step) + 1
            assertThat(tickCount).isEqualTo(3L)
            assertThat(scale.min + (scale.max - scale.min) / 2.0).isEqualTo(100.0)
        }
    }

    @Nested
    inner class ZeroFloorNiceRangeTest {

        @Test
        fun `all-positive data floors at exactly zero`() {
            val scale = zeroFloorNiceRange(dataMin = 5.0, dataMax = 82.0)
            assertThat(scale.min).isEqualTo(0.0)
            assertThat(scale.max).isEqualTo(100.0)
        }

        @Test
        fun `tiny negative excursion relative to a large positive side gets its own independent sliver`() {
            // ratio (8.0 / 0.05 = 160) is far above the default disparityRatio of 10 — the negative
            // sliver must stay tight (its own nice magnitude), not stretched to match the positive step.
            val scale = zeroFloorNiceRange(dataMin = -0.05, dataMax = 8.0)
            assertThat(scale.min).isEqualTo(-0.05)
            assertThat(scale.max).isEqualTo(10.0)
        }

        @Test
        fun `comparable-magnitude negative and positive share one unified nice scale`() {
            val scale = zeroFloorNiceRange(dataMin = -4.0, dataMax = 6.0)
            assertThat(scale.min).isEqualTo(-4.0)
            assertThat(scale.max).isEqualTo(6.0)
            assertThat(scale.step).isEqualTo(2.0)
        }

        @Test
        fun `right at the disparity ratio boundary takes the independent-sliver branch`() {
            // ratio == disparityRatio exactly (10.0 / 1.0 = 10.0) must take the ">=" sliver branch,
            // not silently fall through to the unified one.
            val scale = zeroFloorNiceRange(dataMin = -1.0, dataMax = 10.0)
            assertThat(scale.min).isEqualTo(-1.0)
            assertThat(scale.max).isEqualTo(10.0)
            assertThat(scale.step).isEqualTo(2.0)
        }

        @Test
        fun `never clips real data even when the negative side is larger than the positive side`() {
            val scale = zeroFloorNiceRange(dataMin = -12.0, dataMax = 3.0)
            assertThat(scale.min).isAtMost(-12.0)
            assertThat(scale.max).isAtLeast(3.0)
        }
    }

    @Nested
    inner class SplitBgLineByBandTest {

        // Thresholds in display units (mg/dL): low 70, high 180, very high 250.
        private val low = 70.0
        private val high = 180.0
        private val veryHigh = 250.0

        private fun split(points: List<Pair<Double, Double>>) =
            splitBgLineByBand(points, low, high, veryHigh)

        @Test
        fun `all points in one band produce a single run`() {
            val runs = split(listOf(0.0 to 100.0, 5.0 to 120.0, 10.0 to 150.0))
            assertThat(runs).hasSize(1)
            assertThat(runs[0].band).isEqualTo(BgBand.TARGET)
            assertThat(runs[0].points).hasSize(3)
        }

        @Test
        fun `a single upward crossing inserts a shared point at the threshold`() {
            val runs = split(listOf(0.0 to 100.0, 10.0 to 200.0))
            assertThat(runs).hasSize(2)
            assertThat(runs[0].band).isEqualTo(BgBand.TARGET)
            assertThat(runs[1].band).isEqualTo(BgBand.HIGH)
            // Both runs end/start at the same crossing so the line stays continuous.
            val crossing = runs[0].points.last()
            assertThat(crossing).isEqualTo(runs[1].points.first())
            assertThat(crossing.second).isEqualTo(high)
            // Linear interpolation: (200-100)/(200-100) of the way from x=0 to x=10 at y=180 → x=8.
            assertThat(crossing.first).isWithin(1e-9).of(8.0)
        }

        @Test
        fun `a single downward crossing inserts a shared point at the threshold`() {
            val runs = split(listOf(0.0 to 200.0, 10.0 to 100.0))
            assertThat(runs).hasSize(2)
            assertThat(runs[0].band).isEqualTo(BgBand.HIGH)
            assertThat(runs[1].band).isEqualTo(BgBand.TARGET)
            assertThat(runs[0].points.last().second).isEqualTo(high)
            assertThat(runs[1].points.first().second).isEqualTo(high)
        }

        @Test
        fun `a segment that jumps across two thresholds inserts both crossings in order`() {
            val runs = split(listOf(0.0 to 100.0, 10.0 to 300.0))
            assertThat(runs).hasSize(3)
            assertThat(runs.map { it.band })
                .containsExactly(BgBand.TARGET, BgBand.HIGH, BgBand.VERY_HIGH)
                .inOrder()
            // Crossings must be strictly increasing in x.
            val xs = runs.flatMap { run -> listOf(run.points.first().first, run.points.last().first) }
            assertThat(xs).isInOrder()
        }

        @Test
        fun `a point sitting exactly on a threshold is not treated as a crossing`() {
            // Starts on lowMark (70) and goes up: no synthetic point at x=0.
            val runs = split(listOf(0.0 to 70.0, 10.0 to 100.0))
            assertThat(runs).hasSize(1)
            assertThat(runs[0].points).hasSize(2)
            assertThat(runs[0].band).isEqualTo(BgBand.TARGET)
        }

        @Test
        fun `low and very high bands are both reachable`() {
            val runs = split(listOf(0.0 to 50.0, 10.0 to 300.0))
            assertThat(runs.first().band).isEqualTo(BgBand.LOW)
            assertThat(runs.last().band).isEqualTo(BgBand.VERY_HIGH)
        }

        @Test
        fun `empty input produces no runs`() {
            assertThat(split(emptyList())).isEmpty()
        }

        @Test
        fun `adjacent runs share the crossing x so the polyline has no gap`() {
            val runs = split(listOf(0.0 to 50.0, 10.0 to 100.0, 20.0 to 200.0, 30.0 to 300.0))
            for (i in 0 until runs.size - 1) {
                assertThat(runs[i].points.last()).isEqualTo(runs[i + 1].points.first())
            }
        }
    }

    @Nested
    inner class PredictionFadeAlphaTest {

        @Test
        fun `full alpha at the start of the window`() {
            assertThat(predictionFadeAlpha(x = 30.0, startDataX = 30.0, endDataX = 150.0, startAlpha = 0.9f, endAlpha = 0f))
                .isEqualTo(0.9f)
        }

        @Test
        fun `lowest alpha at the end of the window`() {
            assertThat(predictionFadeAlpha(x = 150.0, startDataX = 30.0, endDataX = 150.0, startAlpha = 0.9f, endAlpha = 0.1f))
                .isWithin(1e-5f).of(0.1f)
        }

        @Test
        fun `midpoint is halfway between the two alphas`() {
            assertThat(predictionFadeAlpha(x = 90.0, startDataX = 30.0, endDataX = 150.0, startAlpha = 1f, endAlpha = 0f))
                .isEqualTo(0.5f)
        }

        @Test
        fun `outside the window the alpha is clamped to the nearer end`() {
            assertThat(predictionFadeAlpha(x = 0.0, startDataX = 30.0, endDataX = 150.0, startAlpha = 0.9f, endAlpha = 0.1f))
                .isEqualTo(0.9f)
            assertThat(predictionFadeAlpha(x = 200.0, startDataX = 30.0, endDataX = 150.0, startAlpha = 0.9f, endAlpha = 0.1f))
                .isWithin(1e-5f).of(0.1f)
        }

        @Test
        fun `empty or inverted window keeps the start alpha`() {
            assertThat(predictionFadeAlpha(x = 10.0, startDataX = 50.0, endDataX = 50.0, startAlpha = 0.8f, endAlpha = 0f))
                .isEqualTo(0.8f)
            assertThat(predictionFadeAlpha(x = 10.0, startDataX = 80.0, endDataX = 20.0, startAlpha = 0.8f, endAlpha = 0f))
                .isEqualTo(0.8f)
        }
    }

    @Nested
    inner class FindSmbCanvasHitTest {

        private val hitA = SmbCanvasHit(canvasX = 100f, canvasY = 200f, timestampEpochMs = 1_000L)
        private val hitB = SmbCanvasHit(canvasX = 300f, canvasY = 200f, timestampEpochMs = 2_000L)

        @Test
        fun `tap on the triangle returns that hit`() {
            val result = findSmbCanvasHit(listOf(hitA, hitB), tapX = 100f, tapY = 200f, radiusPx = 28f)
            assertThat(result).isEqualTo(hitA)
        }

        @Test
        fun `tap within the radius but off-centre still returns the hit`() {
            val result = findSmbCanvasHit(listOf(hitA), tapX = 120f, tapY = 210f, radiusPx = 28f)
            assertThat(result).isEqualTo(hitA)
        }

        @Test
        fun `tap outside the radius returns null`() {
            val result = findSmbCanvasHit(listOf(hitA), tapX = 100f, tapY = 250f, radiusPx = 28f)
            assertThat(result).isNull()
        }

        @Test
        fun `nearest hit wins when both are inside the radius`() {
            val close = SmbCanvasHit(canvasX = 110f, canvasY = 200f, timestampEpochMs = 3_000L)
            val result = findSmbCanvasHit(listOf(hitA, close), tapX = 112f, tapY = 200f, radiusPx = 28f)
            assertThat(result).isEqualTo(close)
        }

        @Test
        fun `empty list or non-positive radius returns null`() {
            assertThat(findSmbCanvasHit(emptyList(), tapX = 0f, tapY = 0f, radiusPx = 28f)).isNull()
            assertThat(findSmbCanvasHit(listOf(hitA), tapX = 100f, tapY = 200f, radiusPx = 0f)).isNull()
        }
    }
}
