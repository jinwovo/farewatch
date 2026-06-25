package com.portfolio.farewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolio.farewatch.api.Airport
import com.portfolio.farewatch.api.ApiClient
import com.portfolio.farewatch.api.CreateWatchRequest
import com.portfolio.farewatch.api.NearbyAirport
import com.portfolio.farewatch.ui.theme.Blue200
import com.portfolio.farewatch.ui.theme.BlueDeep
import com.portfolio.farewatch.ui.theme.CanvasWhite
import com.portfolio.farewatch.ui.theme.Coral
import com.portfolio.farewatch.ui.theme.Hairline
import com.portfolio.farewatch.ui.theme.Ink
import com.portfolio.farewatch.ui.theme.Steel
import com.portfolio.farewatch.ui.theme.Stone
import com.portfolio.farewatch.ui.theme.Surface
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TIME_PRESETS = listOf(
    "아무때나" to (null to null),
    "새벽 0–6" to ("00:00" to "06:00"),
    "아침 6–12" to ("06:00" to "12:00"),
    "오후 12–18" to ("12:00" to "18:00"),
    "저녁 18–24" to ("18:00" to "23:59"),
)

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

    Column(
        Modifier.fillMaxSize().background(CanvasWhite).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BackBar(onBack)
        Text("워치 만들기", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)

        AirportPicker("출발지", origin, "서울 ICN") { origin = it }
        Box(
            Modifier.size(44.dp).border(1.dp, Hairline, RoundedCornerShape(50))
                .clickable { val t = origin; origin = dest; dest = t },
            contentAlignment = Alignment.Center,
        ) { Text("⇅", fontSize = 18.sp) }
        AirportPicker("도착지", dest, "도쿄 NRT") { dest = it }

        TripToggle(roundTrip) { roundTrip = it; fromDate = ""; toDate = "" }

        Calendar(fromDate, toDate, roundTrip) { f, t -> fromDate = f; toDate = t }

        SegLabel(if (roundTrip) "가는 편 출발 시간대" else "출발 시간대")
        TimeField(departTime) { departTime = it }
        if (roundTrip) {
            SegLabel("오는 편 출발 시간대")
            TimeField(returnTime) { returnTime = it }
        }

        SegLabel("여행자 · 좌석")
        PaxCabinRow(passengers, { passengers = it }, cabin, { cabin = it })

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }

        PillButton(
            if (busy) "만드는 중…" else "＋  워치 만들기",
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                val o = origin; val d = dest
                if (o == null || d == null || fromDate.isBlank()) { error = "출발·도착 공항과 가는 날짜를 선택하세요."; return@PillButton }
                if (roundTrip && toDate.isBlank()) { error = "오는 날짜도 선택하세요."; return@PillButton }
                scope.launch {
                    busy = true; error = null
                    try {
                        ApiClient.api.createWatch(
                            CreateWatchRequest(
                                userRef = "demo-user",
                                origin = o.iata, destination = d.iata,
                                tripType = if (roundTrip) "ROUND_TRIP" else "ONE_WAY",
                                departDateFrom = fromDate,
                                departDateTo = if (roundTrip) fromDate else toDate.ifBlank { fromDate },
                                returnDateFrom = if (roundTrip) toDate else null,
                                returnDateTo = if (roundTrip) toDate else null,
                                departTimeFrom = departTime.first, departTimeTo = departTime.second,
                                returnTimeFrom = if (roundTrip) returnTime.first else null,
                                returnTimeTo = if (roundTrip) returnTime.second else null,
                                passengers = passengers, cabin = cabin,
                            ),
                        )
                        onCreated()
                    } catch (e: Exception) { error = e.message ?: "생성 실패" } finally { busy = false }
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SegLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Steel)
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BlueDeep, unfocusedBorderColor = Hairline,
    focusedContainerColor = CanvasWhite, unfocusedContainerColor = CanvasWhite,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirportPicker(label: String, selected: Airport?, placeholder: String, onSelect: (Airport?) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var nearby by remember { mutableStateOf<List<NearbyAirport>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(200)
        results = runCatching { ApiClient.api.searchAirports(query) }.getOrDefault(emptyList())
    }
    LaunchedEffect(selected) {
        nearby = if (selected != null) runCatching { ApiClient.api.nearbyAirports(selected.iata) }.getOrDefault(emptyList()) else emptyList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SegLabel(label)
        if (selected != null && query.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.korean ?: selected.municipality ?: selected.name} (${selected.iata})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("변경", color = Coral, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onSelect(null) })
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text(placeholder, color = Stone) },
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        if (query.isNotBlank()) {
            if (results.isEmpty()) Text("검색 결과 없음", color = Stone, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
            results.take(6).forEach { a ->
                AirportRow(a.iata, a.korean ?: a.municipality ?: a.name, "${a.name} · ${a.country}") { onSelect(a); query = "" }
            }
        } else if (selected != null && nearby.isNotEmpty()) {
            Text("${selected.municipality ?: selected.name} 근처 공항", color = Steel, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            nearby.forEach { a ->
                AirportRow(a.iata, a.municipality ?: a.name, "${a.country} · ${a.distanceKm.toInt()}km") {
                    onSelect(Airport(a.iata, a.name, a.municipality, a.country, a.large)); query = ""
                }
            }
        }
    }
}

@Composable
private fun AirportRow(iata: String, city: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("✈", color = Steel, fontSize = 16.sp)
        Column {
            Row {
                Text(city, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(" ($iata)", fontSize = 15.sp, color = Steel)
            }
            Text(sub, fontSize = 12.5.sp, color = Steel)
        }
    }
}

@Composable
private fun TripToggle(roundTrip: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.background(Surface, RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to "편도", true to "왕복").forEach { (rt, lbl) ->
            val active = rt == roundTrip
            Box(
                Modifier.weight(1f).background(if (active) Ink else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(50))
                    .clickable { onChange(rt) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(lbl, color = if (active) CanvasWhite else Steel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
    }
}

private val DOW = listOf("일", "월", "화", "수", "목", "금", "토")

private fun monthCells(ym: YearMonth): List<LocalDate?> {
    val first = ym.atDay(1)
    val lead = first.dayOfWeek.value % 7 // SUNDAY(7) -> 0
    val cells = ArrayList<LocalDate?>()
    repeat(lead) { cells.add(null) }
    for (d in 1..ym.lengthOfMonth()) cells.add(ym.atDay(d))
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

@Composable
private fun Calendar(from: String, to: String, roundTrip: Boolean, onChange: (String, String) -> Unit) {
    val today = remember { LocalDate.now() }
    val todayStr = today.toString()
    var view by remember { mutableStateOf(YearMonth.from(today)) }
    var flexible by remember { mutableStateOf(to.isNotEmpty() && to != from) }
    val range = roundTrip || flexible

    fun clickDay(date: LocalDate) {
        val ds = date.toString()
        if (date.isBefore(today)) return
        if (!range) { onChange(ds, ds); return }
        if (from.isEmpty() || to.isNotEmpty()) onChange(ds, "")
        else if (ds >= from) onChange(from, ds) else onChange(ds, "")
    }

    val label = when {
        roundTrip && from.isNotEmpty() && to.isNotEmpty() -> "$from → $to"
        roundTrip && from.isNotEmpty() -> "$from → 오는 날 선택"
        roundTrip -> "가는 날을 선택하세요"
        from.isNotEmpty() && to.isNotEmpty() && to != from -> "$from ~ $to"
        from.isNotEmpty() -> from
        else -> "날짜를 선택하세요"
    }

    Column(
        Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!roundTrip) {
            Row(
                Modifier.background(Surface, RoundedCornerShape(50)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(false to "특정 날짜", true to "날짜 조정 가능").forEach { (flex, lbl) ->
                    val active = flex == flexible
                    Box(
                        Modifier.background(if (active) Ink else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(50))
                            .clickable { flexible = flex; if (!flex && from.isNotEmpty()) onChange(from, from) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text(lbl, color = if (active) CanvasWhite else Steel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
            }
        }
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).border(1.dp, Hairline, RoundedCornerShape(50)).clickable { view = view.minusMonths(1) }, contentAlignment = Alignment.Center) { Text("‹", fontSize = 18.sp) }
            Text("${view.year}년 ${view.monthValue}월", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Box(Modifier.size(34.dp).border(1.dp, Hairline, RoundedCornerShape(50)).clickable { view = view.plusMonths(1) }, contentAlignment = Alignment.Center) { Text("›", fontSize = 18.sp) }
        }
        Row(Modifier.fillMaxWidth()) {
            DOW.forEach { Text(it, Modifier.weight(1f), color = Stone, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val cells = monthCells(view)
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val ds = date.toString()
                            val past = ds < todayStr
                            val sel = ds == from || ds == to
                            val inRange = range && from.isNotEmpty() && to.isNotEmpty() && ds > from && ds < to
                            Box(
                                Modifier.fillMaxSize()
                                    .background(if (sel) Ink else if (inRange) Blue200 else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(9.dp))
                                    .clickable(enabled = !past) { clickDay(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    color = if (sel) CanvasWhite else if (past) Hairline else if (inRange) BlueDeep else Ink,
                                    fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (range) {
            Text(
                if (roundTrip) "가는 날을 누른 뒤 오는 날을 누르세요." else "시작일을 누른 뒤 종료일을 누르면 유연한 날짜 범위가 됩니다.",
                color = Stone, fontSize = 12.5.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(value: Pair<String?, String?>, onChange: (Pair<String?, String?>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TIME_PRESETS.forEach { (label, pair) ->
                val active = value == pair
                Box(
                    Modifier.border(1.dp, if (active) Ink else Hairline, RoundedCornerShape(50))
                        .background(if (active) Ink else CanvasWhite, RoundedCornerShape(50))
                        .clickable { onChange(pair) }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(label, color = if (active) CanvasWhite else Steel, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("직접 지정", color = Steel, fontSize = 13.sp)
            TimePick(value.first) { onChange(it to (value.second ?: it)) }
            Text("~", color = Steel)
            TimePick(value.second) { onChange((value.first) to it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePick(time: String?, onPick: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box(
        Modifier.border(1.dp, Hairline, RoundedCornerShape(8.dp)).clickable { show = true }.padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(time ?: "--:--", fontSize = 14.sp, color = if (time == null) Stone else Ink) }
    if (show) {
        val parts = time?.split(":")
        val state = rememberTimePickerState(
            initialHour = parts?.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = parts?.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)); show = false }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { show = false }) { Text("취소") } },
            text = { TimePicker(state = state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaxCabinRow(passengers: Int, onPax: (Int) -> Unit, cabin: String, onCabin: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(34.dp).border(1.dp, Hairline, RoundedCornerShape(50)).clickable { if (passengers > 1) onPax(passengers - 1) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp) }
        Text("$passengers 명", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(34.dp).border(1.dp, Hairline, RoundedCornerShape(50)).clickable { if (passengers < 9) onPax(passengers + 1) }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp) }
        Spacer(Modifier.width(4.dp))
        Box {
            var open by remember { mutableStateOf(false) }
            Box(Modifier.border(1.dp, Hairline, RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(cabinKo(cabin), fontSize = 14.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                listOf("ECONOMY", "PREMIUM_ECONOMY", "BUSINESS", "FIRST").forEach { c ->
                    DropdownMenuItem(text = { Text(cabinKo(c)) }, onClick = { onCabin(c); open = false })
                }
            }
        }
    }
}
