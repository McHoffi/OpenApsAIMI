package app.aaps.plugins.sync.nsclientV3.activity

import app.aaps.core.data.model.HR
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.StepDevices
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ActivityRecordsTest {

    private val createdAt = "2026-09-30T06:40:00.000Z"
    private val eventTime = 1_780_000_000_000L

    private fun sc(steps5min: Int, device: String, timestamp: Long = eventTime) = SC(
        timestamp = timestamp,
        duration = 300_000L,
        steps5min = steps5min,
        steps10min = 0,
        steps15min = 0,
        steps30min = 0,
        steps60min = 0,
        steps180min = 0,
        device = device,
    )

    @Test
    fun `heart rate record is hr-bpm with an int bpm`() {
        val record = HR(
            timestamp = eventTime,
            duration = 60_000L,
            beatsPerMinute = 88.4,
            device = "garmin",
        ).toActivityRecord(createdAt)

        assertThat(record.id).isEqualTo(activityUploadId(ActivityUploadKind.HeartRate, eventTime))
        assertThat(record.type).isEqualTo("hr-bpm")
        assertThat(record.timeStamp).isEqualTo(eventTime)
        assertThat(record.createdAt).isEqualTo(createdAt)
        assertThat(record.bpm).isEqualTo(88)
        // The steps fields stay off the heart rate record.
        assertThat(record.metric).isNull()
        assertThat(record.source).isNull()
        assertThat(record.device).isNull()
    }

    @Test
    fun `watchface step record is steps-total with metric and source 0`() {
        val record = sc(steps5min = 76, device = StepDevices.GARMIN_WATCHFACE).toActivityRecord(createdAt)

        assertThat(record.id).isEqualTo(activityUploadId(ActivityUploadKind.Steps, eventTime))
        assertThat(record.type).isEqualTo("steps-total")
        assertThat(record.timeStamp).isEqualTo(eventTime)
        assertThat(record.createdAt).isEqualTo(createdAt)
        // metric, NOT steps: the steps shape is flagged "possible running total" and never counted.
        assertThat(record.metric).isEqualTo(76)
        assertThat(record.source).isEqualTo(0)
        assertThat(record.device).isEqualTo(StepDevices.GARMIN_WATCHFACE)
        assertThat(record.bpm).isNull()
    }

    @Test
    fun `watchface rows win when the batch also has ciq rows`() {
        val records = listOf(
            sc(steps5min = 10, device = StepDevices.GARMIN_WATCHFACE),
            sc(steps5min = 99, device = StepDevices.GARMIN_CIQ),
        ).toActivityRecords { createdAt }

        assertThat(records).hasSize(1)
        assertThat(records.single().metric).isEqualTo(10)
        assertThat(records.single().device).isEqualTo(StepDevices.GARMIN_WATCHFACE)
    }

    @Test
    fun `every watchface row goes out when the batch is all watchface`() {
        val records = listOf(
            sc(steps5min = 10, device = StepDevices.GARMIN_WATCHFACE, timestamp = eventTime),
            sc(steps5min = 20, device = StepDevices.GARMIN_WATCHFACE, timestamp = eventTime + 300_000L),
        ).toActivityRecords { createdAt }

        assertThat(records).hasSize(2)
        assertThat(records.map { it.metric }).containsExactly(10, 20).inOrder()
    }

    @Test
    fun `ciq rows are sent when the batch has no watchface row`() {
        val records = listOf(sc(steps5min = 76, device = StepDevices.GARMIN_CIQ))
            .toActivityRecords { createdAt }

        assertThat(records).hasSize(1)
        assertThat(records.single().type).isEqualTo("steps-total")
        assertThat(records.single().metric).isEqualTo(76)
        assertThat(records.single().source).isEqualTo(0)
        assertThat(records.single().device).isEqualTo(StepDevices.GARMIN_CIQ)
    }

    @Test
    fun `ciq rows in one 5-minute bucket collapse to the largest`() {
        val bucket = 5 * 60 * 1000L
        val records = listOf(
            sc(steps5min = 76, device = StepDevices.GARMIN_CIQ, timestamp = eventTime),
            sc(steps5min = 3, device = StepDevices.GARMIN_CIQ, timestamp = eventTime + 60_000L),
            sc(steps5min = 12, device = StepDevices.GARMIN_CIQ, timestamp = eventTime + bucket),
        ).toActivityRecords { createdAt }

        assertThat(records).hasSize(2)
        assertThat(records.map { it.metric }).containsExactly(76, 12).inOrder()
    }

    @Test
    fun `health connect and phone rows are not uploaded`() {
        val records = listOf(
            sc(steps5min = 10, device = "HealthConnect"),
            sc(steps5min = 10, device = "PhoneSensor"),
        ).toActivityRecords { createdAt }

        assertThat(records).isEmpty()
    }

    @Test
    fun `older untagged rows are not uploaded`() {
        // Before the source tags existed, rows carried "garmin" / "Garmin-Watchface". Those are
        // Garmin family but the feed is unknown, so they stay out rather than risk double-counting.
        val records = listOf(sc(steps5min = 10, device = "Garmin")).toActivityRecords { createdAt }

        assertThat(records).isEmpty()
    }
}
