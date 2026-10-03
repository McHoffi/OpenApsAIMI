package app.aaps.plugins.sync.nsclientV3.activity

/**
 * Kind of Nightscout activity record this app uploads.
 *
 * The two kinds are stored in one local table but must not share an id space,
 * because a heart rate sample and a step count can carry the same event time.
 */
enum class ActivityUploadKind(val wireType: String, private val idTag: Int) {

    HeartRate(wireType = "hr-bpm", idTag = 0x48),
    Steps(wireType = "steps-total", idTag = 0x53),
    ;

    internal fun idTagByte(): Byte = idTag.toByte()
}

/**
 * Stable 24-hex `_id` for one activity record.
 *
 * Classic Nightscout only accepts `_id` as a 24-character hex ObjectId, or
 * nothing at all. Omitting it makes Nightscout invent one per request, so a
 * retry would duplicate the record. We always send an id derived from the kind
 * and the event time, so resending the same sample upserts instead.
 *
 * Nocturne dedupes heart rate and `metric` step counts on this same field, so
 * one id protects both hops.
 */
fun activityUploadId(kind: ActivityUploadKind, timestampMs: Long): String {
    // 12 bytes -> 24 hex chars, the length Nightscout requires.
    val bytes = ByteArray(12)
    bytes[0] = kind.idTagByte()
    // Big-endian event time in bytes 1..6. 48 bits is enough for any real
    // sample time and leaves bytes 7..11 zero.
    for (i in 1..6) {
        val shift = 8 * (6 - i)
        bytes[i] = ((timestampMs ushr shift) and 0xFF).toByte()
    }
    return bytesToHex24(bytes)
}

private fun bytesToHex24(bytes: ByteArray): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        out.append(digits[v ushr 4])
        out.append(digits[v and 0x0F])
    }
    return out.toString()
}
