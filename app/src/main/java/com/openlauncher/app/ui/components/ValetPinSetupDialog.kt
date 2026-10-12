package com.openlauncher.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openlauncher.app.util.ValetPinSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ValetPinSetupDialog(
    existingSalt: String,
    existingHash: String,
    onSave: (ValetPinSecurity.PinRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val hasExistingPin = existingSalt.isNotBlank() && existingHash.isNotBlank()
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun filterPin(value: String): String = value.filter(Char::isDigit).take(8)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.width(420.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(if (hasExistingPin) "Change Valet PIN" else "Set Valet PIN", fontSize = 18.sp)
                Text("Use 4–8 digits.", fontSize = 11.sp, color = LocalContentColor.current.copy(alpha = 0.62f))
                Spacer(Modifier.height(14.dp))

                if (hasExistingPin) {
                    PinField("Current PIN", currentPin, { currentPin = filterPin(it); error = "" })
                    Spacer(Modifier.height(8.dp))
                }
                PinField("New PIN", newPin, { newPin = filterPin(it); error = "" })
                Spacer(Modifier.height(8.dp))
                PinField("Confirm PIN", confirmPin, { confirmPin = filterPin(it); error = "" })

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    Button(
                        onClick = {
                            when {
                                newPin.length !in 4..8 -> error = "PIN must contain 4–8 digits"
                                newPin != confirmPin -> error = "PINs do not match"
                                else -> {
                                    saving = true
                                    scope.launch {
                                        val currentValid = if (hasExistingPin) {
                                            withContext(Dispatchers.Default) {
                                                ValetPinSecurity.verify(currentPin, existingSalt, existingHash)
                                            }
                                        } else true
                                        if (!currentValid) {
                                            saving = false
                                            error = "Current PIN is incorrect"
                                        } else {
                                            val record = withContext(Dispatchers.Default) {
                                                ValetPinSecurity.create(newPin)
                                            }
                                            saving = false
                                            onSave(record)
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !saving
                    ) {
                        if (saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        else Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}
