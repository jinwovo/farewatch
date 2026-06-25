'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import type { Alert, BuySignal, CalendarCell, PricePoint, Watch, WeatherEstimate } from '@/lib/api';
import PriceChart from '@/components/PriceChart';
import PriceHeatmap from '@/components/PriceHeatmap';

export default function WatchDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;

  const [watch, setWatch] = useState<Watch | null>(null);
  const [prices, setPrices] = useState<PricePoint[]>([]);
  const [calendar, setCalendar] = useState<CalendarCell[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [weather, setWeather] = useState<WeatherEstimate[]>([]);
  const [signal, setSignal] = useState<BuySignal | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [w, p, c, al, wx, sig] = await Promise.all([
        api.getWatch(id),
        api.getPrices(id),
        api.getCalendar(id),
        api.getAlerts(id),
        api.getWeather(id),
        api.getSignal(id),
      ]);
      setWatch(w);
      setPrices(p);
      setCalendar(c);
      setAlerts(al);
      setWeather(wx);
      setSignal(sig);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const fmt = (v?: number | null) => (v == null ? '—' : v.toLocaleString('ko-KR'));
  const fmtT = (t?: string | null) => (t ? t.slice(0, 5) : '');
  const recLabel = (r: string) => (r === 'BUY' ? '🟢 지금 사세요' : r === 'WAIT' ? '🔵 기다려도 OK' : '🟡 고민해보세요');
  const lowest = prices.length ? Math.min(...prices.map((p) => p.amount)) : null;
  const lowestPp = lowest != null ? prices.find((p) => p.amount === lowest) ?? null : null;
  const isRound = watch?.tripType === 'ROUND_TRIP';
  const dateStr = watch
    ? isRound && watch.returnDateFrom
      ? `${watch.departDateFrom} → ${watch.returnDateFrom}`
      : `${watch.departDateFrom}${watch.departDateTo !== watch.departDateFrom ? ` ~ ${watch.departDateTo}` : ''}`
    : '';
  const outTime = watch?.departTimeFrom && watch?.departTimeTo ? ` · 가는편 ${fmtT(watch.departTimeFrom)}–${fmtT(watch.departTimeTo)}` : '';
  const retTime = isRound && watch?.returnTimeFrom && watch?.returnTimeTo ? ` · 오는편 ${fmtT(watch.returnTimeFrom)}–${fmtT(watch.returnTimeTo)}` : '';

  return (
    <div className="stack">
      <Link href="/" className="muted">← 목록</Link>
      {error && <p className="error">{error}</p>}
      {watch && (
        <>
          <div className="detail-head">
            <div>
              <h1>
                {watch.origin} <span className="arrow">→</span> {watch.destination}
              </h1>
              <div className="meta">
                {dateStr} · {isRound ? '왕복' : '편도'} · {watch.cabin} · 성인 {watch.passengers}
                {outTime}
                {retTime} · 알림 {watch.alertRule}
              </div>
            </div>
          </div>

          <div className="lowprice">
            <div className="k">현재 최저가</div>
            <div className="v">
              {fmt(lowest)}
              <span className="cur">{watch.currency}</span>
            </div>
            <div className="sub">
              {prices.length}회 관측
              {lowestPp ? ` · 출발 ${lowestPp.departDate}` : ''}
              {lowestPp ? ` · ${lowestPp.source}` : ''}
            </div>
            {lowestPp?.deepLink && (
              <div className="book">
                <a className="btn" href={lowestPp.deepLink} target="_blank" rel="noreferrer">
                  최저가 사이트로 이동 →
                </a>
              </div>
            )}
          </div>

          {signal && signal.recommendation !== 'NO_DATA' && (
            <section className={`signal sig-${signal.recommendation.toLowerCase()}`}>
              <div className="sig-head">
                <span className="sig-badge">{recLabel(signal.recommendation)}</span>
                <span className="sig-score">
                  딜 스코어 <b>{signal.score}</b>
                  <i>/100</i>
                </span>
              </div>
              <p className="sig-reason">{signal.reason}</p>
              <div className="sig-facts">
                현재 {fmt(signal.currentAmount)} {watch.currency} · 이보다 쌌던 적 {Math.round(signal.percentile)}% · 추세{' '}
                {signal.trendPct > 0 ? '+' : ''}
                {signal.trendPct}% · 변동성 {signal.volatilityPct}% · 출발 {signal.daysToDeparture}일 전
              </div>
            </section>
          )}

          {prices.length > 0 &&
            (() => {
              const lo = prices.reduce((a, b) => (b.amount < a.amount ? b : a));
              const cur = prices[prices.length - 1];
              const diff = cur.amount - lo.amount;
              const pct = lo.amount ? (diff / lo.amount) * 100 : 0;
              const days = Math.max(0, Math.round((new Date(cur.observedAt).getTime() - new Date(lo.observedAt).getTime()) / 86400000));
              return (
                <section className="card">
                  <h3 className="card-title">가격 인사이트</h3>
                  <div className="insights">
                    <div className="ins">
                      <span className="ins-k">역대 최저</span>
                      <span className="ins-v">
                        {fmt(lo.amount)} <i>{watch.currency}</i>
                      </span>
                      <span className="ins-s">{lo.observedAt.slice(0, 10)} 기록</span>
                    </div>
                    <div className="ins">
                      <span className="ins-k">현재가</span>
                      <span className="ins-v">
                        {fmt(cur.amount)} <i>{watch.currency}</i>
                      </span>
                      <span className={`ins-s ${diff <= 0 ? 'low' : 'high'}`}>
                        {diff <= 0 ? '역대 최저가 갱신 중 🎉' : `최저보다 +${fmt(diff)} (+${pct.toFixed(1)}%)`}
                      </span>
                    </div>
                    <div className="ins">
                      <span className="ins-k">최저가 미갱신</span>
                      <span className="ins-v">
                        {days}
                        <i>일</i>
                      </span>
                      <span className="ins-s">{days === 0 ? '오늘 최저가 기록' : `${days}일째 안 옴`}</span>
                    </div>
                  </div>
                </section>
              );
            })()}

          {alerts.length > 0 && (
            <section className="card">
              <h3 className="card-title">알림 내역</h3>
              <ul className="alerts">
                {alerts.map((a) => (
                  <li key={a.id} className={`alert-row${a.mistakeFare ? ' mistake' : ''}`}>
                    <span className="alert-main">
                      {a.mistakeFare && <span className="mistake-badge">🔥 에러요금 의심</span>}
                      🎉 새 최저가 {a.newLow.toLocaleString('ko-KR')} {watch.currency}
                      {a.previousLow ? ` (이전 ${a.previousLow.toLocaleString('ko-KR')})` : ''}
                    </span>
                    <span className="alert-channels">
                      {a.notifications.map((n) => (
                        <span
                          key={n.channel}
                          className={`chan ${n.status === 'SENT' ? 'sent' : n.status === 'FAILED' ? 'failed' : 'pending'}`}
                        >
                          {n.channel === 'PUSH' ? '📱 푸시' : '✉ 이메일'}{' '}
                          {n.status === 'SENT' ? '✓' : n.status === 'FAILED' ? '✕' : '…'}
                        </span>
                      ))}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {weather.length > 0 && (
            <section className="card">
              <h3 className="card-title">도착지 날씨 — {watch.destination}</h3>
              <div className="weather">
                {weather.map((d) => (
                  <div key={d.date} className="wx-day">
                    <span className="wx-date">{d.date.slice(5)}</span>
                    <span className="wx-temp">
                      {d.tempMaxC != null ? `${Math.round(d.tempMaxC)}°` : '—'}
                      <span className="wx-min"> / {d.tempMinC != null ? `${Math.round(d.tempMinC)}°` : '—'}</span>
                    </span>
                    <span className="wx-rain">☔ {d.precipProbPct ?? '—'}%</span>
                    <span className={`wx-src ${d.source === 'FORECAST' ? 'fc' : 'cn'}`}>
                      {d.source === 'FORECAST' ? '예보' : '평년값'}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          )}

          <section className="card">
            <h3 className="card-title">날짜별 최저가</h3>
            <PriceHeatmap cells={calendar} />
          </section>

          <section className="card">
            <h3 className="card-title">가격 추이</h3>
            <PriceChart points={prices} />
          </section>
        </>
      )}
    </div>
  );
}
