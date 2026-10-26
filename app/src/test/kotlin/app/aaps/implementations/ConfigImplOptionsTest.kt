package app.aaps.implementations

import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.di.ExternalOptionsOverride
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConfigImplOptionsTest {

    private val fileListProvider: FileListProvider = mock()

    private fun sut() = ConfigImpl({ fileListProvider }, ExternalOptionsOverride.NONE)

    @Test
    fun `option is on when the plain file exists`() {
        whenever(fileListProvider.extraFileExists("engineering_mode")).thenReturn(true)

        assertThat(sut().isEnabled(ExternalOptions.ENGINEERING_MODE)).isTrue()
        verify(fileListProvider, never()).ensureExtraDirExists()
    }

    @Test
    fun `plain file check is final when it says no`() {
        whenever(fileListProvider.extraFileExists("engineering_mode")).thenReturn(false)

        assertThat(sut().isEnabled(ExternalOptions.ENGINEERING_MODE)).isFalse()
        verify(fileListProvider, never()).ensureExtraDirExists()
    }

    @Test
    fun `chosen folder is asked when the plain file check is not possible`() {
        whenever(fileListProvider.extraFileExists("engineering_mode")).thenReturn(null)
        whenever(fileListProvider.ensureExtraDirExists()).thenReturn(null)

        assertThat(sut().isEnabled(ExternalOptions.ENGINEERING_MODE)).isFalse()
        verify(fileListProvider).ensureExtraDirExists()
    }
}
