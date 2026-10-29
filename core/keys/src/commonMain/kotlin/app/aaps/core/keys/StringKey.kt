package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef

enum class StringKey(
    override val key: String,
    override val defaultValue: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesRefs: Map<String, TextRef> = emptyMap(),
    /**
     * Entry labels that are not a resource in this module. Only the units preference needs this:
     * "mg/dL" and "mmol/L" read the same in every language, and the `units_*` strings themselves
     * now live in `:core:ui`, which this module cannot depend on.
     */
    private val entriesLiterals: Map<String, String> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val isHashed: Boolean = false,
    override val validator: StringValidator = StringValidator.NONE,
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val sync: SyncSpec? = null
) : StringPreferenceKey {

    GeneralUnits(
        key = "units",
        defaultValue = "mg/dl",
        title = KeysStrings.pref_title_units,
        preferenceType = PreferenceType.LIST,
        entriesLiterals = mapOf(
            "mg/dl" to "mg/dL",
            "mmol" to "mmol/L"
        ),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    GeneralLanguage(
        key = "language",
        defaultValue = "default",
        title = KeysStrings.pref_title_language,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "default" to KeysStrings.lang_default,
            "en" to KeysStrings.lang_en,
            "af" to KeysStrings.lang_af,
            "bg" to KeysStrings.lang_bg,
            "cs" to KeysStrings.lang_cs,
            "de" to KeysStrings.lang_de,
            "dk" to KeysStrings.lang_dk,
            "fr" to KeysStrings.lang_fr,
            "nl" to KeysStrings.lang_nl,
            "es" to KeysStrings.lang_es,
            "el" to KeysStrings.lang_el,
            "ga" to KeysStrings.lang_ga,
            "it" to KeysStrings.lang_it,
            "ko" to KeysStrings.lang_ko,
            "lt" to KeysStrings.lang_lt,
            "nb" to KeysStrings.lang_nb,
            "pl" to KeysStrings.lang_pl,
            "pt" to KeysStrings.lang_pt,
            "pt_BR" to KeysStrings.lang_pt_br,
            "ro" to KeysStrings.lang_ro,
            "ru" to KeysStrings.lang_ru,
            "sk" to KeysStrings.lang_sk,
            "sv" to KeysStrings.lang_sv,
            "tr" to KeysStrings.lang_tr,
            "zh_TW" to KeysStrings.lang_zh_tw,
            "zh_CN" to KeysStrings.lang_zh_cn
        ),
        defaultedBySM = true,
        // Android 12 has no app languages: AAPS follows the phone there
        visibility = ElementVisibility { it.supportsAppLanguage }
    ),
    // Not the patient: the person using THIS phone. It names this AAPS instance (export file names,
    // the OTP label, the watch), so it stays device-local and carries no SyncSpec - a client must not
    // take the master's name. The stored key keeps its old "patient_name" spelling on purpose, so no
    // installation loses its value.
    GeneralInstanceName(
        key = "patient_name",
        defaultValue = "",
        title = KeysStrings.pref_title_patient_name,
        summary = KeysStrings.pref_summary_patient_name,
        validator = StringValidator.personName()
    ),
    GeneralDarkMode(
        key = "use_dark_mode",
        defaultValue = "dark",
        title = KeysStrings.pref_title_app_color_scheme,
        summary = KeysStrings.pref_summary_theme_switcher,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "dark" to KeysStrings.pref_dark_theme,
            "light" to KeysStrings.pref_light_theme,
            "system" to KeysStrings.pref_follow_system_theme
        ),
        defaultedBySM = true
    ),
    GeneralSkin(
        key = "skin",
        defaultValue = "",
        title = KeysStrings.pref_title_skin,
        preferenceType = PreferenceType.LIST),

    AapsDirectoryUri(key = "aaps_directory", defaultValue = "", title = KeysStrings.pref_title_aaps_directory),

    ProtectionMasterPassword(key = "master_password", defaultValue = "", title = KeysStrings.pref_title_master_password, isPassword = true, isHashed = true),
    ProtectionSettingsPassword(
        key = "settings_password", defaultValue = "", title = KeysStrings.pref_title_settings_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeSettings }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionSettingsPin(
        key = "settings_pin", defaultValue = "", title = KeysStrings.pref_title_settings_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeSettings }, ProtectionType.CUSTOM_PIN.ordinal)
    ),
    ProtectionApplicationPassword(
        key = "application_password", defaultValue = "", title = KeysStrings.pref_title_application_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeApplication }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionApplicationPin(
        key = "application_pin", defaultValue = "", title = KeysStrings.pref_title_application_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeApplication }, ProtectionType.CUSTOM_PIN.ordinal)
    ),
    ProtectionBolusPassword(
        key = "bolus_password", defaultValue = "", title = KeysStrings.pref_title_bolus_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeBolus }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionBolusPin(
        key = "bolus_pin", defaultValue = "", title = KeysStrings.pref_title_bolus_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeBolus }, ProtectionType.CUSTOM_PIN.ordinal)
    ),

    SafetyAge(key = "age", defaultValue = "adult", title = KeysStrings.pref_title_patient_age, preferenceType = PreferenceType.LIST, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    MaintenanceEmail(
        key = "maintenance_logs_email",
        defaultValue = "logs@aaps.app",
        title = KeysStrings.maintenance_email,
        defaultedBySM = true,
        validator = StringValidator.email()
    ),
    MaintenanceIdentification(key = "email_for_crash_report", defaultValue = "", title = KeysStrings.pref_title_identification),
    AutomationLocation(
        key = "location",
        defaultValue = "PASSIVE",
        title = KeysStrings.pref_title_automation_location,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "PASSIVE" to KeysStrings.automation_location_passive,
            "NETWORK" to KeysStrings.automation_location_network,
            "GPS" to KeysStrings.automation_location_gps
        ),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    SmsAllowedNumbers(
        key = "smscommunicator_allowednumbers",
        defaultValue = "",
        title = KeysStrings.smscommunicator_allowednumbers,
        summary = KeysStrings.smscommunicator_allowednumbers_summary,
        validator = StringValidator.multiPhone()
    ),
    SmsOtpPassword(
        key = "smscommunicator_otp_password",
        defaultValue = "",
        title = KeysStrings.smscommunicator_otp_pin,
        summary = KeysStrings.smscommunicator_otp_pin_summary,
        dependency = BooleanKey.SmsAllowRemoteCommands,
        isPassword = true,
        validator = StringValidator.pinStrength()
    ),

    VirtualPumpType(key = "virtualpump_type", defaultValue = "Generic AAPS", title = KeysStrings.pref_title_virtual_pump_type, preferenceType = PreferenceType.LIST),

    /**
     * Which embedded Watch Face Format face the wear app installs through Watch Face Push on a
     * Wear OS 6+ watch. Watch Face Push gives an app one slot, so this is a choice, not a set.
     * Values in [PushedWatchfaceId]. Older watches receive and keep the value but cannot act on it.
     *
     * Not in any preference screen: the Wear plugin's main page shows it as a card, and only once
     * the watch has reported that it has Watch Face Push. No summary for that reason.
     */
    WearPushedWatchface(
        key = "wear_pushed_watchface",
        defaultValue = PushedWatchfaceId.CWF,
        title = KeysStrings.pref_title_wear_pushed_watchface,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            PushedWatchfaceId.WFS to KeysStrings.wear_pushed_watchface_wfs,
            PushedWatchfaceId.CWF to KeysStrings.wear_pushed_watchface_cwf
        )
    ),

    NsClientUrl(
        key = "nsclientinternal_url",
        defaultValue = "",
        title = KeysStrings.ns_client_url_title,
        summary = KeysStrings.ns_client_url_summary,
        validator = StringValidator.httpsUrl()
    ),
    NsClientApiSecret(
        key = "nsclientinternal_api_secret",
        defaultValue = "",
        title = KeysStrings.ns_client_secret_title,
        summary = KeysStrings.ns_client_secret_summary,
        isPassword = true,
        validator = StringValidator.minLength(12)
    ),
    NsClientWifiSsids(
        key = "ns_wifi_ssids",
        defaultValue = "",
        title = KeysStrings.ns_wifi_ssids,
        summary = KeysStrings.ns_wifi_ssids_summary,
        dependency = BooleanKey.NsClientUseWifi
    ),
    NsClientAccessToken(
        key = "nsclient_token",
        defaultValue = "",
        title = KeysStrings.nsclient_token_title,
        summary = KeysStrings.nsclient_token_summary,
        isPassword = true,
        validator = StringValidator.minLength(17)
    ),
    ApsBoostIntradayStepBank( "boost_intraday_step_bank", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostDailyStepHistory( "boost_daily_step_history", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostMealTimeHistory( "boost_meal_time_history", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostMlRingBuffer( "boost_ml_ring_buffer", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostSleepHistory( "boost_sleep_history", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostSleepState( "boost_sleep_state", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostAnticipHistory( "boost_anticip_history", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostVwaTddShadowState( "boost_vwa_tdd_shadow_state", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostIsfShadowState( "boost_isf_shadow_state", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostV7ResidualPools( "boost_v7_residual_pools", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostV5State( "boost_v5_state", "", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostNightModeEnd( "boost_night_mode_end", "07:00", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostNightModeStart( "boost_night_mode_start", "22:00", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostEndTime( "boost_end_time", "07:01", title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostStartTime( "boost_start_time", "07:00", title = TextRef.Literal(""), defaultedBySM = true),
    AimiEmergencySosPhone2(
        key = "aimi_emergency_sos_phone2",
        defaultValue = "",
        title = KeysStrings.pref_title_aimi_sos_phone2,
        summary = KeysStrings.pref_summary_aimi_sos_phone2,
        dependency = BooleanKey.AimiEmergencySosEnable),
    AimiEmergencySosPhone(
        key = "aimi_emergency_sos_phone",
        defaultValue = "",
        title = KeysStrings.pref_title_aimi_sos_phone,
        summary = KeysStrings.pref_summary_aimi_sos_phone,
        dependency = BooleanKey.AimiEmergencySosEnable),
    /** Sound of the low-glucose alarms (hypo, rapid fall): the AAPS sound or a soft generated melody. */
    AlertSoundLowGlucose(
        key = "alert_sound_low_glucose",
        defaultValue = "default",
        title = KeysStrings.pref_title_alert_sound_low_glucose,
        summary = KeysStrings.pref_summary_alert_sound,
        preferenceType = PreferenceType.LIST, entriesRefs = mapOf(
            "default" to KeysStrings.alert_sound_default,
            "soft_chime" to KeysStrings.alert_sound_soft_chime,
            "warm_bells" to KeysStrings.alert_sound_warm_bells,
            "marimba" to KeysStrings.alert_sound_marimba,
            "gentle_pulse" to KeysStrings.alert_sound_gentle_pulse
        )),

    /** Sound of every other urgent alarm (pump, missed readings, …). Same choices. */
    AlertSoundOtherAlarms(
        key = "alert_sound_other_alarms",
        defaultValue = "default",
        title = KeysStrings.pref_title_alert_sound_other_alarms,
        summary = KeysStrings.pref_summary_alert_sound,
        preferenceType = PreferenceType.LIST, entriesRefs = mapOf(
            "default" to KeysStrings.alert_sound_default,
            "soft_chime" to KeysStrings.alert_sound_soft_chime,
            "warm_bells" to KeysStrings.alert_sound_warm_bells,
            "marimba" to KeysStrings.alert_sound_marimba,
            "gentle_pulse" to KeysStrings.alert_sound_gentle_pulse
        )),
    OverviewVicoChartBackdrop(
        key = "overview_vico_chart_backdrop",
        defaultValue = "theme",
        title = KeysStrings.pref_title_vico_chart_backdrop,
        summary = KeysStrings.pref_summary_vico_chart_backdrop,
        preferenceType = PreferenceType.LIST, entriesRefs = mapOf(
            "theme" to KeysStrings.pref_vico_backdrop_theme,
            "deep" to KeysStrings.pref_vico_backdrop_deep,
            "warm" to KeysStrings.pref_vico_backdrop_warm,
            "cool" to KeysStrings.pref_vico_backdrop_cool,
            "muted" to KeysStrings.pref_vico_backdrop_muted)),
    OverviewVicoBgReadingTint(
        key = "overview_vico_bg_reading_tint",
        defaultValue = "theme",
        title = KeysStrings.pref_title_vico_bg_reading_tint,
        summary = KeysStrings.pref_summary_vico_bg_reading_tint,
        preferenceType = PreferenceType.LIST, entriesRefs = mapOf(
            "theme" to KeysStrings.pref_vico_tint_theme,
            "mauve" to KeysStrings.pref_vico_tint_mauve,
            "ocean" to KeysStrings.pref_vico_tint_ocean,
            "amber" to KeysStrings.pref_vico_tint_amber,
            "rose" to KeysStrings.pref_vico_tint_rose)),
    OApsAIMIThyroidGuardLevel( "key_aimi_thyroid_guard_level", "HIGH", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    OApsAIMIThyroidTreatmentPhase( "key_aimi_thyroid_treatment_phase", "NONE", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    OApsAIMIThyroidManualStatus( "key_aimi_thyroid_manual_status", "EUTHYROID", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    OApsAIMIThyroidMode( "key_aimi_thyroid_mode", "MANUAL", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    AimiPhysioLLMProvider( "aimi_physio_llm_provider", "gpt4", title = TextRef.Literal("")),
    OApsAIMIContextStorage( "aimi_context_storage", "", title = TextRef.Literal("")),
    OApsAIMIUnstableModeState( "key_oaps_aimi_mode_state", "", title = TextRef.Literal("")),
    ContextMode( "aimi_context_mode", "BALANCED", title = TextRef.Literal("")),
    ContextLLMClaudeKey( "aimi_context_llm_claude_key", "", title = TextRef.Literal(""), isPassword = true),
    ContextLLMDeepSeekKey( "aimi_context_llm_deepseek_key", "", title = TextRef.Literal(""), isPassword = true),
    ContextLLMGeminiKey( "aimi_context_llm_gemini_key", "", title = TextRef.Literal(""), isPassword = true),
    ContextLLMOpenAIKey( "aimi_context_llm_openai_key", "", title = TextRef.Literal(""), isPassword = true),
    ContextLLMProvider( "aimi_context_llm_provider", "OPENAI", title = TextRef.Literal("")),
    AimiAuditorMode( "aimi_auditor_mode", "AUDIT_ONLY", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    AimiTuningContextSelection( "aimi_tuning_context_selection", "AUTO_BALANCE", title = TextRef.Literal("")),
    AimiAdvisorProvider( "aimi_advisor_provider", "OPENAI", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    AimiAdvisorClaudeKey( "aimi_advisor_claude_key", "", title = TextRef.Literal(""), isPassword = true),
    AimiAdvisorDeepSeekKey( "aimi_advisor_deepseek_key", "", title = TextRef.Literal(""), isPassword = true),
    AimiAdvisorGeminiKey( "aimi_advisor_gemini_key", "", title = TextRef.Literal(""), isPassword = true),
    AimiAdvisorOpenAIKey( "aimi_advisor_openai_key", "", title = TextRef.Literal(""), isPassword = true),
    OApsAIMINightGrowthEnd( "key_oaps_aimi_ngr_night_end", "06:00", title = TextRef.Literal("")),
    OApsAIMINightGrowthStart( "key_oaps_aimi_ngr_night_start", "22:00", title = TextRef.Literal("")),
    OApsAIMIWCycleVerneuil( "key_oaps_aimi_wcycle_verneuil", "NONE", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    OApsAIMIWCycleThyroid( "key_oaps_aimi_wcycle_thyroid", "EUTHYROID", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    OApsAIMIWCycleContraceptive( "key_oaps_aimi_wcycle_contraceptive", "NONE", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    OApsAIMIWCycleTrackingMode( "key_oaps_aimi_wcycle_tracking_mode", "FIXED_28", title = TextRef.Literal(""), preferenceType = PreferenceType.LIST),
    PumpCommonTbrStorage( "pump_sync_storage_tbr", "", title = TextRef.Literal("")),
    PumpCommonBolusStorage( "pump_sync_storage_bolus", "", title = TextRef.Literal("")),

    ;

    override val entries: Map<String, TextRef> =
        entriesRefs + entriesLiterals.mapValues { TextRef.Literal(it.value) }
}
