package com.chatarena.ui.auth

import android.util.Log
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
import com.chatarena.network.UpdateProfileRequest
import com.chatarena.network.VerifyRequest
import com.chatarena.utils.CryptoManager
import com.chatarena.utils.TokenManager
import kotlinx.coroutines.launch

private const val TAG = "AuthScreen"

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
                    errorMessage = response.body()?.error ?: "Failed. HTTP Code: ${response.code()}"
                }
            } catch (e: Exception) {
                buttonText = "Send OTP"
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
                    val body = response.body()
                    val token = body?.chatToken
                    val user = body?.user

                    if (token != null && user != null) {
                        // 1. Token save karo
                        TokenManager.saveUserData(
                            context = context,
                            token = token,
                            userCode = user.userCode,
                            email = user.email,
                            fullName = user.fullName
                        )

                        // 2. Local E2E key ensure karo
                        val myPublicKey = CryptoManager.ensureE2EKeys(context)
                        val serverPublicKey = user.publicKey ?: ""

                        // 3. Purana user vs Naya user
                        if (!user.mobile.isNullOrEmpty()) {
                            // ========== PURANA USER ==========
                            // 🔥 FIX: response CHECK karo, silent fail band karo
                            if (serverPublicKey.isEmpty() || serverPublicKey != myPublicKey) {
                                val uploadOk = try {
                                    val updateResp = RetrofitClient.apiService.updateProfile(
                                        token = "Bearer $token",
                                        request = UpdateProfileRequest(
                                            fullName = user.fullName,
                                            mobile = user.mobile,
                                            publicKey = myPublicKey
                                        )
                                    )
                                    val ok = updateResp.isSuccessful && updateResp.body()?.success == true
                                    if (!ok) {
                                        Log.e(TAG, "Key upload failed: HTTP ${updateResp.code()} - ${updateResp.body()?.error}")
                                    }
                                    ok
                                } catch (e: Exception) {
                                    Log.e(TAG, "Key upload exception: ${e.message}")
                                    false
                                }

                                if (uploadOk) {
                                    Log.d(TAG, "Public key synced with server ✅")
                                } else {
                                    Log.w(TAG, "⚠️ Key upload FAILED — user may see 🔒 on other devices until next retry")
                                    toast("⚠️ Encryption setup issue. Messages may not decrypt.")
                                }
                            }

                            toast("Welcome back!")
                            onLoginSuccess()
                        } else {
                            // ========== NAYA USER ==========
                            setupName = if (user.fullName != user.userCode) user.fullName else ""
                            isProfileSetup = true
                        }
                    } else {
                        errorMessage = "Invalid response from server"
                    }
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
        scope.launch {
            try {
                val savedToken = TokenManager.getToken(context) ?: ""
                val myPublicKey = CryptoManager.ensureE2EKeys(context)

                val response = RetrofitClient.apiService.updateProfile(
                    token = "Bearer $savedToken",
                    request = UpdateProfileRequest(
                        fullName = cleanName,
                        mobile = cleanDigits,
                        publicKey = myPublicKey
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val userCode = TokenManager.getUserCode(context) ?: ""
                    val userEmail = TokenManager.getEmail(context) ?: ""
                    TokenManager.saveUserData(
                        context = context,
                        token = savedToken,
                        userCode = userCode,
                        email = userEmail,
                        fullName = cleanName
                    )
                    isProfileSetup = false
                    toast("Profile saved successfully!")
                    onLoginSuccess()
                } else {
                    errorMessage = response.body()?.error ?: "Failed to update profile (HTTP ${response.code()})"
                }
            } catch (e: Exception) {
                errorMessage = "Network Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
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

// ... baaki LoginCard, ProfileSetupModal, TopBar, NeumorphicInput, NeumorphicButton — jo the waise hi rakho
