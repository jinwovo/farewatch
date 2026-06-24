package com.portfolio.farewatch.api

import com.portfolio.farewatch.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    val api: FarewatchApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(FarewatchApi::class.java)
}
