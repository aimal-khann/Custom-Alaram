package com.custom.intervalalarm

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.custom.intervalalarm.databinding.ActivityMainBinding
import com.custom.intervalalarm.databinding.DialogCreateAlarmBinding
import com.custom.intervalalarm.databinding.DialogSchedulePreviewBinding
import com.custom.intervalalarm.databinding.ItemTimeChipBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
            showAlarmEditorDialog(null)
        }

        binding.btnEmptyCreate.setOnClickListener {
            showAlarmEditorDialog(null)
        }

        updateHeaderSummary()
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
                updateUIState()
            },
            onEdit = { plan ->
                showAlarmEditorDialog(plan)
            },
            onPreviewDates = { plan ->
                showUpcomingDatesDialog(plan)
            },
            onDelete = { plan ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Alarm?")
                    .setMessage("Are you sure you want to remove '${plan.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        AlarmScheduler.cancelPlan(this, plan)
                        plans.remove(plan)
                        storageManager.savePlans(plans)
                        updateUIState()
                        Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvPlans.layoutManager = LinearLayoutManager(this)
        binding.rvPlans.adapter = adapter
        updateUIState()
    }

    private fun updateUIState() {
        adapter.notifyDataSetChanged()
        if (plans.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvPlans.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvPlans.visibility = View.VISIBLE
        }
        updateHeaderSummary()
    }

    private fun updateHeaderSummary() {
        val nextTimes = plans.filter { it.isEnabled }
            .mapNotNull { AlarmScheduler.getNextTriggerTime(it) }
        
        if (nextTimes.isNotEmpty()) {
            val closest = nextTimes.minOrNull()!!
            val dateText = DateFormat.format("EEE, MMM d 'at' hh:mm a", Date(closest))
            val diffMs = closest - System.currentTimeMillis()
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffDays = diffHours / 24
            val remHours = diffHours % 24

            val relativeTime = if (diffDays > 0) {
                "in $diffDays d, $remHours h"
            } else {
                "in $diffHours hours"
            }

            binding.tvUpcomingSummary.text = "Next: $dateText ($relativeTime)"
        } else {
            binding.tvUpcomingSummary.text = if (plans.isEmpty()) "Tap '+ New Alarm' to get started" else "All alarms are paused"
        }
    }

    private fun showAlarmEditorDialog(existingPlan: AlarmPlan?) {
        val dialogBinding = DialogCreateAlarmBinding.inflate(layoutInflater)
        val isEditing = existingPlan != null
        val timesList = (existingPlan?.times?.map { it.copy() } ?: mutableListOf()).toMutableList()
        var selectedStartDateMillis = existingPlan?.startDateMillis ?: System.currentTimeMillis()

        dialogBinding.tvDialogTitle.text = if (isEditing) "Edit Interval Alarm" else "Create Interval Alarm"
        dialogBinding.etTitle.setText(existingPlan?.title ?: "")
        dialogBinding.etIntervalDays.setText((existingPlan?.intervalDays ?: 10).toString())

        // Quick interval preset chips
        dialogBinding.chipEvery5.setOnClickListener { dialogBinding.etIntervalDays.setText("5") }
        dialogBinding.chipEvery10.setOnClickListener { dialogBinding.etIntervalDays.setText("10") }
        dialogBinding.chipEvery15.setOnClickListener { dialogBinding.etIntervalDays.setText("15") }
        dialogBinding.chipEvery30.setOnClickListener { dialogBinding.etIntervalDays.setText("30") }

        // Start Date button
        val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
        fun refreshDateButton() {
            dialogBinding.btnPickStartDate.text = "Base Date: " + dateFormat.format(Date(selectedStartDateMillis))
        }
        refreshDateButton()

        dialogBinding.btnPickStartDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedStartDateMillis }
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth, 0, 0, 0)
                    selectedStartDateMillis = cal.timeInMillis
                    refreshDateButton()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Times Chip Group builder
        fun renderTimeChips() {
            dialogBinding.chipGroupTimes.removeAllViews()
            if (timesList.isEmpty()) {
                dialogBinding.tvNoTimesNotice.visibility = View.VISIBLE
            } else {
                dialogBinding.tvNoTimesNotice.visibility = View.GONE
                timesList.sortedBy { it.hour * 60 + it.minute }.forEach { time ->
                    val chipView = ItemTimeChipBinding.inflate(LayoutInflater.from(this), dialogBinding.chipGroupTimes, false)
                    chipView.tvChipTime.text = time.format()
                    chipView.btnRemoveChip.setOnClickListener {
                        timesList.remove(time)
                        renderTimeChips()
                    }
                    dialogBinding.chipGroupTimes.addView(chipView.root)
                }
            }
        }
        renderTimeChips()

        dialogBinding.btnAddTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    timesList.add(AlarmTime(hour = hourOfDay, minute = minute))
                    renderTimeChips()
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        }

        // Snooze duration spinner
        val snoozeOptions = listOf("5 minutes", "10 minutes", "15 minutes")
        val snoozeValues = listOf(5, 10, 15)
        val snoozeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, snoozeOptions)
        dialogBinding.spinnerSnooze.adapter = snoozeAdapter
        val currentSnooze = existingPlan?.snoozeMinutes ?: 10
        dialogBinding.spinnerSnooze.setSelection(snoozeValues.indexOf(currentSnooze).coerceAtLeast(0))

        // Switches
        dialogBinding.switchVibrate.isChecked = existingPlan?.vibrate ?: true
        dialogBinding.switchGradualVolume.isChecked = existingPlan?.gradualVolume ?: false

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val interval = dialogBinding.etIntervalDays.text.toString().toIntOrNull() ?: 0
            if (interval <= 0) {
                Toast.makeText(this, "Please enter a valid interval (at least 1 day)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (timesList.isEmpty()) {
                Toast.makeText(this, "Please add at least one time of day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val titleInput = dialogBinding.etTitle.text.toString().trim()
            val finalTitle = if (titleInput.isEmpty()) "Every $interval Days Alarm" else titleInput
            val selectedSnooze = snoozeValues[dialogBinding.spinnerSnooze.selectedItemPosition]

            if (isEditing) {
                existingPlan!!.title = finalTitle
                existingPlan.intervalDays = interval
                existingPlan.startDateMillis = selectedStartDateMillis
                existingPlan.times = timesList
                existingPlan.vibrate = dialogBinding.switchVibrate.isChecked
                existingPlan.gradualVolume = dialogBinding.switchGradualVolume.isChecked
                existingPlan.snoozeMinutes = selectedSnooze
                AlarmScheduler.schedulePlan(this, existingPlan)
            } else {
                val newPlan = AlarmPlan(
                    id = UUID.randomUUID().toString(),
                    title = finalTitle,
                    intervalDays = interval,
                    startDateMillis = selectedStartDateMillis,
                    times = timesList,
                    isEnabled = true,
                    vibrate = dialogBinding.switchVibrate.isChecked,
                    gradualVolume = dialogBinding.switchGradualVolume.isChecked,
                    snoozeMinutes = selectedSnooze
                )
                plans.add(newPlan)
                AlarmScheduler.schedulePlan(this, newPlan)
            }

            storageManager.savePlans(plans)
            updateUIState()
            dialog.dismiss()
            Toast.makeText(this, if (isEditing) "Alarm updated!" else "Alarm created!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showUpcomingDatesDialog(plan: AlarmPlan) {
        val previewBinding = DialogSchedulePreviewBinding.inflate(layoutInflater)
        previewBinding.tvPreviewTitle.text = plan.title

        val upcomingMillis = AlarmScheduler.getUpcomingOccurrences(plan, count = 5)
        if (upcomingMillis.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No upcoming dates found."
                setTextColor(getColor(R.color.onSurfaceVariant))
            }
            previewBinding.layoutUpcomingList.addView(emptyTv)
        } else {
            for ((idx, millis) in upcomingMillis.withIndex()) {
                val formatted = DateFormat.format("EEEE, MMMM d, yyyy 'at' hh:mm a", Date(millis))
                val tv = TextView(this).apply {
                    text = "${idx + 1}.  $formatted"
                    textSize = 15f
                    setTextColor(getColor(R.color.onSurface))
                    setPadding(0, 10, 0, 10)
                }
                previewBinding.layoutUpcomingList.addView(tv)
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(previewBinding.root)
            .create()

        previewBinding.btnClosePreview.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
