package app.aaps.plugins.sync.nsclientV3.activity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ActivityUploadCursorTest {

    @Test
    fun `never started is 0`() {
        assertThat(ActivityUploadCursor().lastUploadedAt).isEqualTo(ActivityUploadCursor.NEVER)
        assertThat(ActivityUploadCursor.NEVER).isEqualTo(0L)
    }

    @Test
    fun `only records after the cursor are due`() {
        val cursor = ActivityUploadCursor(lastUploadedAt = 100L)
        assertThat(cursor.isDue(101L)).isTrue()
        assertThat(cursor.isDue(100L)).isFalse()
        assertThat(cursor.isDue(99L)).isFalse()
    }

    @Test
    fun `everything is due before the first upload`() {
        val cursor = ActivityUploadCursor()
        assertThat(cursor.isDue(1L)).isTrue()
    }

    @Test
    fun `success advances to the newest event time in the batch`() {
        val cursor = ActivityUploadCursor(lastUploadedAt = 100L)
        assertThat(cursor.afterSuccess(250L).lastUploadedAt).isEqualTo(250L)
    }

    @Test
    fun `success never moves the cursor backwards`() {
        val cursor = ActivityUploadCursor(lastUploadedAt = 250L)
        assertThat(cursor.afterSuccess(100L).lastUploadedAt).isEqualTo(250L)
        assertThat(cursor.afterSuccess(250L).lastUploadedAt).isEqualTo(250L)
    }

    @Test
    fun `first enable starts from now and sends nothing older`() {
        val started = ActivityUploadCursor().startingFrom(1_780_000_000_000L)
        assertThat(started.lastUploadedAt).isEqualTo(1_780_000_000_000L)
        assertThat(started.isDue(1_779_999_999_999L)).isFalse()
        assertThat(started.isDue(1_780_000_000_001L)).isTrue()
    }

    @Test
    fun `a later start does not reopen an already started cursor`() {
        val started = ActivityUploadCursor(lastUploadedAt = 500L).startingFrom(9_999L)
        assertThat(started.lastUploadedAt).isEqualTo(500L)
    }

    @Test
    fun `batch size is 100`() {
        assertThat(ActivityUploadCursor.BATCH_SIZE).isEqualTo(100)
    }
}
