package com.goth.messenger.data.api

import com.goth.messenger.data.models.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<LoginResponse>>
    
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>
    
    @GET("api/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ApiResponse<User>>
    
    @GET("api/chats")
    suspend fun getChats(@Header("Authorization") token: String): Response<ApiResponse<List<Chat>>>
    
    @GET("api/chats/{chatId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Long,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ApiResponse<List<Message>>>
    
    @Multipart
    @POST("api/upload")
    suspend fun uploadFile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<UploadResponse>>
    
    @POST("api/chats/create")
    suspend fun createChat(
        @Header("Authorization") token: String,
        @Body request: CreateChatRequest
    ): Response<ApiResponse<Chat>>
    
    @POST("api/chats/{chatId}/join")
    suspend fun joinChat(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Long
    ): Response<ApiResponse<Unit>>
    
    @POST("api/calls/initiate")
    suspend fun initiateCall(
        @Header("Authorization") token: String,
        @Body request: CallRequest
    ): Response<ApiResponse<CallResponse>>
}