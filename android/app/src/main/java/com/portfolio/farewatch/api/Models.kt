package com.portfolio.farewatch.api

// Dates/times arrive as ISO strings; kept as String (no Gson date adapter needed).

data class Watch(
    val id: String,
    val userRef: String,
    val origin: String,
    val destination: String,
    val tripType: String,
    val departDateFrom: String,
    val departDateTo: String,
    val returnDateFrom: String?,
    val returnDateTo: String?,
    val cabin: String,
    val currency: String,
    val alertRule: String,
)

data class PricePoint(
    val id: String,
    val source: String,
    val amount: Double,
    val currency: String,
    val departDate: String,
    val deepLink: String?,
    val observedAt: String,
)

data class NotificationDelivery(
    val channel: String,
    val status: String,
    val attempts: Int,
)

data class Alert(
    val id: String,
    val rule: String,
    val previousLow: Double?,
    val newLow: Double,
    val createdAt: String,
    val notifications: List<NotificationDelivery>,
)

data class WeatherEstimate(
    val date: String,
    val tempMaxC: Double?,
    val tempMinC: Double?,
    val precipProbPct: Int?,
    val source: String,
)

data class PollResult(
    val watchId: String,
    val lowestAmount: Double?,
    val lowestCurrency: String?,
    val newLow: Boolean,
)
