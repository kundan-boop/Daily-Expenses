package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.AuthViewModel
import com.example.ui.theme.*

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val authUiState by authViewModel.authUiState.collectAsState()

    var mobileNumber by remember { mutableStateOf("9876543210") }
    var countryCode by remember { mutableStateOf("+91") }
    var otpCode by remember { mutableStateOf("") }

    // Auto-fill OTP if simulated OTP code becomes available
    LaunchedEffect(authUiState.simulatedOtp) {
        authUiState.simulatedOtp?.let { code ->
            if (otpCode.isBlank()) {
                otpCode = code
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2E7D32),
                        Color(0xFF1B5E20)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Auto-fill real time OTP when sent
        LaunchedEffect(authUiState.simulatedOtp) {
            if (!authUiState.simulatedOtp.isNullOrBlank()) {
                otpCode = authUiState.simulatedOtp!!
            }
        }

        // Real-Time SMS Alert Banner for testing / OTP verification preview
        AnimatedVisibility(
            visible = authUiState.showSmsBanner && authUiState.simulatedOtp != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GreenLight,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Sms, contentDescription = null, tint = GreenPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("📩 Real-Time SMS Received", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                        Text(
                            "OTP Code: ${authUiState.simulatedOtp}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900
                        )
                    }

                    IconButton(
                        onClick = { authViewModel.dismissSmsBanner() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate500, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Branding Logo
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.size(90.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_icon_1784889509457),
                        contentDescription = "Daily Expense & Ledger Icon",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Daily Expense & Ledger",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Track Expenses, Manage Customers, Stay Organized.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Auth Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = if (!authUiState.isOtpSent) "Mobile Number Login" else "Verify OTP Code",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!authUiState.isOtpSent) {
                        // Mobile Input Step
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Country Code Dropdown/Prefix
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceBackground,
                                modifier = Modifier
                                    .height(56.dp)
                                    .padding(end = 8.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = countryCode,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = Slate900
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = mobileNumber,
                                onValueChange = { mobileNumber = it },
                                label = { Text("Mobile Number", color = Slate700) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = GreenPrimary) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    unfocusedBorderColor = Slate400,
                                    focusedTextColor = Slate900,
                                    unfocusedTextColor = Slate900,
                                    focusedContainerColor = SurfaceBackground,
                                    unfocusedContainerColor = SurfaceBackground
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mobile_number_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        if (authUiState.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = authUiState.errorMessage!!,
                                fontSize = 12.sp,
                                color = RedDebit,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                authViewModel.sendOtp("$countryCode$mobileNumber", activity)
                            },
                            enabled = !authUiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("send_otp_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (authUiState.isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SEND OTP CODE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    } else {
                        // OTP Code Verification Step
                        Text(
                            text = "SMS code sent to $countryCode $mobileNumber",
                            fontSize = 12.sp,
                            color = Slate500,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            label = { Text("Enter OTP Code", color = Slate700) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = GreenPrimary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
                            isError = authUiState.errorMessage != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Slate400,
                                focusedTextColor = Slate900,
                                unfocusedTextColor = Slate900,
                                focusedContainerColor = SurfaceBackground,
                                unfocusedContainerColor = SurfaceBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_code_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (authUiState.errorMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = authUiState.errorMessage!!,
                                fontSize = 12.sp,
                                color = RedDebit,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                authViewModel.verifyOtp("$countryCode$mobileNumber", otpCode, onLoginSuccess)
                            },
                            enabled = !authUiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("verify_otp_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (authUiState.isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            } else {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VERIFY & LOGIN", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Countdown Timer & Resend Option Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    if (authUiState.canResend) {
                                        authViewModel.resendOtp("$countryCode$mobileNumber", activity)
                                    }
                                },
                                enabled = authUiState.canResend && !authUiState.isLoading
                            ) {
                                Text(
                                    text = if (authUiState.canResend) "Resend OTP" else "Resend in ${authUiState.countdownSeconds}s",
                                    color = if (authUiState.canResend) GreenPrimary else Slate400,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            TextButton(
                                onClick = {
                                    authViewModel.changePhoneNumber()
                                    otpCode = ""
                                }
                            ) {
                                Text("Change Number", color = Slate700, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
