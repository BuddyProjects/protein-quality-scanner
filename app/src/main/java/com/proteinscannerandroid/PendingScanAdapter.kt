package com.proteinscannerandroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.proteinscannerandroid.data.PendingScan
import com.proteinscannerandroid.databinding.ItemPendingScanBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PendingScanAdapter(
    private val onRetryClick: (PendingScan) -> Unit,
    private val onItemClick: (PendingScan) -> Unit
) : ListAdapter<PendingScan, PendingScanAdapter.PendingScanViewHolder>(PendingScanDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingScanViewHolder {
        val binding = ItemPendingScanBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PendingScanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PendingScanViewHolder, position: Int) {
        holder.bind(getItem(position), onRetryClick, onItemClick)
    }

    class PendingScanViewHolder(
        private val binding: ItemPendingScanBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: PendingScan,
            onRetryClick: (PendingScan) -> Unit,
            onItemClick: (PendingScan) -> Unit
        ) {
            binding.tvBarcode.text = item.barcode
            binding.tvTimestamp.text = "Queued ${getRelativeTime(item.timestamp)}"

            // Show error reason if available
            if (!item.errorReason.isNullOrBlank()) {
                binding.tvErrorReason.text = item.errorReason
                binding.tvErrorReason.visibility = View.VISIBLE
            } else {
                binding.tvErrorReason.visibility = View.GONE
            }

            binding.btnRetry.setOnClickListener {
                onRetryClick(item)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun getRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
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

    class PendingScanDiffCallback : DiffUtil.ItemCallback<PendingScan>() {
        override fun areItemsTheSame(oldItem: PendingScan, newItem: PendingScan): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PendingScan, newItem: PendingScan): Boolean {
            return oldItem == newItem
        }
    }
}
