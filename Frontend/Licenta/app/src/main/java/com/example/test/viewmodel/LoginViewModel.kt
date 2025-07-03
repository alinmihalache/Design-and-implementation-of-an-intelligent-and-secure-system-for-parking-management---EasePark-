package com.example.test.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.auth.AuthManager
import com.example.test.auth.LoginResult
import kotlinx.coroutines.launch

sealed class LoginState {
    object Initial : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    object TwoFactorStep : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {
    private val authManager = AuthManager.getInstance()
    private val _loginState = MutableLiveData<LoginState>(LoginState.Initial)
    val loginState: LiveData<LoginState> = _loginState

    private var tempEmail: String? = null
    private var tempPassword: String? = null

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Toate câmpurile sunt obligatorii")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            when (val result = authManager.login(email, password)) {
                is LoginResult.Success -> {
                    _loginState.postValue(LoginState.Success)
                }
                is LoginResult.TwoFactorRequired -> {
                    tempEmail = email
                    tempPassword = password
                    _loginState.postValue(LoginState.TwoFactorStep)
                }
                is LoginResult.Error -> {
                    _loginState.postValue(LoginState.Error(result.message))
                }
            }
        }
    }

    fun submit2faToken(token: String) {
        val email = tempEmail
        val password = tempPassword

        if (token.isBlank() || email == null || password == null) {
            _loginState.value = LoginState.Error("Codul 2FA este invalid sau sesiunea a expirat.")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            when (val result = authManager.login(email, password, token)) {
                is LoginResult.Success -> {
                    _loginState.postValue(LoginState.Success)
                }
                is LoginResult.TwoFactorRequired -> {
                    // Should not happen in the second step. Treat as an error.
                    _loginState.postValue(LoginState.Error("A apărut o eroare neașteptată."))
                }
                is LoginResult.Error -> {
                    _loginState.postValue(LoginState.Error(result.message))
                }
            }
        }
    }

    fun register(firstName: String, lastName: String, email: String, phoneNumber: String, password: String) {
        if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || phoneNumber.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Toate câmpurile sunt obligatorii")
            return
        }

        if (!isValidEmail(email)) {
            _loginState.value = LoginState.Error("Adresa de email nu este validă")
            return
        }

        if (!isValidPhoneNumber(phoneNumber)) {
            _loginState.value = LoginState.Error("Numărul de telefon nu este valid")
            return
        }

        if (password.length < 6) {
            _loginState.value = LoginState.Error("Parola trebuie să aibă cel puțin 6 caractere")
            return
        }

        // Username will be equal to firstName
        val username = firstName

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val result = authManager.register(username, firstName, lastName, email, phoneNumber, password)
            if (result.isSuccess) {
                _loginState.value = LoginState.Success
            } else {
                _loginState.value = LoginState.Error(result.exceptionOrNull()?.message ?: "Înregistrare eșuată")
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.matches(Regex("^[0-9]{10}$"))
    }
} 