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
import androidx.core.content.ContextCompat
import com.example.data.CustomerEntity
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerDialog(
    customerToEdit: CustomerEntity? = null,
    viewModel: DailyExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(customerToEdit?.name ?: "") }
    var mobileNumber by remember { mutableStateOf(customerToEdit?.mobileNumber ?: "") }
    var address by remember { mutableStateOf(customerToEdit?.address ?: "") }
    var email by remember { mutableStateOf(customerToEdit?.email ?: "") }
    var notes by remember { mutableStateOf(customerToEdit?.notes ?: "") }
    var collectToday by remember { mutableStateOf(customerToEdit?.collectToday ?: false) }
    var collectDueDate by remember { mutableStateOf<Long?>(customerToEdit?.collectDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

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
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .padding(horizontal = 15.dp),
        title = {
            Text(
                text = if (customerToEdit == null) "Add New Customer" else "Edit Customer Info",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Slate900
            )
        },
        text = {
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OceanBlue)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp), tint = OceanBlue)
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
                    label = { Text("Customer Name *", color = Slate700) },
                    isError = nameError,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GreenPrimary) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = Slate400,
                        errorBorderColor = RedDebit,
                        focusedLabelColor = GreenPrimary,
                        unfocusedLabelColor = Slate700,
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = { mobileNumber = it },
                    label = { Text("Mobile Number (Optional)", color = Slate700) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = OceanBlue) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OceanBlue,
                        unfocusedBorderColor = Slate400,
                        focusedLabelColor = OceanBlue,
                        unfocusedLabelColor = Slate700,
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_mobile_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Location (Optional)", color = Slate700) },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = Slate500) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = Slate400,
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Business Details (Optional)", color = Slate700) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, tint = Slate500) },
                    textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = Slate400,
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900
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
                            colors = CheckboxDefaults.colors(checkedColor = GreenPrimary, uncheckedColor = Slate500)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mark 'Collect Today' Badge",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (collectDueDate != null) GreenPrimary else Slate400)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
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
                                    tint = RedDebit,
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
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        if (customerToEdit == null) {
                            viewModel.setSearchQuery("")
                            viewModel.addCustomer(
                                name = name,
                                mobileNumber = mobileNumber,
                                address = address,
                                email = email,
                                notes = notes,
                                collectToday = collectToday,
                                collectDueDate = collectDueDate
                            )
                        } else {
                            viewModel.updateCustomer(
                                customerToEdit.copy(
                                    name = name.trim(),
                                    mobileNumber = mobileNumber.trim(),
                                    address = address.trim(),
                                    email = email.trim(),
                                    notes = notes.trim(),
                                    collectToday = collectToday,
                                    collectDueDate = collectDueDate
                                )
                            )
                        }
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_customer_button")
            ) {
                Text(if (customerToEdit == null) "Add Customer" else "Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate500)
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
                    Text("OK", fontWeight = FontWeight.Bold, color = GreenPrimary)
                }
            },
            dismissButton = {
                Row {
                    if (collectDueDate != null) {
                        TextButton(onClick = {
                            collectDueDate = null
                            showDatePicker = false
                        }) {
                            Text("Clear Date", color = RedDebit, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel", color = Slate500)
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

