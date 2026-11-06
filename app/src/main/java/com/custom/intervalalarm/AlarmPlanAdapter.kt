package com.custom.intervalalarm

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.custom.intervalalarm.databinding.ItemAlarmPlanBinding
import java.util.Date

class AlarmPlanAdapter(
    private val plans: MutableList<AlarmPlan>,
    private val onToggle: (AlarmPlan, Boolean) -> Unit,
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
            tvInterval.text = "Repeats every ${plan.intervalDays} days"

            val timesStr = if (plan.times.isEmpty()) {
                "No times configured"
            } else {
                plan.times.joinToString(", ") { it.format() }
            }
            tvTimes.text = "Times: $timesStr"

            switchEnabled.isChecked = plan.isEnabled

            val nextTrigger = AlarmScheduler.getNextTriggerTime(plan)
            if (nextTrigger != null && plan.isEnabled) {
                val dateText = DateFormat.format("EEE, MMM d, yyyy 'at' hh:mm a", Date(nextTrigger))
                tvNextRing.text = "Next: $dateText"
            } else {
                tvNextRing.text = if (plan.isEnabled) "No upcoming alarms" else "Alarm is off"
            }

            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(plan, isChecked)
            }

            btnDelete.setOnClickListener {
                onDelete(plan)
            }
        }
    }
}
