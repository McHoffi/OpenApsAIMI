package app.aaps.core.interfaces.overview.graph

import app.aaps.core.interfaces.overview.graph.GraphConfig.Companion.MAX_GRAPH_HEIGHT_DP
import kotlinx.coroutines.flow.StateFlow

/**
 * Series types available for all graphs (BG primary and secondary).
 * Each type maps to a data flow in OverviewDataCache.
 * IOB implicitly includes bolus markers, COB implicitly includes carbs markers.
 *
 * Not all types are allowed on all graphs — the UI enforces allowed sets per graph type.
 * BG graph: only BASAL, ACTIVITY.
 * Secondary graphs: all types.
 */
enum class SeriesType {

    BASAL,  // Profile + actual delivered basal step-fill on BG graph
    IOB,
    ABS_IOB,
    COB,
    BGI,
    DEVIATIONS,
    SENSITIVITY,
    VAR_SENSITIVITY,
    DEV_SLOPE,
    HEART_RATE,
    STEPS,
    ACTIVITY,
    PREDICTIONS,
    MODES,
    PULSE,
    TIR,
    BOLUS,   // SMB triangle markers on BG graph (fixed at bottom)

    // AutoISF-specific series (only populated when AutoISF plugin is active)
    IOB_THRESHOLD,  // Effective IOB threshold (insulin units, same scale as IOB)
    FINAL_ISF,      // Final combined ISF factor
    ACCE_ISF,       // Acceleration ISF factor
    BG_ISF,         // BG-based ISF factor
    PP_ISF,         // Post-prandial ISF factor
    DURA_ISF,       // Duration ISF factor

    // BOOST algorithm data panel
    BOOST,          // DynISF | TDD | IOB | Status card (active only when BOOST is the APS)

    // MealHypothesis step line (renders nothing when BOOST is not the active algorithm)
    MHS
}

/**
 * Secondary graph entry: series list + per-graph height (dp).
 * Max 5 series per graph. Height is per-graph user-adjustable.
 */
data class SecondaryGraph(
    val series: List<SeriesType>,
    val height: Int = GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
)

/**
 * Configuration for the overview graphs.
 *
 * Fixed graphs (not removable):
 * - BG graph: blood glucose with optional activity overlay (toggled via [bgOverlays]).
 * - IOB graph: IOB line with bolus markers, plus optional flipped basal and optional activity
 *   overlay (both toggled via [iobOverlays]).
 *
 * @param bgOverlays       Overlay toggles for the BG graph (currently only [SeriesType.ACTIVITY]).
 * @param iobOverlays      Overlay toggles for the fixed IOB graph: [SeriesType.BASAL] is the
 *   flipped basal half, [SeriesType.ACTIVITY] the activity line. Default is basal on, activity
 *   off. Restore of the strip keeps whatever the user last chose here.
 * @param secondaryGraphs  Ordered list of user-configurable secondary graph configurations.
 *   IOB cannot appear here (it has a dedicated fixed slot). Each graph is a List (not Set) to
 *   preserve selection order:
 *   - list[0] = left axis (start), list[1] = right axis (end).
 *   Max 5 series per graph. FIFO: adding a 6th deselects the oldest.
 */
data class GraphConfig(
    val bgOverlays: List<SeriesType> = listOf(SeriesType.BASAL, SeriesType.BOLUS, SeriesType.ACTIVITY),
    val showIobGraph: Boolean = true,
    val iobOverlays: List<SeriesType> = listOf(SeriesType.BASAL),
    val bgHeight: Int = DEFAULT_GRAPH_HEIGHT_DP,
    val iobHeight: Int = DEFAULT_GRAPH_HEIGHT_DP,
    val secondaryGraphs: List<SecondaryGraph> = listOf(
        SecondaryGraph(listOf(SeriesType.COB))
    )
) {

    companion object {

        /** Maximum number of secondary graphs allowed */
        const val MAX_SECONDARY_GRAPHS = 5

        /** Smallest graph height the user can pick, in dp (half the default) */
        const val MIN_GRAPH_HEIGHT_DP = 50

        /** Graph height a new graph starts with, in dp. The user can change it between [MIN_GRAPH_HEIGHT_DP] and [MAX_GRAPH_HEIGHT_DP] */
        const val DEFAULT_GRAPH_HEIGHT_DP = 100

        /** Maximum graph height in dp (2.5x default) */
        const val MAX_GRAPH_HEIGHT_DP = 250
    }
}

/**
 * Repository for graph configuration persistence.
 * Loads/saves GraphConfig and exposes it as a reactive StateFlow.
 */
interface GraphConfigRepository {

    /** Current graph configuration, updated reactively on changes */
    val graphConfigFlow: StateFlow<GraphConfig>

    /** Update and persist graph configuration */
    fun update(config: GraphConfig)
}
