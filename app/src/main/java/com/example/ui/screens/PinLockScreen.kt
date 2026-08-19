package com.example.ui.screens

import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.AuthViewModel
import com.example.ui.theme.*
import com.example.utils.BiometricAuthManager

@Composable
fun PinLockScreen(
    authViewModel: AuthViewModel,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    val userProfile by authViewModel.userProfile.collectAsState()
    val authUiState by authViewModel.authUiState.collectAsState()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }

    fun triggerBiometricPrompt() {
        if (activity != null) {
            BiometricAuthManager.promptBiometricOrDeviceLock(
                activity = activity,
                title = "Unlock App Security",
                subtitle = "Use Face, Fingerprint, Pattern or Phone PIN",
                onSuccess = {
                    errorMessage = null
                    authViewModel.unlockAppDirectly()
                    onUnlocked()
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    // Auto trigger biometric prompt on launch if biometric lock is enabled
    LaunchedEffect(Unit) {
        if (userProfile.isBiometricLockEnabled) {
            triggerBiometricPrompt()
        }
    }

    // Auto check PIN when 4 digits entered
    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            val success = authViewModel.unlockApp(enteredPin)
            if (success) {
                errorMessage = null
                onUnlocked()
            } else {
                errorMessage = "Incorrect Passcode. Please try again."
                enteredPin = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Icon
            Surface(
                shape = CircleShape,
                color = GreenLight,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Lock",
                        tint = GreenPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = userProfile.businessName.ifBlank { "General Store" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (userProfile.isBiometricLockEnabled) "App Locked with Phone Security" else "Enter 4-Digit Passcode",
                fontSize = 14.sp,
                color = Slate500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Biometric / Device Lock Unlock Button
            if (userProfile.isBiometricLockEnabled) {
                Button(
                    onClick = { triggerBiometricPrompt() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Lock",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Unlock with Face / Fingerprint / PIN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 4 PIN Input Boxes (shown if PIN is configured or as fallback)
            if (userProfile.isPinEnabled && userProfile.pinCode.isNotBlank()) {
                Text(
                    text = "Or enter 4-digit passcode:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val digit = if (i < enteredPin.length) enteredPin[i].toString() else ""
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CardBackground,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 2.dp,
                                color = if (digit.isNotEmpty()) GreenPrimary else Slate400
                            ),
                            modifier = Modifier.size(width = 52.dp, height = 60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Keypad Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keypad = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("CLEAR", "0", "BACK")
                    )

                    for (row in keypad) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            for (key in row) {
                                Surface(
                                    shape = CircleShape,
                                    color = CardBackground,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clickable {
                                            when (key) {
                                                "CLEAR" -> {
                                                    enteredPin = ""
                                                    errorMessage = null
                                                }
                                                "BACK" -> {
                                                    if (enteredPin.isNotEmpty()) {
                                                        enteredPin = enteredPin.dropLast(1)
                                                        errorMessage = null
                                                    }
                                                }
                                                else -> {
                                                    if (enteredPin.length < 4) {
                                                        errorMessage = null
                                                        enteredPin += key
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when (key) {
                                            "BACK" -> Icon(
                                                Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace",
                                                tint = Slate700,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            "CLEAR" -> Text(
                                                text = "C",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = RedDebit
                                            )
                                            else -> Text(
                                                text = key,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    color = RedDebit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Forgot Passcode Option
            if (userProfile.isPinEnabled && userProfile.pinCode.isNotBlank()) {
                TextButton(
                    onClick = {
                        showForgotDialog = true
                        authViewModel.sendOtp(userProfile.phone, null)
                    }
                ) {
                    Text(
                        text = "Forgot Passcode?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary
                    )
                }
            }
        }
    }

    // Forgot Passcode Dialog via SMS OTP Verification
    if (showForgotDialog) {
        var otpInput by remember { mutableStateOf("") }
        var newPinInput by remember { mutableStateOf("") }
        var isOtpVerified by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        // Auto pre-fill OTP from SMS banner for real-time convenience
        LaunchedEffect(authUiState.simulatedOtp) {
            if (!authUiState.simulatedOtp.isNullOrBlank()) {
                otpInput = authUiState.simulatedOtp!!
            }
        }

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = GreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!isOtpVerified) "Reset Security Passcode" else "Set New 4-Digit PIN",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    if (!isOtpVerified) {
                        Text(
                            text = "We sent an OTP verification code to ${userProfile.phone} to authorize passcode reset.",
                            fontSize = 13.sp,
                            color = Slate500
                        )

                        if (!authUiState.simulatedOtp.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GreenLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "📩 Real-Time OTP: ${authUiState.simulatedOtp}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GreenPrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { if (it.length <= 6) otpInput = it },
                            label = { Text("6-Digit OTP Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Enter a new 4-digit PIN for your app security.",
                            fontSize = 13.sp,
                            color = Slate500
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { if (it.length <= 4) newPinInput = it.filter { c -> c.isDigit() } },
                            label = { Text("New 4-Digit PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (dialogError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dialogError!!,
                            color = RedDebit,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isOtpVerified) {
                            if (otpInput.length < 4) {
                                dialogError = "Please enter valid OTP code"
                            } else {
                                authViewModel.verifyOtp(userProfile.phone, otpInput) {
                                    isOtpVerified = true
                                    dialogError = null
                                }
                            }
                        } else {
                            if (newPinInput.length != 4) {
                                dialogError = "PIN must be exactly 4 digits"
                            } else {
                                authViewModel.resetPinAndUnlock(newPinInput)
                                showForgotDialog = false
                                onUnlocked()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text(if (!isOtpVerified) "Verify OTP" else "Save & Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Cancel", color = Slate500)
                }
            }
        )
    }
}
