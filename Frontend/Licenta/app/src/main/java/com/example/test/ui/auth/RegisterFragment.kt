package com.example.test.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.test.R
import com.example.test.databinding.FragmentRegisterBinding
import com.example.test.viewmodel.LoginState
import com.example.test.viewmodel.LoginViewModel
import com.example.test.viewmodel.LoginViewModelFactory

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()
        setupTextWatchers()
    }

    private fun setupViews() {
        binding.apply {
            btnRegister.setOnClickListener {
                val firstName = etFirstName.text.toString()
                val lastName = etLastName.text.toString()
                val email = etEmail.text.toString()
                val phone = etPhone.text.toString()
                val password = etPassword.text.toString()
                val confirmPassword = etConfirmPassword.text.toString()

                if (validateInput(firstName, lastName, email, phone, password, confirmPassword)) {
                    viewModel.register(firstName, lastName, email, phone, password)
                }
            }

            btnBackToLogin.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun setupTextWatchers() {
        binding.etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePhone(s.toString())
            }
        })

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePassword(s.toString())
            }
        })
    }

    private fun validatePhone(phone: String): Boolean {
        return if (phone.length < 10) {
            binding.tilPhone.error = "Phone number must be at least 10 digits"
            false
        } else {
            binding.tilPhone.error = null
            binding.tilPhone.helperText = "Phone number is valid"
            true
        }
    }

    private fun validatePassword(password: String) {
        val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$".toRegex()
        when {
            password.length < 8 -> {
                binding.tilPassword.error = "Password must be at least 8 characters"
                binding.tilPassword.helperText = null
            }
            !password.matches(passwordPattern) -> {
                binding.tilPassword.error = "Must contain uppercase, lowercase, number, and special character."
                binding.tilPassword.helperText = null
            }
            else -> {
                binding.tilPassword.error = null
                binding.tilPassword.helperText = "Password is valid"
            }
        }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoginState.Initial -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                }
                is LoginState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnRegister.isEnabled = false
                }
                is LoginState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    // Navigarea către MapFragment este eliminată!
                    // AuthManager deja setează isAuthenticated = true, MainActivity va naviga automat
                }
                is LoginState.TwoFactorStep -> {
                    // This should not happen during registration
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(context, "Unexpected error during registration.", Toast.LENGTH_SHORT).show()
                }
                is LoginState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun validateInput(firstName: String, lastName: String, email: String, phone: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (firstName.isBlank()) {
            binding.tilFirstName.error = getString(R.string.error_invalid_name)
            isValid = false
        } else {
            binding.tilFirstName.error = null
        }

        if (lastName.isBlank()) {
            binding.tilLastName.error = getString(R.string.error_invalid_name)
            isValid = false
        } else {
            binding.tilLastName.error = null
        }

        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        if (!validatePhone(phone)) {
            isValid = false
        }

        // Final check on password before submitting
        validatePassword(password)
        if (binding.tilPassword.error != null) {
            isValid = false
        }

        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = getString(R.string.msg_passwords_dont_match)
            isValid = false
        } else {
            if (password.isNotEmpty()) { // Only clear error if passwords match and are not empty
                binding.tilConfirmPassword.error = null
            }
        }

        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 