package app.aaps.core.interfaces.notifications

/** Which user-chosen alarm sound applies: low-glucose alarms, or every other urgent alarm. */
enum class AlarmSoundCategory {

    LOW_GLUCOSE,
    OTHER;

    companion object {

        /** Low-glucose alarms get their own sound choice; every other urgent alarm shares the second one. */
        fun of(id: NotificationId): AlarmSoundCategory = when (id) {
            NotificationId.BG_HYPO, NotificationId.BG_RAPID_FALL -> LOW_GLUCOSE
            else                                                 -> OTHER
        }
    }
}
