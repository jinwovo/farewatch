package com.portfolio.farewatch.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FarewatchApi {
    @GET("api/airports")
    suspend fun searchAirports(@Query("q") q: String): List<Airport>

    @GET("api/airports/{iata}/nearby")
    suspend fun nearbyAirports(@Path("iata") iata: String, @Query("limit") limit: Int = 5): List<NearbyAirport>

    @GET("api/watches/{id}/calendar")
    suspend fun calendar(@Path("id") id: String): List<CalendarCell>

    @POST("api/watches")
    suspend fun createWatch(@Body request: CreateWatchRequest): Watch

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

    @GET("api/watches/{id}/signal")
    suspend fun signal(@Path("id") id: String): BuySignal

    @POST("api/watches/{id}/poll")
    suspend fun poll(@Path("id") id: String): PollResult
}
