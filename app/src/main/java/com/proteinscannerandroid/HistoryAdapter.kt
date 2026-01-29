package com.proteinscannerandroid

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
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
    private val onItemClick: (ScanHistoryEntity) -> Unit,
    private val onItemLongClick: (ScanHistoryEntity) -> Unit = {},
    private val onSelectionChanged: (Set<Long>) -> Unit = {}
) : ListAdapter<ScanHistoryEntity, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<Long>()

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) {
            selectedIds.clear()
        }
        notifyDataSetChanged()
    }

    fun isSelectionMode(): Boolean = isSelectionMode

    fun getSelectedIds(): Set<Long> = selectedIds.toSet()

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            if (selectedIds.size < 2) {
                selectedIds.add(id)
            }
        }
        onSelectionChanged(selectedIds.toSet())
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            isSelectionMode,
            selectedIds.contains(getItem(position).id),
            onItemClick,
            onItemLongClick,
            { toggleSelection(getItem(position).id) }
        )
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ScanHistoryEntity,
            isSelectionMode: Boolean,
            isSelected: Boolean,
            onItemClick: (ScanHistoryEntity) -> Unit,
            onItemLongClick: (ScanHistoryEntity) -> Unit,
            onToggleSelection: () -> Unit
        ) {
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

            // Selection mode UI
            binding.checkbox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.ivArrow.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            binding.checkbox.isChecked = isSelected

            // Highlight selected cards
            val strokeColor = if (isSelected) {
                ContextCompat.getColor(binding.root.context, R.color.primary_accent)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.border_dark)
            }
            (binding.root as com.google.android.material.card.MaterialCardView).strokeColor = strokeColor

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    onToggleSelection()
                } else {
                    onItemClick(item)
                }
            }

            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }

            binding.checkbox.setOnClickListener {
                onToggleSelection()
            }
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
