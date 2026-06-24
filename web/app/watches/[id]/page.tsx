'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import type { PollResult, PricePoint, Watch } from '@/lib/api';
import PriceChart from '@/components/PriceChart';

export default function WatchDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;

  const [watch, setWatch] = useState<Watch | null>(null);
  const [prices, setPrices] = useState<PricePoint[]>([]);
  const [poll, setPoll] = useState<PollResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [w, p] = await Promise.all([api.getWatch(id), api.getPrices(id)]);
      setWatch(w);
      setPrices(p);
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

  return (
    <div className="stack">
      <Link href="/" className="muted">← 목록</Link>
      {error && <p className="error">{error}</p>}
      {watch && (
        <>
          <div className="head">
            <h2>{watch.origin} → {watch.destination}</h2>
            <button onClick={doPoll} disabled={busy}>{busy ? '폴링 중…' : '지금 폴'}</button>
          </div>
          <div className="meta">
            {watch.departDateFrom}
            {watch.departDateTo !== watch.departDateFrom ? ` ~ ${watch.departDateTo}` : ''} ·{' '}
            {watch.tripType === 'ROUND_TRIP' ? '왕복' : '편도'} · {watch.cabin} · 알림 {watch.alertRule}
          </div>

          <div className="stats">
            <div className="stat">
              <span className="k">현재 최저가</span>
              <span className="v">{fmt(lowest)} {watch.currency}</span>
            </div>
            <div className="stat">
              <span className="k">관측 수</span>
              <span className="v">{prices.length}</span>
            </div>
            {lowestPp?.deepLink && (
              <a className="book" href={lowestPp.deepLink} target="_blank" rel="noreferrer">
                최저가 사이트로 이동 →
              </a>
            )}
          </div>

          {poll && (
            <p className={poll.newLow ? 'flash low' : 'flash'}>
              {poll.newLow
                ? `🎉 새 최저가! ${fmt(poll.lowestAmount)} ${poll.lowestCurrency ?? ''}`
                : `갱신 없음 (현재 최저 ${fmt(poll.lowestAmount)})`}
            </p>
          )}

          <section className="panel">
            <h3>가격 추이</h3>
            <PriceChart points={prices} />
          </section>
        </>
      )}
    </div>
  );
}
