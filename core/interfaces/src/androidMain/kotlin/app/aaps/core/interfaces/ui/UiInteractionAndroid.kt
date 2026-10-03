package app.aaps.core.interfaces.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.R

/**
 * The Android-only half of `UiInteraction`.
 *
 * Upstream removed these members when the old XML screens were replaced by Compose. The fork still
 * uses them in its Android UI (old overview screen, dashboard shell, in-app notification list), so
 * they live here instead of in commonMain. The Android implementation is bound to both interfaces
 * with a repeated `@ContributesBinding`, so a caller that needs these members injects this type
 * directly while common code keeps injecting `UiInteraction`.
 */
interface UiInteractionAndroid : UiInteraction {

    /**
     * Posts a sound-bearing notification for an in-app notification (overview/dashboard list).
     */
    fun postNotificationSoundAlarm(notificationKey: Int, @RawRes soundId: Int, title: String, body: String, urgent: Boolean)

    /**
     * Cancels the sound notification for a single in-app notification key without stopping other active alarms.
     */
    fun cancelNotificationSoundAlarm(notificationKey: Int)

    /**
     * Displays a simple alert dialog with a title, a message, and an OK button.
     *
     * @param onFinish runs on the UI thread when the button is clicked or the dialog is dismissed.
     */
    fun showOkDialog(context: Context, title: String, message: String, onFinish: (() -> Unit)? = null)

    /** @see showOkDialog */
    fun showOkDialog(context: Context, @StringRes title: Int, @StringRes message: Int, onFinish: (() -> Unit)? = null)

    /**
     * Displays a confirmation dialog with a title, a message, and OK/Cancel buttons.
     *
     * @param ok runs on the UI thread when the OK button is clicked.
     * @param cancel runs on the UI thread when the Cancel button is clicked or the dialog is dismissed.
     * @param icon optional icon for the dialog.
     */
    fun showOkCancelDialog(context: Context, @StringRes title: Int = R.string.confirmation, @StringRes message: Int, ok: (() -> Unit)?, cancel: (() -> Unit)? = null, @DrawableRes icon: Int? = null)

    /** @see showOkCancelDialog */
    fun showOkCancelDialog(context: Context, title: String = context.getString(R.string.confirmation), message: String, ok: (() -> Unit)?, cancel: (() -> Unit)? = null, @DrawableRes icon: Int? = null)

    /**
     * Shows an alert dialog with a title, two messages, a custom icon, and OK/Cancel buttons.
     *
     * @param secondMessage shown under the first message, in the accent color.
     */
    fun showOkCancelDialog(context: Context, title: String = context.getString(R.string.confirmation), message: String, secondMessage: String, ok: (() -> Unit)?, cancel: (() -> Unit)? = null, @DrawableRes icon: Int? = null)

    /**
     * OK/Cancel confirm with an optional meal carb field under the message.
     *
     * @param ok runs on the UI thread when the OK button is clicked. It gets the raw carb text
     * the user typed, empty when the field was left blank. The dialog does not parse the text:
     * an invalid amount must not block the action the dialog is about.
     * @param cancel runs on the UI thread when the Cancel button is clicked or the dialog is dismissed.
     */
    fun showOkCancelDialogWithCarbs(context: Context, title: String = context.getString(R.string.confirmation), message: String, ok: ((carbsText: String) -> Unit)?, cancel: (() -> Unit)? = null)

    /** Opens running mode management in the Compose-based main activity. */
    fun openRunningModeScreen(activity: FragmentActivity)

    /** Opens the insulin bolus screen in the Compose main activity. */
    fun openInsulinScreen(activity: FragmentActivity)

    /** Opens temp target management in the Compose main activity. */
    fun openTempTargetManagementScreen(activity: FragmentActivity)

    /** Opens profile management in the Compose main activity. */
    fun openProfileManagementScreen(activity: FragmentActivity)

    /** Opens the profile activation / switch flow in the Compose main activity. */
    fun openProfileActivationScreen(activity: FragmentActivity, profileIndex: Int = 0)

    /** Brings the main activity to front and requests navigation to the given route. */
    fun openComposeMainAtRoute(context: Context, navRoute: String)
}
