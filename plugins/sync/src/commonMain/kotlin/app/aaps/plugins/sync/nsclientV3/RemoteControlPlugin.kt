package app.aaps.plugins.sync.nsclientV3

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.plugins.sync.SyncStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.IntKey as MetroIntKey
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Remote Control Plugin for NSClient Parent App.
 * Provides secure interface for parents to send AIMI commands remotely.
 * Only visible in AAPSClient (not in AAPS main app).
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@MetroIntKey(315)
@SingleIn(AppScope::class)
class RemoteControlPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    private val config: Config,
    notificationManager: NotificationManager
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        // The fragment itself is Android only (it reads an XML layout), so its name is written out
        // here as a plain string.
        .fragmentClass("app.aaps.plugins.sync.nsclientV3.RemoteControlFragment")
        .icon(Icons.Default.Home)
        .pluginName(SyncStrings.remote_control_title)
        .showInList { config.AAPSCLIENT }  // Only show in AAPSClient
        .enforceEnabledOnlyWhen { config.AAPSCLIENT }  // Only enable in AAPSClient
        .description(SyncStrings.remote_control_description),
    aapsLogger, rh, notificationManager
)
