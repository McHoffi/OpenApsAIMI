package app.aaps.core.interfaces.rx.weardata

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Covers [LoopStatusData] + nested [TempTargetInfo]/[TargetRange]/[OapsResultInfo]/LoopMode via JSON round-trip. */
class LoopStatusDataTest {

    private val json = Json

    @Test
    fun fullRoundTrip_preservesAllFields() {
        val data = LoopStatusData(
            timestamp = 1000L,
            loopMode = LoopStatusData.LoopMode.CLOSED,
            apsName = "SMB",
            lastRun = 2000L,
            lastEnact = 3000L,
            tempTarget = TempTargetInfo(targetDisplay = "100 mg/dl", endTime = 5000L, durationMinutes = 30, units = "mg/dl"),
            autosensTarget = "1.0",
            defaultRange = TargetRange(lowDisplay = "80", highDisplay = "120", targetDisplay = "100", units = "mg/dl"),
            oapsResult = OapsResultInfo(
                changeRequested = true, isLetTempRun = false, rate = 1.2, ratePercent = 120,
                duration = 30, reason = "test", smbAmount = 0.5
            ),
            modeEndTime = 9000L
        )
        val encoded = json.encodeToString(LoopStatusData.serializer(), data)
        val restored = json.decodeFromString(LoopStatusData.serializer(), encoded)
        assertEquals(data, restored)
        assertEquals(30, restored.tempTarget?.durationMinutes)
        assertEquals("120", restored.defaultRange.highDisplay)
        assertEquals(1.2, restored.oapsResult?.rate)
        assertEquals(9000L, restored.modeEndTime)
    }

    @Test
    fun missingModeEndTimeDecodesAsNull() {
        // payload shape from a phone older than the modeEndTime field
        val legacy = """{"timestamp":0,"loopMode":"SUSPENDED","apsName":null,"lastRun":null,"lastEnact":null,"tempTarget":null,""" +
            """"defaultRange":{"lowDisplay":"a","highDisplay":"b","targetDisplay":"c","units":"u"},"oapsResult":null}"""
        assertNull(json.decodeFromString(LoopStatusData.serializer(), legacy).modeEndTime)
    }

    @Test
    fun profileAndSceneMarksRoundTrip() {
        val data = LoopStatusData(
            timestamp = 0L,
            loopMode = LoopStatusData.LoopMode.CLOSED,
            apsName = null,
            lastRun = null,
            lastEnact = null,
            tempTarget = TempTargetInfo(targetDisplay = "100 mg/dl", endTime = 5000L, durationMinutes = 30, units = "mg/dl", fromScene = true),
            defaultRange = TargetRange("70", "180", "110", "mg/dl"),
            oapsResult = null,
            activeScene = ActiveSceneInfo(name = "Sleep", endTime = 9000L, chainTargetName = null),
            profile = ProfileInfo(name = "Night", percentage = 120, timeshiftHours = -2, endTime = 9000L, returnsTo = "Default", fromScene = true),
            modeFromScene = true,
            sensitivity = listOf("Autosens Value: 95%", "ISF (profile): 3.5")
        )
        val restored = json.decodeFromString(LoopStatusData.serializer(), json.encodeToString(LoopStatusData.serializer(), data))
        assertThat(restored).isEqualTo(data)
    }

    @Test
    fun missingProfileAndSceneMarksDecodeAsAbsent() {
        // payload shape from a phone older than the profile card and the scene marks
        val legacy = """{"timestamp":0,"loopMode":"CLOSED","apsName":null,"lastRun":null,"lastEnact":null,""" +
            """"tempTarget":{"targetDisplay":"100","endTime":5000,"durationMinutes":30,"units":"mg/dl"},""" +
            """"defaultRange":{"lowDisplay":"a","highDisplay":"b","targetDisplay":"c","units":"u"},"oapsResult":null}"""
        val decoded = json.decodeFromString(LoopStatusData.serializer(), legacy)
        assertThat(decoded.profile).isNull()
        assertThat(decoded.tempTarget?.fromScene).isFalse()
        assertThat(decoded.modeFromScene).isFalse()
        assertThat(decoded.sensitivity).isEmpty()
    }

    @Test
    fun roundTrip_withNullableFieldsNull() {
        val data = LoopStatusData(
            timestamp = 0L,
            loopMode = LoopStatusData.LoopMode.DISABLED,
            apsName = null,
            lastRun = null,
            lastEnact = null,
            tempTarget = null,
            defaultRange = TargetRange("70", "180", "110", "mg/dl"),
            oapsResult = null
        )
        val encoded = json.encodeToString(LoopStatusData.serializer(), data)
        assertEquals(data, json.decodeFromString(LoopStatusData.serializer(), encoded))
    }

    @Test
    fun everyLoopModeRoundTrips() {
        for (mode in LoopStatusData.LoopMode.entries) {
            val d = LoopStatusData(0L, mode, null, null, null, null, null, TargetRange("a", "b", "c", "u"), null)
            val restored = json.decodeFromString(LoopStatusData.serializer(), json.encodeToString(LoopStatusData.serializer(), d))
            assertEquals(mode, restored.loopMode)
        }
    }
}
