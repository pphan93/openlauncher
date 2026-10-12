package com.openlauncher.app.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.util.ValetPinSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ValetModeScreen(
    pinSalt: String,
    pinHash: String,
    onUnlocked: () -> Unit
) {
    var showKeypad by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        if (showKeypad) {
            showKeypad = false
            pin = ""
            error = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = !showKeypad) { showKeypad = true }
    ) {
        if (showKeypad) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(330.dp)
                    .background(Color(0xFF101010), RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("VALET MODE", color = Color.White, fontSize = 15.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (pin.isEmpty()) "Enter PIN" else "•".repeat(pin.length),
                    color = if (error.isNotEmpty()) Color(0xFFFF7777) else Color.White,
                    fontSize = 18.sp,
                    letterSpacing = 5.sp
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Color(0xFFFF7777), fontSize = 10.sp)
                }
                Spacer(Modifier.height(12.dp))

                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CANCEL", "0", "DEL")
                ).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { key ->
                            TextButton(
                                onClick = {
                                    error = ""
                                    when (key) {
                                        "CANCEL" -> {
                                            showKeypad = false
                                            pin = ""
                                        }
                                        "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        else -> if (pin.length < 8) pin += key
                                    }
                                },
                                enabled = !checking,
                                shape = CircleShape,
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text(key, fontSize = if (key.length == 1) 17.sp else 8.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        checking = true
                        scope.launch {
                            val valid = withContext(Dispatchers.Default) {
                                ValetPinSecurity.verify(pin, pinSalt, pinHash)
                            }
                            checking = false
                            if (valid) {
                                pin = ""
                                onUnlocked()
                            } else {
                                pin = ""
                                error = "Incorrect PIN"
                            }
                        }
                    },
                    enabled = pin.length >= 4 && !checking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text("UNLOCK")
                    }
                }
            }
        }
    }
}
