package com.yashu.igris

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("command")
    fun sendCommand(
        @Body request: CommandRequest
    ): Call<CommandResponse>
}