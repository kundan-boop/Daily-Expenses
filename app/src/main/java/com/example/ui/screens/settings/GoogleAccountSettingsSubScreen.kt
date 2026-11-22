package com.example.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.auth.GoogleAuthHelper
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings
import kotlinx.coroutines.launch

@Composable
fun GoogleAccountSettingsSubScreen(
    authViewModel: AuthViewModel,
    dailyExpenseViewModel: DailyExpenseViewModel?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    val googleAccount by (dailyExpenseViewModel?.googleAccountInfo?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })
    val isFirebaseBusy by (dailyExpenseViewModel?.isFirebaseBusy?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })
    val connectionMessage by (dailyExpenseViewModel?.firebaseConnectionMessage?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

    var isSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dailyExpenseViewModel?.refreshFirebaseState()
    }

    val launchGoogleSignIn: () -> Unit = {
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, "Unable to find active window for Google Sign-In.", Toast.LENGTH_SHORT).show()
        } else if (!isSigningIn) {
            isSigningIn = true
            authViewModel.suppressAutoLockOnce()
            coroutineScope.launch {
                val res = GoogleAuthHelper.signInWithGoogle(activity)
                isSigningIn = false
                res.onSuccess { acct ->
                    dailyExpenseViewModel?.onGoogleAccountSignedIn()
                    Toast.makeText(context, "Signed in as ${acct.email}", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        err.localizedMessage ?: "Google Sign-In failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    SettingsSubScreenScaffold(
        title = s.googleAccountTitle,
        subtitle = "Cloud authentication & connectivity check",
        onBack = onBack
    ) {
        // Google Account Identity Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.googleAccountTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (googleAccount != null) c.greenTint else c.cardElevated
                    ) {
                        Text(
                            text = if (googleAccount != null) "CONNECTED" else "SIGNED OUT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (googleAccount != null) c.greenPrimary else c.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (googleAccount != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.cardElevated, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(c.greenTint),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!googleAccount?.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = googleAccount?.photoUrl,
                                    contentDescription = "Google Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = (googleAccount?.displayName ?: "G").take(1).uppercase(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = c.greenPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = googleAccount?.displayName ?: "Google User",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = googleAccount?.email ?: "",
                                fontSize = 12.sp,
                                color = c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = launchGoogleSignIn,
                            enabled = !isSigningIn,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.borderStrong),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("switch_google_account_button")
                        ) {
                            Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                dailyExpenseViewModel?.signOutGoogleAccount {
                                    Toast.makeText(
                                        context,
                                        "Signed out of Google account. Your local ledger stays available.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            border = BorderStroke(1.dp, c.redDebit.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("sign_out_google_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = c.redDebit, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.redDebit, maxLines = 1)
                        }
                    }
                } else {
                    Text(
                        text = "Sign in with Google to sync encrypted backups to Firebase Firestore. Your local ledger works offline even without signing in.",
                        fontSize = 12.5.sp,
                        color = c.textSecondary
                    )

                    Button(
                        onClick = launchGoogleSignIn,
                        enabled = !isSigningIn,
                        colors = ButtonDefaults.buttonColors(containerColor = c.blueAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Signing in...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In with Google", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Cloud Diagnostics Card Only (Drive & Firebase kept strictly on their own separated menus)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Cloud Diagnostics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)

                val clientConfigOk = remember { GoogleAuthHelper.isClientConfigAvailable(context) }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (clientConfigOk) c.greenTint else c.redTint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_client_config_status")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (clientConfigOk) Icons.Default.Verified else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (clientConfigOk) c.greenPrimary else c.redDebit,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (clientConfigOk) {
                                "Client configuration ready (OAuth Web Client ID bundled). Ensure release/debug SHA-1 & SHA-256 match Firebase Console."
                            } else {
                                "Client configuration unavailable: missing default_web_client_id. Add SHA-1/SHA-256 in Firebase Console & refresh google-services.json."
                            },
                            fontSize = 11.5.sp,
                            color = if (clientConfigOk) c.greenPrimary else c.redDebit,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!connectionMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.cardElevated,
                        border = BorderStroke(1.dp, c.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = connectionMessage ?: "",
                            fontSize = 12.sp,
                            color = c.textSecondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        dailyExpenseViewModel?.testFirebaseConnection { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isFirebaseBusy,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.borderStrong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_firebase_connection_button")
                ) {
                    if (isFirebaseBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Connection...", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Firebase Connection", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
