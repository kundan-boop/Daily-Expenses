package com.example.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import com.example.ui.AuthViewModel
import com.example.ui.theme.*
import com.example.utils.ImageUtils
import com.example.utils.LocalAppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsSubScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()

    var name by remember(userProfile.name) { mutableStateOf(userProfile.name) }
    var businessName by remember(userProfile.businessName) { mutableStateOf(userProfile.businessName) }
    var phone by remember(userProfile.phone) { mutableStateOf(userProfile.phone) }
    var email by remember(userProfile.email) { mutableStateOf(userProfile.email) }

    val view = LocalView.current
    val isImeVisible = WindowInsets.isImeVisible
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = true) {
        val liveIme = isImeVisible ||
            (ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
        if (liveIme) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { sourceUri ->
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    ImageUtils.saveProfileAvatar(context, sourceUri.toString())
                }
                if (savedPath != null) {
                    authViewModel.updateProfileImage(savedPath)
                    Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                } else {
                    authViewModel.updateProfileImage(sourceUri.toString())
                    Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsSubScreenScaffold(
        title = s.profileTitle,
        subtitle = s.profileSubtitle,
        onBack = onBack
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(c.greenTint)
                        .clickable {
                            authViewModel.suppressAutoLockOnce()
                            galleryLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!userProfile.profileImageUri.isNullOrBlank()) {
                        val imgUri = userProfile.profileImageUri!!
                        AsyncImage(
                            model = if (imgUri.startsWith("/")) File(imgUri) else imgUri,
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = name.take(1).uppercase().ifBlank { "U" },
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.greenPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(c.greenPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Change Profile Photo",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            authViewModel.suppressAutoLockOnce()
                            galleryLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, c.borderStrong),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }

                    if (!userProfile.profileImageUri.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                authViewModel.updateProfileImage(null)
                                Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(s.remove, color = c.redDebit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Business & Contact Details",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = c.greenPrimary) },
                    singleLine = true,
                    textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business / Store Name") },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = c.greenPrimary) },
                    singleLine = true,
                    textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_business_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = c.blueAccent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_phone_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = c.blueAccent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_email_input")
                )

                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        authViewModel.updateProfile(
                            name = name.trim().ifBlank { userProfile.name },
                            phone = phone.trim(),
                            email = email.trim(),
                            businessName = businessName.trim().ifBlank { "General Store" }
                        )
                        Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_profile_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Profile", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
