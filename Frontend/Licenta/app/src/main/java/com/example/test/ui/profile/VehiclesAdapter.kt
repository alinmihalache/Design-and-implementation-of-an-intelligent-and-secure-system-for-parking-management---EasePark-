package com.example.test.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.test.R
import com.example.test.databinding.ItemVehicleBinding
import com.example.test.models.Vehicle

class VehiclesAdapter(
    private val onDeleteClick: (Long) -> Unit
) : ListAdapter<Vehicle, VehiclesAdapter.VehicleViewHolder>(VehicleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val binding = ItemVehicleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VehicleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VehicleViewHolder(
        private val binding: ItemVehicleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(vehicle: Vehicle) {
            binding.apply {
                tvPlateNumber.text = vehicle.plateNumber
                tvBrandModel.text = "${vehicle.make} ${vehicle.model}"
                tvYearType.text = "${vehicle.year}  |  ${vehicle.type.toString().lowercase()}"
                btnDelete.setOnClickListener {
                    onDeleteClick(vehicle.id)
                }
                // Set icon based on type
                val iconRes = when (vehicle.type.toString().lowercase()) {
                    "car" -> R.drawable.ic_vehicle
                    "motorcycle" -> R.drawable.ic_motorcycle
                    "van" -> R.drawable.ic_van
                    else -> R.drawable.ic_vehicle
                }
                ivTypeIcon.setImageResource(iconRes)
            }
        }
    }

    private class VehicleDiffCallback : DiffUtil.ItemCallback<Vehicle>() {
        override fun areItemsTheSame(oldItem: Vehicle, newItem: Vehicle): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Vehicle, newItem: Vehicle): Boolean {
            return oldItem == newItem
        }
    }
} 