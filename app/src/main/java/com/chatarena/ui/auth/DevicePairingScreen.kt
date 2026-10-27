package com.chatarena.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class PairingMode {
    START, WAITING, ENTER_PIN, APPROVE_REQUEST, SHOW_PIN
}

@Composable
fun DevicePairingScreen(
    isOldDeviceMode: Boolean = false,
    sessionId: String = "",
    onDone: (Boolean) -> Unit
) {
    var mode by remember { mutableStateOf(if (isOldDeviceMode) PairingMode.APPROVE_REQUEST else PairingMode.START) }
    var pinInput by remember { mutableStateOf("") }
    var generatedPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableIntStateOf(60) }

    // Timer Logic for SHOW_PIN (Old Device)
    LaunchedEffect(mode, timeLeft) {
        if (mode == PairingMode.SHOW_PIN && timeLeft > 0) {
            delay(1000)
            timeLeft--
            if (timeLeft == 0) onDone(false)
        }
    }

    // Polling Logic for WAITING (New Device)
    LaunchedEffect(mode) {
        if (mode == PairingMode.WAITING) {
            while (true) {
                delay(3000)
                // TODO: API Call - checkPairingStatus(sessionId)
                // Mocking: 5 seconds baad approve ho gaya maan lo
                // mode = PairingMode.ENTER_PIN 
            }
        }
    }

    fun handleApprove() {
        loading = true
        generatedPin = Random.nextInt(10000000, 99999999).toString()
        // TODO: API Call - approvePairing(sessionId, generatedPin)
        loading = false
        mode = PairingMode.SHOW_PIN
        timeLeft = 60
    }

    fun handleRestore() {
        if (pinInput.length != 8) {
            errorMsg = "Please enter the 8-digit PIN."
            return
        }
        loading = true
        errorMsg = ""
        // TODO: API Call - restoreFromPairing()
        loading = false
        onDone(true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E5EC))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (mode) {
                PairingMode.START -> {
                    Text("🔐", fontSize = 56.sp, modifier = Modifier.padding(bottom = 20.dp))
                    Text("Link Your Device", color = TextColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    Text("To sync your secure messages and media, please link this device to your existing account.",
                        color = SubTextColor, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 30.dp))
                    
                    MainButton(
                        text = "Link with Existing Device",
                        loading = loading,
                        onClick = { 
                            loading = true
                            // TODO: API Call initPairingSession
                            mode = PairingMode.WAITING 
                        }
                    )
                    
                    TextButton(onClick = { onDone(false) }) {
                        Text("Skip & Continue Fresh", color = DangerRed, fontWeight = FontWeight.Bold)
                    }
                    Text("(Note: Old messages will not be restored without the PIN)", color = SubTextColor, fontSize = 11.sp)
                }

                PairingMode.WAITING -> {
                    CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(50.dp).padding(bottom = 20.dp))
                    Text("Approval Pending", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    Text("A secure request has been sent to your other device. Please approve the login request there.",
                        color = SubTextColor, fontSize = 14.sp, textAlign = TextAlign.Center)
                }

                PairingMode.ENTER_PIN -> {
                    Text("🔑", fontSize = 48.sp, modifier = Modifier.padding(bottom = 20.dp))
                    Text("Secure Sync", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    Text("Enter the 8-digit PIN displayed on your other device to restore your encryption keys.",
                        color = SubTextColor, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 24.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                    ) {
                        BasicTextField(
                            value = pinInput, onValueChange = { if (it.length <= 8) pinInput = it.filter { char -> char.isDigit() } },
                            textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 12.sp, textAlign = TextAlign.Center, color = TextColor),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            decorationBox = { inner -> if (pinInput.isEmpty()) Text("••••••••", color = SubTextColor, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center); inner() }
                        )
                    }

                    if (errorMsg.isNotEmpty()) Text(errorMsg, color = DangerRed, fontSize = 13.sp, modifier = Modifier.padding(bottom = 15.dp))

                    MainButton(text = "Sync & Restore", loading = loading, onClick = { handleRestore() })
                }

                PairingMode.APPROVE_REQUEST -> {
                    Text("🛡️", fontSize = 56.sp, modifier = Modifier.padding(bottom = 20.dp))
                    Text("New Login Request", color = TextColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    Text("A new device is requesting access to your secure chats. If this is you, approve the request below.",
                        color = SubTextColor, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 30.dp))

                    MainButton(text = "Approve Device", loading = loading, color = Color(0xFF00C853), onClick = { handleApprove() })
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { onDone(false) }) {
                        Text("Deny Request", color = SubTextColor, fontWeight = FontWeight.Bold)
                    }
                }

                PairingMode.SHOW_PIN -> {
                    Text("📲", fontSize = 48.sp, modifier = Modifier.padding(bottom = 20.dp))
                    Text("Device Sync PIN", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    Text("Enter this PIN on your new device to complete the sync.",
                        color = SubTextColor, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.dp))

                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp, modifier = Modifier.padding(bottom = 20.dp)) {
                        Text(generatedPin, color = PrimaryBlue, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 10.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp))
                    }

                    Text("Expires in $timeLeft seconds", color = DangerRed, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Text("For security, do not share this PIN with anyone.", color = SubTextColor, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MainButton(text: String, loading: Boolean, color: Color = PrimaryBlue, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !loading,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Please wait...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
