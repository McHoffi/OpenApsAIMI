package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.overview.OverviewAndroid
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.ui.UiInteractionAndroid
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.ui.compose.icons.library.unused.IcPluginOverview
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.overview.OverviewDataImpl
import app.aaps.plugins.main.general.overview.OverviewEntryFragment
import app.aaps.plugins.main.general.overview.boost.BoostOverviewFragment
import app.aaps.plugins.main.general.overview.boost.BoostOverviewV2Fragment
import app.aaps.plugins.main.general.overview.keys.OverviewStringKey
import app.aaps.shared.impl.rx.bus.RxBusImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.serialization.json.JsonObject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.IntKey as MetroIntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.Inject

@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@MetroIntKey(20)
@SingleIn(AppScope::class)
class OverviewPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    preferences: Preferences,
    private val rxBus: RxBus,
    private val overviewData: OverviewDataImpl,
    private val overviewMenus: OverviewMenus,
    private val context: Context,
    private val constraintsChecker: ConstraintsChecker,
    private val uiInteraction: UiInteractionAndroid,
    private val nsSettingStatus: NSSettingsStatus,
    private val config: Config,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    // PluginBase holds this as a property for us, so it is only passed on here.
    notificationManager: NotificationManager,
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(OverviewEntryFragment::class.qualifiedName)
        .alwaysVisible(true)
        .enforce(EnforcedState.Enabled)
        .simpleModePosition(PluginDescription.Position.TAB)
        .icon(IcPluginOverview)
        .pluginName(TextRef.AndroidRes(app.aaps.core.ui.R.string.overview))
        .description(TextRef.AndroidRes(R.string.description_overview)),
    ownPreferences = OverviewStringKey.entries,
    aapsLogger, rh, preferences, notificationManager
), OverviewAndroid {

    /** Runs the bus subscriptions. [onStop] cancels them, [onStart] can subscribe again. */
    private val subscriptionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val overviewBus = RxBusImpl(aapsLogger)

    override suspend fun onStart() {
        super.onStart()
        overviewMenus.loadGraphConfig()
        overviewData.initRange()

        rxBus.toFlow(EventIobCalculationProgress::class)
            .collectResilient(subscriptionScope, aapsLogger, LTag.CORE, start = CoroutineStart.UNDISPATCHED) { event ->
                overviewData.calcProgressPct = event.finalPercent
                overviewBus.send(EventUpdateOverviewCalcProgress("EventIobCalculationProgress"))
            }
        rxBus.toFlow(EventPumpStatusChanged::class)
            .collectResilient(subscriptionScope, aapsLogger, LTag.CORE, start = CoroutineStart.UNDISPATCHED) { event ->
                overviewData.pumpStatus = rh.gs(event.getStatus())
            }
    }

    override suspend fun onStop() {
        subscriptionScope.coroutineContext.cancelChildren()
        super.onStop()
    }


    @SuppressLint("SetTextI18n")
    override fun setVersionView(view: TextView) {
        if (config.APS || config.PUMPCONTROL) {
            view.text = "${config.VERSION_NAME} (${config.BUILD_VERSION.substringAfterLast("-")})"
            if (config.COMMITTED) {
                view.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.omniGrayColor))
                view.alpha = 1.0f
            } else if (preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME) != 0L) {
                view.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.metadataTextWarningColor))
            } else {
                view.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.urgentColor))
            }
        } else view.text = ""
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "overview_settings",
        titleResId = app.aaps.core.ui.R.string.overview,
        items = listOf(
            PreferenceSubScreenDef(
                key = "overview_buttons_settings",
                titleResId = R.string.overview_buttons_selection,
                items = listOf(
                    BooleanKey.OverviewShowTreatmentButton,
                    BooleanKey.OverviewShowWizardButton,
                    BooleanKey.OverviewShowInsulinButton,
                    DoubleKey.OverviewInsulinButtonIncrement1,
                    DoubleKey.OverviewInsulinButtonIncrement2,
                    DoubleKey.OverviewInsulinButtonIncrement3,
                    BooleanKey.OverviewShowCarbsButton,
                    IntKey.OverviewCarbsButtonIncrement1,
                    IntKey.OverviewCarbsButtonIncrement2,
                    IntKey.OverviewCarbsButtonIncrement3,
                    BooleanKey.OverviewShowCgmButton,
                    BooleanKey.OverviewShowCalibrationButton
                )
            ),
            PreferenceSubScreenDef(
                key = "prime_fill_settings",
                titleResId = app.aaps.core.ui.R.string.prime_fill,
                items = listOf(
                    DoubleKey.ActionsFillButton1,
                    DoubleKey.ActionsFillButton2,
                    DoubleKey.ActionsFillButton3
                )
            ),
            PreferenceSubScreenDef(
                key = "statuslights_overview_advanced",
                titleResId = app.aaps.core.ui.R.string.statuslights,
                items = listOf(
                    IntKey.OverviewCageWarning,
                    IntKey.OverviewCageCritical,
                    IntKey.OverviewIageWarning,
                    IntKey.OverviewIageCritical,
                    IntKey.OverviewSageWarning,
                    IntKey.OverviewSageCritical,
                    IntKey.OverviewSbatWarning,
                    IntKey.OverviewSbatCritical,
                    IntKey.OverviewResWarning,
                    IntKey.OverviewResCritical,
                    IntKey.OverviewBattWarning,
                    IntKey.OverviewBattCritical,
                    IntKey.OverviewBageWarning,
                    IntKey.OverviewBageCritical
                )
            ),
            IntKey.OverviewBolusPercentage,
            IntKey.OverviewResetBolusPercentageTime,
            BooleanKey.OverviewUseBolusAdvisor,
            BooleanKey.OverviewUseBolusReminder,
            PreferenceSubScreenDef(
                key = "overview_advanced_settings",
                titleResId = app.aaps.core.ui.R.string.advanced_settings_title,
                items = listOf(BooleanKey.OverviewUseSuperBolus)
            )
        ),
        icon = pluginDescription.icon
    )

    override fun applyStatusLightsFromNs(context: Context?) {
        if (context != null) uiInteraction.showOkCancelDialog(context = context, title = app.aaps.core.ui.R.string.statuslights, message = app.aaps.core.ui.R.string.copy_existing_values, ok = { applyStatusLightsFromNsExec() })
        else applyStatusLightsFromNsExec()
    }

    fun applyStatusLightsFromNsExec() {
        val cageWarn = nsSettingStatus.getExtendedWarnValue("cage", "warn")?.toInt()
        val cageCritical = nsSettingStatus.getExtendedWarnValue("cage", "urgent")?.toInt()
        val iageWarn = nsSettingStatus.getExtendedWarnValue("iage", "warn")?.toInt()
        val iageCritical = nsSettingStatus.getExtendedWarnValue("iage", "urgent")?.toInt()
        val sageWarn = nsSettingStatus.getExtendedWarnValue("sage", "warn")?.toInt()
        val sageCritical = nsSettingStatus.getExtendedWarnValue("sage", "urgent")?.toInt()
        val bageWarn = nsSettingStatus.getExtendedWarnValue("bage", "warn")?.toInt()
        val bageCritical = nsSettingStatus.getExtendedWarnValue("bage", "urgent")?.toInt()
        cageWarn?.let { preferences.put(IntKey.OverviewCageWarning, it) }
        cageCritical?.let { preferences.put(IntKey.OverviewCageCritical, it) }
        iageWarn?.let { preferences.put(IntKey.OverviewIageWarning, it) }
        iageCritical?.let { preferences.put(IntKey.OverviewIageCritical, it) }
        sageWarn?.let { preferences.put(IntKey.OverviewSageWarning, it) }
        sageCritical?.let { preferences.put(IntKey.OverviewSageCritical, it) }
        bageWarn?.let { preferences.put(IntKey.OverviewBageWarning, it) }
        bageCritical?.let { preferences.put(IntKey.OverviewBageCritical, it) }
        uel.log(Action.NS_SETTINGS_COPIED, Sources.NSClient)
    }

}
