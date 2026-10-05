package app.aaps.persistentNotification

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.MetroService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import dev.zacsweers.metro.Inject

/**
 * Keeps AAPS in foreground state, so it won't be terminated by Android nor get restricted by the background execution limits
 */
class DummyService : MetroService() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var notificationHolder: NotificationHolder

    // App exit is the only thing this listens for, and the service dies with it - so an IO scope
    // cancelled in onDestroy, matching what the CompositeDisposable did.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    inner class LocalBinder : Binder() {

        fun getService(): DummyService = this@DummyService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        try {
            aapsLogger.debug("Starting DummyService with ID ${notificationHolder.notificationID} notification ${notificationHolder.notification}")
            startForeground(notificationHolder.notificationID, notificationHolder.notification)
        } catch (e: Exception) {
            startForeground(4711, Notification())
        }
        rxBus.toFlow(EventAppExit::class)
            .collectResilient(scope, aapsLogger, LTag.CORE, start = CoroutineStart.UNDISPATCHED) {
                aapsLogger.debug(LTag.CORE, "EventAppExit received")
                stopSelf()
            }    }

    override fun onDestroy() {
        aapsLogger.debug(LTag.CORE, "onDestroy")
        scope.cancel()
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundSafe()
        return START_STICKY
    }

    private fun startForegroundSafe() {
        try {
            aapsLogger.debug("Starting DummyService with ID ${notificationHolder.notificationID}")
            
            // Must match plugins/main AndroidManifest: DummyService uses foregroundServiceType "specialUse".
            // Using DATA_SYNC here caused InvalidForegroundServiceTypeException / FGS failure on API 34+.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    notificationHolder.notificationID,
                    notificationHolder.notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(notificationHolder.notificationID, notificationHolder.notification)
            }
        } catch (se: SecurityException) {
            aapsLogger.error(LTag.CORE, "❌ CRITICAL: Failed to start Foreground Service due to missing permissions (Android 14+). Service will stop.", se)
            // Do NOT retry. Retrying causes the crash loop.
            // Just stop this service to let the App UI survive and ask for permissions.
            stopSelf()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error starting FGS, retrying with fallback", e)
            try {
                startForeground(4711, Notification())
            } catch (e2: Exception) {
                aapsLogger.error(LTag.CORE, "Fallback FGS failed too. Giving up.", e2)
                stopSelf()
            }
        }
    }
}
