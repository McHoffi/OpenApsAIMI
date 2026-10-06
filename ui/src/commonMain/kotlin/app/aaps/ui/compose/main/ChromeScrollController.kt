package app.aaps.ui.compose.main

/**
 * Decides when the home chrome (top bar + bottom bars) should show or hide.
 *
 * Rule in plain words:
 * Scroll toward the end of the content hides the bars. Scrolling back does not bring them
 * back on its own — only a pull at the very top of the content does. After they show, they
 * go away again if you stop touching the screen for 3 seconds.
 *
 * Pure logic: no Compose, no clock, no Android. The caller owns the idle timer and feeds it
 * back through [onIdleTimeout].
 *
 * Sign of `deltaYPx` is the Compose nested-scroll Y sign:
 * negative = content moves toward the end of the page (user scrolls down).
 * positive = content moves toward the start of the page (user scrolls up).
 *
 * [onChanged] fires whenever [isVisible], [showToken] or [isPinned] changes. The Compose
 * layer mirrors those three values into `mutableStateOf` so recomposition and
 * `LaunchedEffect` keys work. Plain `var`s on this class are not observable.
 */
class ChromeScrollController(
    initiallyVisible: Boolean,
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
    private val onChanged: (isVisible: Boolean, showToken: Int, isPinned: Boolean) -> Unit = { _, _, _ -> },
) {

    var isVisible: Boolean = initiallyVisible
        private set

    /** When true (search open / drawer open), scroll must not hide the bars. */
    var isPinned: Boolean = false
        private set

    /**
     * Increments every time the bars are shown or a top-of-page pull extends the peek.
     * The caller restarts the 3 s idle timer when this changes.
     */
    var showToken: Int = 0
        private set

    /** Accumulated Y in the current direction. Reset when the direction flips. */
    private var accumulatedY = 0f

    /** -1 = toward the end (hide), +1 = toward the start (show), 0 = none yet. */
    private var direction = 0

    /**
     * Feed vertical scroll that moves (or tries to move) the content toward the end.
     * Only this direction can hide. A reverse scroll in the middle of the page is not
     * passed in here — that must not drop the bars.
     */
    fun onScrollTowardEnd(deltaYPx: Float) {
        if (deltaYPx >= 0f) return
        accumulate(deltaYPx)
        if (-accumulatedY >= hideThresholdPx) {
            resetAccumulation()
            hide()
        }
    }

    /**
     * Feed a pull toward the start that the content did not take. That leftover only happens
     * when the page is already at the top, so this is the one scroll gesture that drops the
     * bars back down.
     */
    fun onOverscrollTowardStart(deltaYPx: Float) {
        if (deltaYPx <= 0f) return
        accumulate(deltaYPx)
        if (accumulatedY >= showThresholdPx) {
            resetAccumulation()
            show()
        }
    }

    /** Stationary tap while hidden. Shows the bars. No-op while visible. */
    fun onRevealTap() {
        if (isVisible || isPinned) return
        show()
    }

    /** Pin / unpin (search or drawer). Unpinning does not hide by itself. */
    fun setPinned(pinned: Boolean) {
        if (isPinned == pinned) return
        isPinned = pinned
        if (pinned && !isVisible) {
            isVisible = true
            showToken++
        }
        notifyChanged()
    }

    /** Idle timer fired. Hides only when visible and not pinned. */
    fun onIdleTimeout() {
        if (!isVisible || isPinned) return
        hide()
    }

    /** Drop accumulated drag. */
    fun resetAccumulation() {
        accumulatedY = 0f
        direction = 0
    }

    private fun accumulate(deltaYPx: Float) {
        val newDirection = if (deltaYPx < 0f) -1 else 1
        if (newDirection != direction) {
            direction = newDirection
            accumulatedY = 0f
        }
        accumulatedY += deltaYPx
    }

    private fun hide() {
        if (!isVisible || isPinned) return
        isVisible = false
        notifyChanged()
    }

    private fun show() {
        if (!isVisible) isVisible = true
        showToken++
        notifyChanged()
    }

    private fun notifyChanged() = onChanged(isVisible, showToken, isPinned)
}
