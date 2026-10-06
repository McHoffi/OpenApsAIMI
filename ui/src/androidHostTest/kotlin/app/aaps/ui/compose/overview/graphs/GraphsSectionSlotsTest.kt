package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the slot helpers behind the overview graph list: which entries are hidden while BOOST is
 * off, how the edit-sheet number is computed, and how a free slot is made when adding a graph.
 */
internal class GraphsSectionSlotsTest {

    private val cob = SecondaryGraph(listOf(SeriesType.COB))
    private val mhsOnly = SecondaryGraph(listOf(SeriesType.MHS))
    private val boostGraph = SecondaryGraph(listOf(SeriesType.BOOST))
    private val boostWithCob = SecondaryGraph(listOf(SeriesType.BOOST, SeriesType.COB))
    private val modes = SecondaryGraph(listOf(SeriesType.MODES))

    @Test
    fun `MHS-only and BOOST graphs are hidden while BOOST is not active`() {
        assertThat(isHiddenSecondarySlot(mhsOnly, isBoostActive = false)).isTrue()
        assertThat(isHiddenSecondarySlot(boostGraph, isBoostActive = false)).isTrue()
        assertThat(isHiddenSecondarySlot(boostWithCob, isBoostActive = false)).isTrue()
    }

    @Test
    fun `those graphs show again when BOOST is active`() {
        assertThat(isHiddenSecondarySlot(mhsOnly, isBoostActive = true)).isFalse()
        assertThat(isHiddenSecondarySlot(boostGraph, isBoostActive = true)).isFalse()
        assertThat(isHiddenSecondarySlot(boostWithCob, isBoostActive = true)).isFalse()
    }

    @Test
    fun `normal graphs are never hidden`() {
        assertThat(isHiddenSecondarySlot(cob, isBoostActive = false)).isFalse()
        assertThat(isHiddenSecondarySlot(modes, isBoostActive = false)).isFalse()
    }

    @Test
    fun `display number counts only visible graphs and starts at 2`() {
        val graphs = listOf(cob, mhsOnly, cob, boostGraph, cob)
        // index 0 → first visible → 2
        assertThat(secondaryGraphDisplayNumber(0, graphs, isBoostActive = false)).isEqualTo(2)
        // index 1 is hidden; the next visible is the second on screen → 3
        assertThat(secondaryGraphDisplayNumber(2, graphs, isBoostActive = false)).isEqualTo(3)
        // last visible is the third on screen → 4 (not 6)
        assertThat(secondaryGraphDisplayNumber(4, graphs, isBoostActive = false)).isEqualTo(4)
    }

    @Test
    fun `display number counts every graph when BOOST is active`() {
        val graphs = listOf(cob, mhsOnly, cob, boostGraph, cob)
        assertThat(secondaryGraphDisplayNumber(4, graphs, isBoostActive = true)).isEqualTo(6)
    }

    @Test
    fun `compact drops only hidden slots`() {
        val graphs = listOf(cob, mhsOnly, cob, boostGraph, cob)
        assertThat(compactHiddenSecondarySlots(graphs, isBoostActive = false))
            .containsExactly(cob, cob, cob)
            .inOrder()
    }

    @Test
    fun `compact keeps everything when BOOST is active`() {
        val graphs = listOf(cob, mhsOnly, cob, boostGraph, cob)
        assertThat(compactHiddenSecondarySlots(graphs, isBoostActive = true))
            .containsExactlyElementsIn(graphs)
            .inOrder()
    }

    @Test
    fun `BOOST and MHS are not offered while BOOST is not the active APS`() {
        val series = availableSecondarySeries(
            isAutoIsfActive = false,
            isAIMIActive = true,
            isBoostActive = false,
        )
        assertThat(series).doesNotContain(SeriesType.BOOST)
        assertThat(series).doesNotContain(SeriesType.MHS)
        // Normal series and PULSE stay available on every APS.
        assertThat(series).contains(SeriesType.COB)
        assertThat(series).contains(SeriesType.PULSE)
    }

    @Test
    fun `BOOST and MHS are offered while BOOST is the active APS`() {
        val series = availableSecondarySeries(
            isAutoIsfActive = false,
            isAIMIActive = false,
            isBoostActive = true,
        )
        assertThat(series).contains(SeriesType.BOOST)
        assertThat(series).contains(SeriesType.MHS)
        assertThat(series).contains(SeriesType.COB)
    }

    @Test
    fun `AIMI-only series appear only when AIMI is active`() {
        val withAimi = availableSecondarySeries(isAutoIsfActive = false, isAIMIActive = true, isBoostActive = false)
        val withoutAimi = availableSecondarySeries(isAutoIsfActive = false, isAIMIActive = false, isBoostActive = false)

        assertThat(withAimi).contains(SeriesType.MODES)
        assertThat(withoutAimi).doesNotContain(SeriesType.FINAL_ISF)
    }
}
