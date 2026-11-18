package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.data.CustomerEntity
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCustomerDialog(
    customerToEdit: CustomerEntity? = null,
    viewModel: DailyExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    var dialogViewRef by remember { mutableStateOf<android.view.View?>(null) }
    var lastImeCloseTime by remember { mutableLongStateOf(0L) }

    val draftKey = remember(customerToEdit) {
        customerToEdit?.let { "${it.id}_${it.lastUpdatedTimestamp}" } ?: "new"
    }
    var name by remember(draftKey) { mutableStateOf(customerToEdit?.name ?: "") }
    var mobileNumber by remember(draftKey) { mutableStateOf(customerToEdit?.mobileNumber ?: "") }
    var address by remember(draftKey) { mutableStateOf(customerToEdit?.address ?: "") }
    var email by remember(draftKey) { mutableStateOf(customerToEdit?.email ?: "") }
    var notes by remember(draftKey) { mutableStateOf(customerToEdit?.notes ?: "") }
    var collectToday by remember(draftKey) { mutableStateOf(customerToEdit?.collectToday ?: false) }
    var collectDueDate by remember(draftKey) { mutableStateOf<Long?>(customerToEdit?.collectDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            c.getString(nameIndex)?.let { pickedName ->
                                name = pickedName
                            }
                        }
                        val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val hasPhoneIndex = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        if (idIndex >= 0 && hasPhoneIndex >= 0 && c.getInt(hasPhoneIndex) > 0) {
                            val id = c.getString(idIndex)
                            val phoneCursor = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(id),
                                null
                            )
                            phoneCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val phoneIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (phoneIndex >= 0) {
                                        val rawPhone = pc.getString(phoneIndex) ?: ""
                                        mobileNumber = rawPhone.replace("\\s+".toRegex(), "")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load contact info", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, "Contacts permission required to import", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = {
            val liveIme = dialogViewRef?.let {
                ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
            } == true
            if (!liveIme && System.currentTimeMillis() - lastImeCloseTime > 300L) {
                onDismiss()
            }
        },
        containerColor = c.card,
        shape = RoundedCornerShape(20.dp),
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false
        ),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .imePadding()
            .padding(horizontal = 15.dp),
        title = {
            Text(
                text = if (customerToEdit == null) s.addNewCustomer else s.editCustomer,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = c.textPrimary
            )
        },
        text = {
            val dialogView = LocalView.current
            val sheetImeVisible = WindowInsets.isImeVisible
            val dialogKeyboardController = LocalSoftwareKeyboardController.current
            val dialogFocusManager = LocalFocusManager.current
            var prevIme by remember { mutableStateOf(false) }

            LaunchedEffect(dialogView) { dialogViewRef = dialogView }
            LaunchedEffect(sheetImeVisible) {
                if (prevIme && !sheetImeVisible) {
                    lastImeCloseTime = System.currentTimeMillis()
                }
                prevIme = sheetImeVisible
            }

            BackHandler(enabled = true) {
                val liveIme = sheetImeVisible ||
                    (ViewCompat.getRootWindowInsets(dialogView)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                if (liveIme) {
                    lastImeCloseTime = System.currentTimeMillis()
                    dialogKeyboardController?.hide()
                    dialogFocusManager.clearFocus()
                } else if (System.currentTimeMillis() - lastImeCloseTime > 300L) {
                    onDismiss()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quick Contact Import Button
                if (customerToEdit == null) {
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                contactPickerLauncher.launch(null)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.blueAccent.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.blueAccent)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.blueAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import from Phone Contacts", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Customer Name *", color = c.textSecondary) },
                    isError = nameError,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = c.greenPrimary) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.greenPrimary,
                        unfocusedBorderColor = c.borderStrong,
                        errorBorderColor = c.redDebit,
                        focusedLabelColor = c.greenPrimary,
                        unfocusedLabelColor = c.textSecondary,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.surfaceSubtle,
                        unfocusedContainerColor = c.surfaceSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = { mobileNumber = it },
                    label = { Text("Mobile Number (Optional)", color = c.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = c.blueAccent) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.blueAccent,
                        unfocusedBorderColor = c.borderStrong,
                        focusedLabelColor = c.blueAccent,
                        unfocusedLabelColor = c.textSecondary,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.surfaceSubtle,
                        unfocusedContainerColor = c.surfaceSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_mobile_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Location (Optional)", color = c.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = c.textMuted) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.greenPrimary,
                        unfocusedBorderColor = c.borderStrong,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.surfaceSubtle,
                        unfocusedContainerColor = c.surfaceSubtle
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Business Details (Optional)", color = c.textSecondary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, tint = c.textMuted) },
                    textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.greenPrimary,
                        unfocusedBorderColor = c.borderStrong,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.surfaceSubtle,
                        unfocusedContainerColor = c.surfaceSubtle
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = collectToday,
                            onCheckedChange = { collectToday = it },
                            colors = CheckboxDefaults.colors(checkedColor = c.greenPrimary, uncheckedColor = c.textMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = s.collectTodayBadge,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.greenPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (collectDueDate != null) c.greenPrimary else c.borderStrong)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.greenPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            val dateText = if (collectDueDate != null) {
                                val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).apply {
                                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                                }
                                "Due: " + sdf.format(java.util.Date(collectDueDate!!))
                            } else {
                                "Set Collect Due Date (Optional)"
                            }
                            Text(dateText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (collectDueDate != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { collectDueDate = null },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear Due Date",
                                    tint = c.redDebit,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        isSaving = true
                        if (customerToEdit == null) {
                            viewModel.clearCustomerSearchQuery()
                            viewModel.addCustomer(
                                name = name,
                                mobileNumber = mobileNumber,
                                address = address,
                                email = email,
                                notes = notes,
                                collectToday = collectToday,
                                collectDueDate = collectDueDate,
                                onSuccess = {
                                    isSaving = false
                                    onDismiss()
                                },
                                onError = { err ->
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        "Failed to save customer: ${err.localizedMessage ?: "Unknown error"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        } else {
                            viewModel.updateCustomer(
                                customer = customerToEdit.copy(
                                    name = name.trim(),
                                    mobileNumber = mobileNumber.trim(),
                                    address = address.trim(),
                                    email = email.trim(),
                                    notes = notes.trim(),
                                    collectToday = collectToday,
                                    collectDueDate = collectDueDate
                                ),
                                onSuccess = {
                                    isSaving = false
                                    onDismiss()
                                },
                                onError = { err ->
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        "Failed to update customer: ${err.localizedMessage ?: "Unknown error"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_customer_button")
            ) {
                Text(if (customerToEdit == null) s.addCustomer else s.save, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text(s.cancel, color = c.textMuted)
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = collectDueDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    collectDueDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("OK", fontWeight = FontWeight.Bold, color = c.greenPrimary)
                }
            },
            dismissButton = {
                Row {
                    if (collectDueDate != null) {
                        TextButton(onClick = {
                            collectDueDate = null
                            showDatePicker = false
                        }) {
                            Text("Clear Date", color = c.redDebit, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(s.cancel, color = c.textMuted)
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

