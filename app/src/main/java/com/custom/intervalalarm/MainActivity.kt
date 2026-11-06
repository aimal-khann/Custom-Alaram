package com.custom.intervalalarm

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.custom.intervalalarm.databinding.ActivityMainBinding
import com.custom.intervalalarm.databinding.DialogCreateAlarmBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storageManager: StorageManager
    private val plans = mutableListOf<AlarmPlan>()
    private lateinit var adapter: AlarmPlanAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storageManager = StorageManager(this)
        plans.addAll(storageManager.getPlans())

        setupRecyclerView()
        checkPermissions()

        binding.fabAdd.setOnClickListener {
            showCreateAlarmDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = AlarmPlanAdapter(
            plans,
            onToggle = { plan, isChecked ->
                plan.isEnabled = isChecked
                storageManager.savePlans(plans)
                if (isChecked) {
                    AlarmScheduler.schedulePlan(this, plan)
                } else {
                    AlarmScheduler.cancelPlan(this, plan)
                }
                adapter.notifyDataSetChanged()
            },
            onDelete = { plan ->
                AlarmScheduler.cancelPlan(this, plan)
                plans.remove(plan)
                storageManager.savePlans(plans)
                updateUIState()
            }
        )

        binding.rvPlans.layoutManager = LinearLayoutManager(this)
        binding.rvPlans.adapter = adapter
        updateUIState()
    }

    private fun updateUIState() {
        adapter.notifyDataSetChanged()
        if (plans.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvPlans.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvPlans.visibility = View.VISIBLE
        }
    }

    private fun showCreateAlarmDialog() {
        val dialogBinding = DialogCreateAlarmBinding.inflate(layoutInflater)
        val selectedTimes = mutableListOf<AlarmTime>()
        var selectedStartDateMillis = System.currentTimeMillis()

        val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
        dialogBinding.btnPickStartDate.text = "Start: " + dateFormat.format(selectedStartDateMillis)

        dialogBinding.btnPickStartDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth, 0, 0, 0)
                    selectedStartDateMillis = cal.timeInMillis
                    dialogBinding.btnPickStartDate.text = "Start: " + dateFormat.format(selectedStartDateMillis)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        fun updateTimesSummary() {
            if (selectedTimes.isEmpty()) {
                dialogBinding.tvTimesSummary.text = "No times added yet"
            } else {
                dialogBinding.tvTimesSummary.text = selectedTimes.joinToString(", ") { it.format() }
            }
        }

        dialogBinding.btnAddTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedTimes.add(AlarmTime(hourOfDay, minute))
                    selectedTimes.sortBy { it.hour * 60 + it.minute }
                    updateTimesSummary()
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Create Interval Alarm")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val title = dialogBinding.etTitle.text.toString().trim()
                val intervalStr = dialogBinding.etIntervalDays.text.toString().trim()
                val interval = intervalStr.toIntOrNull() ?: 10

                if (interval <= 0) {
                    Toast.makeText(this, "Interval must be 1 day or more", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (selectedTimes.isEmpty()) {
                    // Default to 8:00 AM if no time picked
                    selectedTimes.add(AlarmTime(8, 0))
                }

                val newPlan = AlarmPlan(
                    id = UUID.randomUUID().toString(),
                    title = if (title.isEmpty()) "Alarm (Every $interval days)" else title,
                    intervalDays = interval,
                    startDateMillis = selectedStartDateMillis,
                    times = selectedTimes,
                    isEnabled = true
                )

                plans.add(newPlan)
                storageManager.savePlans(plans)
                AlarmScheduler.schedulePlan(this, newPlan)
                updateUIState()
                Toast.makeText(this, "Interval alarm scheduled!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
