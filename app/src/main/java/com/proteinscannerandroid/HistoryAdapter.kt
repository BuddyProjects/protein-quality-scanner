package com.proteinscannerandroid

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.proteinscannerandroid.data.ScanHistoryEntity
import com.proteinscannerandroid.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HistoryAdapter(
    private val onItemClick: (ScanHistoryEntity) -> Unit
) : ListAdapter<ScanHistoryEntity, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScanHistoryEntity, onItemClick: (ScanHistoryEntity) -> Unit) {
            binding.tvProductName.text = item.productName
            binding.tvPdcaasBadge.text = String.format("%.2f", item.pdcaasScore)
            binding.tvTimestamp.text = getRelativeTime(item.timestamp)

            // Color the badge based on PDCAAS score
            val color = when {
                item.pdcaasScore >= 0.9 -> ContextCompat.getColor(binding.root.context, R.color.quality_excellent)
                item.pdcaasScore >= 0.75 -> ContextCompat.getColor(binding.root.context, R.color.quality_good)
                item.pdcaasScore >= 0.5 -> ContextCompat.getColor(binding.root.context, R.color.quality_medium)
                else -> ContextCompat.getColor(binding.root.context, R.color.quality_low)
            }

            val background = binding.tvPdcaasBadge.background as? GradientDrawable
            background?.setColor(color)

            binding.root.setOnClickListener { onItemClick(item) }
        }

        private fun getRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> {
                    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                    "$mins ${if (mins == 1L) "minute" else "minutes"} ago"
                }
                diff < TimeUnit.DAYS.toMillis(1) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    "$hours ${if (hours == 1L) "hour" else "hours"} ago"
                }
                diff < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    "$days ${if (days == 1L) "day" else "days"} ago"
                }
                else -> {
                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<ScanHistoryEntity>() {
        override fun areItemsTheSame(oldItem: ScanHistoryEntity, newItem: ScanHistoryEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScanHistoryEntity, newItem: ScanHistoryEntity): Boolean {
            return oldItem == newItem
        }
    }
}
