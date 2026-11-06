package com.custom.intervalalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Calendar

object AlarmScheduler {

    /**
     * Calculates the next upcoming trigger timestamp for this alarm plan
     * based on start date, interval in days, and configured times of day.
     */
    fun getNextTriggerTime(plan: AlarmPlan, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (!plan.isEnabled || plan.times.isEmpty() || plan.intervalDays <= 0) return null

        val intervalMillis = plan.intervalDays * 24L * 60L * 60L * 1000L

        // Reference start calendar
        val startCal = Calendar.getInstance().apply {
            timeInMillis = plan.startDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Determine current cycle number relative to start date
        val diff = nowMillis - startCal.timeInMillis
        var cycle = if (diff < 0) 0L else (diff / intervalMillis)

        // Search through the next few cycles (up to 3 cycles ahead) for the next valid time
        for (c in cycle..(cycle + 3)) {
            val cycleDateMillis = startCal.timeInMillis + (c * intervalMillis)
            
            // For this cycle date, check each configured time
            val candidateTimes = plan.times.map { time ->
                val targetCal = Calendar.getInstance().apply {
                    timeInMillis = cycleDateMillis
                    set(Calendar.HOUR_OF_DAY, time.hour)
                    set(Calendar.MINUTE, time.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                targetCal.timeInMillis
            }.filter { it > nowMillis }

            if (candidateTimes.isNotEmpty()) {
                return candidateTimes.minOrNull()
            }
        }

        return null
    }

    fun schedulePlan(context: Context, plan: AlarmPlan) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val nextTime = getNextTriggerTime(plan)
        val pendingIntent = getPendingIntent(context, plan)

        if (nextTime != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    // Requires exact alarm permission
                    return
                }
            }

            // Android 6.0+ exact alarm that wakes device even during Doze/Idle
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTime,
                pendingIntent
            )
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun cancelPlan(context: Context, plan: AlarmPlan) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getPendingIntent(context, plan)
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context) {
        val plans = StorageManager(context).getPlans()
        for (plan in plans) {
            if (plan.isEnabled) {
                schedulePlan(context, plan)
            } else {
                cancelPlan(context, plan)
            }
        }
    }

    private fun getPendingIntent(context: Context, plan: AlarmPlan): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("PLAN_ID", plan.id)
            putExtra("PLAN_TITLE", plan.title)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, plan.id.hashCode(), intent, flags)
    }
}

