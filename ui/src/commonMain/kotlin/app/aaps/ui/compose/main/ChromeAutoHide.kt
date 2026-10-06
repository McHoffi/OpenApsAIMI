package app.aaps.ui.compose.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.aaps.core.ui.compose.AapsSpacing

/**
 * How far one clear finger drag must go before the bars move. Uses the shared spacing scale.
 */
private val ChromeHideThreshold: Dp = AapsSpacing.xxLarge
private val ChromeShowThreshold: Dp = AapsSpacing.xxLarge

/**
 * Observable home-chrome visibility plus the pure [ChromeScrollController] behind it.
 *
 * The controller keeps plain `var`s on purpose (easy to unit-test). This holder mirrors
 * them into Compose state so [isVisible], [showToken] and [isPinned] drive recomposition
 * and `LaunchedEffect` keys.
 */
@Stable
class ChromeAutoHideState internal constructor(
    initiallyVisible: Boolean,
    hideThresholdPx: Float,
    showThresholdPx: Float,
) {
    var isVisible by mutableStateOf(initiallyVisible)
        private set

    var showToken by mutableIntStateOf(0)
        private set

    var isPinned by mutableStateOf(false)
        private set

    private fun publish(isVisible: Boolean, showToken: Int, isPinned: Boolean) {
        this.isVisible = isVisible
        this.showToken = showToken
        this.isPinned = isPinned
    }

    val controller: ChromeScrollController = ChromeScrollController(
        initiallyVisible = initiallyVisible,
        hideThresholdPx = hideThresholdPx,
        showThresholdPx = showThresholdPx,
        onChanged = ::publish,
    )

    /** Named [pin], not `setPinned`, so it does not clash with the `isPinned` setter on the JVM. */
    fun pin(pinned: Boolean) = controller.setPinned(pinned)

    fun onIdleTimeout() = controller.onIdleTimeout()

    fun onRevealTap() = controller.onRevealTap()
}

/**
 * Remembers the home-chrome auto-hide state. Call once from the chrome host
 * (`MainScreen`), not from every screen.
 */
@Composable
fun rememberChromeAutoHide(initiallyVisible: Boolean): ChromeAutoHideState {
    val density = LocalDensity.current
    return remember {
        ChromeAutoHideState(
            initiallyVisible = initiallyVisible,
            hideThresholdPx = with(density) { ChromeHideThreshold.toPx() },
            showThresholdPx = with(density) { ChromeShowThreshold.toPx() },
        )
    }
}

/**
 * Observes child `verticalScroll` without consuming any of it. Must stay on an ancestor of
 * the scrollables, never on a full-screen sibling box (a sibling ate the gesture stream and
 * blocked scrolling underneath).
 */
fun Modifier.chromeAutoHideScroll(
    controller: ChromeScrollController,
): Modifier = nestedScroll(
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // Never steal the scroll. Horizontal pan / pinch has y == 0 and is ignored.
            //
            // Hide on what the content actually moved toward the end (plus leftover at the
            // bottom, which is the same direction). Show only on a pull toward the start that
            // the content did not take — that leftover means the page is already at the top.
            // A normal reverse scroll in the middle of the page consumes its own delta and
            // must not drop the bars.
            if (consumed.y < 0f) controller.onScrollTowardEnd(consumed.y)
            if (available.y > 0f) controller.onOverscrollTowardStart(available.y)
            else if (available.y < 0f) controller.onScrollTowardEnd(available.y)
            return Offset.Zero
        }
    }
)
