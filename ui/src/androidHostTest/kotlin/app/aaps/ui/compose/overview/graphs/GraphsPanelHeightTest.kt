package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins that a panel uses the height the user gave it.
 *
 * The height row offers 50 dp to 250 dp, and these panels used to ignore everything above their
 * own content: the MODES buttons stayed 32 dp tall and the TIR bar 7 dp, so a taller panel was
 * mostly empty space.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GraphsPanelHeightTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = listOf(
        AutomationEventData(id = "1", title = "Eat"),
        AutomationEventData(id = "2", title = "Sport")
    )

    private val tir = TirUiState(
        veryLow = 10f, low = 10f, inRange = 60f, high = 10f, veryHigh = 10f,
        readingCount = 100, avgMgDl = 120f, a1c = 6.0f
    )

    @Test
    fun `mode buttons fill the panel height`() {
        compose.setContent {
            MaterialTheme {
                Column {
                    ModesPanel(events = events, onRunEvent = { _, _ -> }, modifier = Modifier.fillMaxWidth().height(60.dp))
                    ModesPanel(events = events, onRunEvent = { _, _ -> }, modifier = Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }

        // The panel pads 4 dp top and bottom; the rest of its height belongs to the buttons.
        val buttons = compose.onAllNodesWithText("Eat")
        assertThat(buttons[0].heightDp()).isWithin(1f).of(52f)
        assertThat(buttons[1].heightDp()).isWithin(1f).of(192f)
    }

    @Test
    fun `the TIR bar takes the height the panel has left`() {
        // The default height and the tallest the height row offers. A shorter panel is no good
        // here: the title and the two text lines below the bar need about 56 dp, so below that
        // the bar is squeezed to nothing and a taller panel adds nothing to compare.
        compose.setContent {
            MaterialTheme {
                Column {
                    TirPanel(
                        state = tir,
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("default")
                    )
                    TirPanel(
                        state = tir,
                        modifier = Modifier.fillMaxWidth().height(250.dp).testTag("tall")
                    )
                }
            }
        }

        // Everything under the bar has a fixed size, so inside its own panel the label row sits
        // exactly as much lower as the panel is taller - the bar took the rest. Measured against
        // each panel's own top, because root coordinates also carry the first panel's height.
        val panelTop = { tag: String -> compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value }
        val labels = compose.onAllNodesWithText("60%")
        val labelInPanel = { i: Int, tag: String -> labels[i].getUnclippedBoundsInRoot().top.value - panelTop(tag) }

        assertThat(labelInPanel(1, "tall") - labelInPanel(0, "default")).isWithin(1f).of(150f)
    }

    @Test
    fun `the bar draws at the height it is given`() {
        // The half the panel cannot check on its own: the panel may hand the bar a taller row and
        // the bar still paint a thin strip in it, which is what it used to do at a fixed 7 dp.
        compose.setContent {
            MaterialTheme {
                Column {
                    TirColorBar(
                        segments = listOf(10f to Color.Red, 60f to Color.Green, 30f to Color.Blue),
                        modifier = Modifier.fillMaxWidth().height(24.dp).testTag("bar")
                    )
                    TirColorBar(
                        segments = listOf(10f to Color.Red, 60f to Color.Green, 30f to Color.Blue),
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("tall bar")
                    )
                }
            }
        }

        assertThat(compose.onNodeWithTag("bar").heightDp()).isWithin(1f).of(24f)
        assertThat(compose.onNodeWithTag("tall bar").heightDp()).isWithin(1f).of(120f)
    }

    // DpRect has no height member - only the four edges - and `height(...)` in scope is the
    // layout modifier, so the height has to be subtracted by hand.
    private fun SemanticsNodeInteraction.heightDp(): Float {
        val bounds = getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }
}
