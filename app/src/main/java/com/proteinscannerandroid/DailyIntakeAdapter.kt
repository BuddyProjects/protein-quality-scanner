package com.proteinscannerandroid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.proteinscannerandroid.data.DailyIntakeEntity
import com.proteinscannerandroid.databinding.ItemDailyIntakeBinding
import java.text.SimpleDateFormat
import java.util.*

class DailyIntakeAdapter(
    private val onDeleteClick: (DailyIntakeEntity) -> Unit
) : ListAdapter<DailyIntakeEntity, DailyIntakeAdapter.ViewHolder>(DiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class DiffCallback : DiffUtil.ItemCallback<DailyIntakeEntity>() {
        override fun areItemsTheSame(a: DailyIntakeEntity, b: DailyIntakeEntity) = a.id == b.id
        override fun areContentsTheSame(a: DailyIntakeEntity, b: DailyIntakeEntity) = a == b
    }

    class ViewHolder(val binding: ItemDailyIntakeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDailyIntakeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.binding.tvTime.text = timeFormat.format(Date(entry.timestamp))
        holder.binding.tvProductName.text = entry.productName
        holder.binding.tvProteinGrams.text = String.format("%.1fg", entry.proteinGrams)

        holder.binding.btnDelete.setOnClickListener {
            onDeleteClick(entry)
        }
    }
}
