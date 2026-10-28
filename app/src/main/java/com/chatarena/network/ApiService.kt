package com.chatarena.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    // 1. Email OTP bhejne ke liye
    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body request: OTPRequest): Response<OTPResponse>

    // 2. OTP verify karne ke liye
    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyRequest): Response<VerifyResponse>

    // 3. Naya User Profile Update karne ke liye
    @POST("api/auth/update-profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<VerifyResponse>

    // 4. AI Chat ke liye
    @POST("api/ai/chat")
    suspend fun aiChat(
        @Header("Authorization") token: String,
        @Body request: AIRequest
    ): Response<AIResponse>
}
