package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.SensitivityOverview
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.resources.formatTemplate
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.extensions.displayText
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Stable
data class BoostChipState(
    val state: String = "",       // IDLE/OBSERVING/CONFIRMED/COMMITTED/RECOVERING
    val color: Long = 0xFF78909C, // blue-grey default
    val detail: String = "",      // e.g. "×1.20"
    val tier: String = "",        // V1 tier fallback
    val isBoost: Boolean = false, // true when BOOST is active APS
    // Widget-style data
    val dynIsf: String = "",      // e.g. "32.1"
    val tdd: String = "",         // e.g. "38.4U"
    val profilePct: String = "",  // e.g. "130%"
    val activity: String = "",    // e.g. "INACTIVE" or V5 score
    val iob: String = "",         // e.g. "4.6U"
    val boostTier: String = "",   // e.g. "UAM_BOOST"
    val mlRisk: String = ""       // e.g. "0.12"
)

@Stable
@AssistedInject
class ChipsViewModel(
    @Assisted cache: OverviewDataCache,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val sensitivityOverview: SensitivityOverview,
    private val activePlugin: ActivePlugin,
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val decimalFormatter: DecimalFormatter,
    private val rxBus: RxBus
) : ViewModel() {

    init {
        aapsLogger.debug(LTag.UI, "BOOST_DASH ChipsViewModel created, activeAPS=${activePlugin.activeAPS?.let { it::class.simpleName }.orEmpty()} algo=${activePlugin.activeAPS?.algorithm}")
        val initial = buildBoostChipState()
        aapsLogger.debug(LTag.UI, "BOOST_DASH Initial boost chip: isBoost=${initial.isBoost} state=${initial.state}")
    }

    @AssistedFactory
    interface Factory {

        fun create(cache: OverviewDataCache): ChipsViewModel
    }

    private val iobCobTicker = flow {
        while (true) {
            emit(Unit)
            delay(150_000L) // 2.5 minutes
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val iobUiState: StateFlow<IobUiState> = iobCobTicker.combine(cache.iobGraphFlow) { _, _ ->
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val total = bolusIob.iob + basalIob.basaliob
        IobUiState(
            text = rh.gs(InterfacesStrings.format_insulin_units, total),
            iobTotal = total
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IobUiState()
    )

    val cobUiState: StateFlow<CobUiState> = iobCobTicker.combine(cache.cobGraphFlow) { _, _ ->
        val cobInfo = iobCobCalculator.getCobInfo("ChipsViewModel COB")
        var cobText = cobInfo.displayText(rh, decimalFormatter)
            ?: rh.gs(CoreUiStrings.value_unavailable_short)
        var carbsReq = 0

        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                val lastCarbsTime = persistenceLayer.getNewestCarbs()?.timestamp ?: 0L
                if (lastCarbsTime < lastRun.lastAPSRun) {
                    cobText += " ${constraintsProcessed.carbsReq}${rh.gs(CoreUiStrings.required)}"
                }
                carbsReq = constraintsProcessed.carbsReq
            }
        }

        CobUiState(text = cobText, carbsReq = carbsReq, cobValue = cobInfo.displayCob ?: 0.0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CobUiState()
    )

    // The IOB graph is published before the loop runs in the same calculation chain, so on its
    // own it would show the previous loop's ratio and variable ISF. Predictions are published
    // right after the loop ran on the master, and right after a device status came in on a
    // client, so they carry the fresh values on both sides.
    val sensitivityUiState: StateFlow<SensitivityUiState> = combine(iobCobTicker, cache.iobGraphFlow, cache.predictionsFlow) { _, _, _ ->
        buildSensitivityUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SensitivityUiState()
    )

    val boostChipState: StateFlow<BoostChipState> = flow {
        while (true) { emit(buildBoostChipState()); delay(30_000L) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = buildBoostChipState()
    )

    private fun buildBoostChipState(): BoostChipState {
        val aps = activePlugin.activeAPS
        aapsLogger.debug(LTag.UI, "buildBoostChipState: activeAPS=${aps?.let { it::class.simpleName }.orEmpty()} algo=${aps?.algorithm}")
        if (aps == null) return BoostChipState()
        if (aps.algorithm != APSResult.Algorithm.BOOST) {
            aapsLogger.debug(LTag.UI, "buildBoostChipState: not BOOST, algo=${aps.algorithm}")
            return BoostChipState()
        }
        val result = aps.lastAPSResult
        aapsLogger.debug(LTag.UI, "buildBoostChipState: lastAPSResult=$result boostV5_state=${(result?.rawData() as? RT)?.boostV5_state}")
        if (result == null) return BoostChipState(isBoost = true, state = "BOOST", color = 0xFF4CAF50L)
        val raw = result.rawData()
        if (raw !is RT) return BoostChipState(isBoost = true, state = "BOOST", color = 0xFF4CAF50L)
        val v5State = raw.boostV5_state
        // formatTemplate, not String.format: the app's own formatter, and the only one that also
        // works on the targets that have no java.util.Formatter.
        val dynIsf = result.variableSens?.let { formatTemplate("%.0f", listOf(it)) } ?: "--"
        val tdd = raw.tdd?.let { formatTemplate("%.1fU", listOf(it)) } ?: "--"
        val profilePct = raw.boostProfileSwitch?.let { "${it}%" } ?: "--"
        val activity = v5State?.let { raw.boostV5_score?.let { score -> formatTemplate("%.2f", listOf(score)) } ?: "--" }
            ?: raw.boostActive?.let { if (it) "ACTIVE" else "INACTIVE" } ?: "--"
        val iob = raw.IOB?.let { formatTemplate("%.1fU", listOf(it)) } ?: "--"
        val boostTier = raw.boostTier ?: "--"
        val mlRisk = raw.mlHypoRisk?.let { formatTemplate("%.2f", listOf(it)) } ?: "--"

        return if (v5State != null) {
            BoostChipState(
                state = v5State,
                color = when (v5State.uppercase()) {
                    "OBSERVING" -> 0xFFFFC107L
                    "CONFIRMED" -> 0xFFFF6E40L
                    "COMMITTED" -> 0xFFFF9800L
                    "RECOVERING" -> 0xFF26C6DAL
                    else -> 0xFF78909CL
                },
                detail = raw.boostV5_actionMult?.let { formatTemplate("×%.2f", listOf(it)) } ?: "",
                isBoost = true,
                dynIsf = dynIsf, tdd = tdd, profilePct = profilePct, activity = activity, iob = iob,
                boostTier = boostTier, mlRisk = mlRisk
            )
        } else {
            BoostChipState(
                state = raw.boostTier ?: "BOOST",
                color = 0xFF4CAF50L,
                tier = raw.boostTier ?: "",
                isBoost = true,
                dynIsf = dynIsf, tdd = tdd, profilePct = profilePct, activity = activity, iob = iob,
                boostTier = boostTier, mlRisk = mlRisk
            )
        }
    }

    private suspend fun buildSensitivityUiState(): SensitivityUiState {
        // Worked out in one place for the chip, its dialog and the watch - see SensitivityOverview
        val data = sensitivityOverview.build()
        return SensitivityUiState(
            asText = data.asText,
            isfFrom = data.isfFrom,
            isfTo = data.isfTo,
            dialogText = data.lines.joinToString("\n"),
            ratio = data.ratio,
            isEnabled = data.isEnabled,
            hasData = data.hasData
        )
    }

    fun showIobInfo() {
        viewModelScope.launch {
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            val total = bolusIob.iob + basalIob.basaliob
            val message =
                rh.gs(CoreUiStrings.bolus_iob_label) + ": " + rh.gs(InterfacesStrings.format_insulin_units, bolusIob.iob) + "\n" +
                    rh.gs(CoreUiStrings.treatments_wizard_basaliob_label) + ": " + rh.gs(InterfacesStrings.format_insulin_units, basalIob.basaliob) + "\n" +
                    rh.gs(CoreUiStrings.iob) + ": " + rh.gs(InterfacesStrings.format_insulin_units, total)
            rxBus.send(
                EventShowDialog.Ok(
                    title = rh.gs(CoreUiStrings.iob),
                    message = message
                )
            )
        }
    }
}
