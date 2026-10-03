package app.aaps.plugins.sync.nsclientV3.activity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ActivityUploadIdTest {

    @Test
    fun `id is 24 lower-case hex characters`() {
        val id = activityUploadId(ActivityUploadKind.HeartRate, 1_780_000_000_000L)
        assertThat(id).hasLength(24)
        assertThat(id).matches("[0-9a-f]{24}")
    }

    @Test
    fun `same kind and time give the same id`() {
        val a = activityUploadId(ActivityUploadKind.Steps, 1_780_000_000_000L)
        val b = activityUploadId(ActivityUploadKind.Steps, 1_780_000_000_000L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `different kinds at the same time do not collide`() {
        val hr = activityUploadId(ActivityUploadKind.HeartRate, 1_780_000_000_000L)
        val steps = activityUploadId(ActivityUploadKind.Steps, 1_780_000_000_000L)
        assertThat(hr).isNotEqualTo(steps)
    }

    @Test
    fun `different times do not collide`() {
        val a = activityUploadId(ActivityUploadKind.HeartRate, 1_780_000_000_000L)
        val b = activityUploadId(ActivityUploadKind.HeartRate, 1_780_000_000_001L)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `id is pinned`() {
        // Guard against an accidental change of the derivation: Nightscout upserts on `_id`, so a
        // new scheme would re-send every record that is already stored.
        assertThat(activityUploadId(ActivityUploadKind.HeartRate, 0L)).isEqualTo("480000000000000000000000")
        assertThat(activityUploadId(ActivityUploadKind.Steps, 0L)).isEqualTo("530000000000000000000000")
    }
}
