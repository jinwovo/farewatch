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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.portfolio.farewatch.api.Watch
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
    var watches by remember { mutableStateOf<List<Watch>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(refreshKey) {
        loading = true
        try {
            watches = ApiClient.api.watches()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }
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
            Spacer(Modifier.height(16.dp))
            when {
                loading -> CircularProgressIndicator(color = Coral)
                error != null -> Text("백엔드 연결 실패: $error", color = MaterialTheme.colorScheme.error)
                watches.isEmpty() -> Text("워치가 없습니다. ＋ 워치 검색으로 만들어보세요.", color = Stone)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(watches) { w -> WatchCard(w) { onOpen(w.id) } }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
fun WatchCard(w: Watch, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.origin, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("  →  ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Coral)
            Text(w.destination, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        if (w.originKorean != null || w.destKorean != null) {
            Spacer(Modifier.height(2.dp))
            Text("${w.originKorean ?: w.origin} → ${w.destKorean ?: w.destination}", color = Steel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        val window = if (w.departDateTo != w.departDateFrom) "${w.departDateFrom} ~ ${w.departDateTo}" else w.departDateFrom
        val trip = if (w.tripType == "ROUND_TRIP") "왕복" else "편도"
        Spacer(Modifier.height(6.dp))
        Text("$window · $trip · ${cabinKo(w.cabin)} · 알림 ${w.alertRule}", color = Steel, fontSize = 14.sp)
    }
}

fun cabinKo(c: String) = when (c) {
    "ECONOMY" -> "일반석"
    "PREMIUM_ECONOMY" -> "프리미엄"
    "BUSINESS" -> "비즈니스"
    "FIRST" -> "일등석"
    else -> c
}

@Composable
fun WatchDetailScreen(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var watch by remember { mutableStateOf<Watch?>(null) }
    var prices by remember { mutableStateOf<List<PricePoint>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
    var weather by remember { mutableStateOf<List<WeatherEstimate>>(emptyList()) }
    var calendar by remember { mutableStateOf<List<CalendarCell>>(emptyList()) }
    var signal by remember { mutableStateOf<BuySignal?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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

        signal?.let { if (it.recommendation != "NO_DATA") BuySignalCard(it) }

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
