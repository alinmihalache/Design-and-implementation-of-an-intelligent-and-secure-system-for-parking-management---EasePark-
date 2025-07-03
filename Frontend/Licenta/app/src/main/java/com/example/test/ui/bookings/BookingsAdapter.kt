package com.example.test.ui.bookings

import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.test.R
import com.example.test.databinding.ItemBookingBinding
import com.example.test.models.Reservation
import com.example.test.models.ReservationStatus
import com.example.test.utils.ParkingSpotUtils
import java.text.SimpleDateFormat
import java.util.*

class BookingsAdapter(
    private val onExtendClick: (Reservation) -> Unit,
    private val onCancelClick: (Reservation) -> Unit
) : ListAdapter<Reservation, BookingsAdapter.BookingViewHolder>(BookingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemBookingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookingViewHolder(
        private val binding: ItemBookingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(reservation: Reservation) {
            val context = binding.root.context
            binding.apply {
                parkingSpotName.text = context.getString(R.string.booking_spot_label, reservation.parkingSpotId)
                
                try {
                    val startDate = ParkingSpotUtils.parseISO8601ToDate(reservation.startTime)
                    val endDate = ParkingSpotUtils.parseISO8601ToDate(reservation.endTime)
                    
                    if (startDate != null && endDate != null) {
                        reservationTime.text = "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
                    } else {
                        reservationTime.text = context.getString(R.string.error_date_format)
                    }
                    
                    // Debug logging to see what values we're getting
                    Log.d("BookingsAdapter", "Reservation ${reservation.id}: totalPrice='${reservation.totalPrice}', pricePerHour='${reservation.pricePerHour}'")
                    
                    reservationAmount.text = "${reservation.totalPrice} RON"
                } catch (e: Exception) {
                    Log.e("BookingsAdapter", "Error processing reservation dates", e)
                    reservationTime.text = context.getString(R.string.error_date_format)
                    reservationAmount.text = "0.00 RON"
                }
                
                val displayStatus = reservation.getDisplayStatus()
                val (statusText, statusColor) = when (displayStatus) {
                    ReservationStatus.PENDING -> Pair(context.getString(R.string.booking_status_pending), R.color.status_reserved)
                    ReservationStatus.ACTIVE -> Pair(context.getString(R.string.booking_status_active), R.color.status_available)
                    ReservationStatus.COMPLETED -> Pair(context.getString(R.string.booking_status_completed), R.color.colorPrimary)
                    ReservationStatus.CANCELLED -> Pair(context.getString(R.string.booking_status_cancelled), R.color.status_occupied)
                }
                
                reservationStatus.apply {
                    text = statusText
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, statusColor)
                    )
                }

                extendButton.isEnabled = displayStatus == ReservationStatus.ACTIVE
                cancelButton.isEnabled = (displayStatus == ReservationStatus.PENDING || displayStatus == ReservationStatus.ACTIVE)

                extendButton.setOnClickListener {
                    onExtendClick(reservation)
                }

                cancelButton.setOnClickListener {
                    onCancelClick(reservation)
                }
            }
        }
    }

    private class BookingDiffCallback : DiffUtil.ItemCallback<Reservation>() {
        override fun areItemsTheSame(oldItem: Reservation, newItem: Reservation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Reservation, newItem: Reservation): Boolean {
            return oldItem == newItem
        }
    }
} 