package com.chatarena.network

import com.google.gson.annotations.SerializedName

// 1. Send OTP Request & Response
data class OTPRequest(
    val email: String
)

data class OTPResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

// 2. Verify OTP Request & Response
data class VerifyRequest(
    val email: String,
    val code: String = "",
    val token: String = ""
)

data class VerifyResponse(
    val success: Boolean,
    val ticket: String? = null,
    @SerializedName("chat_token") val chatToken: String? = null,
    val email: String? = null,
    val user: UserData? = null,
    val error: String? = null
)

// 3. User Data Model
data class UserData(
    @SerializedName("userCode") val userCode: String,
    val email: String,
    @SerializedName("fullName") val fullName: String,
    val avatar: String,
    val mobile: String? = null,
    @SerializedName("lastSeen") val lastSeen: Long? = null,
    @SerializedName("publicKey") val publicKey: String? = null
)

// 4. Update Profile Request
data class UpdateProfileRequest(
    val fullName: String,
    val mobile: String,
    val publicKey: String = ""
)

// 5. AI Chat Request & Response
data class AIRequest(
    val message: String
)

data class AIResponse(
    val success: Boolean,
    val reply: String? = null,
    val error: String? = null
)

// 6. Search User Response (Naya Add Kiya Gaya Hai)
data class SearchUserResponse(
    val success: Boolean,
    val user: SearchUserData? = null,
    val error: String? = null
)

data class SearchUserData(
    val userCode: String,
    val fullName: String,
    val avatar: String? = null,
    val publicKey: String? = null
)
