package app.aaps.core.nssdk

import app.aaps.core.nssdk.remotemodel.RemoteActivity
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Pins the exact v1 activity wire shape. Nocturne's `ActivityDecomposer` keys off these field
 * names, and classic Nightscout rejects `_id` that is not 24-hex.
 */
class RemoteActivityWireFormatTest {

    private fun encoded(record: RemoteActivity): Map<String, String> {
        val text = nsSdkJson.encodeToString(ListSerializer(RemoteActivity.serializer()), listOf(record))
        val first = Json.parseToJsonElement(text).jsonArray.single().jsonObject
        return first.mapValues { (_, v) -> v.jsonPrimitive.content }
    }

    @Test
    fun `heart rate uses hr-bpm and bpm`() {
        val json = encoded(
            RemoteActivity(
                id = "480000000000000000000000",
                type = "hr-bpm",
                timeStamp = 1_780_000_000_000L,
                createdAt = "2026-09-30T06:40:00.000Z",
                bpm = 88,
            )
        )
        assertThat(json.keys).containsExactly("_id", "type", "timeStamp", "created_at", "bpm")
        assertThat(json["_id"]).isEqualTo("480000000000000000000000")
        assertThat(json["type"]).isEqualTo("hr-bpm")
        assertThat(json["timeStamp"]).isEqualTo("1780000000000")
        assertThat(json["created_at"]).isEqualTo("2026-09-30T06:40:00.000Z")
        assertThat(json["bpm"]).isEqualTo("88")
    }

    @Test
    fun `steps use metric and source, never the steps key`() {
        val json = encoded(
            RemoteActivity(
                id = "530000000000000000000000",
                type = "steps-total",
                timeStamp = 1_780_000_000_000L,
                createdAt = "2026-09-30T06:40:00.000Z",
                metric = 76,
                source = 0,
                device = "garmin-watchface",
            )
        )
        assertThat(json.keys).containsExactly("_id", "type", "timeStamp", "created_at", "metric", "source", "device")
        // "steps" + "steps-total" is the shape Nocturne stores but never counts.
        assertThat(json.keys).doesNotContain("steps")
        assertThat(json["type"]).isEqualTo("steps-total")
        assertThat(json["metric"]).isEqualTo("76")
        assertThat(json["source"]).isEqualTo("0")
        assertThat(json["device"]).isEqualTo("garmin-watchface")
        assertThat(json.keys).doesNotContain("bpm")
    }

    @Test
    fun `null fields are omitted from the body`() {
        val json = encoded(
            RemoteActivity(
                id = "480000000000000000000000",
                type = "hr-bpm",
                timeStamp = 1L,
                createdAt = "2026-09-30T06:40:00.000Z",
                bpm = 1,
            )
        )
        assertThat(json.keys).doesNotContain("metric")
        assertThat(json.keys).doesNotContain("source")
        assertThat(json.keys).doesNotContain("device")
    }

    @Test
    fun `batch is a raw json array`() {
        val text = nsSdkJson.encodeToString(
            ListSerializer(RemoteActivity.serializer()),
            listOf(
                RemoteActivity(id = "480000000000000000000000", type = "hr-bpm", timeStamp = 1L, createdAt = "a", bpm = 1),
                RemoteActivity(id = "480000000000000000000001", type = "hr-bpm", timeStamp = 2L, createdAt = "b", bpm = 2),
            )
        )
        val array = Json.parseToJsonElement(text).jsonArray
        assertThat(array).hasSize(2)
    }
}
