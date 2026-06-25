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
    val originKorean: String? = null,
    val originName: String? = null,
    val destKorean: String? = null,
    val destName: String? = null,
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

data class BuySignal(
    val recommendation: String, // BUY / WAIT / CONSIDER / NO_DATA
    val score: Int,
    val currentAmount: Double,
    val lowestAmount: Double,
    val percentile: Double,
    val trendPct: Double,
    val volatilityPct: Double,
    val daysToDeparture: Long,
    val reason: String,
)

data class Airport(
    val iata: String,
    val name: String,
    val municipality: String?,
    val country: String,
    val large: Boolean,
    val korean: String? = null,
)

data class NearbyAirport(
    val iata: String,
    val name: String,
    val municipality: String?,
    val country: String,
    val large: Boolean,
    val distanceKm: Double,
)

data class CalendarCell(
    val date: String,
    val lowestAmount: Double,
    val currency: String,
)

// Gson omits null fields, so optional values simply fall back to backend defaults.
data class CreateWatchRequest(
    val userRef: String,
    val origin: String,
    val destination: String,
    val tripType: String,
    val departDateFrom: String,
    val departDateTo: String,
    val returnDateFrom: String? = null,
    val returnDateTo: String? = null,
    val departTimeFrom: String? = null,
    val departTimeTo: String? = null,
    val returnTimeFrom: String? = null,
    val returnTimeTo: String? = null,
    val passengers: Int = 1,
    val cabin: String = "ECONOMY",
    val alertRule: String = "NEW_LOW",
)
