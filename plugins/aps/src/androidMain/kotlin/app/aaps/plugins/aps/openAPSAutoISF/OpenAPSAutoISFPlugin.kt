package app.aaps.plugins.aps.openAPSAutoISF

import android.content.Context
import android.content.Intent
import androidx.collection.LongSparseArray
import androidx.collection.forEach
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.AIV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.HR
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.LongKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.keys.interfaces.withClick
import app.aaps.core.keys.interfaces.withEntries
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.data.model.getPassedDurationToTimeInMinutes
import app.aaps.core.objects.extensions.plannedRemainingMinutes
import app.aaps.core.objects.extensions.target
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.compose.icons.IcPluginOpenAPS
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.ui.compose.preference.AdaptiveDoublePreferenceItem
import app.aaps.core.ui.compose.preference.AdaptiveIntPreferenceItem
import app.aaps.core.ui.compose.preference.AdaptiveSwitchPreferenceItem
// import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import app.aaps.plugins.aps.openAPSAutoISF.PhoneMovementDetector
import app.aaps.plugins.aps.openAPSAutoISF.advisor.AutoIsfProfileAdvisorActivity
import app.aaps.plugins.aps.openAPSAIMI.StepService
import app.aaps.plugins.aps.openAPSAIMI.physio.AIMIInsulinDecisionAdapterMTR
import app.aaps.plugins.aps.openAPSAIMI.sos.AIMIEmergencySosPermissionActivityMTR
import app.aaps.plugins.aps.openAPSAIMI.steps.UnifiedActivityProviderMTR
import app.aaps.plugins.aps.openAPSAIMI.steps.AIMIStepsManagerMTR
import app.aaps.plugins.aps.openAPSAIMI.keys.AimiStringKey
import app.aaps.plugins.aps.keys.ApsIntentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import java.util.Locale
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.IntKey as MetroIntKey
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import java.util.Calendar
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@SingleIn(AppScope::class)
@Inject
open class OpenAPSAutoISFPlugin(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintsChecker: ConstraintsChecker,
    // This plugin still needs Android resource ids (R.string literals), so it keeps the Android
    // resolver instead of the TextResolver the migrated plugins use.
    override val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val config: Config,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val hardLimits: HardLimits,
    preferences: Preferences,
    protected val dateUtil: DateUtil,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val bgQualityCheck: BgQualityCheck,
    notificationManager: NotificationManager,
    private val determineBasalAutoISF: DetermineBasalAutoISF,
    private val profiler: Profiler,
    private val glucoseStatusCalculatorAutoIsf: GlucoseStatusCalculatorAutoIsf,
    private val apsResultProvider: () -> APSResult,
    private val ch: ConcentrationHelper,
    private val automationStateService: AutomationStateInterface,
    private val unifiedActivityProvider: UnifiedActivityProviderMTR, // 🏃 MTR Activity Provider
    private val physioAdapter: AIMIInsulinDecisionAdapterMTR, // 🏥 MTR Physio Adapter
    private val stepsManager: AIMIStepsManagerMTR, // 🏃 MTR Steps Manager
    private val loopProvider: () -> Loop,
    // Needed to open the advisor and SOS permission screens from the preference list. Those rows are
    // IntentPreferenceKey entries carrying a withClick lambda, and only the plugin that builds the
    // list knows which Activity each one means.
    private val context: Context,
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.APS)
        .composeContent { plugin ->
            app.aaps.plugins.aps.compose.OpenAPSComposeContent(
                apsPlugin = plugin as APS,
                loop = loopProvider(),
                rxBus = rxBus,
                rh = rh,
                dateUtil = dateUtil
            )
        }
        .icon(IcPluginOpenAPS)
        .pluginName(ApsStrings.openaps_auto_isf)
        .preferencesVisibleInSimpleMode(false)
        // AAPSCLIENT: visible so a client can select the master's APS. The engineering/dev half is NOT
        // repeated here - the enforcement below carries it, and a plugin forced off is hidden by
        // PluginBase.showInList.
        .showInList { config.APS || config.AAPSCLIENT }
        // AutoISF is a development feature, so it cannot be turned on outside an engineering dev build -
        // not even by an imported settings file. Forced off only; inside such a build it stays selectable.
        .enforce(EnforcedState.Disabled) { !(config.isEngineeringMode() && config.isDev()) }
        .description(ApsStrings.description_auto_isf),
    ownPreferences = ApsIntentKey.entries,
    aapsLogger, rh, preferences, notificationManager
), APS, PluginConstraints {

    // last values
    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.AUTO_ISF
    override var lastAPSResult: APSResult? = null
    private var consoleError = mutableListOf<String>()
    private var consoleLog = mutableListOf<String>()
    val autoIsfVersion = "3.2.0-RB"
    val autoIsfWeights; get() = preferences.get(BooleanKey.ApsUseAutoIsfWeights)
    private val autoISF_max; get() = preferences.get(DoubleKey.ApsAutoIsfMax)
    private val autoISF_min; get() = preferences.get(DoubleKey.ApsAutoIsfMin)
    private val bgAccel_ISF_weight; get() = preferences.get(DoubleKey.ApsAutoIsfBgAccelWeight)
    private val bgBrake_ISF_weight; get() = preferences.get(DoubleKey.ApsAutoIsfBgBrakeWeight)
    private val pp_ISF_weight; get() = preferences.get(DoubleKey.ApsAutoIsfPpWeight)
    private val lower_ISFrange_weight; get() = preferences.get(DoubleKey.ApsAutoIsfLowBgWeight)
    private val higher_ISFrange_weight; get() = preferences.get(DoubleKey.ApsAutoIsfHighBgWeight)
    private val dura_ISF_weight; get() = preferences.get(DoubleKey.ApsAutoIsfDuraWeight)
    private val smb_delivery_ratio; get() = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatio)
    private val smb_delivery_ratio_min; get() = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMin)
    private val smb_delivery_ratio_max; get() = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax)
    private val smb_delivery_ratio_bg_range
        get() = if (preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange) < 10.0) preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange) * Constants.MMOLL_TO_MGDL else preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange)
    val smbMaxRangeExtension; get() = preferences.get(DoubleKey.ApsAutoIsfSmbMaxRangeExtension)
    private val enableSMB_EvenOn_OddOff_always; get() = preferences.get(BooleanKey.ApsAutoIsfSmbOnEvenTarget) // for profile target
    val iobThresholdPercent; get() = preferences.get(IntKey.ApsAutoIsfIobThPercent)
    private val exerciseMode; get() = SMBDefaults.exercise_mode
    private val highTemptargetRaisesSensitivity; get() = preferences.get(BooleanKey.ApsAutoIsfHighTtRaisesSens)
    val mgdlHalfBasalExerciseTarget;  get() = preferences.get(IntKey.ApsAutoIsfHalfBasalExerciseTarget) * if (profileFunction.getUnits() == GlucoseUnit.MMOL) GlucoseUnit.MMOLL_TO_MGDL else 1.0
    val normalTarget = Constants.NORMAL_TARGET_MGDL
    val calibrationDuration = preferences.get(IntKey.FslCalibrationDuration)
    private val minutesClass; get() = if (preferences.get(IntKey.ApsMaxSmbFrequency) == 1) 6L else 30L  // ga-zelle: later get correct 1 min CGM flag from glucoseStatus ? ... or from apsResults?
    // create array for key AutoISF results with defaults
    var autoIsfValues = AIV(
        timestamp = 0L,
        acceIsf = 1.0,
        bgIsf =  1.0,
        ppIsf = 1.0,
        driftIsf = 1.0,
        duraIsf = 1.0,
        finalIsf = 1.0,
        iobThEffective = 0.0
    )
    // Activity detection (steps)
    private var recentSteps5Minutes = 0
    private var recentSteps10Minutes = 0
    private var recentSteps15Minutes = 0
    private var recentSteps30Minutes = 0
    private var recentSteps60Minutes = 0

    // Last activity-monitor result from invoke(); read by autoISF() (also from the cached
    // getIsfMgdl() path) so we don't smuggle transient state through SharedPreferences.
    private var lastActivityRatio = 1.0
    private var lastStepActivityDetected = false
    private var lastStepInactivityDetected = false

    private val heartRatesSnapshotRef = AtomicReference<List<HR>>(emptyList())
    private val heartRatesRefreshInFlight = AtomicBoolean(false)

    private val stepsSnapshotRef = AtomicReference<List<SC>>(emptyList())
    private val stepsRefreshInFlight = AtomicBoolean(false)
    private val determineIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val phone_moved; get() = PhoneMovementDetector.phoneMoved()

    override suspend fun onStart() {
        super.onStart()
        var count = 0
        val apsResults = persistenceLayer.getApsResults(dateUtil.now() - T.days(1).msecs(), dateUtil.now())
        apsResults.forEach {
            val glucose = it.glucoseStatus?.glucose ?: return@forEach
            val variableSens = it.variableSens ?: return@forEach
            val timestamp = it.date
            val key = timestamp - timestamp % T.mins(minutesClass).msecs() + glucose.toLong()
            if (variableSens > 0) synchronized(autoIsfCache) { autoIsfCache.put(key, variableSens) }
            count++
        }
        aapsLogger.debug(LTag.APS, "Loaded $count variable sensitivity values from database")

        // 🏃 Start MTR Steps Manager (shared with AIMI — ref-counted)
        try {
            stepsManager.ensureStarted()
            aapsLogger.info(LTag.APS, "✅ AutoISF: MTR Steps Manager active")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "❌ AutoISF: Failed to start MTR Steps Manager", e)
        }
    }

    override suspend fun onStop() {
        // 🏃 Release MTR Steps Manager reference
        try {
            stepsManager.ensureStopped()
            aapsLogger.info(LTag.APS, "🛑 AutoISF: MTR Steps Manager ref released")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Error releasing MTR Steps Manager", e)
        }
        super.onStop()
    }

    override fun usingDynamicIsf() = true //: Boolean = preferences.get(BooleanKey.ApsUseAutoIsf)

    override fun getIsfMgdl(profile: Profile, caller: String): Double? {
        val start = dateUtil.now()
        val multiplier = (profile as ProfileSealed.EPS).value.originalPercentage / 100.0
        val sensitivity = runBlocking { calculateVariableIsf(start) }
        if (sensitivity.second == null && caller == "OpenAPSSMBPlugin")
            notificationManager.post(
                NotificationId.DYN_ISF_FALLBACK,
                ApsStrings.fallback_to_isf_no_tdd.withArgs(sensitivity.first),
                level = NotificationLevel.INFO, date = start, validTo = dateUtil.now() + T.mins(1).msecs()
            )
        else
            notificationManager.dismiss(NotificationId.DYN_ISF_FALLBACK)
        profiler.log(LTag.APS, String.format(Locale.getDefault(), "getIsfMgdl() %s %f %s %s", sensitivity.first, sensitivity.second, dateUtil.dateAndTimeAndSecondsString(start), caller), start)
        return sensitivity.second?.let { it * multiplier }
    }

    override fun getAverageIsfMgdl(timestamp: Long, caller: String): Double? {
        var count = 0
        var sum = 0.0
        val start = timestamp - T.hours(24).msecs()
        synchronized(autoIsfCache) {
            autoIsfCache.forEach { key, value ->
                if (key in start..timestamp) {
                    count++
                    sum += value
                }
            }
        }
        val sensitivity = if (count == 0) null else sum / count
        //aapsLogger.debug(LTag.APS, "getAverageIsfMgdl() $sensitivity from $count values ${dateUtil.dateAndTimeAndSecondsString(timestamp)} $caller")
        return sensitivity
    }

    override fun getSensitivityOverviewString(): String? = null // placeholder for Auto ISF Detailed information for overview

    private val autoIsfCache = LongSparseArray<Double>()

    private suspend fun calculateVariableIsf(timestamp: Long): Pair<String, Double?> {
        val profile = profileFunction.getProfile(timestamp) ?: return Pair("OFF", null)
        val glucose = glucoseStatusProvider.glucoseStatusData?.glucose ?: return Pair("GLUC", null)
        // Round down to minutesClass min and use it as a key for caching
        // Add BG to key as it affects calculation
        val key = timestamp - timestamp % T.mins(minutesClass).msecs() + glucose.toLong()
        val sensitivity = autoISF(profile)
        if (sensitivity > 0) {
            // can default to 0, e.g. for the first 2-3 loops in a virgin setup
            aapsLogger.debug("calculateVariableIsf CALC ${dateUtil.dateAndTimeAndSecondsString(timestamp)} $sensitivity")
            synchronized(autoIsfCache) {
                autoIsfCache.put(key, sensitivity)
                if (autoIsfCache.size() > 1000) autoIsfCache.clear()
            }
        }
        // this return is mandatory, otherwise it messed up the AutoISF algo.
        return Pair("OFF", null)
    }

    override suspend fun invoke(initiator: String, tempBasalFallback: Boolean) = withContext(Dispatchers.Default) {
        aapsLogger.debug(LTag.APS, "invoke from $initiator tempBasalFallback: $tempBasalFallback")
        lastAPSResult = null
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        if (profile == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(app.aaps.core.ui.R.string.no_profile_set)))
            aapsLogger.debug(LTag.APS, rh.gs(app.aaps.core.ui.R.string.no_profile_set))
            return@withContext
        }
        if (!isEnabled()) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_disabled)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_disabled))
            return@withContext
        }
        if (glucoseStatus == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_no_glucose_data)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_no_glucose_data))
            return@withContext
        }

        val inputConstraints = ConstraintObject(0.0, aapsLogger) // fake. only for collecting all results

        if (!hardLimits.checkHardLimits(profile.iCfg.dia, app.aaps.core.interfaces.R.string.profile_dia, hardLimits.diaRange().start, hardLimits.diaRange().endInclusive)) return@withContext
        if (!hardLimits.checkHardLimits(
                profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()),
                app.aaps.core.interfaces.R.string.profile_carbs_ratio_value,
                hardLimits.icRange().start,
                hardLimits.icRange().endInclusive
            )
        ) return@withContext
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl("OpenAPSAutoISFPlugin"), app.aaps.core.interfaces.R.string.profile_sensitivity_value, HardLimits.LIMIT_ISF.start, HardLimits.LIMIT_ISF.endInclusive)) return@withContext
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), app.aaps.core.ui.R.string.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return@withContext
        if (!hardLimits.checkHardLimits(ch.fromPump(pump.baseBasalRate), app.aaps.core.ui.R.string.current_basal_value, 0.01, hardLimits.maxBasal())) return@withContext

        // End of check, start gathering data

        val autoIsfMode = usingDynamicIsf()  // preferences.get(BooleanKey.ApsUseAutoIsf)
        val smbEnabled = preferences.get(BooleanKey.ApsUseSmb)
        val advancedFiltering = constraintsChecker.isAdvancedFilteringEnabled().also { inputConstraints.copyReasons(it) }.value()

        val now = dateUtil.now()
        val tb = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val currentTemp = CurrentTemp(
            duration = tb?.plannedRemainingMinutes ?: 0,
            rate = tb?.convertedToAbsolute(now, profile) ?: 0.0,
            minutesrunning = tb?.getPassedDurationToTimeInMinutes(now)
        )
        var minBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetLowMgdl(), 0.1), app.aaps.core.interfaces.R.string.profile_low_target, HardLimits.LIMIT_MIN_BG)
        var maxBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetHighMgdl(), 0.1), app.aaps.core.interfaces.R.string.profile_high_target, HardLimits.LIMIT_MAX_BG)
        var targetBg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TARGET_BG)
        var isTempTarget = false
        persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tempTarget ->
            isTempTarget = true
            minBg = hardLimits.verifyHardLimits(tempTarget.lowTarget, app.aaps.core.ui.R.string.temp_target_low_target, HardLimits.LIMIT_TEMP_MIN_BG)
            maxBg = hardLimits.verifyHardLimits(tempTarget.highTarget, app.aaps.core.ui.R.string.temp_target_high_target, HardLimits.LIMIT_TEMP_MAX_BG)
            targetBg = hardLimits.verifyHardLimits(tempTarget.target(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TEMP_TARGET_BG)
        }
        // for key AutoISF results assign defaults
        autoIsfValues = AIV(
            timestamp = now,
            acceIsf = 1.0,
            bgIsf =  1.0,
            ppIsf = 1.0,
            driftIsf = 1.0,
            duraIsf = 1.0,
            finalIsf = 1.0,
            iobThEffective = 0.0
        )

        var autosensResult = AutosensResult()
        var variableSensitivity = profile.getProfileIsfMgdl()
        val sens = profile.getIsfMgdl("OpenAPSAutoISFPlugin")

        if (constraintsChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSAutoISFPlugin")
            if (autosensData == null) {
                rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openaps_no_as_data)))
                return@withContext
            }
            autosensResult = autosensData.autosensResult
        } else autosensResult.sensResult = "autosens disabled"
        val iobArray = iobCobCalculator.calculateIobArrayForSMB(autosensResult, preferences.get(BooleanKey.ApsAutoIsfHighTtRaisesSens), preferences.get(IntKey.ApsAutoIsfHalfBasalExerciseTarget), isTempTarget)
        val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
        val iobData = iobArray[0]
        val profile_percentage = if (profile is ProfileSealed.EPS) profile.value.originalPercentage else 100
        val microBolusAllowed = constraintsChecker.isSMBModeEnabled(ConstraintObject(tempBasalFallback.not(), aapsLogger)).also { inputConstraints.copyReasons(it) }.value()

        aapsLogger.debug(LTag.APS, "invoke found step counts 5m:$recentSteps5Minutes, 10m:$recentSteps10Minutes, 15m:$recentSteps15Minutes, 30m:$recentSteps30Minutes, 60m:$recentSteps60Minutes")
        consoleError.clear()
        consoleLog.clear()
        val calendar = Calendar.getInstance()
        val hour = max(1, calendar.get(Calendar.HOUR_OF_DAY))
        val activityRatio = activityMonitor(isTempTarget, glucoseStatus.glucose, targetBg, hour)
        if (consoleLog.size==0) "Activity Monitor skipped" else consoleLog[0]
        consoleLog.clear()
        var stepActivityDetected = false
        var stepInactivityDetected = false
        if (activityRatio < 1) { stepActivityDetected = true
        } else if (activityRatio>1)   { stepInactivityDetected = true}
        lastActivityRatio = activityRatio
        lastStepActivityDetected = stepActivityDetected
        lastStepInactivityDetected = stepInactivityDetected
        aapsLogger.debug(LTag.APS , "Activity Monitor: StepsActive $stepActivityDetected StepsInactive $stepInactivityDetected ")
        if (autoIsfMode) {
            consoleError = mutableListOf()
            consoleLog = mutableListOf()
            variableSensitivity = autoISF(profile)
        }
        val lastAppStart = preferences.get(LongKey.AppStart)
        val elapsedTimeSinceLastStart = (dateUtil.now() - lastAppStart).milliseconds.inWholeMinutes
        val ketoacidosisProtectionIob: Double = runBlocking(Dispatchers.IO) {
            iobCobCalculator.calculateIobFromBolus().iob +
                iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().basaliob
        }
        val oapsProfile = OapsProfileAutoIsf(
            dia = 0.0, // not used
            min_5m_carbimpact = 0.0, // not used
            max_iob = constraintsChecker.getMaxIOBAllowed().also { inputConstraints.copyReasons(it) }.value(),
            max_daily_basal = profile.getMaxDailyBasal(),
            max_basal = constraintsChecker.getMaxBasalAllowed(profile).also { inputConstraints.copyReasons(it) }.value(),
            min_bg = minBg,
            max_bg = maxBg,
            target_bg = targetBg,
            carb_ratio = profile.getIc(),
            sens = sens,
            autosens_adjust_targets = false, // not used
            max_daily_safety_multiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier),
            current_basal_safety_multiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier),
            lgsThreshold = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.ApsLgsThreshold)).toInt(),
            high_temptarget_raises_sensitivity = exerciseMode || highTemptargetRaisesSensitivity, //was false,
            low_temptarget_lowers_sensitivity = preferences.get(BooleanKey.ApsAutoIsfLowTtLowersSens), // was false,
            sensitivity_raises_target = preferences.get(BooleanKey.ApsSensitivityRaisesTarget),
            resistance_lowers_target = preferences.get(BooleanKey.ApsResistanceLowersTarget),
            adv_target_adjustments = SMBDefaults.adv_target_adjustments,
            exercise_mode = SMBDefaults.exercise_mode,
            half_basal_exercise_target = preferences.get(IntKey.ApsAutoIsfHalfBasalExerciseTarget),
            // mod activity mode
            activity_detection = preferences.get(BooleanKey.ActivityMonitorDetection),
            recent_steps_5_minutes  = recentSteps5Minutes,
            recent_steps_10_minutes = recentSteps10Minutes,
            recent_steps_15_minutes = recentSteps15Minutes,
            recent_steps_30_minutes = recentSteps30Minutes,
            recent_steps_60_minutes = recentSteps60Minutes,
            phone_moved = phone_moved,
            time_since_start = elapsedTimeSinceLastStart,
            now = calendar.get(Calendar.HOUR_OF_DAY),
            activity_ratio = activityRatio,
            steps_activity_detected = stepActivityDetected,
            steps_inactivity_detected = stepInactivityDetected,
            // end mod
            maxCOB = SMBDefaults.maxCOB,
            skip_neutral_temps = pump.setNeutralTempAtFullHour(),
            remainingCarbsCap = SMBDefaults.remainingCarbsCap,
            enableUAM = constraintsChecker.isUAMEnabled().also { inputConstraints.copyReasons(it) }.value(),
            A52_risk_enable = SMBDefaults.A52_risk_enable,
            SMBInterval = preferences.get(IntKey.ApsMaxSmbFrequency),
            enableSMB_with_COB = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithCob),
            enableSMB_with_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithLowTt),
            allowSMB_with_high_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithHighTt),
            enableSMB_always = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAlways) && advancedFiltering,
            enableSMB_after_carbs = smbEnabled && (advancedFiltering || preferences.get(BooleanKey.ApsUseSmbAfterCarbs)),
            maxSMBBasalMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb),
            maxUAMSMBBasalMinutes = preferences.get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb),
            bolus_increment = pump.pumpDescription.bolusStep,
            carbsReqThreshold = preferences.get(IntKey.ApsCarbsRequestThreshold),
            current_basal = ch.fromPump(activePlugin.activePump.baseBasalRate),
            temptargetSet = isTempTarget,
            autosens_max = preferences.get(DoubleKey.AutosensMax),
            out_units = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "mmol/L" else "mg/dl",
            variable_sens = variableSensitivity,
            autoISF_version = autoIsfVersion,
            enable_autoISF = autoIsfWeights,
            autoISF_max = autoISF_max,
            autoISF_min = autoISF_min,
            bgAccel_ISF_weight = bgAccel_ISF_weight,
            bgBrake_ISF_weight = bgBrake_ISF_weight,
            pp_ISF_weight = pp_ISF_weight,
            lower_ISFrange_weight = lower_ISFrange_weight,
            higher_ISFrange_weight = higher_ISFrange_weight,
            dura_ISF_weight = dura_ISF_weight,
            smb_delivery_ratio = smb_delivery_ratio,
            smb_delivery_ratio_min = smb_delivery_ratio_min,
            smb_delivery_ratio_max = smb_delivery_ratio_max,
            smb_delivery_ratio_bg_range = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange),   //smb_delivery_ratio_bg_range was always in mg/dL
            smb_max_range_extension = smbMaxRangeExtension,
            enableSMB_EvenOn_OddOff_always = enableSMB_EvenOn_OddOff_always,
            iob_threshold_percent = iobThresholdPercent,
            profile_percentage = profile_percentage,
            ketoacidosisProtection = preferences.get(BooleanKey.ApsKetoacidosisProtection),
            ketoacidosisProtectionStrategy = preferences.get(BooleanKey.ApsKetoacidosisProtectionStrategy),
            ketoacidosisProtectionBasal = preferences.get(IntKey.ApsKetoacidosisProtectionBasal),
            ketoacidosisProtectionIob = ketoacidosisProtectionIob
        )

        // Refuse to run the algorithm with degenerate ISF inputs — division by these would produce NaN/Infinity in
        // the result. carb_ratio feeds csf (sens/carb_ratio) and autosensResult.ratio becomes the non-autoISF
        // sensitivityRatio (sens = profile.sens/sensitivityRatio); a non-finite/≤0 value of any cascades to a NaN
        // carbsReq (round() crash). Mirrors the OpenAPSSMBPlugin guard.
        val invalidInputs = !oapsProfile.sens.isFinite() || oapsProfile.sens <= 0.0 ||
            !oapsProfile.carb_ratio.isFinite() || oapsProfile.carb_ratio <= 0.0 ||
            !autosensResult.ratio.isFinite() || autosensResult.ratio <= 0.0 ||
            (autoIsfMode && (!oapsProfile.variable_sens.isFinite() || oapsProfile.variable_sens <= 0.0))
        if (invalidInputs) {
            val msg = "OpenAPS AutoISF aborting: invalid ISF inputs " +
                "autoIsfMode=$autoIsfMode sens=${oapsProfile.sens} carb_ratio=${oapsProfile.carb_ratio} " +
                "autosensRatio=${autosensResult.ratio} variable_sens=${oapsProfile.variable_sens}"
            aapsLogger.error(LTag.APS, msg)
            rxBus.send(EventResetOpenAPSGui(msg))
            return@withContext
        }
        //done calculate exercise ratio
        var sensitivityRatio = 1.0
        // TODO eliminate
        val target_bg = (minBg + maxBg) / 2
        val exerciseModeActive = highTemptargetRaisesSensitivity && isTempTarget && target_bg > normalTarget
        val resistanceModeActive = oapsProfile.low_temptarget_lowers_sensitivity && isTempTarget && target_bg < normalTarget
        if ( exerciseModeActive || resistanceModeActive || stepActivityDetected || stepInactivityDetected ) {
            if (exerciseModeActive || resistanceModeActive) {
                // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
                // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
                //sensitivityRatio = 2/(2+(target_bg-normalTarget)/40);
                val resistanceMax = min(1.5, preferences.get(DoubleKey.AutosensMax))  // additional safety limit
                val c = (mgdlHalfBasalExerciseTarget - normalTarget)
                if (c * (c + target_bg - normalTarget) <= 0.0) {
                    sensitivityRatio = resistanceMax
                } else {
                    sensitivityRatio = c / (c + target_bg - normalTarget)
                    // limit sensitivityRatio to profile.autosens_max (1.2x by default)
                    sensitivityRatio = min(sensitivityRatio, resistanceMax)
                    sensitivityRatio = round(sensitivityRatio, 2)
                    //exerciseRatio = sensitivityRatio
                }
            } else {
                sensitivityRatio = activityRatio
            }
        }
        var iobTH_reduction_ratio = 1.0
        var use_iobTH = false
        if (iobThresholdPercent != 100) {
            iobTH_reduction_ratio = profile_percentage / 100.0 * sensitivityRatio
            use_iobTH = true
        }
        val iobTHtolerance = 130.0
        val iobTHvirtual = iobThresholdPercent * iobTHtolerance / 10000.0 * oapsProfile.max_iob * iobTH_reduction_ratio
        val loopWantedSmb = loop_smb(microBolusAllowed, oapsProfile, iobData.iob, use_iobTH, iobTHvirtual / iobTHtolerance * 100.0)
        val flatBGsDetected = bgQualityCheck.state == BgQualityCheck.State.FLAT
        val smbRatio = determine_varSMBratio(glucoseStatus.glucose.toInt(), target_bg, loopWantedSmb)

        val gson = Gson()
        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal AutoISF <<<")
        aapsLogger.debug(LTag.APS, "Glucose status:     $glucoseStatus")
        aapsLogger.debug(LTag.APS, "Current temp:       $currentTemp")
        aapsLogger.debug(LTag.APS, "IOB data:           ${iobArray.firstOrNull()?.iob}")
        aapsLogger.debug(LTag.APS, "Profile:            $oapsProfile")
        aapsLogger.debug(LTag.APS, "Autosens data:      $autosensResult")
        aapsLogger.debug(LTag.APS, "Meal data:          $mealData")
        aapsLogger.debug(LTag.APS, "MicroBolusAllowed:  $microBolusAllowed")
        aapsLogger.debug(LTag.APS, "flatBGsDetected:    $flatBGsDetected")
        aapsLogger.debug(LTag.APS, "AutoIsfMode:        $autoIsfMode")
        //aapsLogger.debug(LTag.APS, "AutoISF extras:     ${Json.encodeToString(OapsProfile.serializer(), oapsProfile)}")

        determineBasalAutoISF.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobArray,
            profile = oapsProfile,
            autosens_data = autosensResult,
            meal_data = mealData,
            microBolusAllowed = microBolusAllowed,
            currentTime = now,
            flatBGsDetected = flatBGsDetected,
            autoIsfMode = autoIsfMode,
            loop_wanted_smb = loopWantedSmb,
            profile_percentage = profile_percentage,
            smb_ratio = smbRatio,
            smb_max_range_extension = smbMaxRangeExtension,
            iob_threshold_percent = iobThresholdPercent,
            auto_isf_consoleError = consoleError,
            auto_isf_consoleLog = consoleLog
        ).also {
            val determineBasalResult = apsResultProvider().with(it)
            // Preserve input data
            determineBasalResult.inputConstraints = inputConstraints
            determineBasalResult.autosensResult = autosensResult
            determineBasalResult.iobData = iobArray
            determineBasalResult.glucoseStatus = glucoseStatus
            determineBasalResult.currentTemp = currentTemp
            determineBasalResult.oapsProfileAutoIsf = oapsProfile
            determineBasalResult.mealData = mealData
            lastAPSResult = determineBasalResult
            lastAPSRun = now
            aapsLogger.debug(LTag.APS, "Result: $it")
            rxBus.send(EventAPSCalculationFinished())
        }
        autoIsfValues.timestamp = now
        //aapsLogger.debug(LTag.APS, "autoIsfValues to write contains: $autoIsfValues")
        persistenceLayer.insertOrUpdateAutoIsfValues(autoIsfValues)
        //val autoIsfRecords = persistenceLayer.getAutoIsfValuesFromTime(now-100000L)
        //aapsLogger.debug(LTag.APS, "autoIsfValues records read contain: $autoIsfRecords")
        rxBus.send(EventOpenAPSUpdateGui())
    }

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = glucoseStatusCalculatorAutoIsf.getGlucoseStatusData(allowOldData)

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        value.set(false)
        return value
    }

    override suspend fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (isEnabled()) {
            val maxIobPref = preferences.get(DoubleKey.ApsSmbMaxIob)
            maxIob.setIfSmaller(maxIobPref, rh.gs(R.string.limiting_iob, maxIobPref, rh.gs(R.string.maxvalueinpreferences)), this)
            maxIob.setIfSmaller(hardLimits.maxIobSMB(), rh.gs(R.string.limiting_iob, hardLimits.maxIobSMB(), rh.gs(R.string.hardlimit)), this)
        }
        return maxIob
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        if (isEnabled()) {
            var maxBasal = preferences.get(DoubleKey.ApsMaxBasal)
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal()
                absoluteRate.addReason(rh.gs(R.string.increasing_max_basal), this)
            }
            absoluteRate.setIfSmaller(maxBasal, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxBasal, rh.gs(R.string.maxvalueinpreferences)), this)

            // Check percentRate but absolute rate too, because we know real current basal in pump
            val maxBasalMultiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.getBasal() * 100) / 100
            absoluteRate.setIfSmaller(
                maxFromBasalMultiplier,
                rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxFromBasalMultiplier, rh.gs(R.string.max_basal_multiplier)),
                this
            )
            val maxBasalFromDaily = preferences.get(DoubleKey.ApsMaxDailyMultiplier)
            val maxFromDaily = floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100
            absoluteRate.setIfSmaller(maxFromDaily, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxFromDaily, rh.gs(R.string.max_daily_basal_multiplier)), this)
        }
        return absoluteRate
    }

    override suspend fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseSmb)
        if (!enabled) value.set(false, rh.gs(R.string.smb_disabled_in_preferences), this)
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseUam)
        if (!enabled) value.set(false, rh.gs(R.string.uam_disabled_in_preferences), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseAutosens)
        if (!enabled) value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        return value
    }

    // Rounds value to 'digits' decimal places
    // different for negative numbers fun round(value: Double, digits: Int): Double = BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).toDouble()
    fun round(value: Double, digits: Int): Double {
        // Pass NaN AND ±Infinity through untouched - Math.round saturates at Long.MAX_VALUE, which would
        // turn an infinite value into a normal looking number and hide it from the invalidInputs check.
        if (!value.isFinite()) return value
        val scale = 10.0.pow(digits.toDouble())
        return Math.round(value * scale) / scale
    }

    fun convert_bg(value: Double): String =
        profileUtil.fromMgdlToStringInUnits(value).replace("-0.0", "0.0")

    fun convert_bg_to_units(value: Double, profile: OapsProfileAutoIsf): Double =
        if (profile.out_units == "mmol/L") value * Constants.MGDL_TO_MMOLL else value

    private fun heartRatesCached(now: Long): List<HR> {
        refreshHeartRatesAsync(now)
        return heartRatesSnapshotRef.get()
    }

    private fun refreshHeartRatesAsync(now: Long) {
        if (!heartRatesRefreshInFlight.compareAndSet(false, true)) return
        determineIoScope.launch {
            try {
                val start = now - 200 * 60 * 1000
                heartRatesSnapshotRef.set(persistenceLayer.getHeartRatesFromTimeToTime(start, now))
            } catch (_: Exception) {
                heartRatesSnapshotRef.set(emptyList())
            } finally {
                heartRatesRefreshInFlight.set(false)
            }
        }
    }

    fun activityMonitor(isTempTarget: Boolean, bg: Double, target_bg: Double, now: Int): Double
    {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        //update steps
        //SC records are written at 5-minute intervals. The loop also runs every 5 minutes.
        // When the loop is running, the latest record is always located either just exactly at the edge of the 5-minute window or outside of it.
        val now = System.currentTimeMillis()
        val timeMillis5 = now - 6 * 60 * 1000
        val timeMillis10 = now - 11 * 60 * 1000
        val timeMillis15 = now - 16 * 60 * 1000
        val timeMillis30 = now - 31 * 60 * 1000
        val timeMillis60 = now - 61 * 60 * 1000

        if (preferences.get(BooleanKey.ActivityMonitorUseSteps)) {
            val rawStepsCounts = stepsCountsCached(now)

            // 🏃 MTR source-mode filtering (shared with AIMI's ActivitySourceMode setting)
            val sourceMode = preferences.get(AimiStringKey.ActivitySourceMode)
            val allStepsCounts = when (sourceMode) {
                UnifiedActivityProviderMTR.MODE_PREFER_WEAR ->
                    rawStepsCounts.filter { UnifiedActivityProviderMTR.isWearDevice(it.device) }
                        .ifEmpty { rawStepsCounts.filter { UnifiedActivityProviderMTR.isGarminDevice(it.device) } }
                UnifiedActivityProviderMTR.MODE_HEALTH_CONNECT_ONLY ->
                    rawStepsCounts.filter { it.device == "HealthConnect" }
                UnifiedActivityProviderMTR.MODE_DISABLED ->
                    emptyList()
                else -> // MODE_AUTO_FALLBACK (default): Garmin > Wear > HC/Phone
                    rawStepsCounts.filter { UnifiedActivityProviderMTR.isGarminDevice(it.device) }
                        .ifEmpty {
                            rawStepsCounts.filter { UnifiedActivityProviderMTR.isWearDevice(it.device) }
                                .ifEmpty { rawStepsCounts.filter { it.device == "HealthConnect" || it.device == "PhoneSensor" } }
                        }
            }

            if (allStepsCounts.isNotEmpty()) {
                val lastSteps = allStepsCounts.maxByOrNull { it.timestamp }
                aapsLogger.debug(LTag.APS, "Activity Monitor: Found ${allStepsCounts.size} records (mode=$sourceMode). Last: ${lastSteps?.steps5min} steps @ ${java.util.Date(lastSteps?.timestamp ?: 0)}")
            } else {
                aapsLogger.debug(LTag.APS, "Activity Monitor: No records found in last 210 mins (mode=$sourceMode)")
            }

            val valid5 = allStepsCounts.filter { it.timestamp >= timeMillis5 }.maxByOrNull { it.timestamp }

            val fallbackRecord = if (valid5 == null) {
                allStepsCounts.filter { it.timestamp >= (now - 30 * 60 * 1000) }.maxByOrNull { it.timestamp }
            } else null

            aapsLogger.debug(LTag.APS, "Activity Monitor: fallback=${fallbackRecord?.timestamp} steps5=${fallbackRecord?.steps5min} recentSteps5=${valid5?.steps5min ?: fallbackRecord?.steps5min ?: 0}")

            recentSteps5Minutes = valid5?.steps5min ?: fallbackRecord?.steps5min ?: 0

            // FIX: Health Connect sends updates every few seconds, each record containing
            // steps5min = steps accumulated in the last 5 minutes at that moment.
            // sumOf() across all records in a window multiplies the true count by the
            // update frequency (~35x for a 10-min window at 17-sec intervals → >90k steps).
            // Correct approach: bucket into 5-min slices, take the LAST record per bucket.
            fun lastStepsInBucket(from: Long, to: Long): Int =
                (allStepsCounts
                    .filter { it.timestamp in from..to }
                    .maxByOrNull { it.timestamp }
                    ?.steps5min ?: 0)
                    .coerceAtMost(1500)  // max 160 steps/min = schnelles Gehen

            recentSteps10Minutes = lastStepsInBucket(timeMillis10, timeMillis5) +
                lastStepsInBucket(timeMillis5,  now)
            recentSteps15Minutes = lastStepsInBucket(timeMillis15, timeMillis10) +
                lastStepsInBucket(timeMillis10, timeMillis5) +
                lastStepsInBucket(timeMillis5,  now)
            recentSteps30Minutes = (0 until 6).sumOf { i ->
                lastStepsInBucket(now - (i + 1) * 5 * 60_000L, now - i * 5 * 60_000L)
            }
            recentSteps60Minutes = (0 until 12).sumOf { i ->
                lastStepsInBucket(now - (i + 1) * 5 * 60_000L, now - i * 5 * 60_000L)
            }
        } else {
            // stepsservice not really working on my deice. Only for fallback.
            recentSteps5Minutes = StepService.getRecentStepCount5Min()
            recentSteps10Minutes = StepService.getRecentStepCount10Min()
            recentSteps15Minutes = StepService.getRecentStepCount15Min()
            recentSteps30Minutes = StepService.getRecentStepCount30Min()
            recentSteps60Minutes = StepService.getRecentStepCount60Min()
        }

        aapsLogger.debug(LTag.APS, "Activity Monitor: Steps: $recentSteps5Minutes $recentSteps10Minutes $recentSteps15Minutes ")

        // === HR-based activity detection (cycling, rowing, etc.) ===
        val allHeartRates = heartRatesCached(now)
        fun getHrForWindow(windowMillis: Long): List<HR> {
            val windowStart = now - windowMillis
            return allHeartRates.filter { (it.timestamp + it.duration) >= windowStart }
        }
        val hrAvg5  = getHrForWindow(5  * 60 * 1000).let { if (it.isNotEmpty()) it.map { h -> h.beatsPerMinute.toInt() }.average() else Double.NaN }
        val hrAvg60 = getHrForWindow(60 * 60 * 1000).let { if (it.isNotEmpty()) it.map { h -> h.beatsPerMinute.toInt() }.average() else 70.0 }
        val hrAvg180= getHrForWindow(180* 60 * 1000).let { if (it.isNotEmpty()) it.map { h -> h.beatsPerMinute.toInt() }.average() else 70.0 }

        val hrBaseline = if (hrAvg180 > 40) hrAvg180 else hrAvg60
        val hrNow      = if (!hrAvg5.isNaN()) hrAvg5 else hrBaseline
        val hrTrend    = hrNow / hrBaseline

        // only active when swimming, biking,....
        val hrOnlyActivityHigh = !hrAvg5.isNaN() && recentSteps5Minutes < 100 && recentSteps15Minutes < 200 && hrTrend > 1.30
        val hrOnlyActivityMid  = !hrAvg5.isNaN() && recentSteps5Minutes < 100 && recentSteps15Minutes < 200 && hrTrend > 1.15
        // HR only confirms inactivity when it is truly idle (or there is no data)
        val hrConfirmsInactivity = hrAvg5.isNaN() || hrTrend < 1.05

        aapsLogger.debug(LTag.APS, "Activity Monitor: HR hrNow=${"%.1f".format(hrNow)} baseline=${"%.1f".format(hrBaseline)} trend=${"%.2f".format(hrTrend)} highAct=$hrOnlyActivityHigh midAct=$hrOnlyActivityMid")

        val phoneMoved = true // To be implemented!! PhoneMovementDetector.phoneMoved()
        val lastAppStart = preferences.get(LongKey.AppStart)
        val time_since_start = (dateUtil.now() - lastAppStart).milliseconds.inWholeMinutes

        // ActivityMonitorDetection — Enable Activity Monitor
        // Master switch. Off → activityRatio = 1.0 at all times, no effect on ISF/Basal.
        val activityDetection = preferences.get(BooleanKey.ActivityMonitorDetection)
        // ActivityScaleFactor — Activity Scaling
        // Amplifies the sensitivity boost when movement is detected.
        // + → greater ISF increase during activity (activityRatio decreases more sharply, more insulin)
        // - → weaker effect, smoother transition
        val activity_scale_factor = preferences.get(DoubleKey.ActivityScaleFactor)              // profile.activity_scale_factor;
        // InactivityScaleFactor — Inactivity scaling
        // Increases ISF protection during inactivity.
        // + → greater reduction in insulin during inactivity (activityRatio increases more sharply)
        // - → weaker effect
        val inactivity_scale_factor = preferences.get(DoubleKey.InactivityScaleFactor)          // profile.inactivity_scale_factor;
        var activityRatio = 1.0
        // ActivityMonitorOvernight — Ignore Inactivity at Night
        // Prevents prolonged lying still while sleeping from being classified as “inactivity” and incorrectly triggering the inactivity penalty.
        // true → Inactivity detection disabled during sleeping hours
        // false → Active even at night (Risk: Sleep = Inactivity → too little insulin)
        val ignore_inactivity_overnight = preferences.get(BooleanKey.ActivityMonitorOvernight)  // profile.ignore_inactivity_overnight;
        // ActivityMonitorIdleStart — Start of sleeping hours (hour, 0–23)
        // When nighttime suppression takes effect (e.g., 22 = 10:00 PM).
        // + (later) → shorter night window, inactivity detection active again earlier
        // - (earlier) → longer night window
        val inactivity_idle_start =  preferences.get(IntKey.ActivityMonitorIdleStart)           // profile.inactivity_idle_start;
        // ActivityMonitorIdleEnd — End of sleep hours (hour, 0–23)
        // Until what time night suppression applies (e.g., 6 = 6:00 a.m.). Supports crossing midnight (IdleStart > IdleEnd).
        // + (wake up later) → longer night window
        // - (wake up earlier) → shorter night window
        val inactivity_idle_end = preferences.get(IntKey.ActivityMonitorIdleEnd)                // profile.inactivity_idle_end;

        val existSleepState = automationStateService.hasStateValues("Sleeping")
        val useSleepState = automationStateService.inState("Sleeping", "True")
        //aapsLogger.debug(LTag.APS, "State json for Sleep mode: {\"Sleeping\":\"${automationStateService.getState("Sleeping")}\"}")
        // really still sleeping?
        if (useSleepState && (recentSteps5Minutes+recentSteps10Minutes+recentSteps15Minutes == recentSteps30Minutes) ) {
            automationStateService.setState("query_got_up", "query_it")
        }
        //aapsLogger.debug(LTag.APS, "State json for got up query: {\"query_got_up\":\"${automationStateService.getState("query_got_up")}\"}")

        if ( !activityDetection ) {
            consoleLog.add("Activity monitor disabled in settings")
        } else if ( isTempTarget ) {
            consoleLog.add("Activity monitor disabled: tempTarget")
        } else if ( !phoneMoved ) {
            consoleLog.add("Activity monitor disabled: Phone seems not to be carried for the last 15m")
        } else {
            if ( time_since_start < 60 && recentSteps60Minutes <= 200 ) {
                consoleLog.add("Activity monitor initialising for ${60 - time_since_start} more minutes: inactivity detection disabled")
            } else if ( useSleepState && recentSteps60Minutes <= 200) {
                consoleLog.add("Activity monitor disabled inactivity detection: sleeping state")
            } else if ( (( inactivity_idle_start>inactivity_idle_end && (currentHour !in inactivity_idle_end..<inactivity_idle_start) )  // includes midnight
                || (currentHour in inactivity_idle_start..<inactivity_idle_end)  )                                                       // excludes midnight
                && recentSteps60Minutes <= 200 && ignore_inactivity_overnight && !existSleepState) {
                consoleLog.add("Activity monitor disabled inactivity detection: sleeping hours")
            } else if ( recentSteps5Minutes > 300 || recentSteps10Minutes > 300
                || recentSteps15Minutes > 300 || recentSteps30Minutes > 1500
                || recentSteps60Minutes > 2500 || hrOnlyActivityHigh ) {
                activityRatio = 1 - 0.3 * activity_scale_factor
                val src = if (hrOnlyActivityHigh && recentSteps5Minutes < 100) "HR" else "steps"
                consoleLog.add("Activity monitor detected activity ($src), sensitivity ratio: $activityRatio")
            } else if ( recentSteps5Minutes > 200 || recentSteps10Minutes > 200
                || recentSteps15Minutes > 200 || recentSteps30Minutes > 500
                || recentSteps60Minutes > 800 || hrOnlyActivityMid ) {
                activityRatio = 1 - 0.15 * activity_scale_factor
                val src = if (hrOnlyActivityMid && recentSteps5Minutes < 100) "HR" else "steps"
                consoleLog.add("Activity monitor detected partial activity ($src), sensitivity ratio: $activityRatio")
            } else if ( bg < target_bg && recentSteps60Minutes <= 200 ) {
                consoleLog.add("Activity monitor disabled inactivity detection: bg < target")
            } else if ( recentSteps60Minutes < 50 && hrConfirmsInactivity ) {
                activityRatio = 1 + 0.2 * inactivity_scale_factor
                consoleLog.add("Activity monitor detected inactivity, sensitivity ratio: $activityRatio")
            } else if ( recentSteps60Minutes <= 200 && hrConfirmsInactivity ) {
                activityRatio = 1 + 0.1 * inactivity_scale_factor
                consoleLog.add("Activity monitor detected partial inactivity, sensitivity ratio: $activityRatio")
            } else {
                consoleLog.add("Activity monitor detected neutral state")  //, sensitivity ratio unchanged: $activityRatio")
            }
        }
        var activityMsg = "Activity Monitor json: {\"activity_scale_factor\":$activity_scale_factor,\"inactivity_scale_factor\":$inactivity_scale_factor"
        activityMsg += ",\"recentSteps5Minutes\":$recentSteps5Minutes,\"recentSteps10Minutes\":$recentSteps10Minutes,\"recentSteps15Minutes\":$recentSteps15Minutes"
        activityMsg += ",\"recentSteps30Minutes\":$recentSteps30Minutes,\"recentSteps60Minutes\":$recentSteps60Minutes"
        activityMsg += ",\"hrNow\":${"%.1f".format(hrNow)},\"hrBaseline\":${"%.1f".format(hrBaseline)},\"hrTrend\":${"%.2f".format(hrTrend)}"
        activityMsg += ",\"hrOnlyActivityHigh\":$hrOnlyActivityHigh,\"hrOnlyActivityMid\":$hrOnlyActivityMid"
        activityMsg += ",\"phone_moved\":$phoneMoved,\"time_since_start\":$time_since_start,\"activity_detection\":$activityDetection"
        activityMsg += ",\"ignore_inactivity_overnight\":$ignore_inactivity_overnight,\"inactivity_idle_start\":$inactivity_idle_start,\"inactivity_idle_end\":$inactivity_idle_end}"
        aapsLogger.debug(LTag.APS, activityMsg)
        return activityRatio
    }

    private fun stepsCountsCached(now: Long): List<SC> {
        refreshStepsAsync(now)
        return stepsSnapshotRef.get()
    }

    private fun refreshStepsAsync(now: Long) {
        if (!stepsRefreshInFlight.compareAndSet(false, true)) return
        determineIoScope.launch {
            try {
                val start = now - 210 * 60 * 1000
                stepsSnapshotRef.set(persistenceLayer.getStepsCountFromTimeToTime(start, now))
            } catch (_: Exception) {
                stepsSnapshotRef.set(emptyList())
            } finally {
                stepsRefreshInFlight.set(false)
            }
        }
    }

    suspend fun autoISF(profile: Profile): Double {
        val sens = profile.getProfileIsfMgdl()
        val glucose_status = glucoseStatusCalculatorAutoIsf.getGlucoseStatusData(allowOldData = false)

        val high_temptarget_raises_sensitivity = exerciseMode || highTemptargetRaisesSensitivity
        var target_bg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TARGET_BG)
        var isTempTarget = false
        persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tempTarget ->
            isTempTarget = true
            target_bg = hardLimits.verifyHardLimits(tempTarget.target(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TEMP_TARGET_BG)
        }
        val activityRatio = lastActivityRatio
        val stepActivityDetected = lastStepActivityDetected
        val stepInactivityDetected = lastStepInactivityDetected
        var sensitivityRatio = 1.0
        val exerciseModeActive = high_temptarget_raises_sensitivity && isTempTarget && target_bg > normalTarget
        val resistanceModeActive = preferences.get(BooleanKey.ApsAutoIsfLowTtLowersSens)  && isTempTarget && target_bg < normalTarget
        if ( exerciseModeActive || resistanceModeActive || stepActivityDetected || stepInactivityDetected ) {
            if ( exerciseModeActive || resistanceModeActive ) {
                // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
                // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
                //sensitivityRatio = 2/(2+(target_bg-normalTarget)/40);
                val resistanceMax = min(1.5, preferences.get(DoubleKey.AutosensMax))  // additional safety limit
                val c = (mgdlHalfBasalExerciseTarget - normalTarget)
                if (c * (c + target_bg - normalTarget) <= 0.0) {
                    sensitivityRatio = resistanceMax
                    // consoleError.add("Sensitivity decrease for temp target of $target_bg limited by Autosens_max; ")

                } else {
                    sensitivityRatio = c / (c + target_bg - normalTarget)
                    // limit sensitivityRatio to profile.autosens_max (1.2x by default)
                }
                sensitivityRatio = min(sensitivityRatio, resistanceMax)
                sensitivityRatio = round(sensitivityRatio, 2)

            } else if ( stepActivityDetected ) {
                sensitivityRatio = activityRatio
            } else if ( stepInactivityDetected ) {
                sensitivityRatio = activityRatio
            }
        } else {
            var autosensResult = AutosensResult()

            if (constraintsChecker.isAutosensModeEnabled().value()) {
                iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSAutoISFPlugin")?.also {
                    autosensResult = it.autosensResult
                }
            } else autosensResult.sensResult = "autosens disabled"
            sensitivityRatio = autosensResult.ratio
        }
        // 🏥 AIMI Physio modulation: sleep/HRV/inflammation-based ISF adjustments
        // (shared MTR pipeline — benefits both AIMI and AutoISF)
        try {
            val physioMults = physioAdapter.getMultipliers(
                currentBG = glucose_status?.glucose ?: 100.0,
                currentDelta = glucose_status?.delta ?: 0.0,
                iob = 0.0,
                cob = 0.0,
            )
            if (!physioMults.isNeutral()) {
                val before = sensitivityRatio
                sensitivityRatio *= physioMults.isfFactor
                aapsLogger.debug(LTag.APS, "🏥 AutoISF Physio: sensitivityRatio ${String.format("%.2f", before)} → ${String.format("%.2f", sensitivityRatio)} (isfFactor=${physioMults.isfFactor})")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "AutoISF physio modulation failed", e)
        }
        var skipWeights = false
        val calibrationMinutes = calibrationDuration - (dateUtil.now() - preferences.get(LongKey.FslCalibrationStart)) / 60000
        val calibrationStopsSMB = calibrationMinutes > 0 && !preferences.get(BooleanKey.FslCalibrationEnd)
        if (calibrationStopsSMB) {
            consoleError.add("AutoISF weights disabled while calibrating")
            skipWeights = true
        } else if ( !autoIsfWeights || glucose_status == null) {
            consoleError.add("AutoISF weights disabled in Preferences")
            skipWeights = true
        }
        if (skipWeights) {
            consoleError.add("----------------------------------")
            consoleError.add("end AutoISF")
            consoleError.add("----------------------------------")
            return round(sens / sensitivityRatio, 1)
        }
        val autosensResult = AutosensResult()

        if (constraintsChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSAutoISFPlugin")
            if (autosensData == null) {
                rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openaps_no_as_data)))
                return sens
            }
            autosensData.autosensResult
        } else autosensResult.sensResult = "autosens disabled"

        val dura05: Double = glucose_status!!.duraISFminutes
        val avg05: Double = glucose_status.duraISFaverage
        val maxISFReduction: Double = autoISF_max
        var sens_modified = false
        var pp_ISF = 1.0
        var acce_ISF = 1.0
        var acce_weight = 1.0
        val bg_off = target_bg + 10.0 - glucose_status.glucose                      // move from central BG=100 to target+10 as virtual BG'=100

        // calculate acce_ISF from bg acceleration and adapt ISF accordingly
        val fit_corr: Double = glucose_status.corrSqu
        val bg_acce: Double = glucose_status.bgAcceleration
        //consoleError.add("Parabola fit results were acceleration:${round(bg_acce, 2)}, correlation:$fit_corr, duration:${glucose_status.parabolaMinutes}m")
        if (glucose_status.a2 != 0.0 && fit_corr >= 0.9) {
            var minmax_delta: Double = -glucose_status.a1 / 2 / glucose_status.a2 * 5      // back from 5min block to 1 min
            val minmax_value: Double = round(glucose_status.a0 - minmax_delta * minmax_delta / 25 * glucose_status.a2, 1)
            minmax_delta = round(minmax_delta, 1)
            if (minmax_delta > 0 && bg_acce < 0) {
                consoleError.add("Parabolic fit extrapolates a maximum of ${convert_bg(minmax_value)} in about $minmax_delta minutes")
            } else if (minmax_delta > 0 && bg_acce > 0.0) {

                consoleError.add("Parabolic fit extrapolates a minimum of ${convert_bg(minmax_value)} in about $minmax_delta minutes")
                if (minmax_delta <= 30 && minmax_value < target_bg) {   // start braking
                    acce_weight = -bgBrake_ISF_weight
                    consoleError.add("extrapolation below target soon: use bgBrake_ISF_weight instead")
                }
            }
        }
        if (fit_corr < 0.9) {
            consoleError.add("acce_ISF adaptation by-passed as correlation ${round(fit_corr, 3)} is too low")
        } else {
            val fit_share = 10 * (fit_corr - 0.9)                            // 0 at correlation 0.9, 1 at 1.00
            var cap_weight = 1.0                                             // full contribution above target
            if (acce_weight == 1.0 && glucose_status.glucose < target_bg) {  // below target acce goes towards target
                if (bg_acce > 0) {
                    if (bg_acce > 1) {
                        cap_weight = 0.5
                    }            // halve the effect below target
                    acce_weight = bgBrake_ISF_weight
                } else if (bg_acce < 0) {
                    acce_weight = bgAccel_ISF_weight
                }
            } else if (acce_weight == 1.0) {                                 // above target acce goes away from target
                if (bg_acce < 0.0) {
                    acce_weight = bgBrake_ISF_weight
                } else if (bg_acce > 0.0) {
                    acce_weight = bgAccel_ISF_weight
                }
            }
            acce_ISF = 1.0 + bg_acce * cap_weight * acce_weight * fit_share
            consoleError.add("acce_ISF adaptation is ${round(acce_ISF, 2)}")
            if (acce_ISF != 1.0) {
                sens_modified = true
            }
        }
        autoIsfValues.acceIsf = acce_ISF

        val bg_ISF = 1 + interpolate(100 - bg_off)
        consoleError.add("bg_ISF adaptation is ${round(bg_ISF, 2)}")
        autoIsfValues.bgIsf = bg_ISF
        var liftISF: Double
        var final_ISF: Double
        if (bg_ISF < 1.0) {
            liftISF = min(bg_ISF, acce_ISF)
            if (acce_ISF > 1.0) {
                liftISF = bg_ISF * acce_ISF                                 // bg_ISF could become > 1 now
                consoleError.add("bg_ISF adaptation lifted to ${round(liftISF, 2)} as bg accelerates already")
            }
            final_ISF = withinISFlimits(liftISF, autoISF_min, maxISFReduction, sensitivityRatio, exerciseModeActive, resistanceModeActive, stepActivityDetected, stepInactivityDetected)
            return min(720.0, round(sens / final_ISF, 1))         // observe ISF maximum of 720(?)
        } else if (bg_ISF > 1.0) {
            sens_modified = true
        }

        val bg_delta = glucose_status.delta
        val deltaType = "pp"
        when {
            bg_off > 0.0                     -> {
                consoleError.add("${deltaType}_ISF adaptation by-passed as average glucose < $target_bg+10")
            }

            glucose_status.shortAvgDelta < 0 -> {
                consoleError.add("${deltaType}_ISF adaptation by-passed as no rise or too short lived")
            }

            else                             -> {
                pp_ISF = 1.0 + max(0.0, bg_delta * pp_ISF_weight)
                consoleError.add("pp_ISF adaptation is ${round(pp_ISF, 2)}")
                if (pp_ISF != 1.0) {
                    sens_modified = true
                }

            }
        }
        autoIsfValues.ppIsf = pp_ISF

        var dura_ISF = 1.0
        val weightISF: Double = dura_ISF_weight
        when {
            dura05 < 10.0      -> {
                consoleError.add("dura_ISF by-passed; bg is only $dura05 m at level $avg05")
            }

            avg05 <= target_bg -> {
                consoleError.add("dura_ISF by-passed; avg. glucose $avg05 below target $target_bg")
            }

            else               -> {
                // fight the resistance at high levels
                val dura05Weight = dura05 / 60
                val avg05Weight = weightISF / target_bg
                dura_ISF += dura05Weight * avg05Weight * (avg05 - target_bg)
                sens_modified = true
                consoleError.add("dura_ISF adaptation is ${round(dura_ISF, 2)} because ISF ${round(sens, 1)} did not do it for ${round(dura05, 1)}m")
            }
        }
        autoIsfValues.duraIsf = dura_ISF

        if (sens_modified) {
            liftISF = max(dura_ISF, max(bg_ISF, max(acce_ISF, pp_ISF)))
            if (acce_ISF < 1.0) {
                consoleError.add("strongest autoISF factor ${round(liftISF, 2)} weakened to ${round(liftISF * acce_ISF, 2)} as bg decelerates already")
                liftISF = liftISF * acce_ISF
            }
            final_ISF = withinISFlimits(liftISF, autoISF_min, maxISFReduction, sensitivityRatio, exerciseModeActive, resistanceModeActive, stepActivityDetected, stepInactivityDetected)
            return round(sens / final_ISF, 1)
        }
        consoleError.add("----------------------------------")
        consoleError.add("end AutoISF")
        consoleError.add("----------------------------------")
        return round(sens / sensitivityRatio, 1)     // nothing changed
    }

    fun interpolate(xdata: Double): Double {   // interpolate ISF behaviour based on polygons defining nonlinear functions defined by value pairs for ...
        //  ...             <----------------------  glucose  ---------------------->
        val polyX = arrayOf(50.0, 60.0, 80.0, 90.0, 100.0, 110.0, 150.0, 180.0, 200.0)
        val polyY = arrayOf(-0.5, -0.5, -0.3, -0.2, 0.0, 0.0, 0.5, 0.7, 0.7)
        val polymax: Int = polyX.size - 1
        var step = polyX[0]
        var sVal = polyY[0]
        var stepT = polyX[polymax]
        var sValold = polyY[polymax]

        var newVal = 1.0
        var lowVal = 1.0
        val topVal: Double
        val lowX: Double
        val topX: Double
        val myX: Double
        var lowLabl = step

        if (step > xdata) {
            // extrapolate backwards
            stepT = polyX[1]
            sValold = polyY[1]
            lowVal = sVal
            topVal = sValold
            lowX = step
            topX = stepT
            myX = xdata
            newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
        } else if (stepT < xdata) {
            // extrapolate forwards
            step = polyX[polymax - 1]
            sVal = polyY[polymax - 1]
            lowVal = sVal
            topVal = sValold
            lowX = step
            topX = stepT
            myX = xdata
            newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
        } else {
            // interpolate
            for (i: Int in 0..polymax) {
                step = polyX[i]
                sVal = polyY[i]
                if (step == xdata) {
                    newVal = sVal
                    break
                } else if (step > xdata) {
                    topVal = sVal
                    lowX = lowLabl
                    myX = xdata
                    topX = step
                    newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
                    break
                }
                lowVal = sVal
                lowLabl = step
            }
        }
        newVal = if (xdata > 100) {
            newVal * higher_ISFrange_weight
        } else {
            newVal * lower_ISFrange_weight
        }
        return newVal
    }

    fun withinISFlimits(
        liftISF: Double, minISFReduction: Double, maxISFReduction: Double, sensitivityRatio: Double,
        exerciseModeActive: Boolean, resistanceModeActive: Boolean, stepActivityDetected:Boolean, stepInactivityDetected: Boolean
    ): Double {
        var liftISFlimited: Double = liftISF
        if (liftISF < minISFReduction) {
            consoleError.add("weakest autoISF factor ${round(liftISF, 2)} limited by autoISF_min $minISFReduction")
            liftISFlimited = minISFReduction
        } else if (liftISF > maxISFReduction) {
            consoleError.add("strongest autoISF factor ${round(liftISF, 2)} limited by autoISF_max $maxISFReduction")
            liftISFlimited = maxISFReduction
        }
        val finalISF: Double
        var originSens = ""
        when {
            exerciseModeActive          -> {
                finalISF = liftISFlimited * sensitivityRatio                //# on top of TT modification
                originSens = "including exercise mode impact"
            }
            resistanceModeActive        -> {
                finalISF = liftISFlimited * sensitivityRatio                //# on top of TT modification
                originSens = "including resistance mode impact"
            }
            stepActivityDetected        -> {
                finalISF = liftISFlimited * sensitivityRatio                //# on top of activity detection
                originSens  = "including activity detection impact"
            }
            stepInactivityDetected      -> {
                finalISF = liftISFlimited * sensitivityRatio                //# on top of inactivity detection
                originSens  = "including inactivity detection impact"
            }
            liftISFlimited >= 1         -> {                                // can we evr get here?
                finalISF = max(liftISFlimited, sensitivityRatio)
                if (liftISFlimited < sensitivityRatio) {
                    originSens = "from low TT modifier"
                }
            }
            else                        -> {
                finalISF = min(liftISFlimited, sensitivityRatio)            // low TT lowers sensitivity dominates
            }
        }
        consoleError.add("final ISF factor is ${round(finalISF, 2)} " + originSens)
        consoleError.add("----------------------------------")
        consoleError.add("end AutoISF")
        consoleError.add("----------------------------------")
        autoIsfValues.finalIsf = finalISF
        return finalISF
    }

    fun loop_smb(microBolusAllowed: Boolean, profile: OapsProfileAutoIsf, iob_data_iob: Double, useIobTh: Boolean, iobThEffective: Double): String {
        val iobThUser = preferences.get(IntKey.ApsAutoIsfIobThPercent)
        if (useIobTh) {
            val iobThPercent: Double
            if ( profile.max_iob<0.001 ) {
                iobThPercent = 0.0
                consoleLog.add("User setting iobTH disabled in LGS mode")
            } else {
                iobThPercent = round(iobThEffective/profile.max_iob*100.0, 0)
            }
            if (iobThPercent == iobThUser.toDouble()) {
                consoleLog.add("User setting iobTH=$iobThUser% not modulated")
            } else if (iobThPercent > 0.0) {
                consoleLog.add("User setting iobTH=$iobThUser% modulated to ${iobThPercent.toInt()}% or ${round(iobThEffective, 2)}U")
                consoleLog.add("  due to profile %, exercise mode or similar")
            }
        } else {
            consoleLog.add("User setting iobTH=100% disables iobTH method")
        }
        autoIsfValues.iobThEffective = if (useIobTh) iobThEffective else profile.max_iob

        if (!microBolusAllowed) {
            return "AAPS"                                                 // see message in enable_smb
        }

        if (preferences.get(BooleanKey.FslCalibrationTrigger)) {
            preferences.put(LongKey.FslCalibrationStart, dateUtil.now())
            preferences.put(BooleanKey.FslCalibrationTrigger, false)
            preferences.put(BooleanKey.FslCalibrationEnd, false)
        }
        val calibrationMinutes = calibrationDuration - (dateUtil.now() - preferences.get(LongKey.FslCalibrationStart)) / 60000
        val calibrationStopsSMB = calibrationMinutes > 0 && !preferences.get(BooleanKey.FslCalibrationEnd)
        var CalibrationMsg = "Calibration json: {\"calibrationStart\":${preferences.get(LongKey.FslCalibrationStart)},\"calibrationIgnore\":${preferences.get(BooleanKey.FslCalibrationEnd)}"
        CalibrationMsg += "}"
        aapsLogger.debug(LTag.APS, CalibrationMsg)
        if (calibrationStopsSMB) {
            consoleLog.add("SMB disabled while calibrating for another ${calibrationMinutes}m")
            return "blocked"
        } else if (enableSMB_EvenOn_OddOff_always) {
            //TODO: cleaner conversion back to original mmol/L if applicable
            var target = convert_bg_to_units(profile.target_bg, profile)
            // val msgType: String
            val evenTarget: Boolean
            val msgUnits: String
            val msgTail: String
            if (profile.out_units == "mmol/L") {
                evenTarget = round(target * 10.0, 0).toInt() % 2 == 0
                target = round(target, 1)
                msgUnits = "has"
                msgTail = "decimal"
            } else {
                evenTarget = round(target, 0).toInt() % 2 == 0
                target = round(target, 0)
                msgUnits = "is"
                msgTail = "number"
            }
            val msgEven: String = if (evenTarget) "even" else "odd"

            if (!evenTarget) {
                consoleLog.add("SMB disabled; current target $target $msgUnits $msgEven $msgTail")
                consoleLog.add("Loop allows minimal power")
                return "blocked"
            } else if (profile.max_iob == 0.0) {
                consoleLog.add("SMB disabled because of max_iob=0")
                return "blocked"
            } else if (useIobTh && iobThEffective < iob_data_iob) {
                consoleLog.add("SMB disabled by Full Loop logic: iob $iob_data_iob is above effective iobTH $iobThEffective")
                consoleLog.add("Loop power level temporarily capped")
                return "iobTH"
            } else {
                consoleLog.add("SMB enabled; current target $target $msgUnits $msgEven $msgTail")
                return if (profile.target_bg < 100) {     // indirect assessment; later set it in GUI
                    consoleLog.add("Loop allows maximum power")
                    "fullLoop"                                      // even number
                } else {
                    consoleLog.add("Loop allows medium power")
                    "enforced"                                      // even number
                }
            }
        }
        consoleLog.add("Loop allows AAPS power level")
        return "AAPS"                                                      // leave it to standard AAPS
    }

    fun determine_varSMBratio(bg: Int, target_bg: Double, loop_wanted_smb: String): Double {   // let SMB delivery ratio increase from min to max depending on how much bg exceeds target
        val fix_SMB: Double = smb_delivery_ratio
        val lower_SMB = min(smb_delivery_ratio_min, smb_delivery_ratio_max)
        val higher_SMB = max(smb_delivery_ratio_min, smb_delivery_ratio_max)
        val higher_bg = target_bg + smb_delivery_ratio_bg_range
        var new_SMB: Double = fix_SMB
        if (smb_delivery_ratio_bg_range > 0) {
            new_SMB = lower_SMB + (higher_SMB - lower_SMB) * (bg - target_bg) / smb_delivery_ratio_bg_range
            new_SMB = max(lower_SMB, min(higher_SMB, new_SMB))   // cap if outside target_bg--higher_bg
        }
        if (loop_wanted_smb == "fullLoop") {                                // go for max impact
            consoleLog.add("SMB delivery ratio set to ${round(max(fix_SMB, new_SMB), 2)} as max of fixed and interpolated values")
            return max(fix_SMB, new_SMB)
        }

        if (smb_delivery_ratio_bg_range == 0.0) {                     // deactivated in SMB extended menu
            consoleLog.add("SMB delivery ratio set to fixed value ${round(fix_SMB, 2)}")
            return fix_SMB
        }
        if (bg <= target_bg) {
            consoleLog.add("SMB delivery ratio limited by minimum value ${round(lower_SMB, 2)}")
            return lower_SMB
        }
        if (bg >= higher_bg) {
            consoleLog.add("SMB delivery ratio limited by maximum value ${round(higher_SMB, 2)}")
            return higher_SMB
        }
        consoleLog.add("SMB delivery ratio set to interpolated value ${round(new_SMB, 2)}")
        return new_SMB
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "openapsautoisf_settings",
        titleResId = R.string.openaps_auto_isf,
        items = listOf(
            ApsIntentKey.AutoIsfProfileAdvisor.withClick {
                context.startActivity(Intent(context, AutoIsfProfileAdvisorActivity::class.java))
            },
            DoubleKey.ApsMaxBasal,
            DoubleKey.ApsSmbMaxIob,
            BooleanKey.ApsUseAutosens,
            BooleanKey.ApsSensitivityRaisesTarget,
            BooleanKey.ApsResistanceLowersTarget,
            BooleanKey.ApsAutoIsfHighTtRaisesSens,
            BooleanKey.ApsAutoIsfLowTtLowersSens,
            IntKey.ApsAutoIsfHalfBasalExerciseTarget,
            BooleanKey.ApsUseSmb,
            BooleanKey.ApsUseSmbWithHighTt,
            BooleanKey.ApsUseSmbAlways,
            BooleanKey.ApsUseSmbWithCob,
            BooleanKey.ApsUseSmbWithLowTt,
            BooleanKey.ApsUseSmbAfterCarbs,
            BooleanKey.ApsUseUam,
            IntKey.ApsMaxSmbFrequency,
            IntKey.ApsMaxMinutesOfBasalToLimitSmb,
            IntKey.ApsUamMaxMinutesOfBasalToLimitSmb,
            IntKey.ApsCarbsRequestThreshold,
            PreferenceSubScreenDef(
                key = "absorption_smb_advanced",
                titleResId = app.aaps.core.ui.R.string.advanced_settings_title,
                items = listOf(
                    ApsIntentKey.LinkToDocs,
                    BooleanKey.ApsAlwaysUseShortDeltas,
                    DoubleKey.ApsMaxDailyMultiplier,
                    DoubleKey.ApsMaxCurrentBasalMultiplier
                )
            ),
            PreferenceSubScreenDef(
                key = "auto_isf_settings",
                titleResId = R.string.autoISF_settings_title,
                items = listOf(
                    BooleanKey.ApsUseAutoIsfWeights,
                    DoubleKey.ApsAutoIsfMin,
                    DoubleKey.ApsAutoIsfMax,
                    DoubleKey.ApsAutoIsfBgAccelWeight,
                    DoubleKey.ApsAutoIsfBgBrakeWeight,
                    DoubleKey.ApsAutoIsfLowBgWeight,
                    DoubleKey.ApsAutoIsfHighBgWeight,
                    DoubleKey.ApsAutoIsfPpWeight,
                    DoubleKey.ApsAutoIsfDuraWeight,
                    IntKey.ApsAutoIsfIobThPercent
                )
            ),
            PreferenceSubScreenDef(
                key = "smb_delivery_settings",
                titleResId = R.string.smb_delivery_settings_title,
                items = listOf(
                    DoubleKey.ApsAutoIsfSmbDeliveryRatio,
                    DoubleKey.ApsAutoIsfSmbDeliveryRatioMin,
                    DoubleKey.ApsAutoIsfSmbDeliveryRatioMax,
                    DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange,
                    DoubleKey.ApsAutoIsfSmbMaxRangeExtension,
                    BooleanKey.ApsAutoIsfSmbOnEvenTarget
                )
            ),
            PreferenceSubScreenDef(
                key = "aimi_compose_ai_keys",
                titleResId = R.string.aimi_prefs_ai_title,
                items = listOf(
                    StringKey.AimiAdvisorProvider.withEntries(
                        mapOf(
                            "OPENAI" to ApsStrings.aimi_prefs_provider_openai,
                            "GEMINI" to ApsStrings.aimi_prefs_provider_gemini,
                            "DEEPSEEK" to ApsStrings.aimi_prefs_provider_deepseek,
                            "CLAUDE" to ApsStrings.aimi_prefs_provider_claude,
                        )
                    ),
                    StringKey.AimiAdvisorOpenAIKey,
                    StringKey.AimiAdvisorGeminiKey,
                    StringKey.AimiAdvisorDeepSeekKey,
                    StringKey.AimiAdvisorClaudeKey,
                    // BooleanKey.OApsAIMIAdvisorPersonalOrefMl,
                    // BooleanKey.OApsAIMIAdvisorLlmRichOref,
                ),
            ),
            PreferenceSubScreenDef(
                key = "activity_monitor",
                titleResId = R.string.activity_monitor_title,
                summaryResId =  R.string.activity_monitor_summary,
                items = listOf(
                    BooleanKey.ActivityMonitorDetection,
                    DoubleKey.ActivityScaleFactor,
                    DoubleKey.InactivityScaleFactor,
                    BooleanKey.ActivityMonitorOvernight,
                    IntKey.ActivityMonitorIdleStart,
                    IntKey.ActivityMonitorIdleEnd,
                    BooleanKey.ActivityMonitorUseSteps,
                    AimiStringKey.ActivitySourceMode
                )
            ),
            PreferenceSubScreenDef(
                key = "Ketoacidosis_Protection",
                titleResId = app.aaps.core.keys.R.string.ketoacidosis_protection_title,
                summaryResId = app.aaps.core.keys.R.string.ketoacidosis_protection_summary,
                items = buildList {
                    add(BooleanKey.ApsKetoacidosisProtection)
                    add(BooleanKey.ApsKetoacidosisProtectionStrategy)
                    add(IntKey.ApsKetoacidosisProtectionBasal)
                }
            ),
            PreferenceSubScreenDef(
                key = "aimi_compose_sos",
                titleResId = R.string.aimi_sos_title,
                items = buildList {
                    add(BooleanKey.AimiEmergencySosEnable)
                    add(StringKey.AimiEmergencySosPhone)
                    add(StringKey.AimiEmergencySosPhone2)
                    add(IntKey.AimiEmergencySosThreshold)
                    add(IntKey.AimiEmergencySosImmediateThreshold)
                    add(IntKey.AimiEmergencySosStaleThreshold)
                    add(
                        ApsIntentKey.AimiSosPermissions.withClick {
                            context.startActivity(Intent(context, AIMIEmergencySosPermissionActivityMTR::class.java))
                        },
                    )
                },
            ),
        ),
        icon = pluginDescription.icon
    )

}
