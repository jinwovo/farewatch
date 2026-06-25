package com.portfolio.farewatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portfolio.farewatch.api.Airport
import com.portfolio.farewatch.api.ApiClient
import com.portfolio.farewatch.api.CreateWatchRequest
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TIME_PRESETS = listOf(
    "아무때나" to (null to null),
    "새벽 0–6" to ("00:00" to "06:00"),
    "아침 6–12" to ("06:00" to "12:00"),
    "오후 12–18" to ("12:00" to "18:00"),
    "저녁 18–24" to ("18:00" to "23:59"),
)

private fun cabinLabel(c: String) = when (c) {
    "ECONOMY" -> "이코노미"
    "PREMIUM_ECONOMY" -> "프리미엄"
    "BUSINESS" -> "비즈니스"
    "FIRST" -> "퍼스트"
    else -> c
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWatchScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var origin by remember { mutableStateOf<Airport?>(null) }
    var dest by remember { mutableStateOf<Airport?>(null) }
    var roundTrip by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var departTime by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    var returnTime by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    var passengers by remember { mutableStateOf(1) }
    var cabin by remember { mutableStateOf("ECONOMY") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("워치 만들기") },
            navigationIcon = { TextButton(onClick = onBack) { Text("← 목록") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AirportPicker("출발지", origin, "서울 ICN") { origin = it }
            OutlinedButton(onClick = { val t = origin; origin = dest; dest = t }) { Text("⇅ 출도착 바꾸기") }
            AirportPicker("도착지", dest, "도쿄 NRT") { dest = it }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !roundTrip,
                    onClick = { roundTrip = false; fromDate = ""; toDate = "" },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("편도") }
                SegmentedButton(
                    selected = roundTrip,
                    onClick = { roundTrip = true; fromDate = ""; toDate = "" },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("왕복") }
            }

            DateField(if (roundTrip) "가는 날" else "가는 날 (부터)", fromDate) { fromDate = it }
            DateField(if (roundTrip) "오는 날" else "가는 날 (까지 · 선택)", toDate) { toDate = it }

            SectionLabel(if (roundTrip) "가는 편 출발 시간대" else "출발 시간대")
            TimePresets(departTime) { departTime = it }
            if (roundTrip) {
                SectionLabel("오는 편 출발 시간대")
                TimePresets(returnTime) { returnTime = it }
            }

            SectionLabel("여행자 · 좌석")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (passengers > 1) passengers-- }) { Text("−") }
                Text("$passengers 명", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { if (passengers < 9) passengers++ }) { Text("+") }
                Box {
                    var open by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { open = true }) { Text(cabinLabel(cabin)) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        listOf("ECONOMY", "PREMIUM_ECONOMY", "BUSINESS", "FIRST").forEach { c ->
                            DropdownMenuItem(text = { Text(cabinLabel(c)) }, onClick = { cabin = c; open = false })
                        }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val o = origin
                    val d = dest
                    if (o == null || d == null || fromDate.isBlank()) {
                        error = "출발·도착 공항과 가는 날짜를 선택하세요."
                        return@Button
                    }
                    if (roundTrip && toDate.isBlank()) {
                        error = "오는 날짜도 선택하세요."
                        return@Button
                    }
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            ApiClient.api.createWatch(
                                CreateWatchRequest(
                                    userRef = "demo-user",
                                    origin = o.iata,
                                    destination = d.iata,
                                    tripType = if (roundTrip) "ROUND_TRIP" else "ONE_WAY",
                                    departDateFrom = fromDate,
                                    departDateTo = if (roundTrip) fromDate else toDate.ifBlank { fromDate },
                                    returnDateFrom = if (roundTrip) toDate else null,
                                    returnDateTo = if (roundTrip) toDate else null,
                                    departTimeFrom = departTime.first,
                                    departTimeTo = departTime.second,
                                    returnTimeFrom = if (roundTrip) returnTime.first else null,
                                    returnTimeTo = if (roundTrip) returnTime.second else null,
                                    passengers = passengers,
                                    cabin = cabin,
                                ),
                            )
                            onCreated()
                        } catch (e: Exception) {
                            error = e.message ?: "생성 실패"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "만드는 중…" else "＋ 워치 만들기") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirportPicker(label: String, selected: Airport?, placeholder: String, onSelect: (Airport?) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Airport>>(emptyList()) }
    LaunchedEffect(query, selected) {
        if (selected != null || query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(250) // debounce typing
        results = try {
            ApiClient.api.searchAirports(query)
        } catch (e: Exception) {
            emptyList()
        }
    }
    Column {
        SectionLabel(label)
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${selected.iata} · ${selected.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSelect(null); query = "" }) { Text("변경") }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            results.take(6).forEach { a ->
                val city = a.municipality?.let { " ($it)" } ?: ""
                Text(
                    "${a.iata} · ${a.name}$city",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(a); results = emptyList() }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, onPick: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (value.isBlank()) "$label 선택" else "$label: $value")
    }
    if (show) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    show = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePresets(value: Pair<String?, String?>, onChange: (Pair<String?, String?>) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TIME_PRESETS.forEach { (label, pair) ->
            FilterChip(selected = value == pair, onClick = { onChange(pair) }, label = { Text(label) })
        }
    }
}
