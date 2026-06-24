'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import type { Watch } from '@/lib/api';
import SearchBar from '@/components/SearchBar';

export default function HomePage() {
  const [watches, setWatches] = useState<Watch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      setWatches(await api.listWatches());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  return (
    <div className="stack">
      <section className="hero">
        <h1>
          원하는 항공편의 <span className="accent">최저가</span>를
          <br />
          매시간 대신 지켜봐요.
        </h1>
        <p>출발·도착·날짜·시간대를 등록하면 가격을 추적해, 최저가가 깨질 때 알려주고 가장 싼 사이트로 바로 보내드려요.</p>
      </section>

      <SearchBar onCreated={refresh} />

      <section>
        <div className="section-head">
          <h2>내 워치</h2>
          {watches.length > 0 && <span className="muted">{watches.length}개</span>}
        </div>
        {loading && <p className="muted">불러오는 중…</p>}
        {error && <p className="error">백엔드 연결 실패: {error}</p>}
        {!loading && !error && watches.length === 0 && (
          <p className="muted">아직 워치가 없어요. 위에서 하나 만들어 보세요.</p>
        )}
        <ul className="cards">
          {watches.map((w) => (
            <li key={w.id}>
              <Link href={`/watches/${w.id}`} className="watch-card">
                <div className="route">
                  {w.origin}
                  <span className="arrow">→</span>
                  {w.destination}
                </div>
                <div className="meta">
                  {w.departDateFrom}
                  {w.departDateTo !== w.departDateFrom ? ` ~ ${w.departDateTo}` : ''}
                </div>
                <div className="meta">
                  {w.tripType === 'ROUND_TRIP' ? '왕복' : '편도'} · {w.cabin} · 알림 {w.alertRule}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
