package app.aaps.plugins.sync.nsclientV3.activity

/**
 * How far one activity type has been uploaded to Nightscout.
 *
 * [lastUploadedAt] is the event time of the newest record Nightscout accepted for this type. The
 * rule is strict:
 *
 * 1. Only send records with `eventTime > lastUploadedAt`. Never go back.
 * 2. Move the cursor only after HTTP 2xx.
 * 3. On first enable, start from "now". No historical backfill.
 *
 * That keeps `created_at` increasing in Nightscout. Nocturne reads activity from "newest stored
 * minus 5 minutes" and would never fetch a record that lands late with an older `created_at`
 * (nocturne#1778).
 *
 * A 403 is not cursor state: it is a per-app-run pause held by the uploader, so a restart tries
 * again.
 */
data class ActivityUploadCursor(
    val lastUploadedAt: Long = NEVER,
) {

    /** Records at or before [lastUploadedAt] were already accepted. Skip them. */
    fun isDue(eventTime: Long): Boolean = eventTime > lastUploadedAt

    /**
     * After HTTP 2xx for a batch whose newest event time is [batchMaxEventTime].
     *
     * The cursor never moves backwards, so a late or repeated batch cannot reopen an already
     * closed range.
     */
    fun afterSuccess(batchMaxEventTime: Long): ActivityUploadCursor =
        if (batchMaxEventTime > lastUploadedAt) copy(lastUploadedAt = batchMaxEventTime) else this

    /**
     * First enable: jump to [nowMs] and upload nothing yet. Later samples carry event times after
     * this, so they go out on the next cycle.
     */
    fun startingFrom(nowMs: Long): ActivityUploadCursor =
        if (lastUploadedAt == NEVER) copy(lastUploadedAt = nowMs) else this

    companion object {

        /** Nightscout allows 10000 per call. 100 keeps one request small and timely. */
        const val BATCH_SIZE = 100

        /** 0 means "never started". */
        const val NEVER = 0L
    }
}
