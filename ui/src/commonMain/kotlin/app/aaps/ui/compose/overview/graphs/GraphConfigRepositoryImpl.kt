package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class GraphConfigRepositoryImpl(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) : GraphConfigRepository {

    // Not the main dispatcher: all this scope does is parse a preference string into a StateFlow,
    // which is safe from any thread, and reaching for `Dispatchers.Main` in a constructor makes the
    // object graph unbuildable in a plain JVM unit test (no main looper).
    private val scope = CoroutineScope(SupervisorJob() + aapsIoDispatcher)
    private val _graphConfigFlow = MutableStateFlow(load())
    override val graphConfigFlow: StateFlow<GraphConfig> = _graphConfigFlow.asStateFlow()

    init {
        // Sync from preferences when changed externally (e.g. import/restore)
        scope.launch {
            preferences.observe(StringNonKey.ComposeGraphConfig)
                .drop(1) // Skip initial (already loaded)
                .collect { json -> _graphConfigFlow.value = parseJson(json) }
        }
    }

    override fun update(config: GraphConfig) {
        _graphConfigFlow.value = config
        preferences.put(StringNonKey.ComposeGraphConfig, toJson(config))
    }

    private fun load(): GraphConfig = parseJson(preferences.get(StringNonKey.ComposeGraphConfig))

    private fun parseJson(json: String): GraphConfig {
        if (json.isBlank()) return GraphConfig()
        return try {
            fromJson(json)
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to parse graph config, using defaults", e)
            GraphConfig()
        }
    }

    companion object {

        private const val KEY_SECONDARY_GRAPHS = "secondaryGraphs"
        private const val KEY_BG_OVERLAYS = "bgOverlays"
        private const val KEY_SHOW_IOB_GRAPH = "showIobGraph"
        private const val KEY_IOB_OVERLAYS = "iobOverlays"

        /**
         * Written with [KEY_IOB_OVERLAYS] once basal became a real toggle. Its absence means the
         * list was written when basal was always drawn on the IOB strip and could not be stored,
         * so the read path adds [SeriesType.BASAL] back (see [fromJson]).
         */
        private const val KEY_IOB_OVERLAYS_V2 = "iobOverlaysV2"
        private const val KEY_BG_HEIGHT = "bgHeight"
        private const val KEY_IOB_HEIGHT = "iobHeight"
        private const val KEY_SERIES = "series"
        private const val KEY_HEIGHT = "height"

        private fun overlaysToJson(overlays: List<SeriesType>) = buildJsonArray {
            for (type in overlays) add(type.name)
        }

        /**
         * Absent means "never written", so the caller's default applies. An empty array means the
         * user turned them all off, which is a different thing and is kept.
         */
        private fun overlaysFromJson(arr: JsonArray?, default: List<SeriesType>): List<SeriesType> {
            if (arr == null) return default
            val out = mutableListOf<SeriesType>()
            for (element in arr) {
                val type = element.seriesTypeOrNull() ?: continue // Skip unknown types (forward compat)
                if (type !in out) out.add(type)
            }
            return out
        }

        fun toJson(config: GraphConfig): String =
            buildJsonObject {
                put(KEY_BG_OVERLAYS, overlaysToJson(config.bgOverlays))
                put(KEY_SHOW_IOB_GRAPH, config.showIobGraph)
                put(KEY_IOB_OVERLAYS, overlaysToJson(config.iobOverlays))
                put(KEY_IOB_OVERLAYS_V2, true)
                put(KEY_BG_HEIGHT, config.bgHeight)
                put(KEY_IOB_HEIGHT, config.iobHeight)
                put(
                    KEY_SECONDARY_GRAPHS,
                    buildJsonArray {
                        for (graph in config.secondaryGraphs) {
                            addJsonObject {
                                put(KEY_SERIES, buildJsonArray { for (series in graph.series) add(series.name) })
                                put(KEY_HEIGHT, graph.height)
                            }
                        }
                    }
                )
            }.toString()

        private fun parseSeriesArray(seriesArray: JsonArray): List<SeriesType> {
            val series = mutableListOf<SeriesType>()
            for (element in seriesArray) {
                val type = element.seriesTypeOrNull() ?: continue // Skip unknown series (forward compat)
                // IOB is now a fixed graph — strip it from configurable graphs (legacy migration)
                if (type == SeriesType.IOB) continue
                if (type !in series) series.add(type)
            }
            return series.take(5)
        }

        fun fromJson(json: String): GraphConfig {
            val obj = Json.parseToJsonElement(json) as JsonObject
            val bgOverlays = overlaysFromJson(obj.array(KEY_BG_OVERLAYS), listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS))
            val iobOverlays = overlaysFromJson(obj.array(KEY_IOB_OVERLAYS), listOf(SeriesType.BASAL)).let { parsed ->
                if (obj.boolean(KEY_IOB_OVERLAYS_V2, false)) {
                    parsed
                } else {
                    // Legacy document: basal was always on and was not a stored toggle.
                    // Keep it on so an upgrade does not silently drop the basal half of the panel.
                    (listOf(SeriesType.BASAL) + parsed).distinct()
                }
            }
            val bgHeight = obj.height(KEY_BG_HEIGHT)
            val iobHeight = obj.height(KEY_IOB_HEIGHT)
            val graphs = mutableListOf<SecondaryGraph>()
            for (raw in obj.array(KEY_SECONDARY_GRAPHS).orEmpty()) {
                // Legacy format: element is an array of series names.
                // New format: element is an object { series: [...], height: Int }.
                val (series, height) = when (raw) {
                    is JsonArray  -> parseSeriesArray(raw) to GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                    is JsonObject -> (raw.array(KEY_SERIES)?.let { parseSeriesArray(it) } ?: emptyList()) to raw.height(KEY_HEIGHT)
                    else          -> emptyList<SeriesType>() to GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                }
                if (series.isNotEmpty()) graphs.add(SecondaryGraph(series, height))
            }
            return GraphConfig(
                bgOverlays = bgOverlays,
                showIobGraph = obj.boolean(KEY_SHOW_IOB_GRAPH, true),
                iobOverlays = iobOverlays,
                bgHeight = bgHeight,
                iobHeight = iobHeight,
                secondaryGraphs = graphs
            )
        }

        /** The named array, or null when it is absent or is something other than an array. */
        private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

        /**
         * A stored height, defaulted when absent and always brought inside the range the height row
         * offers. The lower bound is [GraphConfig.MIN_GRAPH_HEIGHT_DP] and not the default: this
         * reads back what that row wrote, so clamping to the default would silently raise a smaller
         * height the user picked (the panel jumped back to 100 dp on every change).
         */
        private fun JsonObject.height(key: String): Int =
            ((this[key] as? JsonPrimitive)?.let { runCatching { it.int }.getOrNull() } ?: GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
                .coerceIn(GraphConfig.MIN_GRAPH_HEIGHT_DP, GraphConfig.MAX_GRAPH_HEIGHT_DP)

        /** A stored flag, defaulted when it is absent or is not a boolean. */
        private fun JsonObject.boolean(key: String, default: Boolean): Boolean =
            ((this[key] as? JsonPrimitive)?.let { runCatching { it.boolean }.getOrNull() } ?: default)

        /** The series this element names, or null if it is not a name this version knows. */
        private fun JsonElement.seriesTypeOrNull(): SeriesType? =
            (this as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.let { name -> SeriesType.entries.firstOrNull { it.name == name } }
    }
}
