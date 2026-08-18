package com.portfolio.farewatch.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolio.farewatch.api.Alert
import com.portfolio.farewatch.api.ApiClient
import com.portfolio.farewatch.api.BuySignal
import com.portfolio.farewatch.api.CalendarCell
import com.portfolio.farewatch.api.PricePoint
import com.portfolio.farewatch.api.SparkCell
import com.portfolio.farewatch.api.UpdateWatchRequest
import com.portfolio.farewatch.api.Watch
import com.portfolio.farewatch.api.WatchSummary
import com.portfolio.farewatch.api.WeatherEstimate
import com.portfolio.farewatch.ui.theme.Blue
import com.portfolio.farewatch.ui.theme.CanvasWhite
import com.portfolio.farewatch.ui.theme.Coral
import com.portfolio.farewatch.ui.theme.Hairline
import com.portfolio.farewatch.ui.theme.Ink
import com.portfolio.farewatch.ui.theme.Steel
import com.portfolio.farewatch.ui.theme.Stone
import com.portfolio.farewatch.ui.theme.SuccessBg
import com.portfolio.farewatch.ui.theme.SuccessText
import com.portfolio.farewatch.ui.theme.Surface
import kotlinx.coroutines.launch

private val White = CanvasWhite

@Composable
fun FarewatchApp() {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val id = selectedId
    when {
        creating -> CreateWatchScreen(onBack = { creating = false }, onCreated = { creating = false; refreshKey++ })
        id != null -> WatchDetailScreen(id = id, onBack = { selectedId = null })
        else -> WatchListScreen(refreshKey, onOpen = { selectedId = it }, onCreate = { creating = true })
    }
}

@Composable
fun Brand() {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("fare") }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Coral)) { append("·") }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("watch") }
        },
        fontSize = 22.sp,
        color = Ink,
    )
}

@Composable
fun BackBar(onBack: () -> Unit) {
    Text(
        "←  목록",
        color = Coral,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp),
    )
}

