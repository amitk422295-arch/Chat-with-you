package com.chatarena.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    // 1. Email OTP bhejne ke liye
    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body request: OTPRequest): Response<OTPResponse>

    // 2. OTP verify karne ke liye
    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyRequest): Response<VerifyResponse>

}
