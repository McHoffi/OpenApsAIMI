package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * In `commonTest`, so this runs through Kotlin/Native as well as the JVM. `kotlin.test` rather than
 * Truth and JUnit 5, because neither of those exists off the JVM.
 */
class StepDevicesTest {

    @Test
    fun `watchface and ciq labels are told apart`() {
        assertTrue(StepDevices.isWatchface(StepDevices.GARMIN_WATCHFACE))
        assertTrue(StepDevices.isCiq(StepDevices.GARMIN_CIQ))
        assertFalse(StepDevices.isWatchface(StepDevices.GARMIN_CIQ))
        assertFalse(StepDevices.isCiq(StepDevices.GARMIN_WATCHFACE))
    }

    @Test
    fun `garmin family covers the new and the older labels`() {
        assertTrue(StepDevices.isGarminFamily(StepDevices.GARMIN_WATCHFACE))
        assertTrue(StepDevices.isGarminFamily(StepDevices.GARMIN_CIQ))
        assertTrue(StepDevices.isGarminFamily("garmin"))
        assertTrue(StepDevices.isGarminFamily("Garmin"))
        assertTrue(StepDevices.isGarminFamily("Garmin-Watchface"))
        assertFalse(StepDevices.isGarminFamily("WearOS"))
        assertFalse(StepDevices.isGarminFamily("HealthConnect"))
        assertFalse(StepDevices.isGarminFamily(null))
        assertFalse(StepDevices.isGarminFamily(""))
    }

    @Test
    fun `older shared label is neither watchface nor ciq`() {
        // Rows written before the source tags existed. Readers must not guess a feed from them.
        assertFalse(StepDevices.isWatchface("garmin"))
        assertFalse(StepDevices.isCiq("garmin"))
    }
}