/** Black pill CTA (web .btn-primary). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, enabled: Boolean = true, container: androidx.compose.ui.graphics.Color = Ink, content: androidx.compose.ui.graphics.Color = White, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content, disabledContainerColor = Hairline, disabledContentColor = Stone),
        modifier = modifier,
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchListScreen(refreshKey: Int, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<WatchSummary>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf("recent") }
    LaunchedEffect(refreshKey) {
        loading = true
        try {
            items = ApiClient.api.summaries()
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    fun toggleActive(s: WatchSummary) {
        val next = !s.watch.active
        items = items.map { if (it.watch.id == s.watch.id) it.copy(watch = it.watch.copy(active = next)) else it }
        scope.launch {
            try {
                ApiClient.api.updateWatch(s.watch.id, UpdateWatchRequest(next))
            } catch (e: Exception) { // roll back the optimistic flip
                items = items.map { if (it.watch.id == s.watch.id) it.copy(watch = it.watch.copy(active = !next)) else it }
            }
        }
    }

    val sorted = when (sort) {
        "price" -> items.sortedBy { it.latestAmount ?: Double.MAX_VALUE }
        // expired watches keep their last score but there is nothing left to buy — sink them
        "score" -> items.sortedByDescending {
            if (it.signal.recommendation == "NO_DATA" || it.signal.daysToDeparture < 0) -1 else it.signal.score
        }
        else -> items // server order: newest first
    }
    // keyed LazyColumn anchors scroll to the previously-visible card across a re-sort —
    // jump back to the top so the new #1 is what the user sees
    val listState = rememberLazyListState()
    LaunchedEffect(sort) { listState.scrollToItem(0) }
    Scaffold(
        containerColor = White,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, containerColor = Ink, contentColor = White) {
                Text("＋  워치 검색", fontWeight = FontWeight.SemiBold)
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(20.dp)) {
            Brand()
            Spacer(Modifier.height(4.dp))
            Text("항공권 최저가 감시", color = Steel, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            if (items.isNotEmpty()) {
                SortTabs(sort) { sort = it }
                Spacer(Modifier.height(12.dp))
            }
            when {
                loading -> CircularProgressIndicator(color = Coral)
                error != null -> Text("백엔드 연결 실패: $error", color = MaterialTheme.colorScheme.error)
                items.isEmpty() -> Text("워치가 없습니다. ＋ 워치 검색으로 만들어보세요.", color = Stone)
                else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sorted, key = { it.watch.id }) { s ->
                        SummaryCard(s, onClick = { onOpen(s.watch.id) }, onToggle = { toggleActive(s) })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SortTabs(sort: String, onSort: (String) -> Unit) {
    Row(
        Modifier.background(Surface, RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("recent" to "최신순", "price" to "가격순", "score" to "딜점수순").forEach { (k, label) ->
            val active = sort == k
            Box(
                Modifier
                    .background(if (active) Ink else Surface, RoundedCornerShape(50))
                    .clickable { onSort(k) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (active) White else Steel)
            }
        }
    }
}

private fun shortDate(s: String) = s.drop(5).replace('-', '.')

/** Web wcard parity: price + vs-all-time-low + signal chip + sparkline + pause toggle. */
@Composable
fun SummaryCard(s: WatchSummary, onClick: () -> Unit, onToggle: () -> Unit) {
    val w = s.watch
    val expired = s.signal.daysToDeparture < 0
    val hasSignal = s.signal.recommendation != "NO_DATA" && !expired
    val latest = s.latestAmount
    val atLow = hasSignal && latest != null && latest <= s.signal.lowestAmount
    val overLowPct = if (hasSignal && latest != null && s.signal.lowestAmount > 0) {
        (latest - s.signal.lowestAmount) / s.signal.lowestAmount * 100
    } else {
        null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (w.active) 1f else 0.55f)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.origin, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("  →  ", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Coral)
                    Text(w.destination, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                if (w.originKorean != null || w.destKorean != null) {
                    Text("${w.originKorean ?: w.origin} → ${w.destKorean ?: w.destination}", color = Steel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                val trip = if (w.tripType == "ROUND_TRIP") "왕복" else "편도"
                val dates = if (w.tripType == "ROUND_TRIP" && w.returnDateFrom != null) {
                    "${shortDate(w.departDateFrom)} → ${shortDate(w.returnDateFrom)}"
                } else if (w.departDateTo != w.departDateFrom) {
                    "${shortDate(w.departDateFrom)} ~ ${shortDate(w.departDateTo)}"
                } else {
                    shortDate(w.departDateFrom)
                }
                Spacer(Modifier.height(2.dp))
                Text("$dates · $trip", color = Steel, fontSize = 13.sp)
            }
            Box(
                Modifier.size(32.dp).border(1.dp, Hairline, CircleShape).clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (w.active) "⏸" else "▶", fontSize = 12.sp, color = Steel)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                if (latest != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%,d".format(latest.toLong()), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("  ${w.currency}", color = Steel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    when {
                        atLow -> Text("지금이 역대 최저가 🔥", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        overLowPct != null -> Text(
                            "역대 최저 ${"%,d".format(s.signal.lowestAmount.toLong())} · +${"%.1f".format(overLowPct)}%",
                            color = Steel, fontSize = 12.sp,
                        )
                    }
                } else {
                    Text("첫 가격을 수집하고 있어요…", color = Stone, fontSize = 13.sp)
                }
            }
            MiniSparkline(s.spark)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SignalChip(s.signal)
            if (!w.active) Chip("⏸ 일시정지", Surface, Steel)
            Spacer(Modifier.weight(1f))
            timeAgo(s.latestObservedAt)?.let { Text("$it 확인", color = Stone, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun Chip(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun SignalChip(s: BuySignal) {
    when {
        s.daysToDeparture < 0 -> Chip("🗓 지난 일정", Surface, Steel) // departed — nothing left to buy
        s.recommendation == "NO_DATA" -> Chip("📡 수집 중", Surface, Steel)
        s.recommendation == "BUY" -> Chip("지금 사세요 · ${s.score}", SuccessBg, SuccessText)
        s.recommendation == "WAIT" -> Chip("기다려보세요 · ${s.score}", androidx.compose.ui.graphics.Color(0xFFEFF5FF), androidx.compose.ui.graphics.Color(0xFF1D4ED8))
        else -> Chip("고민해보세요 · ${s.score}", androidx.compose.ui.graphics.Color(0xFFFFF9EC), androidx.compose.ui.graphics.Color(0xFFB8860B))
    }
}

/** Tiny 30-day day-min sparkline; coral dot marks the window minimum. */
@Composable
private fun MiniSparkline(cells: List<SparkCell>) {
    if (cells.isEmpty()) return
    val amounts = cells.map { it.amount }
    val mn = amounts.min()
    val mx = amounts.max()
    val mnIdx = amounts.indexOf(mn)
    Canvas(Modifier.width(96.dp).height(30.dp)) {
        val n = cells.size
        val pad = 8f
        fun px(i: Int) = if (n == 1) size.width / 2 else pad + (size.width - 2 * pad) * i / (n - 1)
        fun py(v: Double): Float {
            val t = if (mx - mn < 1e-6) 0.5 else (mx - v) / (mx - mn)
            return pad + (size.height - 2 * pad) * t.toFloat()
        }
        if (n > 1) {
            val line = Path()
            amounts.forEachIndexed { i, v -> if (i == 0) line.moveTo(px(i), py(v)) else line.lineTo(px(i), py(v)) }
            drawPath(line, Blue, style = Stroke(width = 4f))
        }
        drawCircle(Coral, radius = 7f, center = Offset(px(mnIdx), py(mn)))
    }
}

private fun timeAgo(iso: String?): String? {
    val t = iso?.let { toInstant(it) } ?: return null
    val s = java.time.Duration.between(t, java.time.Instant.now()).seconds.coerceAtLeast(0)
    return when {
        s < 60 -> "방금 전"
        s < 3600 -> "${s / 60}분 전"
        s < 86400 -> "${s / 3600}시간 전"
        else -> "${s / 86400}일 전"
    }
}

fun cabinKo(c: String) = when (c) {
    "ECONOMY" -> "일반석"
    "PREMIUM_ECONOMY" -> "프리미엄"
    "BUSINESS" -> "비즈니스"
    "FIRST" -> "일등석"
    else -> c
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchDetailScreen(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var watch by remember { mutableStateOf<Watch?>(null) }
    var prices by remember { mutableStateOf<List<PricePoint>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
    var weather by remember { mutableStateOf<List<WeatherEstimate>>(emptyList()) }
    var calendar by remember { mutableStateOf<List<CalendarCell>>(emptyList()) }
    var signal by remember { mutableStateOf<BuySignal?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            watch = ApiClient.api.watch(id)
            prices = ApiClient.api.prices(id)
            alerts = ApiClient.api.alerts(id)
            weather = ApiClient.api.weather(id)
            calendar = ApiClient.api.calendar(id)
            signal = ApiClient.api.signal(id)
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(id) { reload() }

    val lowest = prices.minByOrNull { it.amount }
    Column(
        Modifier.fillMaxSize().background(White).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BackBar(onBack)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(watch?.origin ?: "", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("  →  ", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Coral)
                Text(watch?.destination ?: "", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            }
            watch?.let { w ->
                if (w.originKorean != null || w.destKorean != null) {
                    Text("${w.originKorean ?: w.origin} → ${w.destKorean ?: w.destination}", color = Steel, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                if (w.originName != null || w.destName != null) {
                    Text("${w.originName ?: w.origin} · ${w.destName ?: w.destination}", color = Stone, fontSize = 12.sp)
                }
            }
        }
        watch?.let { w ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    if (w.active) "⏸ 일시정지" else "▶ 추적 재개",
                    onClick = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    watch = ApiClient.api.updateWatch(w.id, UpdateWatchRequest(!w.active))
                                } catch (e: Exception) {
                                    error = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                )
                if (confirmDelete) {
                    PillButton(
                        "삭제 확인",
                        onClick = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    try {
                                        ApiClient.api.deleteWatch(w.id)
                                        onBack()
                                    } catch (e: Exception) {
                                        error = e.message
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        container = androidx.compose.ui.graphics.Color(0xFFD45656),
                        content = White,
                    )
                    PillButton("취소", onClick = { confirmDelete = false }, enabled = !busy, container = Surface, content = Ink)
                } else {
                    PillButton(
                        "삭제",
                        onClick = { confirmDelete = true },
                        container = androidx.compose.ui.graphics.Color(0xFFFDE8E8),
                        content = androidx.compose.ui.graphics.Color(0xFFD45656),
                    )
                }
            }
            if (!w.active) {
                Text(
                    "⏸ 일시정지된 워치예요 — 추적 재개를 누르면 다음 스윕부터 다시 모아요.",
                    color = Steel, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(12.dp)).padding(12.dp),
                )
            }
        }
        error?.let { Text("오류: $it", color = MaterialTheme.colorScheme.error) }

        // signature coral hero card
        Column(
            Modifier.fillMaxWidth().background(Coral, RoundedCornerShape(28.dp)).padding(28.dp),
        ) {
            Text("현재 최저가", color = White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(lowest?.let { "%,d".format(it.amount.toLong()) } ?: "—", color = White, fontSize = 46.sp, fontWeight = FontWeight.SemiBold)
                lowest?.let { Text("  ${it.currency}", color = White.copy(alpha = 0.85f), fontSize = 20.sp) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                lowest?.let { "${prices.size}회 관측 · 출발 ${it.departDate} · ${it.source}" } ?: "아직 관측 없음",
                color = White.copy(alpha = 0.9f), fontSize = 13.sp,
            )
            lowest?.deepLink?.let { link ->
                Spacer(Modifier.height(16.dp))
                PillButton("최저가 사이트로 이동  →", onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                }, container = White, content = Coral)
            }
        }

        // no signal for departed watches — nothing left to buy (web parity)
        signal?.let { if (it.recommendation != "NO_DATA" && it.daysToDeparture >= 0) BuySignalCard(it) }

        if (prices.isNotEmpty()) {
            SectionTitle("가격 인사이트")
            PriceInsights(prices, watch?.currency ?: "")
        }
        if (prices.size >= 2) {
            SectionTitle("가격 추이")
            PriceChart(prices)
        }
        if (calendar.isNotEmpty()) {
            SectionTitle("날짜별 최저가")
            Heatmap(calendar)
        }
        if (alerts.isNotEmpty()) {
            SectionTitle("알림 내역")
            alerts.forEach { a -> AlertRow(a, watch?.currency ?: "") }
        }
        if (weather.isNotEmpty()) {
            SectionTitle("도착지 날씨 — ${watch?.destination ?: ""}")
            weather.forEach { d -> WeatherRow(d) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun BuySignalCard(s: BuySignal) {
    val accent: androidx.compose.ui.graphics.Color
    val bg: androidx.compose.ui.graphics.Color
    val label: String
    when (s.recommendation) {
        "BUY" -> { accent = SuccessText; bg = SuccessBg; label = "🟢 지금 사세요" }
        "WAIT" -> { accent = androidx.compose.ui.graphics.Color(0xFF1D4ED8); bg = androidx.compose.ui.graphics.Color(0xFFEFF5FF); label = "🔵 기다려도 OK" }
        else -> { accent = androidx.compose.ui.graphics.Color(0xFFB8860B); bg = androidx.compose.ui.graphics.Color(0xFFFFF9EC); label = "🟡 고민해보세요" }
    }
    Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(16.dp)).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
            Text("딜 스코어 ${s.score}", color = Steel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(s.reason, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "현재 ${"%,d".format(s.currentAmount.toLong())} · 이보다 쌌던 적 ${s.percentile.toInt()}% · 추세 ${if (s.trendPct > 0) "+" else ""}${s.trendPct}% · 출발 ${s.daysToDeparture}일 전",
            color = Steel, fontSize = 12.sp,
        )
    }
}

@Composable
private fun PriceInsights(prices: List<PricePoint>, currency: String) {
    val lo = prices.minByOrNull { it.amount } ?: return
    val cur = prices.maxByOrNull { it.observedAt } ?: prices.last()
    val diff = cur.amount - lo.amount
    val pct = if (lo.amount != 0.0) diff / lo.amount * 100 else 0.0
    val days = daysBetween(lo.observedAt, cur.observedAt)
    fun won(v: Double) = "%,d".format(v.toLong())
    Column(Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(14.dp))) {
        InsightRow("역대 최저", "${won(lo.amount)} $currency", "${lo.observedAt.take(10)} 기록", null)
        InsightRow("현재가", "${won(cur.amount)} $currency", if (diff <= 0) "역대 최저가 갱신 중 🎉" else "최저보다 +${won(diff)} (+${"%.1f".format(pct)}%)", if (diff <= 0) SuccessText else Coral)
        InsightRow("최저가 미갱신", "${days}일", if (days == 0L) "오늘 최저가 기록" else "${days}일째 안 옴", null)
    }
}

@Composable
private fun InsightRow(label: String, value: String, sub: String, subColor: androidx.compose.ui.graphics.Color?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Steel, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(sub, color = subColor ?: Stone, fontSize = 12.5.sp, fontWeight = if (subColor != null) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

private fun toInstant(s: String): java.time.Instant? = try {
    java.time.Instant.parse(s)
} catch (e: Exception) {
    try {
        java.time.OffsetDateTime.parse(s).toInstant()
    } catch (e2: Exception) {
        null
    }
}

private fun daysBetween(loIso: String, curIso: String): Long {
    val a = toInstant(loIso)
    val b = toInstant(curIso)
    return if (a != null && b != null) java.time.Duration.between(a, b).toDays().coerceAtLeast(0) else 0
}

@Composable
private fun AlertRow(a: Alert, currency: String) {
    val coral = androidx.compose.ui.graphics.Color(0xFFFF5530)
    val rowBg = if (a.mistakeFare) androidx.compose.ui.graphics.Color(0xFFFFF3F0) else Surface
    val rowMod = if (a.mistakeFare) {
        Modifier.fillMaxWidth().background(rowBg, RoundedCornerShape(12.dp))
            .border(1.dp, coral, RoundedCornerShape(12.dp)).padding(14.dp)
    } else {
        Modifier.fillMaxWidth().background(rowBg, RoundedCornerShape(12.dp)).padding(14.dp)
    }
    Row(
        rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.mistakeFare) {
                Box(Modifier.background(coral, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("🔥 에러요금 의심", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                }
            }
            Text("🎉 ${"%,d".format(a.newLow.toLong())} $currency", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            a.notifications.forEach { n ->
                val sent = n.status == "SENT"
                Box(Modifier.background(if (sent) SuccessBg else Surface, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(
                        "${if (n.channel == "PUSH") "📱" else "✉"} ${if (sent) "✓" else n.status}",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sent) SuccessText else Steel,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherRow(d: WeatherEstimate) {
    val src = if (d.source == "FORECAST") "예보" else "평년값"
    val max = d.tempMaxC?.toInt()?.toString() ?: "—"
    val min = d.tempMinC?.toInt()?.toString() ?: "—"
    Text("${d.date}   $max° / $min°   ☔ ${d.precipProbPct ?: "—"}%   [$src]", color = Steel, fontSize = 14.sp)
}

/** Down-sample to keep the trend readable with many jittery polls; keep first, last, all-time low. */
private fun prepareChart(points: List<PricePoint>, max: Int = 48): List<PricePoint> {
    if (points.size <= max) return points
    val minIdx = points.indices.minByOrNull { points[it].amount } ?: 0
    val keep = sortedSetOf(0, points.size - 1, minIdx)
    val step = (points.size - 1).toDouble() / (max - 1)
    for (i in 0 until max) keep.add(Math.round(i * step).toInt())
    return keep.map { points[it] }
}

@Composable
private fun PriceChart(prices: List<PricePoint>) {
    val pts = prepareChart(prices)
    val amounts = pts.map { it.amount }
    val min = amounts.min()
    val max = amounts.max()
    val lowIdx = amounts.indexOf(min)
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val n = amounts.size
        val w = size.width
        val h = size.height
        val padV = 16f
        fun px(i: Int) = if (n == 1) w / 2 else w * i / (n - 1)
        fun py(v: Double): Float {
            val t = if (max - min < 1e-6) 0.5 else (v - min) / (max - min)
            return padV + (h - 2 * padV) * (1f - t.toFloat())
        }
        val line = Path()
        amounts.forEachIndexed { i, v -> if (i == 0) line.moveTo(px(i), py(v)) else line.lineTo(px(i), py(v)) }
        val area = Path()
        area.addPath(line)
        area.lineTo(px(n - 1), h)
        area.lineTo(px(0), h)
        area.close()
        drawPath(area, Blue.copy(alpha = 0.07f))
        drawPath(line, Blue, style = Stroke(width = 6f))
        amounts.forEachIndexed { i, v ->
            val low = i == lowIdx
            drawCircle(if (low) Coral else Blue, radius = if (low) 10f else 6f, center = Offset(px(i), py(v)))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Heatmap(cells: List<CalendarCell>) {
    val amounts = cells.map { it.lowestAmount }
    val min = amounts.min()
    val max = amounts.max()
    val cheap = androidx.compose.ui.graphics.Color(0xFF16A34A)
    val pricey = androidx.compose.ui.graphics.Color(0xFFE5484D)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { c ->
            val t = if (max - min < 1e-6) 0f else ((c.lowestAmount - min) / (max - min)).toFloat()
            val best = c.lowestAmount == min
            Column(
                Modifier
                    .width(96.dp)
                    .background(lerp(cheap, pricey, t), RoundedCornerShape(12.dp))
                    .then(if (best) Modifier.border(2.dp, Ink, RoundedCornerShape(12.dp)) else Modifier)
                    .padding(10.dp),
            ) {
                Text(if (best) "최저" else "", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(c.date.drop(5), color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("%,d".format(c.lowestAmount.toLong()), color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
