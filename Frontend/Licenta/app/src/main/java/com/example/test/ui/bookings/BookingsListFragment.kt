package com.example.test.ui.bookings

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test.R
import com.example.test.auth.AuthManager
import com.example.test.databinding.FragmentBookingsListBinding
import com.example.test.databinding.DialogReservationBinding
import com.example.test.models.OperationStatus
import com.example.test.models.Reservation
import com.example.test.repository.ParkingRepository
import com.example.test.utils.ParkingSpotUtils
import com.example.test.viewmodel.BookingsViewModel
import com.example.test.viewmodel.BookingsViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BookingsListFragment : Fragment() {
    private var _binding: FragmentBookingsListBinding? = null
    private val binding get() = _binding!!

    private val bookingsViewModel: BookingsViewModel by activityViewModels {
        BookingsViewModelFactory(ParkingRepository(), AuthManager.getInstance())
    }
    private lateinit var activeBookingsAdapter: BookingsAdapter
    private lateinit var pastBookingsAdapter: BookingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        observeViewModel()
        observeOperationStatus()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time the fragment is shown
        bookingsViewModel.loadReservations()
    }

    private fun setupRecyclerViews() {
        // Create separate adapter instances for each list
        activeBookingsAdapter = BookingsAdapter(
            onExtendClick = { showExtendDialog(it) },
            onCancelClick = { showCancelDialog(it) }
        )
        pastBookingsAdapter = BookingsAdapter(
            onExtendClick = { /* Past bookings cannot be extended */ },
            onCancelClick = { /* Past bookings cannot be cancelled */ }
        )
        binding.mainRecyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            bookingsViewModel.activeReservations.collectLatest { activeReservations ->
                bookingsViewModel.pastReservations.collectLatest { pastReservations ->
                    updateAdapters(activeReservations, pastReservations)
                }
            }
        }
    }

    private fun updateAdapters(active: List<Reservation>, past: List<Reservation>) {
        val adapters = mutableListOf<RecyclerView.Adapter<*>>()

        // Handle overall empty state
        if (active.isEmpty() && past.isEmpty()) {
            binding.mainRecyclerView.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            return
        } else {
            binding.mainRecyclerView.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }

        // Add active bookings section if not empty
        if (active.isNotEmpty()) {
            adapters.add(HeaderAdapter(getString(R.string.label_active_reservations)))
            activeBookingsAdapter.submitList(active)
            adapters.add(activeBookingsAdapter)
        }

        // Add past bookings section if not empty
        if (past.isNotEmpty()) {
            adapters.add(HeaderAdapter(getString(R.string.label_past_reservations)))
            pastBookingsAdapter.submitList(past)
            adapters.add(pastBookingsAdapter)
        }
        
        binding.mainRecyclerView.adapter = ConcatAdapter(adapters)
    }

    private fun observeOperationStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            bookingsViewModel.operationStatus.collect { status ->
                when (status) {
                    is OperationStatus.Success -> {
                        Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                        bookingsViewModel.resetOperationStatus()
                    }
                    is OperationStatus.Error -> {
                        Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                        bookingsViewModel.resetOperationStatus()
                    }
                    else -> { /* Do nothing for Initial or Loading */ }
                }
            }
        }
    }

    private fun showExtendDialog(reservation: Reservation) {
        val calendar = Calendar.getInstance()
        val currentEndTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(reservation.endTime) ?: Date()
        
        calendar.time = currentEndTime

        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            val newEndTime = Calendar.getInstance().apply {
                time = currentEndTime
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
            }
            if (newEndTime.time.after(currentEndTime)) {
                bookingsViewModel.extendReservation(reservation.id, newEndTime.time)
            } else {
                Toast.makeText(context, "New end time must be after the current one.", Toast.LENGTH_SHORT).show()
            }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun showCancelDialog(reservation: Reservation) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_title_cancel_booking))
            .setMessage(getString(R.string.dialog_confirm_cancellation))
            .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.dialog_confirm)) { dialog, _ ->
                bookingsViewModel.cancelReservation(reservation.id)
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}