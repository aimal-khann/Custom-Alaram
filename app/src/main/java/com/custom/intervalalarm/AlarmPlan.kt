package com.custom.intervalalarm

data class AlarmPlan(
    val id: String,
    var title: String,
    var intervalDays: Int, // e.g. 10 or 15 days
    var startDateMillis: Long, // Base reference date
    var times: MutableList<AlarmTime>, // List of times on that day (e.g. 08:00, 14:00, 21:00)
    var isEnabled: Boolean = true
)

data class AlarmTime(
    val hour: Int,
    val minute: Int
) {
    fun format(): String {
        val h = if (hour == 0 || hour == 12) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format("%02d:%02d %s", h, minute, amPm)
    }
}
