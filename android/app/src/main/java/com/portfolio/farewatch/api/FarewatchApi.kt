package com.portfolio.farewatch.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FarewatchApi {
    @GET("api/watches")
    suspend fun watches(): List<Watch>

    @GET("api/watches/{id}")
    suspend fun watch(@Path("id") id: String): Watch

    @GET("api/watches/{id}/prices")
    suspend fun prices(@Path("id") id: String): List<PricePoint>

    @GET("api/watches/{id}/alerts")
    suspend fun alerts(@Path("id") id: String): List<Alert>

    @GET("api/watches/{id}/weather")
    suspend fun weather(@Path("id") id: String): List<WeatherEstimate>

    @POST("api/watches/{id}/poll")
    suspend fun poll(@Path("id") id: String): PollResult
}
