package app.aaps.implementations

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.ComposeMainActivity
import app.aaps.appshell.navigation.AppRoute
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.ui.UiInteractionAndroid
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.ui.alarmSoundFor
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.dialogs.AlertDialogs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

// Bound twice on purpose: `UiInteraction` is what common code injects, and `UiInteractionAndroid`
// is the same instance seen through the Android-only members. Code that needs the dialogs or the
// navigation helpers injects the Android type directly instead of casting at every call site.
@ContributesBinding(AppScope::class, binding = binding<UiInteraction>())
@ContributesBinding(AppScope::class, binding = binding<UiInteractionAndroid>())
@SingleIn(AppScope::class)
@Inject
class UiInteractionImpl(
    private val context: Context,
    rxBus: RxBus,
    private val preferences: Preferences,
    private val alarmNotificationManager: AlarmNotificationManager,
    private val alarmSoundPlayer: AlarmSoundPlayer,
    private val notificationManager: () -> NotificationManager,
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val config: Config,
    @ApplicationScope private val appScope: CoroutineScope
) : UiInteractionAndroid {

    private val alertDialogs: AlertDialogs = AlertDialogs(preferences, rxBus)

    override val mainActivity: KClass<*> = ComposeMainActivity::class
    override val errorHelperActivity: KClass<*> = ErrorActivity::class

    override fun runAlarm(status: String, title: String, sound: AlarmSound?) {
        // Persist the error as an announcement at fire time — gated by the NS-announcement
        // preference + APS build. Done here (not in ErrorActivity) so the record is written for
        // every alarm with the true trigger time, regardless of whether/how it is later
        // acknowledged (phone activity, Wear mute, OS-trimmed notification, or never opened).
        if (config.APS)
            appScope.launch {
                persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = TE.asAnnouncement(status),
                    action = Action.CAREPORTAL,
                    source = Sources.Aaps,
                    note = status,
                    listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
                )
            }

        // ProcessLifecycleOwner.currentState requires main-thread access (officially @MainThread).
        // BLE callbacks, RxJava workers, and coroutine non-main dispatchers all call runAlarm
        // from non-main threads. From those contexts we skip the foreground-direct optimization
        // entirely and use the FSI path, which is safe from any thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            aapsLogger.debug(LTag.CORE, "runAlarm (off-main → FSI): $title - $status (sound=$sound)")
            alarmNotificationManager.postFullScreenAlarm(status = status, title = title, sound = sound)
            return
        }

        if (isAppInForeground()) {
            // Foreground path — launch the activity directly. No notification needed:
            //   • Avoids channel-sound vs activity-ramp conflict.
            //   • Activity opens instantly, owns ramped audio from 0.
            //   • Works because the caller's process is already foreground (Android's
            //     background-activity-start restriction does not apply).
            aapsLogger.debug(LTag.CORE, "runAlarm (foreground direct): $title - $status (sound=$sound)")
            val intent = Intent(context, errorHelperActivity.java).apply {
                putExtra(AlarmIntent.EXTRA_SOUND, sound?.name)
                putExtra(AlarmIntent.EXTRA_STATUS, status)
                putExtra(AlarmIntent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Defensive: if the activity start is rejected for any reason, fall back to
                // the FSI notification path so the alert is never silently lost.
                aapsLogger.error(LTag.CORE, "runAlarm: direct startActivity failed, falling back to FSI", ex)
                postFsiFallback(status, title, sound)
            }
        } else {
            // Background path — the full-screen alarm ("FSI" in the names here, although it no
            // longer uses a full-screen intent): a notification with channel sound plus the looping
            // alarm sound, and a setAlarmClock screen wake that brings up the activity when exact
            // alarms are allowed.
            aapsLogger.debug(LTag.CORE, "runAlarm (background via FSI): $title - $status (sound=$sound)")
            alarmNotificationManager.postFullScreenAlarm(status = status, title = title, sound = sound)
        }
    }


    override fun postNotificationSoundAlarm(notificationKey: Int, @RawRes soundId: Int, title: String, body: String, urgent: Boolean) {
        alarmNotificationManager.postSilentAlarmNotification(
            notificationKey = notificationKey,
            title = title,
            body = body,
            urgent = urgent
        )
        if (soundId != 0) {
            // The player takes an AlarmSound now, and a raw id is the only thing this call still
            // carries. An id we do not know plays nothing.
            alarmSoundFor(soundId)?.let { alarmSoundPlayer.play(it, AlarmSoundPlayer.OWNER_INTERNAL) }
        }
    }

    override fun cancelNotificationSoundAlarm(notificationKey: Int) {
        alarmNotificationManager.cancelSoundAlarm(notificationKey)
        alarmSoundPlayer.stop(AlarmSoundPlayer.OWNER_INTERNAL)
    }

    override fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "stopAlarm: $reason")
        // Route through the registry owner so all audible alarms are actually silenced: clears the
        // internal AlarmSoundPlayer (Wear snooze used to only cancel the system notification, leaving
        // the ramping audio playing), stops the full-screen audio, and cancels the notifications.
        notificationManager().muteAllAlarms()
    }

    override fun showOkDialog(context: Context, title: String, message: String, onFinish: (() -> Unit)?) {
        alertDialogs.showOkDialog(context, title, message, onFinish)
    }

    override fun showOkDialog(context: Context, title: Int, message: Int, onFinish: (() -> Unit)?) {
        alertDialogs.showOkDialog(context, title, message, onFinish)
    }

    override fun showOkCancelDialog(context: Context, title: Int, message: Int, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, ok, cancel, icon)
    }

    override fun showOkCancelDialog(context: Context, title: String, message: String, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, ok, cancel, icon)
    }

    override fun showOkCancelDialog(context: Context, title: String, message: String, secondMessage: String, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, secondMessage, ok, cancel, icon)
    }

    override fun showOkCancelDialogWithCarbs(context: Context, title: String, message: String, ok: ((carbsText: String) -> Unit)?, cancel: (() -> Unit)?) {
        alertDialogs.showOkCancelDialogWithCarbs(context, title, message, ok, cancel)
    }

    override fun openRunningModeScreen(activity: FragmentActivity) {
        activity.startActivity(Intent(activity, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(ComposeMainActivity.EXTRA_NAVIGATE_ROUTE, AppRoute.RunningMode.route)
        })
    }

    override fun openInsulinScreen(activity: FragmentActivity) {
        activity.startActivity(Intent(activity, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(ComposeMainActivity.EXTRA_NAVIGATE_ROUTE, AppRoute.InsulinDialog.route)
        })
    }

    override fun openTempTargetManagementScreen(activity: FragmentActivity) {
        activity.startActivity(Intent(activity, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(ComposeMainActivity.EXTRA_NAVIGATE_ROUTE, AppRoute.TempTargetManagement.createRoute(ScreenMode.EDIT))
        })
    }

    override fun openProfileManagementScreen(activity: FragmentActivity) {
        activity.startActivity(Intent(activity, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(ComposeMainActivity.EXTRA_NAVIGATE_ROUTE, AppRoute.Profile.createRoute(ScreenMode.EDIT))
        })
    }

    override fun openProfileActivationScreen(activity: FragmentActivity, profileIndex: Int) {
        activity.startActivity(Intent(activity, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(ComposeMainActivity.EXTRA_NAVIGATE_ROUTE, AppRoute.ProfileActivation.createRoute(profileIndex))
        })
    }

    override fun openComposeMainAtRoute(context: Context, navRoute: String) {
        context.startActivity(Intent(context, ComposeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(ComposeMainActivity.EXTRA_NAVIGATE_ROUTE, navRoute)
        })
    }

    /**
     * Posts an FSI alarm notification with an additional last-resort Toast safety net.
     * If `POST_NOTIFICATIONS` is also revoked, `postFullScreenAlarm`'s catch block already
     * logs the failure but the alarm is otherwise lost — the Toast provides at least a
     * visible signal that something tried to alarm. Best-effort; Toast can also fail (e.g.
     * if a system overlay permission is denied) but it costs nothing to try.
     */
    private fun postFsiFallback(status: String, title: String, sound: AlarmSound?) {
        alarmNotificationManager.postFullScreenAlarm(status = status, title = title, sound = sound)
        // Toast must be created on the main thread (we are — runAlarm guards above).
        runCatching {
            Toast.makeText(context, "ALARM: $title — $status", Toast.LENGTH_LONG).show()
        }.onFailure {
            aapsLogger.error(LTag.CORE, "runAlarm: Toast fallback also failed; alarm not user-visible", it)
        }
    }

    /**
     * True when the AAPS process has at least one STARTED activity — meaning Android's
     * background-activity-start restriction does not apply and we can [Context.startActivity]
     * without going through a notification PendingIntent.
     *
     * **Must only be called from the main thread.** `Lifecycle.currentState` is officially
     * `@MainThread`; callers are guarded in [runAlarm].
     */
    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
