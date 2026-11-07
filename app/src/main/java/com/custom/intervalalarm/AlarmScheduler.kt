package com.custom.intervalalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    /**
     * Calculates the next upcoming trigger timestamp for this alarm plan.
     */
    fun getNextTriggerTime(plan: AlarmPlan, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (!plan.isEnabled || plan.times.isEmpty() || plan.intervalDays <= 0) return null

        val intervalMillis = plan.intervalDays * 24L * 60L * 60L * 1000L

        val startCal = Calendar.getInstance().apply {
            timeInMillis = plan.startDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diff = nowMillis - startCal.timeInMillis
        val cycle = if (diff < 0) 0L else (diff / intervalMillis)

        // Check the current and next few cycles
        for (c in cycle..(cycle + 10)) {
            val cycleDateMillis = startCal.timeInMillis + (c * intervalMillis)

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

    /**
     * Returns a list of the next N trigger occurrences for calendar preview.
     */
    fun getUpcomingOccurrences(plan: AlarmPlan, count: Int = 5, nowMillis: Long = System.currentTimeMillis()): List<Long> {
        if (plan.times.isEmpty() || plan.intervalDays <= 0) return emptyList()

        val results = mutableListOf<Long>()
        val intervalMillis = plan.intervalDays * 24L * 60L * 60L * 1000L

        val startCal = Calendar.getInstance().apply {
            timeInMillis = plan.startDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diff = nowMillis - startCal.timeInMillis
        var cycle = if (diff < 0) 0L else (diff / intervalMillis)

        while (results.size < count && cycle < 100) {
            val cycleDateMillis = startCal.timeInMillis + (cycle * intervalMillis)
            val sortedTimes = plan.times.sortedBy { it.hour * 60 + it.minute }
            
            for (time in sortedTimes) {
                val targetCal = Calendar.getInstance().apply {
                    timeInMillis = cycleDateMillis
                    set(Calendar.HOUR_OF_DAY, time.hour)
                    set(Calendar.MINUTE, time.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (targetCal.timeInMillis > nowMillis) {
                    results.add(targetCal.timeInMillis)
                    if (results.size >= count) break
                }
            }
            cycle++
        }
        return results
    }

    fun schedulePlan(context: Context, plan: AlarmPlan) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTime = getNextTriggerTime(plan)
        val pendingIntent = getPendingIntent(context, plan)

        if (nextTime != null && plan.isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    return
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTime,
                pendingIntent
            )
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun scheduleSnooze(context: Context, planId: String, planTitle: String, snoozeMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60L * 1000L)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("PLAN_ID", planId)
            putExtra("PLAN_TITLE", "$planTitle (Snoozed)")
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, (planId + "_snooze").hashCode(), intent, flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
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
