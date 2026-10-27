package com.chatarena.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.network.OTPRequest
import com.chatarena.network.RetrofitClient
import com.chatarena.network.VerifyRequest
import kotlinx.coroutines.launch

// Colors
val BgColor = Color(0xFFDDE3ED)
val CardColor = Color(0xFFE0E5EC)
val TextColor = Color(0xFF2D3748)
val SubTextColor = Color(0xFF7E7E92)
val PrimaryBlue = Color(0xFF007AFF)
val DangerRed = Color(0xFFFF3B5C)

@Composable
fun AuthScreen(onLoginSuccess: () -> Unit = {}) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var showOtpBox by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var buttonText by remember { mutableStateOf("Send OTP") }

    // NAYA VARIABLE: Error dikhane ke liye
    var errorMessage by remember { mutableStateOf("") }

    var isProfileSetup by remember { mutableStateOf(false) }
    var setupName by remember { mutableStateOf("") }
    var setupMobile by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun sendAuthOTP() {
        val clean = email.trim().lowercase()
        if (clean.isEmpty() || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(clean)) {
            errorMessage = "Please enter a valid Email ID"; return
        }
        
        isLoading = true; buttonText = "Sending... ⏳"; errorMessage = ""
        
        scope.launch {
            try {
                val response = RetrofitClient.apiService.sendOtp(OTPRequest(clean))
                
                if (response.isSuccessful && response.body()?.success == true) {
                    showOtpBox = true
                    buttonText = "Resend OTP"
                    toast(response.body()?.message ?: "Access Code sent to your email!")
                } else {
                    buttonText = "Send OTP"
                    // API Error Screen par dikhao
                    errorMessage = response.body()?.error ?: "Failed. HTTP Code: ${response.code()}"
                }
            } catch (e: Exception) {
                buttonText = "Send OTP"
                // Network Error Screen par dikhao
                errorMessage = "Network Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun validateAuthOTP() {
        val cleanOtp = otp.trim()
        if (cleanOtp.length != 6 || !Regex("^\\d{6}$").matches(cleanOtp)) {
            errorMessage = "Please enter the 6-digit code"; return
        }
        
        isLoading = true; errorMessage = ""
        
        scope.launch {
            try {
                val response = RetrofitClient.apiService.verifyOtp(
                    VerifyRequest(email = email.trim().lowercase(), code = cleanOtp)
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    isProfileSetup = true
                } else {
                    errorMessage = response.body()?.error ?: "Incorrect OTP"
                }
            } catch (e: Exception) {
                errorMessage = "Network Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun submitProfileSetup() {
        val cleanName = setupName.trim()
        val cleanDigits = setupMobile.replace(Regex("\\D"), "")
        if (cleanName.isEmpty()) { errorMessage = "Please enter your Full Name"; return }
        if (cleanDigits.length != 10) { errorMessage = "Please enter a valid 10-digit mobile number"; return }
        
        isLoading = true; errorMessage = ""
        isLoading = false
        isProfileSetup = false
        toast("Profile saved! (Local only for now)")
        onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // NAYA UI: Agar error hai toh laal rang me screen par dikhao
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = DangerRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (!isProfileSetup) {
                LoginCard(
                    email = email, onEmailChange = { email = it },
                    otp = otp, onOtpChange = { otp = it },
                    showOtpBox = showOtpBox, isLoading = isLoading, buttonText = buttonText,
                    onSendOTP = { sendAuthOTP() },
                    onValidateOTP = { validateAuthOTP() },
                    onChangeEmail = { otp = ""; showOtpBox = false; buttonText = "Send OTP"; errorMessage = "" }
                )
            } else {
                ProfileSetupModal(
                    setupName = setupName, onNameChange = { setupName = it },
                    setupMobile = setupMobile,
                    onMobileChange = { input -> if (input.length <= 10) setupMobile = input.filter { it.isDigit() } },
                    isLoading = isLoading,
                    onSave = { submitProfileSetup() }
                )
            }
        }
    }
}

@Composable
fun LoginCard(
    email: String, onEmailChange: (String) -> Unit,
    otp: String, onOtpChange: (String) -> Unit,
    showOtpBox: Boolean, isLoading: Boolean, buttonText: String,
    onSendOTP: () -> Unit, onValidateOTP: () -> Unit, onChangeEmail: () -> Unit
) {
    Surface(
        modifier = Modifier.width(340.dp),
        shape = RoundedCornerShape(24.dp),
        color = CardColor, shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar()
            Spacer(modifier = Modifier.height(24.dp))
            Text("🔐", fontSize = 46.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Welcome to ChatArena", color = TextColor,
                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                letterSpacing = (-0.5).sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Sign In via OTP", color = SubTextColor,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(24.dp))

            NeumorphicInput(
                value = email, onValueChange = onEmailChange,
                placeholder = "Email ID",
                enabled = !showOtpBox && !isLoading,
                keyboardType = KeyboardType.Email
            )

            if (!showOtpBox) {
                Spacer(modifier = Modifier.height(16.dp))
                NeumorphicButton(text = buttonText, onClick = onSendOTP, enabled = !isLoading)
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                NeumorphicInput(
                    value = otp, onValueChange = onOtpChange,
                    placeholder = "6-digit OTP", enabled = !isLoading, isNumber = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                NeumorphicButton(
                    text = if (isLoading) "Verifying..." else "Validate",
                    onClick = onValidateOTP, enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Change Email", color = DangerRed,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !isLoading) { onChangeEmail() }.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileSetupModal(
    setupName: String, onNameChange: (String) -> Unit,
    setupMobile: String, onMobileChange: (String) -> Unit,
    isLoading: Boolean, onSave: () -> Unit
) {
    Surface(
        modifier = Modifier.width(320.dp),
        shape = RoundedCornerShape(24.dp),
        color = CardColor, shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Help your friends find you! 🌟", color = TextColor,
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Add your mobile number so people you know can easily search and connect with you on ChatArena.",
                color = SubTextColor, fontSize = 13.sp,
                textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                NeumorphicInput(
                    value = setupName, onValueChange = onNameChange,
                    placeholder = "Full Name", enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(12.dp))
                NeumorphicInput(
                    value = setupMobile, onValueChange = onMobileChange,
                    placeholder = "10-digit Mobile Number",
                    enabled = !isLoading, isNumber = true
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            NeumorphicButton(
                text = if (isLoading) "Saving..." else "Save & Continue",
                onClick = onSave, enabled = !isLoading
            )
        }
    }
}

@Composable
fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(BgColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("CA", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 18.sp)
        }
        Surface(shape = RoundedCornerShape(8.dp), color = BgColor, shadowElevation = 4.dp) {
            Text("ℹ Help",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
        }
    }
}

@Composable
fun NeumorphicInput(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    isNumber: Boolean = false, enabled: Boolean = true,
    keyboardType: KeyboardType = if (isNumber) KeyboardType.Number else KeyboardType.Text
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BgColor, shadowElevation = 6.dp
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange, enabled = enabled,
            textStyle = TextStyle(color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = SubTextColor, fontSize = 15.sp)
                inner()
            }
        )
    }
}

@Composable
fun NeumorphicButton(
    text: String, onClick: () -> Unit,
    enabled: Boolean = true, color: Color = PrimaryBlue
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) color else color.copy(alpha = 0.5f),
        shadowElevation = 8.dp,
        onClick = { if (enabled) onClick() }
    ) {
        Text(
            text = text, modifier = Modifier.padding(vertical = 14.dp),
            textAlign = TextAlign.Center, color = Color.White,
            fontSize = 16.sp, fontWeight = FontWeight.Bold
        )
    }
}
