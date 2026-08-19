package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SmsResult
import com.example.data.SmsVerificationService
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserProfile(
    val name: String = "Kundan Sinh",
    val phone: String = "1234567890",
    val email: String = "Store@gmail.com",
    val businessName: String = "General Store",
    val isLoggedIn: Boolean = true,
    val loginMethod: String = "Mobile OTP",
    val isPinEnabled: Boolean = false,
    val isBiometricLockEnabled: Boolean = false,
    val pinCode: String = "",
    val profileImageUri: String? = null,
    val isLocked: Boolean = false
)

data class AuthUiState(
    val isOtpSent: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val countdownSeconds: Int = 60,
    val canResend: Boolean = false,
    val simulatedOtp: String? = null,
    val showSmsBanner: Boolean = false,
    val loginSuccess: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val smsService = SmsVerificationService()

    private val pinEnabled = prefs.getBoolean("is_pin_enabled", false)
    private val biometricEnabled = prefs.getBoolean("is_biometric_enabled", false)
    private val storedPin = prefs.getString("pin_code", "") ?: ""

    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = prefs.getString("user_name", "Kundan Sinh") ?: "Kundan Sinh",
            phone = prefs.getString("user_phone", "1234567890") ?: "1234567890",
            email = prefs.getString("user_email", "Store@gmail.com") ?: "Store@gmail.com",
            businessName = prefs.getString("business_name", "General Store").let { if (it == "Only General store" || it == "Rajesh General Store") "General Store" else (it ?: "General Store") },
            isLoggedIn = prefs.getBoolean("is_logged_in", true),
            loginMethod = prefs.getString("login_method", "Mobile OTP") ?: "Mobile OTP",
            isPinEnabled = pinEnabled,
            isBiometricLockEnabled = biometricEnabled,
            pinCode = storedPin,
            profileImageUri = prefs.getString("profile_image_uri", null),
            isLocked = biometricEnabled || (pinEnabled && storedPin.isNotBlank())
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private var countdownJob: Job? = null

    fun sendOtp(phoneNumber: String, activity: Activity?) {
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        if (cleanNumber.length < 10) {
            _authUiState.value = _authUiState.value.copy(errorMessage = "Please enter a valid 10-digit mobile number")
            return
        }

        val fullPhoneNumber = if (phoneNumber.startsWith("+")) phoneNumber else "+91$cleanNumber"

        _authUiState.value = _authUiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            when (val result = smsService.sendOtp(fullPhoneNumber)) {
                is SmsResult.Success -> {
                    _authUiState.value = _authUiState.value.copy(
                        isLoading = false,
                        isOtpSent = true,
                        simulatedOtp = result.otpCode,
                        showSmsBanner = true,
                        errorMessage = null
                    )
                    startCountdownTimer()
                }
                is SmsResult.Error -> {
                    _authUiState.value = _authUiState.value.copy(
                        isLoading = false,
                        isOtpSent = true,
                        simulatedOtp = result.fallbackOtp,
                        showSmsBanner = true,
                        errorMessage = null
                    )
                    startCountdownTimer()
                }
            }
        }
    }

    fun resendOtp(phoneNumber: String, activity: Activity?) {
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        val fullPhoneNumber = if (phoneNumber.startsWith("+")) phoneNumber else "+91$cleanNumber"

        _authUiState.value = _authUiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            when (val result = smsService.sendOtp(fullPhoneNumber)) {
                is SmsResult.Success -> {
                    _authUiState.value = _authUiState.value.copy(
                        isLoading = false,
                        simulatedOtp = result.otpCode,
                        showSmsBanner = true,
                        errorMessage = null
                    )
                    startCountdownTimer()
                }
                is SmsResult.Error -> {
                    _authUiState.value = _authUiState.value.copy(
                        isLoading = false,
                        simulatedOtp = result.fallbackOtp,
                        showSmsBanner = true,
                        errorMessage = null
                    )
                    startCountdownTimer()
                }
            }
        }
    }

    fun verifyOtp(phoneNumber: String, code: String, onSuccess: () -> Unit) {
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        val fullPhoneNumber = if (phoneNumber.startsWith("+")) phoneNumber else "+91$cleanNumber"

        _authUiState.value = _authUiState.value.copy(isLoading = true, errorMessage = null)

        if (code.isBlank()) {
            _authUiState.value = _authUiState.value.copy(
                isLoading = false,
                errorMessage = "Please enter the OTP code"
            )
            return
        }

        val isValid = smsService.verifyOtp(fullPhoneNumber, code)
        if (isValid) {
            loginWithMobile(fullPhoneNumber, code)
            _authUiState.value = _authUiState.value.copy(isLoading = false, loginSuccess = true)
            onSuccess()
        } else {
            val activeOtp = _authUiState.value.simulatedOtp
            _authUiState.value = _authUiState.value.copy(
                isLoading = false,
                errorMessage = "Invalid OTP code. Enter ${activeOtp ?: "123456"}"
            )
        }
    }


    private fun startCountdownTimer() {
        countdownJob?.cancel()
        _authUiState.value = _authUiState.value.copy(
            countdownSeconds = 60,
            canResend = false
        )
        countdownJob = viewModelScope.launch {
            for (sec in 59 downTo 0) {
                delay(1000)
                _authUiState.value = _authUiState.value.copy(
                    countdownSeconds = sec,
                    canResend = sec == 0
                )
            }
        }
    }

    fun dismissSmsBanner() {
        _authUiState.value = _authUiState.value.copy(showSmsBanner = false)
    }

    fun changePhoneNumber() {
        countdownJob?.cancel()
        _authUiState.value = AuthUiState()
    }

    fun loginWithMobile(phone: String, otp: String) {
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_phone", phone)
            .putString("login_method", "Mobile OTP (Firebase)")
            .apply()

        _userProfile.value = _userProfile.value.copy(
            isLoggedIn = true,
            phone = phone,
            loginMethod = "Mobile OTP (Firebase)"
        )
    }

    fun loginWithGoogle(email: String, name: String) {
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_email", email)
            .putString("user_name", name)
            .putString("login_method", "Google")
            .apply()

        _userProfile.value = _userProfile.value.copy(
            isLoggedIn = true,
            email = email,
            name = name,
            loginMethod = "Google"
        )
    }

    fun logout() {
        try {
            if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                FirebaseAuth.getInstance().signOut()
            }
        } catch (_: Exception) {}
        prefs.edit().putBoolean("is_logged_in", false).apply()
        _userProfile.value = _userProfile.value.copy(isLoggedIn = false)
        _authUiState.value = AuthUiState()
    }

    fun updateProfile(name: String, phone: String, email: String, businessName: String) {
        prefs.edit()
            .putString("user_name", name)
            .putString("user_phone", phone)
            .putString("user_email", email)
            .putString("business_name", businessName)
            .apply()

        _userProfile.value = _userProfile.value.copy(
            name = name,
            phone = phone,
            email = email,
            businessName = businessName
        )
    }

    fun setPin(pin: String, enabled: Boolean) {
        prefs.edit()
            .putString("pin_code", pin)
            .putBoolean("is_pin_enabled", enabled)
            .apply()

        _userProfile.value = _userProfile.value.copy(
            pinCode = pin,
            isPinEnabled = enabled,
            isLocked = false
        )
    }

    fun updateProfileImage(uriString: String?) {
        prefs.edit().putString("profile_image_uri", uriString).apply()
        _userProfile.value = _userProfile.value.copy(
            profileImageUri = uriString
        )
    }

    fun unlockApp(pin: String): Boolean {
        if (_userProfile.value.pinCode == pin) {
            _userProfile.value = _userProfile.value.copy(isLocked = false)
            return true
        }
        return false
    }

    fun unlockAppDirectly() {
        _userProfile.value = _userProfile.value.copy(isLocked = false)
    }

    fun setBiometricLock(enabled: Boolean) {
        prefs.edit().putBoolean("is_biometric_enabled", enabled).apply()
        _userProfile.value = _userProfile.value.copy(
            isBiometricLockEnabled = enabled,
            isLocked = if (!enabled) false else _userProfile.value.isLocked
        )
    }

    fun resetPinAndUnlock(newPin: String) {
        prefs.edit()
            .putString("pin_code", newPin)
            .putBoolean("is_pin_enabled", true)
            .apply()

        _userProfile.value = _userProfile.value.copy(
            pinCode = newPin,
            isPinEnabled = true,
            isLocked = false
        )
    }

    private var suppressNextAutoLock = false

    fun suppressAutoLockOnce() {
        suppressNextAutoLock = true
    }

    fun lockApp() {
        if (suppressNextAutoLock) {
            suppressNextAutoLock = false
            return
        }
        if (_userProfile.value.isBiometricLockEnabled || (_userProfile.value.isPinEnabled && _userProfile.value.pinCode.isNotBlank())) {
            _userProfile.value = _userProfile.value.copy(isLocked = true)
        }
    }
}
