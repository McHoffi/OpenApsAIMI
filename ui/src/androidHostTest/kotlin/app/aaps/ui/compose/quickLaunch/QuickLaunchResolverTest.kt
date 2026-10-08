package app.aaps.ui.compose.quickLaunch

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.shared.tests.generatedTextResolver
import app.aaps.ui.compose.navigation.ElementAvailability
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

/** The scene text the QuickLaunch tooltip, overflow menu and configure rows show. */
internal class QuickLaunchResolverTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var sceneRepository: SceneStore
    @Mock private lateinit var elementAvailability: ElementAvailability

    private lateinit var sut: QuickLaunchResolver

    private val twoActions = listOf(SceneAction.SmbToggle(enabled = false), SceneAction.LoopModeChange(mode = RM.Mode.CLOSED_LOOP_LGS))

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        sut = QuickLaunchResolver(
            preferences, quickWizard, automation, activePlugin, profileRepository,
            sceneRepository, generatedTextResolver(), elementAvailability
        )
    }

    // A saved static button must survive even when its source plugin is not active, because
    // MainViewModel.refreshQuickLaunch deletes every entry isValid() rejects.
    @Test
    fun `static actions stay valid when their source plugin is not active`() {
        whenever(elementAvailability.isAvailable(ElementType.CALIBRATION)).thenReturn(false)
        whenever(elementAvailability.isAvailable(ElementType.CGM_XDRIP)).thenReturn(false)

        assertThat(sut.isValid(QuickLaunchAction.Calibration)).isTrue()
        assertThat(sut.isValid(QuickLaunchAction.Cgm)).isTrue()
        assertThat(sut.isValid(QuickLaunchAction.Afrezza)).isTrue()
        assertThat(sut.isValid(QuickLaunchAction.EversenseCalibration)).isTrue()
    }

    // ... but it renders greyed out, so the user can see it is not usable right now.
    @Test
    fun `an unavailable static action resolves as not enabled`() {
        whenever(elementAvailability.isAvailable(ElementType.CALIBRATION)).thenReturn(false)

        assertThat(sut.resolveItem(QuickLaunchAction.Calibration).enabled).isFalse()
    }

    @Test
    fun `an available static action resolves as enabled`() {
        whenever(elementAvailability.isAvailable(ElementType.CALIBRATION)).thenReturn(true)

        assertThat(sut.resolveItem(QuickLaunchAction.Calibration).enabled).isTrue()
    }

    @Test
    fun `a quick wizard action with an unknown guid is not valid`() {
        assertThat(sut.isValid(QuickLaunchAction.QuickWizardAction("does-not-exist"))).isFalse()
    }

    // Same line as Manage -> Scenes, so a scene reads the same on every surface.
    @Test
    fun `scene description is the scene summary`() {
        whenever(sceneRepository.getScene("s1")).thenReturn(
            Scene(id = "s1", name = "Night", defaultDurationMinutes = 120, actions = twoActions)
        )

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("s1"))).isEqualTo("2 actions, 2 h")
    }

    @Test
    fun `scene description names the follow-up scene`() {
        whenever(sceneRepository.getScene("s1")).thenReturn(
            Scene(id = "s1", name = "Night", defaultDurationMinutes = 120, actions = twoActions, endAction = SceneEndAction.ChainScene("s2"))
        )
        whenever(sceneRepository.getScene("s2")).thenReturn(Scene(id = "s2", name = "Cooldown"))

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("s1"))).isEqualTo("2 actions, 2 h → Cooldown")
    }

    // A glance surface says nothing about a deleted follow-up; the scene list is where that is reported.
    @Test
    fun `scene description leaves out a deleted follow-up`() {
        whenever(sceneRepository.getScene("s1")).thenReturn(
            Scene(id = "s1", name = "Night", defaultDurationMinutes = 120, actions = twoActions, endAction = SceneEndAction.ChainScene("gone"))
        )
        whenever(sceneRepository.getScene("gone")).thenReturn(null)

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("s1"))).isEqualTo("2 actions, 2 h")
    }

    @Test
    fun `indefinite scene says so instead of a duration`() {
        whenever(sceneRepository.getScene("s1")).thenReturn(
            Scene(id = "s1", name = "Night", defaultDurationMinutes = 0, actions = twoActions)
        )

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("s1"))).isEqualTo("2 actions, Indefinite (manual end)")
    }

    @Test
    fun `unknown scene has no description`() {
        whenever(sceneRepository.getScene("missing")).thenReturn(null)

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("missing"))).isNull()
    }
}
