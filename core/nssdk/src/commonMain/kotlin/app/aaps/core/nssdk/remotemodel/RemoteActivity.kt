package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One record of Nightscout `POST /api/v1/activity` (API v1 only — there is no v3 equivalent).
 *
 * Field names are the xDrip+/Nocturne activity shape on purpose. Nocturne's Nightscout-Connector
 * runs `ActivityDecomposer` over these documents:
 *
 * - `bpm` present → heart rate
 * - `metric` present → step count, and it **is** summed into step totals
 * - `steps` + `type == "steps-total"` → step count flagged `PossibleRunningTotalFlag`, stored but
 *   **never counted**. So steps must go out as `metric`, not `steps`.
 *
 * `created_at` is load-bearing for the Nocturne connector: it pages activity on `created_at`, not on
 * `timeStamp`. Both carry the same event time.
 *
 * `_id` must be a 24-character hex ObjectId or classic Nightscout rejects the document with 400.
 * Sending a stable id is what makes a retry an upsert instead of a duplicate, on Nightscout and in
 * Nocturne (which dedupes heart rate and `metric` step counts on this field).
 *
 * Public, unlike its neighbours here, because the batch is assembled in `plugins/sync` from the
 * `HR` and `SC` rows. The wire shape is the only shape — a localmodel twin would be a copy.
 */
@Serializable
data class RemoteActivity(
    @SerialName("_id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("timeStamp") val timeStamp: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("bpm") val bpm: Int? = null,
    @SerialName("metric") val metric: Int? = null,
    @SerialName("source") val source: Int? = null,
    @SerialName("device") val device: String? = null,
)
