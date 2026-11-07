package com.custom.intervalalarm

data class AlarmPlan(
    val id: String,
    var title: String,
    var intervalDays: Int, // e.g. 10 or 15 days
    var startDateMillis: Long, // Base reference date
    var times: MutableList<AlarmTime>, // List of times on that day
    var isEnabled: Boolean = true,
    var snoozeMinutes: Int = 10, // 5, 10, 15
    var ringtoneUri: String? = null, // URI of sound
    var ringtoneTitle: String = "Default Alarm Tone",
    var vibrate: Boolean = true,
    var vibrationPattern: String = "Normal", // "Normal", "Heartbeat", "Urgent"
    var gradualVolume: Boolean = false // Starts quiet and gradually increases
)

data class AlarmTime(
    val id: String = java.util.UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    var label: String = "" // Optional custom sub-label, e.g. "Morning Dose"
) {
    fun format(): String {
        val h = if (hour == 0 || hour == 12) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format("%02d:%02d %s", h, minute, amPm)
    }
}
