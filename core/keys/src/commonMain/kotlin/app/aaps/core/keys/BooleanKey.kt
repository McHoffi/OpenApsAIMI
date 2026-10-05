package app.aaps.core.keys

import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef

enum class BooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.SWITCH,
    override val calculatedDefaultValue: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val platforms: Set<AppPlatform> = AppPlatform.ALL,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val sync: SyncSpec? = null
) : BooleanPreferenceKey {

    GeneralSimpleMode(key = "simple_mode", defaultValue = true, title = KeysStrings.pref_title_simple_mode, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    GeneralLowEndStabilityMode("general_low_end_stability_mode", false, KeysStrings.pref_title_low_end_stability_mode),
    GeneralInsulinConcentration(
        key = "insulin_concentration_enabled", defaultValue = false, title = KeysStrings.pref_title_insulin_concentration, summary = KeysStrings.pref_summary_insulin_concentration,
        defaultedBySM = true,
        enabledCondition = PreferenceEnabledCondition { it.isConcentrationEnabled },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    // Android only: the wake lock is held by ComposeMainActivity, and neither other shell has a
    // counterpart - an iOS app cannot keep the screen lit from the background, and a desktop screen
    // is the machine's business, not the app's.
    OverviewKeepScreenOn(
        key = "keep_screen_on", defaultValue = false, title = KeysStrings.pref_title_keep_screen_on, summary = KeysStrings.pref_summary_keep_screen_on,
        calculatedDefaultValue = true, platforms = AppPlatform.ANDROID_ONLY
    ),
    OverviewShowTreatmentButton(key = "show_treatment_button", defaultValue = false, title = KeysStrings.pref_title_show_treatment_button, defaultedBySM = true),
    OverviewShowWizardButton(key = "show_wizard_button", defaultValue = true, title = KeysStrings.pref_title_show_wizard_button, defaultedBySM = true),
    OverviewShowInsulinButton(key = "show_insulin_button", defaultValue = true, title = KeysStrings.pref_title_show_insulin_button, defaultedBySM = true),
    OverviewShowCarbsButton(key = "show_carbs_button", defaultValue = true, title = KeysStrings.pref_title_show_carbs_button, defaultedBySM = true),
    OverviewShowCgmButton(key = "show_cgm_button", defaultValue = false, title = KeysStrings.pref_title_show_cgm_button, summary = KeysStrings.pref_summary_show_cgm_button, defaultedBySM = true, showInNsClientMode = false),
    OverviewDashboardExtendedMetrics(
        "overview_dashboard_extended_metrics", false,
        KeysStrings.pref_title_overview_dashboard_extended_metrics,
        KeysStrings.pref_summary_overview_dashboard_extended_metrics
    ),
    OverviewShowHybridDashboardAimiPulse(
        "overview_show_hybrid_aimi_pulse", false,
        KeysStrings.pref_title_overview_show_hybrid_aimi_pulse,
        KeysStrings.pref_summary_overview_show_hybrid_aimi_pulse,
        defaultedBySM = true
    ),
    OverviewUseBoostOverviewV2( "use_boost_overview_v2", false, title = TextRef.Literal("")),
    OverviewUseBoostOverview( "use_boost_overview", false, title = TextRef.Literal("")),
    OverviewUseDashboardLayout(
        "overview_use_dashboard", true,
        KeysStrings.pref_title_overview_use_dashboard_layout,
        KeysStrings.pref_summary_overview_use_dashboard_layout
    ),
    OverviewGlassLook(
        key = "overview_glass_look", defaultValue = false,
        title = KeysStrings.pref_title_overview_glass_look,
        summary = KeysStrings.pref_summary_overview_glass_look
    ),
    OverviewShowCalibrationButton(
        key = "show_calibration_button",
        defaultValue = false,
        title = KeysStrings.pref_title_show_calibration_button,
        summary = KeysStrings.pref_summary_show_calibration_button,
        defaultedBySM = true,
        showInNsClientMode = false
    ),
    OverviewShowNotesInDialogs(key = "show_notes_entry_dialogs", defaultValue = false, title = KeysStrings.pref_title_show_notes_in_dialogs, defaultedBySM = true),
    OverviewUseBolusAdvisor("use_bolus_advisor", true, KeysStrings.pref_title_use_bolus_advisor, KeysStrings.pref_summary_use_bolus_advisor, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    OverviewUseBolusReminder("use_bolus_reminder", true, KeysStrings.pref_title_use_bolus_reminder, KeysStrings.pref_summary_use_bolus_reminder, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),

    @Deprecated("Remove support")
    OverviewUseSuperBolus("key_usersuperbolus", false, KeysStrings.pref_title_use_super_bolus, KeysStrings.pref_summary_use_super_bolus, defaultedBySM = true, hideParentScreenIfHidden = true),

    PumpBtWatchdog(
        "bt_watchdog", false, KeysStrings.pref_title_bt_watchdog, KeysStrings.pref_summary_bt_watchdog,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    AlertMissedBgReading("enable_missed_bg_readings", false, KeysStrings.pref_title_alert_missed_bg_reading),
    AlertPumpUnreachable("enable_pump_unreachable_alert", true, KeysStrings.pref_title_alert_pump_unreachable),
    AlertCarbsRequired("enable_carbs_required_alert_local", true, KeysStrings.pref_title_alert_carbs_required),
    // Android only: it decides whether AAPS raises an OS notification at all. iOS gives an app no say
    // in that - the user grants or denies notifications in Settings - so there is nothing here for
    // the switch to do, and a switch that does nothing reads as a promise.
    AlertUrgentAsAndroidNotification(
        "raise_urgent_alarms_as_android_notification", true, KeysStrings.pref_title_alert_urgent_as_android_notification,
        platforms = AppPlatform.ANDROID_ONLY
    ),
    AlertIncreaseVolume("gradually_increase_notification_volume", true, KeysStrings.pref_title_alert_increase_volume),
    AlertOverrideDoNotDisturb("alert_override_dnd", true, KeysStrings.pref_title_alert_override_dnd, KeysStrings.pref_summary_alert_override_dnd, defaultedBySM = true),
    AlertRapidFall("enable_rapid_fall_alert", false, KeysStrings.pref_title_alert_rapid_fall, KeysStrings.pref_summary_alert_rapid_fall),
    AlertHyper("enable_hyper_alert", false, KeysStrings.pref_title_alert_hyper, KeysStrings.pref_summary_alert_hyper),
    AlertHypo("enable_hypo_alert", false, KeysStrings.pref_title_alert_hypo, KeysStrings.pref_summary_alert_hypo),

    BgSourceUploadToNs("dexcomg5_nsupload", true, KeysStrings.pref_title_bg_source_upload_to_ns, defaultedBySM = true, hideParentScreenIfHidden = true),
    BgSourceCreateSensorChange("dexcom_lognssensorchange", true, KeysStrings.pref_title_bg_source_create_sensor_change, KeysStrings.pref_summary_bg_source_create_sensor_change, defaultedBySM = true),
    BgSourceRandomBgRandomize("randombg_randomize", true, KeysStrings.pref_title_random_bg_randomize, KeysStrings.pref_summary_random_bg_randomize, defaultedBySM = true),

    ApsUseDynamicSensitivity("use_dynamic_sensitivity", false, KeysStrings.pref_title_aps_use_dynamic_sensitivity, KeysStrings.pref_summary_aps_use_dynamic_sensitivity, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsUseAutosens(
        "openapsama_useautosens", true, KeysStrings.pref_title_aps_use_autosens, defaultedBySM = true,
        // Hidden only while the active APS both offers dynamic sensitivity and has it enabled.
        // A plain negativeDependency on ApsUseDynamicSensitivity would also hide it on algorithms
        // whose screens never show that toggle (AMA, AutoISF), with no way to reveal it (issue #4482).
        visibility = ElementVisibility { !(it.apsOffersDynamicSensitivity && it.preferences.get(ApsUseDynamicSensitivity)) },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmb("use_smb", true, KeysStrings.pref_title_aps_use_smb, KeysStrings.pref_summary_aps_use_smb, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsUseSmbWithHighTt(
        "enableSMB_with_high_temptarget",
        false,
        KeysStrings.pref_title_aps_use_smb_with_high_tt,
        KeysStrings.pref_summary_aps_use_smb_with_high_tt,
        defaultedBySM = true,
        dependency = ApsUseSmb,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmbAlways(
        "enableSMB_always", true, KeysStrings.pref_title_aps_use_smb_always, KeysStrings.pref_summary_aps_use_smb_always, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = ElementVisibility.ADVANCED_FILTERING,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmbWithCob(
        "enableSMB_with_COB", true, KeysStrings.pref_title_aps_use_smb_with_cob, KeysStrings.pref_summary_aps_use_smb_with_cob, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = ElementVisibility { !it.preferences.get(ApsUseSmbAlways) || !it.advancedFilteringSupported },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmbWithLowTt(
        "enableSMB_with_temptarget", true, KeysStrings.pref_title_aps_use_smb_with_low_tt, KeysStrings.pref_summary_aps_use_smb_with_low_tt, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = ElementVisibility { !it.preferences.get(ApsUseSmbAlways) || !it.advancedFilteringSupported },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmbAfterCarbs(
        "enableSMB_after_carbs", true, KeysStrings.pref_title_aps_use_smb_after_carbs, KeysStrings.pref_summary_aps_use_smb_after_carbs, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = ElementVisibility { !it.preferences.get(ApsUseSmbAlways) && it.advancedFilteringSupported },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseUam("use_uam", true, KeysStrings.pref_title_aps_use_uam, KeysStrings.pref_summary_aps_use_uam, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsSensitivityRaisesTarget(
        "sensitivity_raises_target", true, KeysStrings.pref_title_aps_sensitivity_raises_target, KeysStrings.pref_summary_aps_sensitivity_raises_target, defaultedBySM = true,
        visibility = ElementVisibility {
            if (it.preferences.get(ApsUseDynamicSensitivity)) {
                it.preferences.get(ApsDynIsfAdjustSensitivity)
            } else {
                it.preferences.get(ApsUseAutosens)
            }
        },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsResistanceLowersTarget(
        "resistance_lowers_target", true, KeysStrings.pref_title_aps_resistance_lowers_target, KeysStrings.pref_summary_aps_resistance_lowers_target, defaultedBySM = true,
        visibility = ElementVisibility {
            if (it.preferences.get(ApsUseDynamicSensitivity)) {
                it.preferences.get(ApsDynIsfAdjustSensitivity)
            } else {
                it.preferences.get(ApsUseAutosens)
            }
        },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAlwaysUseShortDeltas(
        "always_use_shortavg",
        false,
        KeysStrings.pref_title_aps_always_use_short_deltas,
        KeysStrings.pref_summary_aps_always_use_short_deltas,
        defaultedBySM = true,
        hideParentScreenIfHidden = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsDynIsfAdjustSensitivity(
        "dynisf_adjust_sensitivity",
        false,
        KeysStrings.pref_title_aps_dynisf_adjust_sensitivity,
        KeysStrings.pref_summary_aps_dynisf_adjust_sensitivity,
        defaultedBySM = true,
        dependency = ApsUseDynamicSensitivity,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAmaAutosensAdjustTargets(
        "autosens_adjust_targets",
        true,
        KeysStrings.pref_title_aps_autosens_adjust_targets,
        KeysStrings.pref_summary_aps_autosens_adjust_targets,
        defaultedBySM = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfHighTtRaisesSens(
        "high_temptarget_raises_sensitivity",
        false,
        KeysStrings.pref_title_aps_high_tt_raises_sensitivity,
        KeysStrings.pref_summary_aps_high_tt_raises_sensitivity,
        defaultedBySM = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfLowTtLowersSens(
        "low_temptarget_lowers_sensitivity",
        false,
        KeysStrings.pref_title_aps_low_tt_lowers_sensitivity,
        KeysStrings.pref_summary_aps_low_tt_lowers_sensitivity,
        defaultedBySM = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseAutoIsfWeights("openapsama_enable_autoISF", false, KeysStrings.pref_title_aps_use_autoisf_weights, KeysStrings.pref_summary_aps_use_autoisf_weights, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsAutoIsfSmbOnEvenTarget(
        "Enable alternative activation of SMB always",
        false,
        KeysStrings.pref_title_aps_smb_on_even_target,
        KeysStrings.pref_summary_aps_smb_on_even_target,
        defaultedBySM = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    MaintenanceEnableFabric("enable_fabric2", true, KeysStrings.pref_title_maintenance_enable_fabric, defaultedBySM = true, hideParentScreenIfHidden = true),
    ActivityMonitorUseSteps( key= "act_mon_use_steps", defaultValue = true, title = KeysStrings.pref_activ_mon_title_act_mon_use_steps, defaultedBySM = true),
    ActivityMonitorDetection( key = "activity_detection", defaultValue = false, title = KeysStrings.pref_activ_mon_title_activity_detection, summary = KeysStrings.pref_activ_mon_sum_activity_detection, defaultedBySM=true),
    ActivityMonitorOvernight( key = "ignore_inactivity_overnight", defaultValue = true, title = KeysStrings.pref_activ_mon_title_ignore_inactivity_overnight, summary = KeysStrings.pref_activ_mon_sum_ignore_inactivity_overnight, defaultedBySM=true, dependency = ActivityMonitorDetection),
    FslCalibrationEnd( "calibration_end", false, title = TextRef.Literal(""), defaultedBySM = true),
    FslCalibrationTrigger( "calibration_stops_SMB", false, title = TextRef.Literal(""), defaultedBySM = true),

    // Master-only (not a follower client): unattended settings export backs up the local config, which on a
    // client is derived from the master. showInNsClientMode=false hides it in apsMode + pumpControlMode only;
    // hideParentScreenIfHidden collapses the now-empty "Unattended Settings Export" subscreen on a client.
    MaintenanceEnableExportSettingsAutomation("enable_unattended_export", false, KeysStrings.pref_title_maintenance_enable_export_automation, defaultedBySM = false, showInNsClientMode = false, hideParentScreenIfHidden = true),

    AutotuneAutoSwitchProfile("autotune_auto", false, KeysStrings.pref_title_autotune_auto_switch_profile, KeysStrings.pref_summary_autotune_auto_switch_profile),
    AutotuneCategorizeUamAsBasal("categorize_uam_as_basal", false, KeysStrings.pref_title_autotune_categorize_uam_as_basal, KeysStrings.pref_summary_autotune_categorize_uam_as_basal),
    AutotuneTuneInsulinCurve("autotune_tune_insulin_curve", false, KeysStrings.pref_title_autotune_tune_insulin_curve),
    AutotuneCircadianIcIsf("autotune_circadian_ic_isf", false, KeysStrings.pref_title_autotune_circadian_ic_isf, KeysStrings.pref_summary_autotune_circadian_ic_isf),
    AutotuneAdditionalLog("autotune_additional_log", false, KeysStrings.pref_title_autotune_additional_log),

    SmsAllowRemoteCommands("smscommunicator_remotecommandsallowed", false, KeysStrings.pref_title_sms_allow_remote_commands),
    SmsReportPumpUnreachable("smscommunicator_report_pump_unreachable", true, KeysStrings.pref_title_sms_report_pump_unreachable, KeysStrings.pref_summary_sms_report_pump_unreachable),

    VirtualPumpStatusUpload("virtualpump_uploadstatus", false, KeysStrings.pref_title_virtual_pump_status_upload, showInNsClientMode = false),
    NsClientUploadData("ns_upload", true, KeysStrings.pref_title_ns_upload_data, KeysStrings.pref_summary_ns_upload_data, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientUploadHeartRate("ns_upload_heart_rate", false, KeysStrings.pref_title_ns_upload_heart_rate, KeysStrings.pref_summary_ns_upload_heart_rate, showInNsClientMode = false),
    NsClientUploadSteps("ns_upload_steps", false, KeysStrings.pref_title_ns_upload_steps, KeysStrings.pref_summary_ns_upload_steps, showInNsClientMode = false),
    NsClientAcceptCgmData("ns_receive_cgm", false, KeysStrings.pref_title_ns_receive_cgm, KeysStrings.pref_summary_ns_receive_cgm, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptProfileStore("ns_receive_profile_store", false, KeysStrings.pref_title_ns_receive_profile_store, KeysStrings.pref_summary_ns_receive_profile_store, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTempTarget("ns_receive_temp_target", false, KeysStrings.pref_title_ns_receive_temp_target, KeysStrings.pref_summary_ns_receive_temp_target, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptProfileSwitch("ns_receive_profile_switch", false, KeysStrings.pref_title_ns_receive_profile_switch, KeysStrings.pref_summary_ns_receive_profile_switch, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptInsulin("ns_receive_insulin", false, KeysStrings.pref_title_ns_receive_insulin, KeysStrings.pref_summary_ns_receive_insulin, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptCarbs("ns_receive_carbs", false, KeysStrings.pref_title_ns_receive_carbs, KeysStrings.pref_summary_ns_receive_carbs, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTherapyEvent("ns_receive_therapy_events", false, KeysStrings.pref_title_ns_receive_therapy_event, KeysStrings.pref_summary_ns_receive_therapy_event, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptRunningMode("ns_receive_running_mode", false, KeysStrings.pref_title_ns_receive_running_mode, KeysStrings.pref_summary_ns_receive_running_mode, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTbrEb("ns_receive_tbr_eb", false, KeysStrings.pref_title_ns_receive_tbr_eb, KeysStrings.pref_summary_ns_receive_tbr_eb, showInNsClientMode = false, engineeringModeOnly = true),
    NsClientNotificationsFromAlarms("ns_alarms", false, KeysStrings.pref_title_ns_notifications_from_alarms, calculatedDefaultValue = true),
    NsClientNotificationsFromAnnouncements("ns_announcements", false, KeysStrings.pref_title_ns_notifications_from_announcements, calculatedDefaultValue = true),
    NsClientUseCellular("ns_cellular", true, KeysStrings.pref_title_ns_use_cellular),
    // Android only. `ReceiverDelegate` reads this in shared code, but the clause it sits in also
    // needs `ev.roaming`, and neither other shell can ever report that: iOS publishes no roaming
    // state at all, and desktop calls every link wifi on purpose so these preferences stay out of
    // the decision. Both say so in their own KDoc. So the row is a control with nothing behind it.
    NsClientUseRoaming( key = "ns_allow_roaming", defaultValue = true, title = KeysStrings.pref_title_ns_use_roaming, platforms = AppPlatform.ANDROID_ONLY, dependency = NsClientUseCellular),
    NsClientUseWifi("ns_wifi", true, KeysStrings.pref_title_ns_use_wifi),
    NsClientUseOnBattery("ns_battery", true, KeysStrings.pref_title_ns_use_on_battery),
    NsClientUseOnCharging("ns_charging", true, KeysStrings.pref_title_ns_use_on_charging),
    NsClientLogAppStart("ns_log_app_started_event", false, KeysStrings.pref_title_ns_log_app_start, calculatedDefaultValue = true),
    NsClientCreateAnnouncementsFromErrors("ns_create_announcements_from_errors", false, KeysStrings.pref_title_ns_create_announcements_from_errors, calculatedDefaultValue = true, showInNsClientMode = false),
    NsClientCreateAnnouncementsFromCarbsReq("ns_create_announcements_from_carbs_req", false, KeysStrings.pref_title_ns_create_announcements_from_carbs_req, calculatedDefaultValue = true, showInNsClientMode = false),
    NsClientSlowSync("ns_sync_slow", false, KeysStrings.pref_title_ns_slow_sync),
    NsClient3UseWs("ns_use_ws", true, KeysStrings.pref_title_ns_use_ws, KeysStrings.pref_summary_ns_use_ws),
    NsClientAllowClientControl(
        "ns_allow_client_control", false,
        KeysStrings.pref_title_ns_allow_client_control, KeysStrings.pref_summary_ns_allow_client_control,
        // The rich stop/allow-communication switch lives on the Authorized clients screen; it is ALSO exposed in a
        // "Remote control" category on the NSCv3 settings screen (NSClientV3Plugin.getPreferenceScreenContent) so it
        // is reachable from search. Default OFF, but ON in simple mode (resolved in PreferencesImpl.calculatedDefaultValue). Hidden on a client.
        calculatedDefaultValue = true, showInNsClientMode = false,
        // Remote control rides the WebSocket — hide the toggle (and its single-item "Remote control" parent category)
        // when WS is off, and on a client where the key is already hidden (so the category never shows empty).
        dependency = NsClient3UseWs, hideParentScreenIfHidden = true,
        // Synced master→client (MasterOnly — the client mirrors, never pushes back) so a paired client knows
        // whether the master is accepting commands and can gate its UI. buildSyncedPrefs publishes the EFFECTIVE
        // value for this key (see RunningConfigurationImpl), not the raw default.
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.MasterOnly)
    ),
    OpenHumansWifiOnly("oh_wifi_only", true, KeysStrings.pref_title_openhumans_wifi_only),
    OpenHumansChargingOnly("oh_charging_only", false, KeysStrings.pref_title_openhumans_charging_only),
    XdripSendStatus("xdrip_send_status", false, KeysStrings.pref_title_xdrip_send_status),
    XdripSendDetailedIob("xdripstatus_detailediob", true, KeysStrings.pref_title_xdrip_send_detailed_iob, KeysStrings.pref_summary_xdrip_send_detailed_iob, defaultedBySM = true, hideParentScreenIfHidden = true),
    XdripSendBgi("xdripstatus_showbgi", true, KeysStrings.pref_title_xdrip_send_bgi, KeysStrings.pref_summary_xdrip_send_bgi, defaultedBySM = true, hideParentScreenIfHidden = true),
    WearControl(key = "wearcontrol", defaultValue = false, title = KeysStrings.pref_title_wear_control, summary = KeysStrings.pref_summary_wear_control),
    WearWizardBg(key = "wearwizard_bg", defaultValue = true, title = KeysStrings.pref_title_wear_wizard_bg, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardTt(key = "wearwizard_tt", defaultValue = false, title = KeysStrings.pref_title_wear_wizard_tt, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardTrend(key = "wearwizard_trend", defaultValue = false, title = KeysStrings.pref_title_wear_wizard_trend, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardCob(key = "wearwizard_cob", defaultValue = true, title = KeysStrings.pref_title_wear_wizard_cob, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardIob(key = "wearwizard_iob", defaultValue = true, title = KeysStrings.pref_title_wear_wizard_iob, dependency = WearControl, hideParentScreenIfHidden = true),
    WearCustomWatchfaceAuthorization(key = "wear_custom_watchface_autorization", defaultValue = false, title = KeysStrings.pref_title_wear_custom_watchface_authorization),
    WearNotifyOnSmb(key = "wear_notifySMB", defaultValue = true, title = KeysStrings.pref_title_wear_notify_on_smb, summary = KeysStrings.pref_summary_wear_notify_on_smb),
    WearBroadcastData(key = "wear_broadcast_data", defaultValue = false, title = KeysStrings.pref_title_wear_broadcast_data, summary = KeysStrings.pref_summary_wear_broadcast_data, showInApsMode = false, showInPumpControlMode = false),
    EversenseEuropeanRegion("eversense_european_region", false, KeysStrings.eversense_european_region, KeysStrings.eversense_european_region_summary),
    EversenseCloudUploadToast("eversense_notif_cloud_upload_toast", false, KeysStrings.eversense_cloud_upload_toast, KeysStrings.eversense_cloud_upload_toast_summary),
    EversenseCloudUploadEnabled("eversense_cloud_upload_enabled", true, KeysStrings.eversense_cloud_upload_enabled),

    SiteRotationManagePump("site_rotation_manage_pump", defaultValue = false, title = KeysStrings.pref_title_site_rotation_manage_pump, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    SiteRotationManageCgm("site_rotation_manage_cgm", defaultValue = false, title = KeysStrings.pref_title_site_rotation_manage_cgm, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsBoostHrStressDetection( "boost_hr_stress_detection", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_hr_stress, defaultedBySM = true),
    ApsBoostHrIntegrationEnabled( "boost_hr_integration_enabled", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_hr_integration, defaultedBySM = true),
    ApsBoostAutosensWhenNoTdd( "boost_autosens_when_no_tdd", false, title = KeysStrings.boost_autosens_when_no_tdd_title, summary = KeysStrings.pref_summary_boost_autosens_no_tdd, defaultedBySM = true),
    ApsBoostActivityShadowEnabled( "boost_activity_shadow_enabled", true, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_activity_shadow, defaultedBySM = true),
    ApsBoostPostExerciseRecoveryEnabled( "boost_post_exercise_recovery_enabled", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_post_exercise_recovery, defaultedBySM = true),
    ApsBoostV5AutoConfigDone( "boost_v5_autoconfig_done", false, title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostV5PrimerBolusMode( "boost_v5_primer_bolus_mode", false, title = KeysStrings.boost_v5_primer_bolus_mode_title, defaultedBySM = true),
    ApsBoostV5PrimerTbrFallback( "boost_v5_primer_tbr_fallback", false, title = TextRef.Literal(""), defaultedBySM = true),
    ApsBoostV5VelocityBudgetActive( "boost_v5_velocity_budget_active", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_v5_velocity_budget, defaultedBySM = true),
    ApsBoostV5ComposedFloorActive( "boost_v5_composed_floor_active", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_v5_floor_active, defaultedBySM = true),
    ApsBoostV5AggressiveEarlyConfirm( "boost_v5_aggressive_early_confirm", false, title = KeysStrings.boost_v5_aggressive_early_confirm_title, summary = KeysStrings.pref_summary_boost_v5_early_confirm, defaultedBySM = true),
    ApsBoostV5FastCarbConfirm( "boost_v5_fast_carb_confirm", true, title = KeysStrings.boost_v5_fast_carb_confirm_title, summary = KeysStrings.pref_summary_boost_v5_fast_carb_confirm, defaultedBySM = true),
    ApsBoostV6PreMealTarget( "boost_v6_pre_meal_target", false, title = KeysStrings.boost_v6_pre_meal_target_title, summary = KeysStrings.pref_summary_boost_v6_pre_meal, defaultedBySM = true),
    ApsBoostV5ActiveDosing( "boost_v5_active_dosing", false, title = KeysStrings.boost_v5_active_dosing_title, summary = KeysStrings.pref_summary_boost_v5_active_dosing, defaultedBySM = true),
    ApsBoostBypassVersionCheck( "boost_bypass_version_check", true, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_bypass_version_check, defaultedBySM = true),
    ApsBoostHealthConnectHrEnabled( "boost_health_connect_hr_enabled", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_health_connect_hr, defaultedBySM = true),
    ApsBoostNightModeAutoBySleep( "boost_night_mode_auto_by_sleep", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_night_auto_sleep, defaultedBySM = true),
    ApsBoostNightModeDisableWithLowTt( "boost_night_mode_disable_with_low_tt", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_night_disable_low_tt, defaultedBySM = true),
    ApsBoostNightModeDisableWithCob( "boost_night_mode_disable_with_cob", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_night_disable_cob, defaultedBySM = true),
    ApsBoostNightModeEnabled( "boost_night_mode_enabled", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_night_mode, defaultedBySM = true),
    ApsBoostAllowAllBgSources( "boost_allow_all_bg_sources", true, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_allow_all_bg_sources, defaultedBySM = true),
    ApsBoostAdjustSensitivity( "boost_adjust_sensitivity", false, title = KeysStrings.boost_adjust_sensitivity_title, summary = KeysStrings.pref_summary_boost_adjust_sensitivity, defaultedBySM = true),
    ApsBoostUseTdd( "boost_use_tdd", false, title = KeysStrings.boost_use_tdd_title, summary = KeysStrings.pref_summary_boost_use_tdd, defaultedBySM = true),
    ApsBoostAllowWithHighTt( "enableBoost_with_high_temptarget", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_allow_high_tt, defaultedBySM = true),
    ApsBoostEnableCircadianIsf( "enableCircadianISF", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_circadian_isf, defaultedBySM = true),
    ApsBoostEnablePercentScale( "enableBoostPercentScale", false, title = TextRef.Literal(""), summary = KeysStrings.pref_summary_boost_percent_scale, defaultedBySM = true),
    OApsAIMITpoEnabled(
        "key_aimi_tpo_enabled",
        true,
        KeysStrings.pref_title_aimi_tpo_enabled,
        KeysStrings.pref_summary_aimi_tpo_enabled),
    OApsAIMITpoNotifyOnApply(
        "key_aimi_tpo_notify_on_apply",
        true,
        KeysStrings.pref_title_aimi_tpo_notify_on_apply,
        KeysStrings.pref_summary_aimi_tpo_notify_on_apply,
        dependency = OApsAIMITpoEnabled),
    OApsAIMITpoLlmConfirmEnabled(
        "key_aimi_tpo_llm_confirm_enabled",
        true,
        KeysStrings.pref_title_aimi_tpo_llm_confirm_enabled,
        KeysStrings.pref_summary_aimi_tpo_llm_confirm_enabled,
        dependency = OApsAIMITpoEnabled),
    OApsAIMIAdvisorLlmRichOref(
        "key_aimi_advisor_llm_rich_oref",
        true,
        KeysStrings.pref_title_aimi_advisor_llm_rich_oref,
        KeysStrings.pref_summary_aimi_advisor_llm_rich_oref),
    OApsAIMIAdvisorPersonalOrefMl(
        "key_aimi_advisor_personal_oref_ml",
        false,
        KeysStrings.pref_title_aimi_advisor_personal_oref_ml,
        KeysStrings.pref_summary_aimi_advisor_personal_oref_ml),
    AimiEmergencySosEnable(
        key = "aimi_emergency_sos_enable",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_sos_enable,
        summary = KeysStrings.pref_summary_aimi_sos_enable),
    AimiCosineGateEnabled( "aimi_cosine_gate_enabled", true, title = TextRef.Literal("")),
    ApsKetoacidosisProtectionStrategy( key ="ketoacidosis_protection_strategy", defaultValue = false, title = KeysStrings.ketoacidosis_protection_strategy_title, summary = KeysStrings.ketoacidosis_protection_strategy_summary, defaultedBySM = true),
    ApsKetoacidosisProtection( key ="ketoacidosis_protection", defaultValue =false, title = KeysStrings.ketoacidosis_protection_title, summary = KeysStrings.ketoacidosis_protection_summary, defaultedBySM = true),
    OApsAIMIMealAdvisorTrigger( "aimi_meal_advisor_trigger", false, title = TextRef.Literal("")),
    AimiEndometriosisPainFlare( "aimi_endo_flare", false, title = TextRef.Literal("")),
    AimiEndometriosisHormonalSuppression( "aimi_endo_suppression", false, title = TextRef.Literal("")),
    AimiEndometriosisEnable( "aimi_endo_enable", false, title = TextRef.Literal("")),
    AimiPhysioDebugLogs( "aimi_physio_debug_logs", false, title = TextRef.Literal("")),
    AimiPhysioLLMAnalysisEnable( "aimi_physio_llm_enable", false, title = TextRef.Literal("")),
    AimiPhysioHRVDataEnable( "aimi_physio_hrv_enable", true, title = TextRef.Literal("")),
    AimiPhysioSleepDataEnable( "aimi_physio_sleep_enable", true, title = TextRef.Literal("")),
    AimiPhysioAssistantEnable( "aimi_physio_assistant_enable", false, title = TextRef.Literal("")),
    OApsAIMIThyroidLogVerbosity( "key_aimi_thyroid_debug", false, title = TextRef.Literal("")),
    OApsAIMIThyroidEnabled( "key_aimi_thyroid_enabled", false, title = TextRef.Literal("")),
    OApsAIMIUndeclaredCobEnabled(
        "key_aimi_undeclared_cob_enabled", false,
        title = KeysStrings.pref_title_aimi_undeclared_cob,
        summary = KeysStrings.pref_summary_aimi_undeclared_cob),
    OApsAIMIT3cBrittleMode( "key_aimi_t3c_brittle_mode", false, title = TextRef.Literal("")),
    OApsAIMIT3cCfrdMode(
        key = "key_aimi_t3c_cfrd_mode",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_t3c_cfrd_mode,
        summary = KeysStrings.pref_summary_aimi_t3c_cfrd_mode,
        dependency = OApsAIMIT3cBrittleMode),
    OApsAIMIT3cCfrdExacerbationMode(
        key = "key_aimi_t3c_cfrd_exacerbation",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_t3c_cfrd_exacerbation,
        summary = KeysStrings.pref_summary_aimi_t3c_cfrd_exacerbation,
        dependency = OApsAIMIT3cCfrdMode),
    OApsAIMIT3cHyperBasalFloor(
        key = "key_aimi_t3c_hyper_basal_floor",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_t3c_hyper_basal_floor,
        summary = KeysStrings.pref_summary_aimi_t3c_hyper_basal_floor,
        dependency = OApsAIMIT3cBrittleMode),
    OApsAIMIT3cAutodriveBasalAuthority(
        key = "key_aimi_t3c_autodrive_basal_authority",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_t3c_autodrive_basal_authority,
        summary = KeysStrings.pref_summary_aimi_t3c_autodrive_basal_authority,
        dependency = OApsAIMIT3cBrittleMode),
    OApsAIMIContextLLMEnabled( "key_aimi_context_llm_enabled", false, title = TextRef.Literal("")),

    /**
     * Keep the trajectory bridge basal cut after the basal schedule has run (opt-in, off by default).
     *
     * When the trajectory safety bridge asks for a lower temp basal, that request can be written over
     * later in the same tick. When ON, the request is applied again at the last point where the rate
     * can still be bound, just before the terminal invariants, and only as `min(scheduled, request)`.
     * It can only lower the rate, never raise it, and it does nothing on a tick where the bridge did
     * not fire. When OFF, the behaviour is exactly as before; the request is still written to
     * `AIMI_Decisions.jsonl` (`adjustments.traj_bridge`) so the effect can be counted first.
     */
    OApsAIMITrajBridgeBasalSurvives(
        "key_aimi_traj_bridge_basal_survives", false,
        title = KeysStrings.pref_title_aimi_traj_bridge_basal_survives,
        summary = KeysStrings.pref_summary_aimi_traj_bridge_basal_survives),

    /**
     * Stop the straight-line tube advisor from vetoing on a railed prediction (opt-in, off by default).
     *
     * The advisor refuses every dose rung when the predicted minimum sits under the hypoglycaemia
     * floor. That minimum comes from curves clamped at 39 mg/dL, so a curve that hit its own floor
     * reads as "a low is coming". Measured on 2026-10-02 between 21:09 and 21:19: glucose 142, 140 and
     * 144 mg/dL, flat, predicted minimum 40.97, 40.22 and 39.00, and the bolus channel was held at
     * 0.05 U for fifteen minutes.
     *
     * **This is the one gesture here that can RAISE a dose.** When ON, the bolus cap is released only
     * while glucose is high and flat, never during sport, never after a low, and never on a fall — the
     * same conditions the plateau floor-artefact lift already uses, including its 160 mg/dL band. The
     * basal trim the advisor asked for is kept. When OFF, nothing changes, and both the strict case and
     * the wider case below that band are written to `AIMI_Decisions.jsonl` so the frequency can be
     * measured before anyone arms this.
     *
     * To be exact about what "released" means: this veto only fires when even a dose of zero is
     * infeasible, so the advisor never produced a graded answer that could be used instead. The cap
     * therefore returns to the value it had BEFORE the advisor ran — not to a smaller graded one. Every
     * limit downstream still applies, in particular the maximum bolus and the maximum insulin on board.
     */
    OApsAIMITubeVetoIgnoreFloorArtefact(
        "key_aimi_tube_veto_ignore_floor_artefact", false,
        title = KeysStrings.pref_title_aimi_tube_veto_ignore_floor_artefact,
        summary = KeysStrings.pref_summary_aimi_tube_veto_ignore_floor_artefact),

    OApsAIMIPkpdPredictionKinetics(
        "key_aimi_pkpd_prediction_kinetics", true,
        title = KeysStrings.pref_title_aimi_pkpd_prediction_kinetics,
        summary = KeysStrings.pref_summary_aimi_pkpd_prediction_kinetics),
    OApsAIMIBasalTerminalInvariants(
        "key_aimi_basal_terminal_invariants", true,
        title = KeysStrings.pref_title_aimi_basal_terminal_invariants,
        summary = KeysStrings.pref_summary_aimi_basal_terminal_invariants),
    OApsAIMIBasalProjectedError(
        "key_aimi_basal_projected_error", true,
        title = KeysStrings.pref_title_aimi_basal_projected_error,
        summary = KeysStrings.pref_summary_aimi_basal_projected_error),
    OApsAIMIBasalChannelSafetyGuards(
        "key_aimi_basal_channel_safety_guards", true,
        title = KeysStrings.pref_title_aimi_basal_channel_safety_guards,
        summary = KeysStrings.pref_summary_aimi_basal_channel_safety_guards),
    OApsAIMIPkpdEndogenousReversion(
        "key_aimi_pkpd_endo_reversion", true,
        title = KeysStrings.pref_title_aimi_pkpd_endo_reversion,
        summary = KeysStrings.pref_summary_aimi_pkpd_endo_reversion),
    OApsAIMIPkpdHyperReversion( key = "key_aimi_pkpd_hyper_reversion", defaultValue = true, title = TextRef.Literal(""), dependency = OApsAIMIPkpdEndogenousReversion),
    OApsAIMIPkpdStackAwareGuardB( key = "key_aimi_pkpd_stack_aware_guardb", defaultValue = false, title = TextRef.Literal(""), dependency = OApsAIMIPkpdHyperReversion),
    OApsAIMIBasalSlewLimitEnabled(
        "key_aimi_basal_slew_limit", true,
        title = KeysStrings.pref_title_aimi_basal_slew_limit,
        summary = KeysStrings.pref_summary_aimi_basal_slew_limit),
    OApsAIMIContextEnabled( "key_aimi_context_enabled", false, title = TextRef.Literal("")),
    OApsAIMIStraightLineTubeAdvisorEnabled(
        key = "key_aimi_straight_line_tube_enabled",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_straight_line_tube,
        summary = KeysStrings.pref_summary_aimi_straight_line_tube),
    OApsAIMITrajectoryGuardEnabled( "key_aimi_trajectory_guard_enabled", false, title = TextRef.Literal("")),
    AimiAuditorEnabled( "aimi_auditor_enabled", false, title = KeysStrings.aimi_auditor_enabled_title),

    /**
     * Opt-in: let the AI auditor move the ISF and the glucose target by at most 15 %, up or down.
     *
     * The factor comes from a separate LLM request made after each external audit. Kotlin checks
     * every number the LLM quotes against the last 30 minutes, refuses more insulin in any
     * low-glucose context, keeps the ISF above its profile floor, and caps ISF + target together at
     * the effect of a single 15 % change. A factor for more insulin lives 15 minutes, a factor for
     * less insulin 30 minutes. All dose limits still apply after it.
     *
     * With this key off nothing changes: the auditor gets the fields it has always been sent, and
     * the ISF and target levels of every tick are written to AIMI_Decisions.jsonl
     * (`adjustments.auditor_profile_factors`) for study only.
     */
    OApsAIMIAuditorProfileFactors(
        key = "key_aimi_auditor_profile_factors",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_auditor_profile_factors,
        summary = KeysStrings.pref_summary_aimi_auditor_profile_factors,
        dependency = AimiAuditorEnabled,
    ),
    OApsAIMIUnifiedReactivityEnabled( "key_use_unified_reactivity", true, title = TextRef.Literal("")),
    OApsAIMIDynIsfTrajectoryTuningEnabled(
        key = "aimi_dyn_isf_trajectory_tuning_enabled",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_dyn_isf_trajectory_tuning,
        summary = KeysStrings.pref_summary_aimi_dyn_isf_trajectory_tuning,
        dependency = ApsUseDynamicSensitivity),
    OApsAIMIDynIsfTrajectoryShadowOnly(
        key = "aimi_dyn_isf_trajectory_shadow_only",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_dyn_isf_trajectory_shadow,
        summary = KeysStrings.pref_summary_aimi_dyn_isf_trajectory_shadow,
        dependency = OApsAIMIDynIsfTrajectoryTuningEnabled),
    OApsAIMIautoDriveActive( key = "key_use_aimi_autodrive_active", defaultValue = true, title = TextRef.Literal("")),
    OApsAIMIRecursiveBeliefWavelet(
        key = "key_aimi_recursive_belief_wavelet",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_recursive_belief_wavelet,
        summary = KeysStrings.pref_summary_aimi_recursive_belief_wavelet,
        dependency = OApsAIMIautoDriveActive),
    OApsAIMIRecursiveBeliefAuthority(
        key = "key_aimi_recursive_belief_authority",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_recursive_belief_authority,
        summary = KeysStrings.pref_summary_aimi_recursive_belief_authority,
        dependency = OApsAIMIautoDriveActive),
    OApsAIMIRecursiveBeliefShadow(
        key = "key_aimi_recursive_belief_shadow",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_recursive_belief_shadow,
        summary = KeysStrings.pref_summary_aimi_recursive_belief_shadow,
        dependency = OApsAIMIautoDriveActive),
    OApsAIMIHyperTrajectoryRelease(
        key = "key_aimi_hyper_trajectory_release",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_hyper_trajectory_release,
        summary = KeysStrings.pref_summary_aimi_hyper_trajectory_release,
        dependency = OApsAIMIautoDriveActive),
    OApsAIMIHyperTrajectoryReleaseAggressive(
        key = "key_aimi_hyper_trajectory_release_aggressive",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_hyper_trajectory_release_aggressive,
        summary = KeysStrings.pref_summary_aimi_hyper_trajectory_release_aggressive,
        dependency = OApsAIMIHyperTrajectoryRelease),
    OApsAIMIEffectiveIobReleaseEnabled( "key_aimi_effective_iob_release_enabled", true, title = TextRef.Literal("")),
    OApsAIMIIobSurveillanceGuard( "key_aimi_iob_surveillance_guard", true, title = TextRef.Literal("")),
    OApsAIMIAimiSmbComparatorEnabled( "key_aimi_smb_comparator_enabled", false, title = TextRef.Literal("")),
    OApsAIMILoopExclusiveInvocationEnabled( "key_aimi_loop_exclusive_invocation", true, title = TextRef.Literal("")),
    OApsAIMILoopBlackboxFileEnabled( "key_aimi_loop_blackbox_file_enabled", true, title = TextRef.Literal("")),
    OApsAIMIPkpdPragmaticReliefEnabled( "key_aimi_pkpd_pragmatic_relief_enabled", true, title = TextRef.Literal("")),
    OApsAIMIHyperDroppingExemptEnabled( "key_aimi_hyper_dropping_exempt_enabled", true, title = TextRef.Literal("")),
    OApsAIMIMealConfirmedEarlyRelease(
        key = "key_aimi_meal_confirmed_early_release",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_meal_confirmed_early_release,
        summary = KeysStrings.pref_summary_aimi_meal_confirmed_early_release),
    OApsAIMISensorConfidenceCgmFirst(
        key = "key_aimi_sensor_confidence_cgm_first",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_sensor_confidence_cgm_first,
        summary = KeysStrings.pref_summary_aimi_sensor_confidence_cgm_first),
    OApsAIMITreeMealRiseFrontLoad(
        key = "key_aimi_tree_meal_rise_frontload",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_tree_meal_rise_frontload,
        summary = KeysStrings.pref_summary_aimi_tree_meal_rise_frontload),
    OApsAIMIMealHyperBypassEnabled( "key_aimi_meal_hyper_bypass_enabled", true, title = TextRef.Literal("")),
    OApsAIMIIntelligenceSnapshotExport( "key_aimi_intelligence_snapshot_export", true, title = TextRef.Literal("")),
    OApsAIMIPredictionAuthorityEnabled( key = "key_aimi_prediction_authority_enabled", defaultValue = true, title = TextRef.Literal(""), dependency = OApsAIMIIntelligenceSnapshotExport),
    OApsAIMIPredictionAuthorityShadow( "key_aimi_prediction_authority_shadow", true, title = TextRef.Literal("")),
    OApsAIMIIntelligenceKineticsProfiler( "key_aimi_intelligence_kinetics_profiler", true, title = TextRef.Literal("")),
    OApsAIMIDiaGovernorEnabled( "key_aimi_dia_governor_enabled", true, title = TextRef.Literal("")),
    OApsAIMIIntelligenceSingleLearnPath( "key_aimi_intelligence_single_learn_path", true, title = TextRef.Literal("")),
    OApsAIMIPeakGovernorEnabled(
        key = "key_aimi_peak_governor_enabled",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_peak_governor_enabled,
        summary = KeysStrings.pref_summary_aimi_peak_governor_enabled),
    OApsAIMIPkpdSetupWizardCompleted( "key_aimi_pkpd_setup_wizard_completed", false, title = TextRef.Literal("")),
    OApsAIMIPkpdEnabled( "key_aimi_pkpd_enabled", false, title = TextRef.Literal("")),
    OApsAIMINightGrowthEnabled( "key_oaps_aimi_ngr_enabled", true, title = TextRef.Literal("")),
    OApsAIMIWCycleRequireConfirm( "key_use_Aimi_wcycle_require_confirm", false, title = TextRef.Literal("")),
    OApsAIMIWCycleShadow( "key_use_Aimi_wcycle_shadow", false, title = TextRef.Literal("")),
    OApsAIMIwcycle( key = "key_use_Aimi_wcycle", defaultValue = false, title = TextRef.Literal("")),
    OApsAIMIautoDriveAuthoritative(
        key = "key_aimi_autodrive_v3_authoritative",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_autodrive_v3_authoritative,
        summary = KeysStrings.pref_summary_aimi_autodrive_v3_authoritative,
        dependency = OApsAIMIautoDriveActive),
    /**
     * Opt-in: while a stress signature holds, forbid the commanded insulin sensitivity from falling
     * under the profile sensitivity of this time of day.
     *
     * The signature is heart rate at least 20 bpm over resting, fewer than 250 steps in the last
     * 15 min, held without a break for at least 10 min. It is evaluated 24 hours a day, with no time
     * window. The gesture only ever **raises** the commanded sensitivity, which makes every prediction
     * attribute a larger effect to the insulin already on board, so it can only make a dose smaller.
     *
     * The verdict is computed and exported on every tick even when this key is false, so the effect can
     * be measured before the gesture is armed. See `StressIsfFloor`.
     */
    OApsAIMIStressIsfFloor(
        key = "key_aimi_stress_isf_floor",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_stress_isf_floor,
        summary = KeysStrings.pref_summary_aimi_stress_isf_floor,
    ),
    /**
     * Opt-in: refuse a **bolus** that repeats the ceiling dose during a fast rise.
     *
     * Refuses only when both conditions of `RiseCeilingGuard` hold: the bolus has come out exactly
     * at a configured ceiling for 3 ticks in a row, and glucose is rising by at least 8 mg/dL per
     * 5 min. The first doses of a rise are never touched, only the ones sent while the earlier ones
     * cannot yet be seen.
     *
     * Bolus channel only: the temporary basal command is untouched. The verdict is computed and
     * exported on every tick even when this key is false, so the effect can be measured before the
     * gesture is armed — the thresholds were chosen after seeing the data and still need a
     * measurement made in advance.
     */
    OApsAIMIRiseCeilingGuard(
        key = "key_aimi_rise_ceiling_guard",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_rise_ceiling_guard,
        summary = KeysStrings.pref_summary_aimi_rise_ceiling_guard,
    ),
    /**
     * Opt-in: spend a bounded budget of insulin as a basal floor once a meal has been **declared**.
     *
     * The person writes a note holding "anticip" with a duration; while that note is open and the
     * declaration still looks true, the basal is held at profile plus
     * `OApsAIMIAnticipBudgetU` spread over the 30-minute window of `AnticipationBasalFloor`. No
     * prebolus is attached, unlike the meal modes, and the target is not touched.
     *
     * ⚠️ This is the one AIMI gesture that **raises** a dose. It is bounded three ways — the budget,
     * the window, and the pump ceiling — it stands down under 80 mg/dL or on a fall of 3 mg/dL per
     * 5 min, and deleting the note ends it at once. Default OFF.
     */
    OApsAIMIAnticipBasalFloor(
        key = "key_aimi_anticip_basal_floor",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_anticip_basal_floor,
        summary = KeysStrings.pref_summary_aimi_anticip_basal_floor,
    ),
    /**
     * Opt-in: let a declared meal count as tree meal evidence.
     *
     * Separate from `OApsAIMIAnticipBasalFloor` on purpose. Tree meal evidence is one of the
     * disjuncts of the early release's `strongMealConfirmed` gate, so switching this on arms that
     * release on demand — a much wider effect than the basal floor. Two keys keep the two effects
     * measurable apart. Default OFF.
     */
    OApsAIMIAnticipMealEvidence(
        key = "key_aimi_anticip_meal_evidence",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_anticip_meal_evidence,
        summary = KeysStrings.pref_summary_aimi_anticip_meal_evidence,
    ),
    OApsAIMIEffortActivityProtection(
        key = "key_aimi_effort_activity_protection",
        defaultValue = true,
        title = KeysStrings.pref_title_aimi_effort_activity_protection,
        summary = KeysStrings.pref_summary_aimi_effort_activity_protection),
    OApsAIMIDescentRedoseGuard(
        key = "key_aimi_descent_redose_guard",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_descent_redose_guard,
        summary = KeysStrings.pref_summary_aimi_descent_redose_guard),
    OApsAIMIautodriveAggressiveSmbFloor(
        key = "key_aimi_autodrive_aggressive_smb_floor",
        defaultValue = false,
        title = KeysStrings.pref_title_aimi_autodrive_aggressive_smb_floor,
        summary = KeysStrings.pref_summary_aimi_autodrive_aggressive_smb_floor,
        dependency = OApsAIMIautoDriveActive),
    OApsAIMIAutodriveV3EnhancedGater( "key_use_aimi_autodrive_v3_enhanced_gater", false, title = TextRef.Literal("")),
    OApsAIMIT3cPhysioInformedEnabled( "key_aimi_t3c_physio_informed", true, title = TextRef.Literal("")),
    OApsAIMIT3cAdaptiveBasalEnabled( "key_use_aimi_t3c_adaptive_basal", true, title = TextRef.Literal("")),
    OApsxdriponeminute( key = "key_use_Aimi_xdripOM", defaultValue = false, title = TextRef.Literal("")),
    OApsAIMIhoneymoon( "key_use_Aimi_honeymoon", false, title = TextRef.Literal("")),
    OApsAIMInight(
        "OApsAIMI_Enable_night",
        false,
        title = KeysStrings.pref_title_oaps_aimi_night_mode,
        summary = KeysStrings.pref_summary_oaps_aimi_night_mode),
    OApsAIMIforcelimits( "key_use_AimiForceLimits", false, title = TextRef.Literal("")),
    OApsAIMIpregnancy( "key_use_AimiPregnancy", false, title = TextRef.Literal("")),
    OApsAIMIEnableStepsFromWatch( "count_steps_watch", false, title = TextRef.Literal("")),
    OApsAIMIEnableBasal( "key_enable_basal", false, title = TextRef.Literal("")),
    OApsAIMIMLtraining( "key_enable_ML_training", false, title = TextRef.Literal("")),

    ;

}
