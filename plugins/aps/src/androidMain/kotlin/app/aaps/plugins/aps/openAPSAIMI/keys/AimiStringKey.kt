package app.aaps.plugins.aps.openAPSAIMI.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.openAPSAIMI.steps.UnifiedActivityProviderMTR

enum class AimiStringKey(
    override val key: String,
    override val defaultValue: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val entries: Map<String, TextRef> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val hideParentScreenIfHidden: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true
) : StringPreferenceKey {
    PregnancyDueDateString(
        key = "aimi_pregnancy_due_date_string",
        defaultValue = "",
        title = ApsStrings.OApsAIMI_PregnancyDueDate_title,
        summary = ApsStrings.OApsAIMI_PregnancyDueDate_summary
    ),
    RemoteControlPin(
        key = "aimi_remote_control_pin",
        defaultValue = "",
        title = ApsStrings.OApsAIMI_RemoteControlPin_title,
        summary = ApsStrings.OApsAIMI_RemoteControlPin_summary,
        isPin = true
    ),

    OuraPersonalAccessToken(
        key = "aimi_oura_personal_access_token",
        defaultValue = "",
        title = ApsStrings.aimi_oura_pat_title,
        summary = ApsStrings.aimi_oura_pat_summary,
        isPassword = true,
        exportable = false,
    ),

    /** Claude model used by every AIMI LLM path when that path uses Claude (Advisor provider, or Physio LLM provider for Physio). See [app.aaps.plugins.aps.openAPSAIMI.llm.claude.ClaudeModelResolver]. */
    AimiAdvisorClaudeModel(
        key = "aimi_advisor_claude_model",
        defaultValue = "claude-sonnet-5",
        title = ApsStrings.aimi_prefs_claude_model_title,
        summary = ApsStrings.aimi_prefs_claude_model_summary,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            "claude-sonnet-5" to ApsStrings.aimi_prefs_claude_model_sonnet5,
            "claude-fable-5-1" to ApsStrings.aimi_prefs_claude_model_fable51,
            "claude-opus-5-5" to ApsStrings.aimi_prefs_claude_model_opus55,
            "claude-haiku-4-5" to ApsStrings.aimi_prefs_claude_model_haiku45,
        ),
    ),

    /** Steps & heart-rate source (same key as [UnifiedActivityProviderMTR.PREF_KEY_SOURCE_MODE]). */
    ActivitySourceMode(
        key = UnifiedActivityProviderMTR.PREF_KEY_SOURCE_MODE,
        defaultValue = UnifiedActivityProviderMTR.DEFAULT_MODE,
        title = ApsStrings.pref_aimi_steps_source_title,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            UnifiedActivityProviderMTR.MODE_PREFER_WEAR to ApsStrings.pref_aimi_steps_source_wear,
            UnifiedActivityProviderMTR.MODE_AUTO_FALLBACK to ApsStrings.pref_aimi_steps_source_auto,
            UnifiedActivityProviderMTR.MODE_HEALTH_CONNECT_ONLY to ApsStrings.pref_aimi_steps_source_hc,
            UnifiedActivityProviderMTR.MODE_DISABLED to ApsStrings.pref_aimi_steps_source_disabled,
        ),
    ),

    // The three keys below are written by the algorithm, not by a user, so they have no title. An
    // empty literal is how a key says that - it replaced the old `titleResId = 0` sentinel.
    OApsAIMIPkpdStateDominantBranch(
        key = "aimi_pkpd_state_dominant_branch",
        defaultValue = "",
        title = TextRef.Literal(""),
        showInApsMode = false,
        showInNsClientMode = false,
        showInPumpControlMode = false
    ),

    /** Last material `PEAK_GOV:` line for APS console / debugging (RFC H.4). */
    OApsAIMIPkpdLastPeakGovLogLine(
        key = "aimi_pkpd_state_last_peak_gov_log",
        defaultValue = "",
        title = TextRef.Literal(""),
        showInApsMode = false,
        showInNsClientMode = false,
        showInPumpControlMode = false
    ),

    /** Last `PEAK_GOV` line echoed to APS console (dedup in DetermineBasalAIMI2). */
    OApsAIMIPkpdLastPeakGovConsoleEchoed(
        key = "aimi_pkpd_state_last_peak_gov_console_echoed",
        defaultValue = "",
        title = TextRef.Literal(""),
        showInApsMode = false,
        showInNsClientMode = false,
        showInPumpControlMode = false
    ),
}
