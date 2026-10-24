package app.aaps.core.interfaces.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.RecordingAAPSLogger
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * That enabling and disabling a plugin can be waited for.
 *
 * [PluginBase.setPluginEnabled] sets the state flag straight away but only *schedules*
 * [PluginBase.onStart] / [PluginBase.onStop] on the plugin's own scope. That is fine on the startup
 * path, and not fine when imported settings are applied to a running app: the caller waits for an idle
 * pump, applies, and then lets commands flow again - which it must not do while a pump driver is still
 * being torn down. The job is returned so that caller can wait for it.
 *
 * In `commonTest` rather than `androidHostTest`: the lifecycle rule is shared code and the two fakes
 * are small. Nothing here writes to the logger or resolves a string, so both are inert.
 */
class PluginBaseLifecycleTest {

    private class TestPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        notificationManager: NotificationManager
    ) : PluginBase(PluginDescription().mainType(PluginType.GENERAL), aapsLogger, rh, notificationManager) {

        // Held open until a test lets the phase through, so "scheduled" and "finished" can be told apart.
        val startGate = CompletableDeferred<Unit>()
        val stopGate = CompletableDeferred<Unit>()
        var started = false
        var stopped = false

        override suspend fun onStart() {
            startGate.await()
            started = true
        }

        override suspend fun onStop() {
            stopGate.await()
            stopped = true
        }
    }

    /**
     * Only ever passed to the constructor, so it resolves nothing. A reference is answered with the
     * text it was built from, which is what a test assertion would want to see if one ever asked.
     */
    private class FakeTextResolver : TextResolver {
        override fun gs(ref: TextRef): String = text(ref)
        override fun gs(ref: TextRef, vararg args: Any?): String = text(ref)
        override fun gsNotLocalised(ref: TextRef): String = text(ref)

        private fun text(ref: TextRef): String = when (ref) {
            is TextRef.Literal    -> ref.text
            is TextRef.Named      -> ref.name
            is TextRef.AndroidRes -> "androidRes(${ref.id})"
        }
    }

    /**
     * Only ever passed to the constructor - this test never posts - so every call is inert. A hand
     * written fake rather than `mock()` because this file is in `commonTest` and mockito is JVM only.
     */
    private class FakeNotifications : NotificationManager {
        override val notifications: StateFlow<List<AapsNotification>> = MutableStateFlow(emptyList())

        override fun cleanUp() {}

        override fun post(
            id: NotificationId,
            text: String,
            level: NotificationLevel,
            validMinutes: Int,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle = NotificationHandle(0)

        override fun post(
            id: NotificationId,
            text: String,
            level: NotificationLevel,
            date: Long,
            validTo: Long,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle = NotificationHandle(0)

        override fun post(
            id: NotificationId,
            textRef: TextRef,
            level: NotificationLevel,
            validMinutes: Int,
            date: Long,
            validTo: Long,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle = NotificationHandle(0)

        override fun dismiss(id: NotificationId) {}

        override fun dismiss(handle: NotificationHandle) {}

        override fun muteAllAlarms() {}
    }

    private fun plugin() = TestPlugin(RecordingAAPSLogger(), FakeTextResolver(), FakeNotifications())

    /** Enables the plugin and waits for it, so a test can start from a genuinely started plugin. */
    private suspend fun TestPlugin.enableAndAwait() {
        startGate.complete(Unit)
        withTimeout(5.seconds) { setPluginEnabledAwaiting(PluginType.GENERAL, true) }
    }

    @Test
    fun `enabling returns the job that runs onStart`() = runBlocking {
        val sut = plugin()

        val job = sut.setPluginEnabled(PluginType.GENERAL, true)

        assertNotNull(job)
        assertFalse(sut.started)   // scheduled, not run
        sut.startGate.complete(Unit)
        withTimeout(5.seconds) { job.join() }
        assertTrue(sut.started)
    }

    @Test
    fun `disabling returns the job that runs onStop`() = runBlocking {
        val sut = plugin()
        sut.enableAndAwait()

        val job = sut.setPluginEnabled(PluginType.GENERAL, false)

        assertNotNull(job)
        assertFalse(sut.stopped)   // scheduled, not run
        sut.stopGate.complete(Unit)
        withTimeout(5.seconds) { job.join() }
        assertTrue(sut.stopped)
    }

    /** Nothing to wait for when the state did not change, so there is no job to return. */
    @Test
    fun `enabling a plugin that is already enabled returns no job`() = runBlocking {
        val sut = plugin()
        sut.enableAndAwait()

        assertNull(sut.setPluginEnabled(PluginType.GENERAL, true))
    }

    @Test
    fun `disabling a plugin that is already disabled returns no job`() {
        val sut = plugin()

        assertNull(sut.setPluginEnabled(PluginType.GENERAL, false))
    }

    /** The whole point: the awaiting form does not come back until onStart has finished. */
    @Test
    fun `the awaiting form waits for onStart`() = runBlocking {
        val sut = plugin()

        val enabling = async { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }
        delay(200)
        assertFalse(enabling.isCompleted)

        sut.startGate.complete(Unit)
        withTimeout(5.seconds) { enabling.await() }
        assertTrue(sut.started)
    }

    /** ...and returns straight away when there was nothing to start. */
    @Test
    fun `the awaiting form returns at once when nothing changed`() = runBlocking {
        val sut = plugin()

        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, false) }

        assertFalse(sut.started)
    }
}
