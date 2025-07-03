package com.example.test.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test.R
import com.example.test.auth.AuthManager
import com.example.test.databinding.DialogShow2faSecretBinding
import com.example.test.databinding.FragmentProfileBinding
import com.example.test.models.OperationStatus
import com.example.test.models.Vehicle
import com.example.test.network.Enable2FAResponse
import com.example.test.viewmodel.Enable2FAState
import com.example.test.viewmodel.ProfileViewModel
import com.example.test.viewmodel.ProfileViewModelFactory
import com.example.test.viewmodel.Verify2FAState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var authManager: AuthManager
    private lateinit var vehiclesAdapter: VehiclesAdapter
    private val viewModel: ProfileViewModel by activityViewModels { ProfileViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        authManager = AuthManager.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupRecyclerView()
        observeViewModel()
        observe2FAStates()
        observeAuthState()
    }

    private fun setupRecyclerView() {
        vehiclesAdapter = VehiclesAdapter { vehicleId ->
            viewModel.deleteVehicle(vehicleId)
        }
        binding.rvVehicles.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = vehiclesAdapter
        }
    }

    private fun setupViews() {
        binding.apply {
            btnLogout.setOnClickListener {
                authManager.signOut()
                // Navigate back to login or another appropriate screen
                findNavController().navigate(R.id.loginFragment)
            }

            btnUpdateProfile.setOnClickListener {
                val fullName = binding.etName.text.toString()
                val phone = binding.etPhone.text.toString()
                val names = fullName.split(" ", limit = 2)
                val firstName = names.getOrNull(0)
                val lastName = names.getOrNull(1)
                viewModel.updateProfile(firstName = firstName, lastName = lastName, phone = phone)
            }

            btnManageVehicles.setOnClickListener {
                findNavController().navigate(R.id.vehiclesFragment)
            }

            btnEnable2fa.setOnClickListener {
                val user = viewModel.userProfile.value
                if (user?.is2faEnabled == true) {
                    showDisable2faConfirmationDialog()
                } else {
                    viewModel.enable2FA()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userProfile.collectLatest { user ->
                user?.let {
                    binding.apply {
                        etName.setText(it.fullName)
                        etEmail.setText(it.email)
                        etPhone.setText(it.phone)

                        // Update 2FA button text based on user status
                        binding.btnEnable2fa.text = if (it.is2faEnabled) {
                            "DISABLE 2-FACTOR AUTHENTICATION"
                        } else {
                            "ENABLE 2-FACTOR AUTHENTICATION"
                        }
                        
                        viewModel.loadUserVehicles(it.id)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userVehicles.collectLatest { vehicles ->
                updateVehiclesList(vehicles)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateStatus.collectLatest { status ->
                handleOperationStatus(status)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.disable2FAStatus.collectLatest { status ->
                handleOperationStatus(status) {
                    viewModel.resetDisable2FAState()
                }
            }
        }
    }

    private fun handleOperationStatus(status: OperationStatus, onConsumed: (() -> Unit)? = null) {
        when (status) {
            is OperationStatus.Success -> {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                onConsumed?.invoke()
            }
            is OperationStatus.Error -> {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                onConsumed?.invoke()
            }
            OperationStatus.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
            }
            OperationStatus.Initial -> {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showDisable2faConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Disable 2FA")
            .setMessage("Are you sure you want to disable two-factor authentication? Your account will be less secure.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Disable") { _, _ ->
                viewModel.disable2FA()
            }
            .show()
    }

    private fun observe2FAStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.enable2FAState.collectLatest { state ->
                when (state) {
                    is Enable2FAState.Loading -> binding.progressBar.visibility = View.VISIBLE
                    is Enable2FAState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        show2faDialog(state.response)
                        viewModel.resetEnable2FAState()
                    }
                    is Enable2FAState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetEnable2FAState()
                    }
                    is Enable2FAState.Idle -> {
                        // Do nothing
                    }
                }
            }
        }
    }

    private fun observeAuthState() {
        authManager.currentUser.observe(viewLifecycleOwner) { user ->
            if (user == null) {
                if (findNavController().currentDestination?.id == R.id.profileFragment) {
                    findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                }
            }
        }
    }

    private fun updateVehiclesList(vehicles: List<Vehicle>) {
        vehiclesAdapter.submitList(vehicles)
    }

    private fun show2faDialog(response: Enable2FAResponse) {
        val dialogBinding = DialogShow2faSecretBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AppDialogTheme)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        var verificationJob: Job

        dialogBinding.tvSecretKey.text = response.secret
        response.qr.toQrBitmap()?.let {
            dialogBinding.ivQrCode.setImageBitmap(it)
        } ?: Toast.makeText(context, "Failed to decode QR code", Toast.LENGTH_LONG).show()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnVerify.setOnClickListener {
            val token = dialogBinding.etVerifyToken.text.toString()
            viewModel.verify2FA(token)
        }

        verificationJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.verify2FAState.collectLatest { state ->
                dialogBinding.tokenVerifyLayout.error = null
                when (state) {
                    is Verify2FAState.Loading -> {
                        dialogBinding.btnVerify.isEnabled = false
                        dialogBinding.btnVerify.text = "..."
                    }
                    is Verify2FAState.Success -> {
                        Toast.makeText(context, "2FA Enabled Successfully!", Toast.LENGTH_SHORT).show()
                        viewModel.resetVerify2FAState()
                        dialog.dismiss()
                    }
                    is Verify2FAState.Error -> {
                        dialogBinding.tokenVerifyLayout.error = state.message
                        dialogBinding.btnVerify.isEnabled = true
                        dialogBinding.btnVerify.text = getString(R.string.action_verify)
                    }
                    is Verify2FAState.Idle -> {
                        dialogBinding.btnVerify.isEnabled = true
                        dialogBinding.btnVerify.text = getString(R.string.action_verify)
                    }
                }
            }
        }

        dialog.setOnDismissListener {
            verificationJob.cancel()
            viewModel.resetVerify2FAState()
        }

        dialog.show()
    }

    private fun String.toQrBitmap(): Bitmap? {
        // The user provided the format: "qr – imaginea QR code (data URL)."
        // This usually means a Base64 encoded string with a data URI scheme.
        if (!this.startsWith("data:image/png;base64,")) return null
        val base64String = this.substringAfter("data:image/png;base64,")
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
