package app.aaps.plugins.aps.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.aps.ApsStrings

enum class ApsIntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.URL,
    // urlRef rather than urlResId: a resource id is an Android Int and means nothing off Android.
    override val urlRef: TextRef? = null,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    LinkToDocs(
        key = "aps_link_to_docs",
        title = ApsStrings.openapsama_link_to_preference_json_doc_txt,
        preferenceType = PreferenceType.URL,
        urlRef = ApsStrings.openapsama_link_to_preference_json_doc
    ),
    PkpdSetup(
        key = "aimi_pkpd_setup_compose",
        title = ApsStrings.aimi_pkpd_compose_title,
        summary = ApsStrings.aimi_pkpd_compose_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
    AimiControlCenter(
        key = "aimi_control_center_compose",
        title = ApsStrings.aimi_control_center_entry_title,
        summary = ApsStrings.aimi_control_center_entry_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
    HormonitorViewer(
        key = "aimi_hormonitor_viewer_compose",
        title = ApsStrings.aimi_hormonitor_viewer_title,
        summary = ApsStrings.aimi_hormonitor_viewer_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
    AimiLearnerOverview(
        key = "aimi_learner_overview_compose",
        title = ApsStrings.aimi_learner_overview_title,
        summary = ApsStrings.aimi_learner_overview_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
    AimiSosPermissions(
        key = "aimi_sos_permissions_compose",
        title = ApsStrings.aimi_sos_permissions_title,
        summary = ApsStrings.aimi_sos_permissions_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
    AimiHealthConnectPermissions(
        key = "aimi_physio_hc_permissions_compose",
        title = ApsStrings.aimi_physio_hc_permissions_title,
        summary = ApsStrings.aimi_physio_hc_permissions_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
    AutoIsfProfileAdvisor(
        key = "autoisf_profile_advisor",
        title = ApsStrings.autoisf_advisor_title,
        summary = ApsStrings.autoisf_advisor_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
    AimiPhysioPatternCatalogInfo(
        key = "aimi_physio_pattern_catalog_info",
        title = ApsStrings.aimi_physio_pattern_catalog_title,
        summary = ApsStrings.aimi_physio_pattern_catalog_summary,
        preferenceType = PreferenceType.CLICK,
    ),
    AimiHypoRiskAlarmInfo(
        key = "aimi_hypo_risk_alarm_info",
        title = ApsStrings.hypo_risk_notification_title,
        summary = ApsStrings.aimi_hypo_risk_alarm_summary,
        preferenceType = PreferenceType.CLICK,
    ),
    AimiAdaptationStatus(
        key = "aimi_adaptation_status_compose",
        title = ApsStrings.aimi_adaptation_status_lab_entry_title,
        summary = ApsStrings.aimi_adaptation_status_lab_entry_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
    AimiSettingsAudit(
        key = "aimi_settings_audit_compose",
        title = ApsStrings.aimi_settings_audit_entry_title,
        summary = ApsStrings.aimi_settings_audit_entry_summary,
        preferenceType = PreferenceType.ACTIVITY,
    ),
}
