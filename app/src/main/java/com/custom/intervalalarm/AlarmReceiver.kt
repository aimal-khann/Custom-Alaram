package com.custom.intervalalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getStringExtra("PLAN_ID") ?: ""
        val planTitle = intent.getStringExtra("PLAN_TITLE") ?: "Interval Alarm"

        // Launch full-screen ringing activity
        val ringIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra("PLAN_ID", planId)
            putExtra("PLAN_TITLE", planTitle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(ringIntent)

        // Reschedule for the next occurrence in this plan
        val plans = StorageManager(context).getPlans()
        val plan = plans.find { it.id == planId }
        if (plan != null && plan.isEnabled) {
            AlarmScheduler.schedulePlan(context, plan)
        }
    }
}
