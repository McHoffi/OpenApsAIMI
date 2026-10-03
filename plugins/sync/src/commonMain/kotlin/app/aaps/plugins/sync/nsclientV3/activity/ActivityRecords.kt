package app.aaps.plugins.sync.nsclientV3.activity

import app.aaps.core.data.model.HR
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.StepDevices
import app.aaps.core.nssdk.remotemodel.RemoteActivity
import kotlin.math.roundToInt

/**
 * Turn a stored sample into one Nightscout activity record.
 *
 * Both builders take [createdAt] rather than a clock, so the mapping is pure and the caller decides
 * the ISO text with `DateUtil.toISOString` — the one format this project uploads to Nightscout.
 *
 * Steps go out as `metric` with `source = 0` (delta), **not** as `steps`. The `steps` + `steps-total`
 * shape is the one Nocturne flags `PossibleRunningTotalFlag` and then leaves out of every total.
 */
internal fun HR.toActivityRecord(createdAt: String): RemoteActivity =
    RemoteActivity(
        id = activityUploadId(ActivityUploadKind.HeartRate, timestamp),
        type = ActivityUploadKind.HeartRate.wireType,
        timeStamp = timestamp,
        createdAt = createdAt,
        bpm = beatsPerMinute.roundToInt(),
    )

/** One step row → one `steps-total` record. [SC.steps5min] is the count Nocturne sums. */
internal fun SC.toActivityRecord(createdAt: String): RemoteActivity =
    RemoteActivity(
        id = activityUploadId(ActivityUploadKind.Steps, timestamp),
        type = ActivityUploadKind.Steps.wireType,
        timeStamp = timestamp,
        createdAt = createdAt,
        metric = steps5min,
        source = 0,
        device = device,
    )

/**
 * Step rows of one batch → the records to upload. Picks **one** Garmin feed, never both, the same
 * way `OverviewViewModel.dedupedTotalForDevice` reads them:
 *
 * - Watchface day-counter rows ([StepDevices.isWatchface]) are a delta chain: a day-start baseline
 *   plus poll deltas in `steps5min`. Every row is one record and Nocturne sums them.
 * - CIQ trailing-window rows ([StepDevices.isCiq]) measure the same walk in overlapping windows.
 *   Used only when the batch has no watchface row. One record per 5-minute bucket, the largest
 *   `steps5min` in that bucket, so two samples in one bucket do not add up twice.
 * - HealthConnect, PhoneSensor and untagged rows stay out. They would double-count against the
 *   Garmin feed, and the source mode already decides which feed the app trusts.
 */
internal fun List<SC>.toActivityRecords(toCreatedAt: (Long) -> String): List<RemoteActivity> {
    val hasWatchface = any { StepDevices.isWatchface(it.device) }
    val chosen = filter {
        StepDevices.isWatchface(it.device) || (StepDevices.isCiq(it.device) && !hasWatchface)
    }
    if (hasWatchface) return chosen.map { it.toActivityRecord(toCreatedAt(it.timestamp)) }
    // Same 5-minute width as OverviewViewModel.dedupedTotalForDevice uses for CIQ rows.
    return chosen
        .groupBy { it.timestamp / (5 * 60 * 1000L) }
        .values
        .mapNotNull { bucket -> bucket.maxByOrNull { it.steps5min } }
        .sortedBy { it.timestamp }
        .map { it.toActivityRecord(toCreatedAt(it.timestamp)) }
}
