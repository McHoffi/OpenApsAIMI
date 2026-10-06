package app.aaps.ui.compose.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the show/hide rules for the home chrome. The Compose layer only mirrors this state,
 * so a wrong rule here is a wrong bar on every layout.
 */
class ChromeScrollControllerTest {

    private val threshold = 24f

    private fun controller(
        initiallyVisible: Boolean = true,
        onChanged: (Boolean, Int, Boolean) -> Unit = { _, _, _ -> },
    ) = ChromeScrollController(
        initiallyVisible = initiallyVisible,
        hideThresholdPx = threshold,
        showThresholdPx = threshold,
        onChanged = onChanged,
    )

    @Test
    fun `tiny deltas do nothing`() {
        val c = controller(initiallyVisible = true)
        repeat(10) { c.onScrollTowardEnd(-2f) }
        assertTrue(c.isVisible)

        val hidden = controller(initiallyVisible = false)
        repeat(10) { hidden.onOverscrollTowardStart(2f) }
        assertFalse(hidden.isVisible)
    }

    @Test
    fun `zero delta is ignored`() {
        val c = controller(initiallyVisible = true)
        c.onScrollTowardEnd(0f)
        c.onOverscrollTowardStart(0f)
        assertTrue(c.isVisible)
    }

    @Test
    fun `scroll toward the end past the threshold hides`() {
        val c = controller(initiallyVisible = true)
        c.onScrollTowardEnd(-10f)
        c.onScrollTowardEnd(-14f)
        assertFalse(c.isVisible)
    }

    @Test
    fun `a reverse scroll in the middle of the page does not show`() {
        // Consumed reverse scroll is not overscroll — the user is still travelling back
        // through the content and the bars must stay away.
        val c = controller(initiallyVisible = false)
        repeat(20) { c.onScrollTowardEnd(10f) }
        assertFalse(c.isVisible)
    }

    @Test
    fun `a pull at the top past the threshold shows`() {
        val c = controller(initiallyVisible = false)
        c.onOverscrollTowardStart(10f)
        assertFalse(c.isVisible)
        c.onOverscrollTowardStart(14f)
        assertTrue(c.isVisible)
    }

    @Test
    fun `direction flip resets the accumulation`() {
        val c = controller(initiallyVisible = true)
        c.onScrollTowardEnd(-20f)
        // A top pull starts a new direction: the old end-scroll must not carry over.
        c.onOverscrollTowardStart(20f)
        assertTrue(c.isVisible)
        // After the flip reset this is only 20f of end-scroll — not enough to hide.
        c.onScrollTowardEnd(-20f)
        assertTrue(c.isVisible)
        c.onScrollTowardEnd(-5f)
        assertFalse(c.isVisible)
    }

    @Test
    fun `pin blocks hide from scroll`() {
        val c = controller(initiallyVisible = true)
        c.setPinned(true)
        c.onScrollTowardEnd(-100f)
        assertTrue(c.isVisible)
    }

    @Test
    fun `pin forces the bars visible`() {
        val c = controller(initiallyVisible = false)
        c.setPinned(true)
        assertTrue(c.isVisible)
    }

    @Test
    fun `unpin does not force hide`() {
        val c = controller(initiallyVisible = true)
        c.setPinned(true)
        c.setPinned(false)
        assertTrue(c.isVisible)
    }

    @Test
    fun `idle timeout hides only when visible and unpinned`() {
        val visible = controller(initiallyVisible = true)
        visible.onIdleTimeout()
        assertFalse(visible.isVisible)

        val pinned = controller(initiallyVisible = true)
        pinned.setPinned(true)
        pinned.onIdleTimeout()
        assertTrue(pinned.isVisible)

        val alreadyHidden = controller(initiallyVisible = false)
        alreadyHidden.onIdleTimeout()
        assertFalse(alreadyHidden.isVisible)
    }

    @Test
    fun `reveal tap shows only when hidden and unpinned`() {
        val hidden = controller(initiallyVisible = false)
        hidden.onRevealTap()
        assertTrue(hidden.isVisible)

        val visible = controller(initiallyVisible = true)
        val tokenBefore = visible.showToken
        visible.onRevealTap()
        assertEquals(tokenBefore, visible.showToken)

        val pinned = controller(initiallyVisible = false)
        pinned.setPinned(true)
        // setPinned already showed it; a tap must not bump the token again.
        val tokenAfterPin = pinned.showToken
        pinned.onRevealTap()
        assertEquals(tokenAfterPin, pinned.showToken)
    }

    @Test
    fun `show while visible bumps showToken so the peek extends`() {
        val c = controller(initiallyVisible = true)
        val before = c.showToken
        c.onOverscrollTowardStart(30f)
        assertTrue(c.isVisible)
        assertEquals(before + 1, c.showToken)
    }

    @Test
    fun `hide does not bump showToken`() {
        val c = controller(initiallyVisible = true)
        val before = c.showToken
        c.onScrollTowardEnd(-30f)
        assertFalse(c.isVisible)
        assertEquals(before, c.showToken)
    }

    @Test
    fun `onChanged fires on every real change`() {
        val events = mutableListOf<Triple<Boolean, Int, Boolean>>()
        val c = controller(initiallyVisible = true) { visible, token, pinned ->
            events += Triple(visible, token, pinned)
        }
        c.onScrollTowardEnd(-30f)
        c.onOverscrollTowardStart(30f)
        c.setPinned(true)
        c.onIdleTimeout()

        // hide, show (token 1), pin (already visible so only the pin flag moves), idle (pinned → no-op)
        assertEquals(3, events.size)
        assertEquals(Triple(false, 0, false), events[0])
        assertEquals(Triple(true, 1, false), events[1])
        assertEquals(Triple(true, 1, true), events[2])
    }
}
