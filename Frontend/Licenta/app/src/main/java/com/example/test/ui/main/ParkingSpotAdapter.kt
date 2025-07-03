package com.example.test.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.test.R
import com.example.test.databinding.ItemParkingSpotBinding
import com.example.test.models.ParkingSpot
import com.example.test.models.ParkingSpotStatus
import com.example.test.utils.ColorUtils
import java.text.SimpleDateFormat
import java.util.*

class ParkingSpotAdapter(
    private val onReserveClick: (ParkingSpot) -> Unit
) : ListAdapter<ParkingSpot, ParkingSpotAdapter.ParkingSpotViewHolder>(ParkingSpotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingSpotViewHolder {
        val binding = ItemParkingSpotBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ParkingSpotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParkingSpotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ParkingSpotViewHolder(
        private val binding: ItemParkingSpotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(spot: ParkingSpot) {
            val displayStatus = spot.getDisplayStatus()
            val isAvailable = spot.isAvailableForReservation()
            
            binding.apply {
                tvSpotName.text = spot.address
                tvSpotAddress.text = spot.address
                tvStatus.text = getStatusText(root.context, displayStatus)
                tvStatus.setTextColor(ColorUtils.getStatusColor(root.context, displayStatus))
                tvPrice.text = String.format("%.2f RON/h", spot.pricePerHour)
                
                // Show reservation info if available
                if (spot.currentReservation != null) {
                    val reservation = spot.currentReservation
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    
                    try {
                        val startDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(reservation.startTime)
                            ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(reservation.startTime)
                        val endDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(reservation.endTime)
                            ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(reservation.endTime)
                        
                        if (startDate != null && endDate != null) {
                            val reservationText = "${dateFormat.format(startDate)} ${timeFormat.format(startDate)} - ${timeFormat.format(endDate)}"
                            tvReservationInfo.text = reservationText
                            tvReservationInfo.visibility = android.view.View.VISIBLE
                        } else {
                            tvReservationInfo.visibility = android.view.View.GONE
                        }
                    } catch (e: Exception) {
                        tvReservationInfo.visibility = android.view.View.GONE
                    }
                } else {
                    tvReservationInfo.visibility = android.view.View.GONE
                }
                
                btnReserve.isEnabled = isAvailable
                btnReserve.setOnClickListener { onReserveClick(spot) }
            }
        }
    }

    private class ParkingSpotDiffCallback : DiffUtil.ItemCallback<ParkingSpot>() {
        override fun areItemsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        fun getStatusText(context: android.content.Context, status: ParkingSpotStatus): String = when (status) {
            ParkingSpotStatus.AVAILABLE -> context.getString(R.string.status_available)
            ParkingSpotStatus.OCCUPIED -> context.getString(R.string.status_occupied)
            ParkingSpotStatus.RESERVED -> context.getString(R.string.status_reserved)
            ParkingSpotStatus.MAINTENANCE -> context.getString(R.string.status_maintenance)
        }
    }
} 