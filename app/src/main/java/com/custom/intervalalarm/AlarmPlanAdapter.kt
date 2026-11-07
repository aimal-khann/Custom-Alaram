package com.custom.intervalalarm

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.custom.intervalalarm.databinding.ItemAlarmPlanBinding
import java.util.Date

class AlarmPlanAdapter(
    private val plans: MutableList<AlarmPlan>,
    private val onToggle: (AlarmPlan, Boolean) -> Unit,
    private val onEdit: (AlarmPlan) -> Unit,
    private val onPreviewDates: (AlarmPlan) -> Unit,
    private val onDelete: (AlarmPlan) -> Unit
) : RecyclerView.Adapter<AlarmPlanAdapter.PlanViewHolder>() {

    inner class PlanViewHolder(val binding: ItemAlarmPlanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemAlarmPlanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlanViewHolder(binding)
    }

    override fun getItemCount(): Int = plans.size

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        val plan = plans[position]
        with(holder.binding) {
            tvTitle.text = plan.title.ifBlank { "Interval Alarm" }
            tvIntervalBadge.text = "Every ${plan.intervalDays} days"

            // Times readout
            val timesStr = if (plan.times.isEmpty()) {
                "No times configured"
            } else {
                plan.times.joinToString(", ") { it.format() }
            }
            tvTimesList.text = "Times: $timesStr"

            // Next trigger preview
            val nextTrigger = AlarmScheduler.getNextTriggerTime(plan)
            if (nextTrigger != null && plan.isEnabled) {
                val dateText = DateFormat.format("EEE, MMM d, yyyy 'at' hh:mm a", Date(nextTrigger))
                tvNextOccurrence.text = "Next: $dateText"
                tvNextOccurrence.setTextColor(ContextCompat.getColor(root.context, R.color.accentGreen))
            } else {
                tvNextOccurrence.text = if (plan.isEnabled) "No upcoming alarms" else "Alarm is paused"
                tvNextOccurrence.setTextColor(ContextCompat.getColor(root.context, R.color.onSurfaceVariant))
            }

            // Remove listener before setting checked to avoid feedback loops
            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = plan.isEnabled
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(plan, isChecked)
            }

            // Card click & button handlers
            cardContainer.setOnClickListener {
                onEdit(plan)
            }

            btnEdit.setOnClickListener {
                onEdit(plan)
            }

            btnSchedulePreview.setOnClickListener {
                onPreviewDates(plan)
            }

            btnDelete.setOnClickListener {
                onDelete(plan)
            }
        }
    }
}
