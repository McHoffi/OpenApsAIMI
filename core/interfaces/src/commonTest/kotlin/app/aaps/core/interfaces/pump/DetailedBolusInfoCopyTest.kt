package app.aaps.core.interfaces.pump

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Pins [DetailedBolusInfo.copy] field by field.
 *
 * It copies fifteen properties by hand and had no test, which is how it came to silently drop
 * `bolusTimestamp` - a field [DetailedBolusInfo.createBolus] reads as `bolusTimestamp ?: timestamp`
 * to decide the timestamp of the stored bolus record. Nothing assigns that field today, so the loss
 * is currently invisible; the moment a driver does set it, a copied info would write the record at
 * the wrong time.
 *
 * A hand written copy needs a test that names every field, otherwise the next one added is dropped
 * the same way.
 */
class DetailedBolusInfoCopyTest {

    private fun populated() = DetailedBolusInfo().apply {
        insulin = 1.25
        carbs = 30.0
        timestamp = 1_700_000_000_000L
        lastKnownBolusTime = 1_699_999_000_000L
        deliverAtTheLatest = 1_700_000_060_000L
        eventType = TE.Type.CORRECTION_BOLUS
        notes = "note"
        mgdlGlucose = 123.0
        glucoseType = TE.MeterType.FINGER
        bolusType = BS.Type.SMB
        carbsDuration = 3_600_000L
        pumpType = PumpType.ACCU_CHEK_COMBO
        pumpSerial = "serial-1"
        bolusPumpId = 42L
        bolusTimestamp = 1_700_000_005_000L
        carbsTimestamp = 1_700_000_010_000L
    }

    @Test
    fun copy_carriesEveryField() {
        val original = populated()

        val copy = original.copy()

        assertEquals(original.insulin, copy.insulin)
        assertEquals(original.carbs, copy.carbs)
        assertEquals(original.timestamp, copy.timestamp)
        assertEquals(original.lastKnownBolusTime, copy.lastKnownBolusTime)
        assertEquals(original.deliverAtTheLatest, copy.deliverAtTheLatest)
        assertEquals(original.bolusCalculatorResult, copy.bolusCalculatorResult)
        assertEquals(original.eventType, copy.eventType)
        assertEquals(original.notes, copy.notes)
        assertEquals(original.mgdlGlucose, copy.mgdlGlucose)
        assertEquals(original.glucoseType, copy.glucoseType)
        assertEquals(original.bolusType, copy.bolusType)
        assertEquals(original.carbsDuration, copy.carbsDuration)
        assertEquals(original.pumpType, copy.pumpType)
        assertEquals(original.pumpSerial, copy.pumpSerial)
        assertEquals(original.bolusPumpId, copy.bolusPumpId)
        assertEquals(original.bolusTimestamp, copy.bolusTimestamp)
        assertEquals(original.carbsTimestamp, copy.carbsTimestamp)
    }

    @Test
    fun copy_isANewInstance() {
        val original = populated()

        val copy = original.copy()
        copy.insulin = 9.9

        assertNotSame(original, copy)
        assertEquals(1.25, original.insulin)
    }

    // There is deliberately no test that `copy()` leaves `id` behind. It cannot be asserted by value:
    // `id` is `Clock.System.now().toEpochMilliseconds()`, so an instance and its copy are built in the
    // same millisecond and get the SAME number - a fresh id is indistinguishable from a copied one.
    // Worth knowing on its own: two DetailedBolusInfo created in one millisecond share an id, which is
    // the same collision that MasterActionExecutorImpl.nextPendingId() exists to avoid for parked doses.

    @Test
    fun createBolus_usesBolusTimestampWhenSet_afterACopy() {
        // The reason the missing field matters: this is what reads it.
        val copy = populated().copy()

        assertEquals(1_700_000_005_000L, copy.createBolus(iCfg = ICfg("", 0, 0)).timestamp)
    }
}
