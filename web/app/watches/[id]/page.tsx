'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import type { CalendarCell, PollResult, PricePoint, Watch } from '@/lib/api';
import PriceChart from '@/components/PriceChart';
import PriceHeatmap from '@/components/PriceHeatmap';

export default function WatchDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;

  const [watch, setWatch] = useState<Watch | null>(null);
  const [prices, setPrices] = useState<PricePoint[]>([]);
  const [calendar, setCalendar] = useState<CalendarCell[]>([]);
  const [poll, setPoll] = useState<PollResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [w, p, c] = await Promise.all([api.getWatch(id), api.getPrices(id), api.getCalendar(id)]);
      setWatch(w);
      setPrices(p);
      setCalendar(c);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function doPoll() {
    setBusy(true);
    setError(null);
    try {
      const result = await api.pollWatch(id);
      setPoll(result);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  const fmt = (v?: number | null) => (v == null ? '—' : v.toLocaleString('ko-KR'));
  const lowest = prices.length ? Math.min(...prices.map((p) => p.amount)) : null;
  const lowestPp = lowest != null ? prices.find((p) => p.amount === lowest) ?? null : null;
  const timeLabel =
    watch?.departTimeFrom && watch?.departTimeTo
      ? ` · ${watch.departTimeFrom.slice(0, 5)}–${watch.departTimeTo.slice(0, 5)}`
      : '';

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
                {watch.departDateFrom}
                {watch.departDateTo !== watch.departDateFrom ? ` ~ ${watch.departDateTo}` : ''} ·{' '}
                {watch.tripType === 'ROUND_TRIP' ? '왕복' : '편도'} · {watch.cabin} · 성인 {watch.passengers}
                {timeLabel} · 알림 {watch.alertRule}
              </div>
            </div>
            <button className="btn btn-primary" onClick={doPoll} disabled={busy}>
              {busy ? '폴링 중…' : '지금 폴 ↻'}
            </button>
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

          {poll && (
            <p className={poll.newLow ? 'flash low' : 'flash'}>
              {poll.newLow
                ? `🎉 새 최저가 갱신! ${fmt(poll.lowestAmount)} ${poll.lowestCurrency ?? ''}`
                : `이번 폴은 갱신 없음 · 현재 최저 ${fmt(poll.lowestAmount)} ${poll.lowestCurrency ?? ''}`}
            </p>
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
