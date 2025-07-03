package com.example.test.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.test.R
import com.example.test.databinding.FragmentLoginBinding
import com.example.test.viewmodel.LoginState
import com.example.test.viewmodel.LoginViewModel
import com.example.test.viewmodel.LoginViewModelFactory

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.apply {
            loginButton.setOnClickListener {
                if (tokenLayout.visibility == View.VISIBLE) {
                    val token = tokenInput.text.toString()
                    viewModel.submit2faToken(token)
                } else {
                    val email = emailInput.text.toString()
                    val password = passwordInput.text.toString()
                    if (validateInput(email, password)) {
                        viewModel.login(email, password)
                    }
                }
            }

            registerButton.setOnClickListener {
                findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            resetToInitialState() // Reset views before applying new state
            when (state) {
                is LoginState.Initial -> {
                    // Handled by resetToInitialState
                }
                is LoginState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.loginButton.isEnabled = false
                    binding.registerButton.isEnabled = false
                }
                is LoginState.Success -> {
                    // Navigation is handled by MainActivity observing AuthManager
                }
                is LoginState.TwoFactorStep -> {
                    binding.emailLayout.visibility = View.GONE
                    binding.passwordLayout.visibility = View.GONE
                    binding.registerButton.visibility = View.GONE
                    binding.tokenLayout.visibility = View.VISIBLE
                    binding.loginButton.setText(R.string.action_submit_code)
                    binding.loginButton.isEnabled = true
                }
                is LoginState.Error -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resetToInitialState() {
        binding.progressBar.visibility = View.GONE
        binding.emailLayout.visibility = View.VISIBLE
        binding.passwordLayout.visibility = View.VISIBLE
        binding.tokenLayout.visibility = View.GONE
        binding.registerButton.visibility = View.VISIBLE
        binding.loginButton.isEnabled = true
        binding.registerButton.isEnabled = true
        binding.loginButton.setText(R.string.title_login)
        binding.emailInput.error = null
        binding.passwordInput.error = null
        binding.tokenInput.error = null
    }

    private fun validateInput(email: String, password: String): Boolean {
        binding.emailInput.error = null
        binding.passwordInput.error = null
        if (email.isBlank()) {
            binding.emailInput.error = getString(R.string.error_invalid_email)
            return false
        }
        if (password.isBlank()) {
            binding.passwordInput.error = getString(R.string.error_invalid_password)
            return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 