package com.custom.intervalalarm

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StorageManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("interval_alarm_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun savePlans(plans: List<AlarmPlan>) {
        val json = gson.toJson(plans)
        prefs.edit().putString("saved_plans", json).apply()
    }

    fun getPlans(): MutableList<AlarmPlan> {
        val json = prefs.getString("saved_plans", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<AlarmPlan>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }
}

