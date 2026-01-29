package com.proteinscannerandroid

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.proteinscannerandroid.data.FavoriteEntity
import com.proteinscannerandroid.databinding.ItemFavoriteBinding
import java.text.SimpleDateFormat
import java.util.*

class FavoritesAdapter(
    private val onItemClick: (FavoriteEntity) -> Unit,
    private val onItemLongClick: (FavoriteEntity) -> Unit,
    private val onSelectionChanged: (Set<Long>) -> Unit
) : ListAdapter<FavoriteEntity, FavoritesAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            isSelectionMode,
            selectedIds.contains(getItem(position).id),
            onItemClick,
            onItemLongClick,
            { toggleSelection(getItem(position).id) }
        )
    }

    class FavoriteViewHolder(
        private val binding: ItemFavoriteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: FavoriteEntity,
            isSelectionMode: Boolean,
            isSelected: Boolean,
            onItemClick: (FavoriteEntity) -> Unit,
            onItemLongClick: (FavoriteEntity) -> Unit,
            onToggleSelection: () -> Unit
        ) {
            binding.tvProductName.text = item.productName
            binding.tvPdcaasBadge.text = String.format("%.2f", item.pdcaasScore)
            binding.tvAddedDate.text = "Added ${formatDate(item.addedAt)}"

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
            binding.ivHeart.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
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

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteEntity>() {
        override fun areItemsTheSame(oldItem: FavoriteEntity, newItem: FavoriteEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FavoriteEntity, newItem: FavoriteEntity): Boolean {
            return oldItem == newItem
        }
    }
}
