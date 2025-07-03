package com.example.test.ui.vehicles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test.databinding.FragmentVehiclesBinding
import com.example.test.databinding.DialogAddVehicleBinding
import com.example.test.ui.profile.VehiclesAdapter
import com.example.test.viewmodel.ProfileViewModel
import com.example.test.viewmodel.ProfileViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.widget.ArrayAdapter
import com.example.test.models.OperationStatus
import java.util.Calendar

class VehiclesFragment : Fragment() {
    private var _binding: FragmentVehiclesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by activityViewModels { ProfileViewModelFactory() }
    private lateinit var vehiclesAdapter: VehiclesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        setupAddVehicleButton()
    }

    private fun setupRecyclerView() {
        vehiclesAdapter = VehiclesAdapter { vehicleId ->
            showDeleteConfirmationDialog(vehicleId)
        }
        binding.rvVehicles.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = vehiclesAdapter
        }
    }

    private fun showDeleteConfirmationDialog(vehicleId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Șterge vehicul")
            .setMessage("Sigur doriți să ștergeți acest vehicul?")
            .setPositiveButton("Da") { _, _ ->
                viewModel.deleteVehicle(vehicleId)
            }
            .setNegativeButton("Nu", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userVehicles.collectLatest { vehicles ->
                vehiclesAdapter.submitList(vehicles)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateStatus.collectLatest { status ->
                when (status) {
                    is OperationStatus.Success -> {
                        Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                        viewModel.resetUpdateStatus()
                    }
                    is OperationStatus.Error -> {
                        Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                        viewModel.resetUpdateStatus()
                    }
                    else -> { /* ignore other states */ }
                }
            }
        }
    }

    private fun setupAddVehicleButton() {
        binding.fabAddVehicle.setOnClickListener {
            showAddVehicleDialog()
        }
    }

    private fun showAddVehicleDialog() {
        val dialogBinding = DialogAddVehicleBinding.inflate(layoutInflater)
        val types = listOf("car", "motorcycle", "van")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        dialogBinding.actvType.setAdapter(adapter)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnAdd.setOnClickListener {
            val plateNumber = dialogBinding.etPlateNumber.text.toString().trim()
            val make = dialogBinding.etMake.text.toString().trim()
            val model = dialogBinding.etModel.text.toString().trim()
            val yearStr = dialogBinding.etYear.text.toString().trim()
            val type = dialogBinding.actvType.text.toString()

            val plateRegex = Regex("^[A-Z0-9]{1,8}(-[A-Z0-9]{1,4})?")
            if (!plateRegex.matches(plateNumber)) {
                Toast.makeText(context, "Invalid license plate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (plateNumber.isBlank() || make.isBlank() || model.isBlank() || yearStr.isBlank() || type.isBlank()) {
                Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val year = yearStr.toIntOrNull()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR) + 1
            if (year == null || year < 1900 || year > currentYear) {
                Toast.makeText(context, "Year must be between 1900 and $currentYear", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (type !in types) {
                Toast.makeText(context, "Invalid vehicle type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addVehicle(plateNumber, make, model, year, type)
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 