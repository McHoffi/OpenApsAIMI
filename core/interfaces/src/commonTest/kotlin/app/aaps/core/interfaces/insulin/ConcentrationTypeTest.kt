package app.aaps.core.interfaces.insulin

import kotlin.test.Test
import kotlin.test.assertEquals

class ConcentrationTypeTest {

    @Test
    fun `fromDouble returns correct type for known values`() {
        assertEquals(ConcentrationType.U10, ConcentrationType.fromDouble(0.1))
        assertEquals(ConcentrationType.U20, ConcentrationType.fromDouble(0.2))
        assertEquals(ConcentrationType.U40, ConcentrationType.fromDouble(0.4))
        assertEquals(ConcentrationType.U50, ConcentrationType.fromDouble(0.5))
        assertEquals(ConcentrationType.U100, ConcentrationType.fromDouble(1.0))
        assertEquals(ConcentrationType.U200, ConcentrationType.fromDouble(2.0))
        assertEquals(ConcentrationType.U300, ConcentrationType.fromDouble(3.0))
        assertEquals(ConcentrationType.U500, ConcentrationType.fromDouble(5.0))
    }

    @Test
    fun `fromDouble returns UNKNOWN for unrecognized value`() {
        assertEquals(ConcentrationType.UNKNOWN, ConcentrationType.fromDouble(0.0))
        assertEquals(ConcentrationType.UNKNOWN, ConcentrationType.fromDouble(1.5))
        assertEquals(ConcentrationType.UNKNOWN, ConcentrationType.fromDouble(7.0))
    }

    @Test
    fun `fromInt returns correct type for known values`() {
        assertEquals(ConcentrationType.U10, ConcentrationType.fromInt(10))
        assertEquals(ConcentrationType.U20, ConcentrationType.fromInt(20))
        assertEquals(ConcentrationType.U40, ConcentrationType.fromInt(40))
        assertEquals(ConcentrationType.U50, ConcentrationType.fromInt(50))
        assertEquals(ConcentrationType.U100, ConcentrationType.fromInt(100))
        assertEquals(ConcentrationType.U200, ConcentrationType.fromInt(200))
        assertEquals(ConcentrationType.U300, ConcentrationType.fromInt(300))
        assertEquals(ConcentrationType.U500, ConcentrationType.fromInt(500))
    }

    @Test
    fun `fromInt returns UNKNOWN for unrecognized value`() {
        assertEquals(ConcentrationType.UNKNOWN, ConcentrationType.fromInt(0))
        assertEquals(ConcentrationType.UNKNOWN, ConcentrationType.fromInt(150))
        assertEquals(ConcentrationType.UNKNOWN, ConcentrationType.fromInt(999))
    }

    @Test
    fun `value property matches expected concentration multiplier`() {
        assertEquals(0.1, ConcentrationType.U10.value)
        assertEquals(0.2, ConcentrationType.U20.value)
        assertEquals(0.4, ConcentrationType.U40.value)
        assertEquals(0.5, ConcentrationType.U50.value)
        assertEquals(1.0, ConcentrationType.U100.value)
        assertEquals(2.0, ConcentrationType.U200.value)
        assertEquals(3.0, ConcentrationType.U300.value)
        assertEquals(5.0, ConcentrationType.U500.value)
        assertEquals(-1.0, ConcentrationType.UNKNOWN.value)
    }
}
