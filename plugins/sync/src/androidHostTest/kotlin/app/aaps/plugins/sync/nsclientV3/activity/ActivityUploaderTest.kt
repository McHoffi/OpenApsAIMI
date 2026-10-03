package app.aaps.plugins.sync.nsclientV3.activity

import app.aaps.core.data.model.HR
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.StepDevices
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.core.nssdk.remotemodel.RemoteActivity
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.plugins.sync.nsclientV3.keys.NsclientLongKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.IOException

/**
 * Progress rules of the activity upload. The wire shape is pinned in `RemoteActivityWireFormatTest`
 * and the cursor arithmetic in `ActivityUploadCursorTest`. This one covers when the cursor moves,
 * when it is held, and what a 403 does.
 */
internal class ActivityUploaderTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var config: Config
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var nsAndroidClient: NSAndroidClient

    private lateinit var sut: ActivityUploader

    private val now = 3_000L

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(nsAndroidClient)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(dateUtil.toISOString(any())).thenReturn("2026-09-30T06:40:00.000Z")
        // Umbrella on by default. The two tests below turn each gate off on purpose.
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        sut = ActivityUploader(aapsLogger, config, preferences, persistenceLayer, dateUtil, nsClientV3Plugin)
    }

    private fun hr(timestamp: Long) = HR(
        timestamp = timestamp,
        duration = 60_000L,
        beatsPerMinute = 80.0,
        device = "garmin",
    )

    private fun sc(steps5min: Int, timestamp: Long, device: String) = SC(
        timestamp = timestamp,
        duration = 300_000L,
        steps5min = steps5min,
        steps10min = 0,
        steps15min = 0,
        steps30min = 0,
        steps60min = 0,
        steps180min = 0,
        device = device,
    )

    @Test
    fun `master upload switch off does nothing at all`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(true)

        sut.uploadPending()

        verifyNoInteractions(persistenceLayer, nsAndroidClient)
        verify(preferences, never()).put(any<NsclientLongKey>(), any())
    }

    @Test
    fun `paused sync does nothing at all`() = runTest {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(true)

        sut.uploadPending()

        verifyNoInteractions(persistenceLayer, nsAndroidClient)
        verify(preferences, never()).put(any<NsclientLongKey>(), any())
    }

    @Test
    fun `both switches off does nothing at all`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(false)

        sut.uploadPending()

        verifyNoInteractions(persistenceLayer, nsAndroidClient)
        verify(preferences, never()).put(any<NsclientLongKey>(), any())
    }

    @Test
    fun `first enable starts from now and sends nothing`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(false)
        whenever(preferences.get(NsclientLongKey.HeartRateLastUploadedAt)).thenReturn(ActivityUploadCursor.NEVER)

        sut.uploadPending()

        verify(preferences).put(NsclientLongKey.HeartRateLastUploadedAt, now)
        verifyNoInteractions(persistenceLayer, nsAndroidClient)
    }

    @Test
    fun `2xx advances the cursor to the newest event time`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(false)
        whenever(preferences.get(NsclientLongKey.HeartRateLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getHeartRatesFromTimeToTime(1_001L, now))
            .thenReturn(listOf(hr(1_500L), hr(2_000L)))
        whenever(nsAndroidClient.createActivities(any())).thenReturn(CreateUpdateResponse(201, null))

        sut.uploadPending()

        verify(nsAndroidClient).createActivities(any())
        verify(preferences).put(NsclientLongKey.HeartRateLastUploadedAt, 2_000L)
    }

    @Test
    fun `a non-2xx answer holds the cursor`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(false)
        whenever(preferences.get(NsclientLongKey.HeartRateLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getHeartRatesFromTimeToTime(1_001L, now))
            .thenReturn(listOf(hr(1_500L)))
        whenever(nsAndroidClient.createActivities(any())).thenReturn(CreateUpdateResponse(500, "boom"))

        sut.uploadPending()

        verify(preferences, never()).put(eq(NsclientLongKey.HeartRateLastUploadedAt), any())
    }

    @Test
    fun `a thrown error holds the cursor`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(false)
        whenever(preferences.get(NsclientLongKey.HeartRateLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getHeartRatesFromTimeToTime(1_001L, now))
            .thenReturn(listOf(hr(1_500L)))
        // thenAnswer, not thenThrow: createActivities declares no checked exception.
        whenever(nsAndroidClient.createActivities(any())).thenAnswer { throw IOException("offline") }

        sut.uploadPending()

        verify(preferences, never()).put(eq(NsclientLongKey.HeartRateLastUploadedAt), any())
    }

    @Test
    fun `403 pauses the type until the next app run`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(false)
        whenever(preferences.get(NsclientLongKey.HeartRateLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getHeartRatesFromTimeToTime(1_001L, now))
            .thenReturn(listOf(hr(1_500L)))
        whenever(nsAndroidClient.createActivities(any())).thenReturn(CreateUpdateResponse(403, "no"))

        sut.uploadPending()
        sut.uploadPending()

        // One attempt only: the pause is what stops the retry loop.
        verify(nsAndroidClient, times(1)).createActivities(any())
        verify(preferences, never()).put(eq(NsclientLongKey.HeartRateLastUploadedAt), any())
    }

    @Test
    fun `403 on heart rate does not pause steps`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(true)
        whenever(preferences.get(NsclientLongKey.HeartRateLastUploadedAt)).thenReturn(1_000L)
        whenever(preferences.get(NsclientLongKey.StepsLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getHeartRatesFromTimeToTime(1_001L, now))
            .thenReturn(listOf(hr(1_500L)))
        whenever(persistenceLayer.getStepsCountFromTimeToTime(1_001L, now))
            .thenReturn(listOf(sc(steps5min = 10, timestamp = 1_500L, device = StepDevices.GARMIN_WATCHFACE)))
        whenever(nsAndroidClient.createActivities(any()))
            .thenReturn(CreateUpdateResponse(403, "no"), CreateUpdateResponse(201, null))

        sut.uploadPending()

        verify(nsAndroidClient, times(2)).createActivities(any())
        verify(preferences).put(NsclientLongKey.StepsLastUploadedAt, 1_500L)
        verify(preferences, never()).put(eq(NsclientLongKey.HeartRateLastUploadedAt), any())
    }

    @Test
    fun `a ciq only batch is sent`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(true)
        whenever(preferences.get(NsclientLongKey.StepsLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(1_001L, now))
            .thenReturn(listOf(sc(steps5min = 76, timestamp = 1_500L, device = StepDevices.GARMIN_CIQ)))
        whenever(nsAndroidClient.createActivities(any())).thenReturn(CreateUpdateResponse(201, null))

        sut.uploadPending()

        verify(nsAndroidClient).createActivities(argThat<List<RemoteActivity>> {
            size == 1 && single().metric == 76
        })
        verify(preferences).put(NsclientLongKey.StepsLastUploadedAt, 1_500L)
    }

    @Test
    fun `a non-garmin only batch is skipped but still advances`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(true)
        whenever(preferences.get(NsclientLongKey.StepsLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(1_001L, now))
            .thenReturn(listOf(sc(steps5min = 76, timestamp = 1_500L, device = "HealthConnect")))

        sut.uploadPending()

        verifyNoInteractions(nsAndroidClient)
        // Past the batch, so the same rows are not scanned again next cycle.
        verify(preferences).put(NsclientLongKey.StepsLastUploadedAt, 1_500L)
    }

    @Test
    fun `only watchface rows are sent in a mixed batch`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(true)
        whenever(preferences.get(NsclientLongKey.StepsLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(1_001L, now))
            .thenReturn(
                listOf(
                    sc(steps5min = 10, timestamp = 1_500L, device = StepDevices.GARMIN_WATCHFACE),
                    sc(steps5min = 20, timestamp = 2_000L, device = StepDevices.GARMIN_CIQ),
                )
            )
        whenever(nsAndroidClient.createActivities(any())).thenReturn(CreateUpdateResponse(201, null))

        sut.uploadPending()

        verify(nsAndroidClient).createActivities(argThat<List<RemoteActivity>> {
            size == 1 && single().metric == 10
        })
        // The cursor covers the whole scanned batch, not only what was sent.
        verify(preferences).put(NsclientLongKey.StepsLastUploadedAt, 2_000L)
    }

    @Test
    fun `an empty window does not write the cursor`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientUploadHeartRate)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadSteps)).thenReturn(false)
        whenever(preferences.get(NsclientLongKey.HeartRateLastUploadedAt)).thenReturn(1_000L)
        whenever(persistenceLayer.getHeartRatesFromTimeToTime(1_001L, now)).thenReturn(emptyList())

        sut.uploadPending()

        verifyNoInteractions(nsAndroidClient)
        verify(preferences, never()).put(eq(NsclientLongKey.HeartRateLastUploadedAt), any())
    }
}
