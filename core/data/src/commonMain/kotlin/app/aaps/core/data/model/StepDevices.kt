package app.aaps.core.data.model

/**
 * Device labels on [SC] rows that say which feed wrote them.
 *
 * Garmin writes steps from two feeds: the watchface day counter (a day-start baseline plus
 * poll-to-poll deltas) and the CIQ app (trailing window counts). Both used to share one label, so a
 * reader had to guess the feed from the row shape. That guess fails twice: a CIQ row can have only
 * [SC.steps5min] set, and a day-start baseline is a full total rather than a delta. The labels make
 * the feed explicit.
 *
 * Both stay in the "garmin" family. Matchers that only care about the brand use
 * [isGarminFamily].
 */
object StepDevices {

    /**
     * Watchface day-counter rows. [SC.steps5min] is either the day-start total or the delta since
     * the last poll. Wider windows are left at 0 (unset). The rows of one day chain up to the
     * watch's day total.
     */
    const val GARMIN_WATCHFACE = "garmin-watchface"

    /** CIQ trailing-window rows. [SC.steps5min]…[SC.steps180min] are window counts and overlap. */
    const val GARMIN_CIQ = "garmin-ciq"

    /** Any Garmin feed, including older labels ("garmin", "Garmin", "Garmin-Watchface"). */
    fun isGarminFamily(device: String?): Boolean =
        !device.isNullOrBlank() && device.startsWith("garmin", ignoreCase = true)

    fun isWatchface(device: String?): Boolean =
        !device.isNullOrBlank() && device.startsWith(GARMIN_WATCHFACE, ignoreCase = true)

    fun isCiq(device: String?): Boolean =
        !device.isNullOrBlank() && device.startsWith(GARMIN_CIQ, ignoreCase = true)
}
