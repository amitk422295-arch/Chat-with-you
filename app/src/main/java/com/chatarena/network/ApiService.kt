package com.chatarena.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body request: OTPRequest): Response<OTPResponse>

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyRequest): Response<VerifyResponse>

    @POST("api/auth/update-profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<VerifyResponse>

    @POST("api/ai/chat")
    suspend fun aiChat(
        @Header("Authorization") token: String,
        @Body request: AIRequest
    ): Response<AIResponse>

    // Naya Search API Endpoint
    @GET("api/user/search")
    suspend fun searchUser(
        @Header("Authorization") token: String,
        @Query("mobile") mobile: String
    ): Response<SearchUserResponse>
}
