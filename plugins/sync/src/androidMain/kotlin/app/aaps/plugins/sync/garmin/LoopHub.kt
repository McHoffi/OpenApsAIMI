package app.aaps.plugins.sync.garmin

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.Profile
import java.time.Instant

/** Abstraction from all the functionality we need from the AAPS app. */
interface LoopHub {

    /** Returns the active insulin profile. */
    val currentProfile: Profile?

    /** Returns the name of the active insulin profile. */
    val currentProfileName: String

    /** Returns the glucose unit (mg/dl or mmol/l) as selected by the user. */
    val glucoseUnit: GlucoseUnit

    /** Returns the remaining bolus insulin on board. */
    val insulinOnboard: Double

    /** Returns the basal insulin on board. */
    val insulinBasalOnboard: Double

    /** Returns the remaining carbs on board. */
    val carbsOnboard: Double?

    /** Returns true if the pump is connected. */
    val isConnected: Boolean

    /** Returns true if the current profile is set of a limited amount of time. */
    val isTemporaryProfile: Boolean

    /** Returns the factor by which the basal rate is currently raised (> 1) or lowered (< 1). */
    val temporaryBasal: Double

    /** Short loop status for the watch: CLOSED / LGS / OPEN / SUSPEND / DISCONN / DISABLED / SUPERBOLUS. */
    val loopStatus: String

    /** Epoch-ms of the last APS run (loop "freshness"), or null if the loop has never run. */
    val lastLoopEpochMs: Long?

    /** Current DynISF / variable sensitivity in the user's glucose units (mg/dL/U or mmol/L/U),
     *  or null if unavailable. Falls back to the profile ISF when the APS hasn't produced a value. */
    val variableSensInUnits: Double?

    /**
     * Remaining temporary basal duration in whole minutes, or null if no active TBR / EB.
     * Used by Blueprint / Cockpit / Atelier watchfaces (`tbrMins`).
     */
    val temporaryBasalRemainingMinutes: Int?

    /**
     * Current glucose target in mg/dL for the watch (`target`):
     * last APS/AIMI `targetBG` (DetermineBasalAIMI2) when available, else active TT midpoint,
     * else profile rounded target.
     */
    val currentTargetMgdl: Double?

    /**
     * Active AIMI therapy mode short label for the watch (`FCL`, `DINNER`, `LUNCH`, …),
     * or null when none is armed. Detected from Careportal NOTE keywords (same rules as `Therapy`).
     */
    val activeTherapyMode: String?

    /** Returns the lower bound of the target glucose range. */
    val lowGlucoseMark: Double

    /** Returns the upper bound of the target glucose range. */
    val highGlucoseMark: Double

    /** Tells the loop algorithm that the pump is physically connected. */
    fun connectPump()

    /** Tells the loop algorithm that the pump will be physically disconnected
     *  for the given number of minutes. */
    fun disconnectPump(minutes: Int)

    /** Retrieves the glucose values starting at from. */
    fun getGlucoseValues(from: Instant, ascending: Boolean): List<GV>

    /** Notifies the system that carbs were eaten and stores the value. */
    fun postCarbs(carbohydrates: Int)

    // mod Bolus and temp target
    /** Triggers a bolus. */
    fun postBolus(bolus: Double)

    /** Stores or cancels a temptarget. */
    fun postTempTarget(target: Double, duration: Int)
    // end mod

    /**
     * Activates an AIMI therapy mode via CarePortal NOTE (keyword only, e.g. "lunch", "fcl", "sport").
     * Duration belongs on the TherapyEvent.duration field — never embed minutes in the note text
     * ([Therapy] matches with note.contains and uses event.duration for the active window).
     * Keywords are detected by openAPSAIMI [Therapy] (`therapy.kt`).
     */
    fun postTherapyMode(keyword: String, durationMin: Int)

    /** Stores hear rate readings that a taken and averaged of the given interval. */
    fun storeHeartRate(
        samplingStart: Instant, samplingEnd: Instant,
        avgHeartRate: Int,
        device: String?
    )

    /** Stores a batch of fine-grained (≈1-min) HR samples from venu3/CIQ background service.
     *  Each pair is (endOfMinuteEpochMs, bpm). Peak preservation is downstream. */
    fun storeHeartRates(samples: List<Pair<Long, Int>>, device: String?)

    // ── BOOST algorithm fields for Garmin watchface ──
    /** True when BOOST is the active APS algorithm */
    val isBoostActive: Boolean
    /** V5 state: IDLE/OBSERVING/CONFIRMED/COMMITTED/RECOVERING */
    val boostV5State: String?
    /** BOOST dosing tier (UAM_BOOST, PERCENT_SCALE, etc.) */
    val boostTier: String?
    /** Dynamic ISF value from BOOST engine */
    val boostDynIsf: String?
    /** Total daily dose from BOOST engine */
    val boostTdd: String?

    /** Stores steps count readings aggregated over multiple intervals. */
    fun storeStepsCount(
        samplingStart: Instant,
        samplingEnd: Instant,
        steps5min: Int,
        steps10min: Int,
        steps15min: Int,
        steps30min: Int,
        steps60min: Int,
        steps180min: Int,
        device: String?,
    )

    /** Stores a Garmin step-count snapshot (venu3/CIQ format) with a single end-of-window
     *  [timestampMs]. The six trailing-window counts are computed device-side. */
    fun storeSteps(
        timestampMs: Long,
        steps5min: Int, steps10min: Int, steps15min: Int,
        steps30min: Int, steps60min: Int, steps180min: Int,
        device: String?
    )
}
