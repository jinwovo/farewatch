package com.portfolio.farewatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.portfolio.farewatch.api.Alert
import com.portfolio.farewatch.api.ApiClient
import com.portfolio.farewatch.api.PricePoint
import com.portfolio.farewatch.api.Watch
import com.portfolio.farewatch.api.WeatherEstimate
import kotlinx.coroutines.launch

@Composable
fun FarewatchApp() {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val id = selectedId
    if (id == null) {
        WatchListScreen(onOpen = { selectedId = it })
    } else {
        WatchDetailScreen(id = id, onBack = { selectedId = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchListScreen(onOpen: (String) -> Unit) {
    var watches by remember { mutableStateOf<List<Watch>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try {
            watches = ApiClient.api.watches()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("fare·watch") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("항공권 최저가 감시", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text("백엔드 연결 실패: $error", color = MaterialTheme.colorScheme.error)
                watches.isEmpty() -> Text("워치가 없습니다.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(watches) { w ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(w.id) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${w.origin} → ${w.destination}", style = MaterialTheme.typography.titleLarge)
                                val window = if (w.departDateTo != w.departDateFrom) "${w.departDateFrom} ~ ${w.departDateTo}" else w.departDateFrom
                                val trip = if (w.tripType == "ROUND_TRIP") "왕복" else "편도"
                                Text("$window · $trip · ${w.cabin} · 알림 ${w.alertRule}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchDetailScreen(id: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var watch by remember { mutableStateOf<Watch?>(null) }
    var prices by remember { mutableStateOf<List<PricePoint>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
    var weather by remember { mutableStateOf<List<WeatherEstimate>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        try {
            watch = ApiClient.api.watch(id)
            prices = ApiClient.api.prices(id)
            alerts = ApiClient.api.alerts(id)
            weather = ApiClient.api.weather(id)
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(id) { reload() }

    val lowest = prices.minByOrNull { it.amount }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(watch?.let { "${it.origin} → ${it.destination}" } ?: "상세") },
            navigationIcon = { TextButton(onClick = onBack) { Text("← 목록") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Text("오류: $it", color = MaterialTheme.colorScheme.error) }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("현재 최저가", style = MaterialTheme.typography.labelMedium)
                    Text(
                        lowest?.let { "${it.amount.toLong()} ${it.currency}" } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text("${prices.size}회 관측", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            ApiClient.api.poll(id)
                            reload()
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "폴링 중…" else "지금 폴") }

            if (alerts.isNotEmpty()) {
                Text("알림 내역", style = MaterialTheme.typography.titleMedium)
                alerts.forEach { a ->
                    val channels = a.notifications.joinToString("  ") { "${it.channel} ${it.status}" }
                    Text("🎉 ${a.newLow.toLong()} ${watch?.currency ?: ""}  —  $channels", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (weather.isNotEmpty()) {
                Text("도착지 날씨", style = MaterialTheme.typography.titleMedium)
                weather.forEach { d ->
                    val src = if (d.source == "FORECAST") "예보" else "평년값"
                    val max = d.tempMaxC?.toInt()?.toString() ?: "—"
                    val min = d.tempMinC?.toInt()?.toString() ?: "—"
                    Text("${d.date}   $max° / $min°   ☔ ${d.precipProbPct ?: "—"}%   [$src]", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
