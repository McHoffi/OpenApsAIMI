package app.aaps.plugins.sync.nsclientV3.activity

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.remotemodel.RemoteActivity
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.plugins.sync.nsclientV3.keys.NsclientLongKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Uploads heart rate and step samples to Nightscout `POST /api/v1/activity`.
 *
 * Separate from [app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3] on purpose. That path is
 * row-id cursors and NS id write-back into the v3 collections. Activity is a v1-only endpoint with
 * timestamp cursors, a generated `_id`, and no id write-back. Forcing it into `processChangedXxx`
 * would bend both.
 *
 * It still rides the same sync tick (see `DataSyncRunner`), so "every cycle" is the same cycle the
 * rest of the upload uses.
 *
 * Rules that are load-bearing:
 *
 * - The master upload switch off, or sync paused → nothing at all. Same umbrella as
 *   `DataSyncSelectorV3.doUpload`, so "Upload data" really means no upload.
 * - Both preferences off → this class does nothing at all. No query, no request.
 * - First enable per type starts from "now". No historical backfill, so nothing older is ever
 *   written after a newer `created_at` (nocturne#1778).
 * - The cursor moves only after HTTP 2xx, and only forward.
 * - HTTP 403 logs once and pauses that type until the next app run. No endless retry.
 */
@SingleIn(AppScope::class)
@Inject
class ActivityUploader(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val nsClientV3Plugin: NSClientV3Plugin,
) {

    private var heartRateForbidden = false
    private var stepsForbidden = false

    suspend fun uploadPending() {
        // Same umbrella as DataSyncSelectorV3.doUpload: the master switch and the pause button.
        if (!config.AAPSCLIENT && !preferences.get(BooleanKey.NsClientUploadData)) return
        if (preferences.get(NsclientBooleanKey.NsPaused)) return

        val uploadHeartRate = preferences.get(BooleanKey.NsClientUploadHeartRate)
        val uploadSteps = preferences.get(BooleanKey.NsClientUploadSteps)
        // Bit-identical while off: no DB read, no HTTP, no cursor write.
        if (!uploadHeartRate && !uploadSteps) return

        if (uploadHeartRate && !heartRateForbidden) uploadHeartRates()
        if (uploadSteps && !stepsForbidden) uploadSteps()
    }

    private suspend fun uploadHeartRates() {
        val cursor = dueCursor(NsclientLongKey.HeartRateLastUploadedAt) ?: return
        val rows = persistenceLayer.getHeartRatesFromTimeToTime(cursor.lastUploadedAt + 1, dateUtil.now())
            .filter { it.isValid }
            .sortedBy { it.timestamp }
            .take(ActivityUploadCursor.BATCH_SIZE)
        if (rows.isEmpty()) return

        val records = rows.map { it.toActivityRecord(dateUtil.toISOString(it.timestamp)) }
        val batchMax = rows.maxOf { it.timestamp }
        send(
            label = "heart rate",
            key = NsclientLongKey.HeartRateLastUploadedAt,
            cursor = cursor,
            records = records,
            batchMaxEventTime = batchMax,
        ) { heartRateForbidden = true }
    }

    private suspend fun uploadSteps() {
        val cursor = dueCursor(NsclientLongKey.StepsLastUploadedAt) ?: return
        val rows = persistenceLayer.getStepsCountFromTimeToTime(cursor.lastUploadedAt + 1, dateUtil.now())
            .filter { it.isValid }
            .sortedBy { it.timestamp }
            .take(ActivityUploadCursor.BATCH_SIZE)
        if (rows.isEmpty()) return

        // Feed pick and 5-minute bucketing live in toActivityRecords. The cursor still covers
        // every scanned row, sent or not, so a skipped row is never rescanned next cycle.
        val records = rows.toActivityRecords { dateUtil.toISOString(it) }
        val batchMax = rows.maxOf { it.timestamp }
        if (records.isEmpty()) {
            // Rows were there but none came from an uploaded feed. Still counts as handled.
            saveCursor(NsclientLongKey.StepsLastUploadedAt, cursor.afterSuccess(batchMax))
            return
        }
        send(
            label = "steps",
            key = NsclientLongKey.StepsLastUploadedAt,
            cursor = cursor,
            records = records,
            batchMaxEventTime = batchMax,
        ) { stepsForbidden = true }
    }

    /**
     * Cursor for [key], starting from "now" the first time this type runs.
     *
     * Returns null when there is nothing due: first run, or the window is empty.
     */
    private fun dueCursor(key: NsclientLongKey): ActivityUploadCursor? {
        val stored = preferences.get(key)
        val now = dateUtil.now()
        if (stored == ActivityUploadCursor.NEVER) {
            saveCursor(key, ActivityUploadCursor(stored).startingFrom(now))
            aapsLogger.info(LTag.NSCLIENT, "Activity upload: first run, start from now")
            return null
        }
        return if (stored < now) ActivityUploadCursor(stored) else null
    }

    private suspend fun send(
        label: String,
        key: NsclientLongKey,
        cursor: ActivityUploadCursor,
        records: List<RemoteActivity>,
        batchMaxEventTime: Long,
        onForbidden: () -> Unit,
    ) {
        val client = nsClientV3Plugin.nsAndroidClient ?: return
        val result = try {
            client.createActivities(records)
        } catch (e: Exception) {
            aapsLogger.warn(LTag.NSCLIENT, "Activity $label upload failed: ${e.message}")
            return
        }
        when {
            result.response in 200..299 -> {
                saveCursor(key, cursor.afterSuccess(batchMaxEventTime))
                aapsLogger.info(LTag.NSCLIENT, "Activity $label: sent ${records.size} up to $batchMaxEventTime")
            }
            result.response == 403 -> {
                // Once per app run: the pause is what stops the retry loop, so this is also the
                // only log line the user sees for it.
                aapsLogger.warn(LTag.NSCLIENT, "Activity $label: forbidden (403). Paused until restart.")
                onForbidden()
            }
            else -> {
                aapsLogger.warn(LTag.NSCLIENT, "Activity $label: HTTP ${result.response}. Cursor held.")
            }
        }
    }

    private fun saveCursor(key: NsclientLongKey, cursor: ActivityUploadCursor) {
        if (cursor.lastUploadedAt != preferences.get(key)) {
            preferences.put(key, cursor.lastUploadedAt)
        }
    }
}
