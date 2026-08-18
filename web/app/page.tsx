'use client';

import { useEffect, useMemo, useState, type MouseEvent } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import type { AlertFeedItem, AlertRule, BuySignal, WatchSummary } from '@/lib/api';
import { timeAgo } from '@/lib/time';
import SearchBar from '@/components/SearchBar';
import Sparkline from '@/components/Sparkline';

type SortKey = 'recent' | 'price' | 'score';

const fmt = (v: number) => Math.round(v).toLocaleString('ko-KR');
const shortDate = (s: string) => s.slice(5).replace('-', '.');
const ruleShort = (r: AlertRule) => (r === 'BELOW_THRESHOLD' ? '목표가 도달' : r === 'DROP_PCT' ? '급락 감지' : '새 최저가');

function SignalChip({ signal }: { signal: BuySignal }) {
  if (signal.daysToDeparture < 0) {
    return <span className="chip chip-nodata">🗓 지난 일정</span>; // departed — nothing left to buy
  }
  if (signal.recommendation === 'NO_DATA') {
    return <span className="chip chip-nodata">📡 수집 중</span>;
  }
  const label =
    signal.recommendation === 'BUY' ? '지금 사세요' : signal.recommendation === 'WAIT' ? '기다려보세요' : '고민해보세요';
  return (
    <span className={`chip chip-${signal.recommendation.toLowerCase()}`}>
      {label} · {signal.score}
    </span>
  );
}

export default function HomePage() {
  const [items, setItems] = useState<WatchSummary[]>([]);
  const [feed, setFeed] = useState<AlertFeedItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sort, setSort] = useState<SortKey>('recent');

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const [sums, alerts] = await Promise.all([
        api.getSummaries(),
        api.getAlertFeed(8).catch(() => [] as AlertFeedItem[]), // feed failure must not blank the page
      ]);
      setItems(sums);
      setFeed(alerts);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function toggleActive(e: MouseEvent, s: WatchSummary) {
    e.preventDefault(); // the button lives inside the card <Link>
    e.stopPropagation();
    const next = !s.watch.active;
    setItems((prev) =>
      prev.map((it) => (it.watch.id === s.watch.id ? { ...it, watch: { ...it.watch, active: next } } : it)),
    );
    try {
      await api.updateWatch(s.watch.id, { active: next });
    } catch {
      refresh(); // roll back the optimistic flip
    }
  }

  const sorted = useMemo(() => {
    const arr = [...items];
    if (sort === 'price') {
      arr.sort((a, b) => (a.latestAmount ?? Infinity) - (b.latestAmount ?? Infinity));
    } else if (sort === 'score') {
      // expired watches keep their last score but there is nothing left to buy — sink them
      const score = (s: WatchSummary) =>
        s.signal.recommendation === 'NO_DATA' || s.signal.daysToDeparture < 0 ? -1 : s.signal.score;
      arr.sort((a, b) => score(b) - score(a));
    }
    return arr; // 'recent': server order (newest first)
  }, [items, sort]);

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
          {items.length > 0 && (
            <div className="list-tools">
              <span className="muted">{items.length}개</span>
              <div className="sorttabs" role="tablist" aria-label="정렬">
                {(
                  [
                    ['recent', '최신순'],
                    ['price', '가격순'],
                    ['score', '딜점수순'],
                  ] as [SortKey, string][]
                ).map(([k, label]) => (
                  <button key={k} type="button" className={sort === k ? 'active' : ''} onClick={() => setSort(k)}>
                    {label}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {error && <p className="error">백엔드 연결 실패: {error}</p>}
        {!loading && !error && items.length === 0 && (
          <p className="muted">아직 워치가 없어요. 위에서 하나 만들어 보세요.</p>
        )}

        {loading ? (
          <ul className="cards">
            {[0, 1, 2].map((i) => (
              <li key={i}>
                <div className="wcard-skel" />
              </li>
            ))}
          </ul>
        ) : (
          <ul className="cards">
            {sorted.map((s) => {
              const w = s.watch;
              const isRound = w.tripType === 'ROUND_TRIP';
              const cities =
                w.originKorean || w.destKorean
                  ? `${w.originKorean ?? w.originName ?? w.origin} → ${w.destKorean ?? w.destName ?? w.destination}`
                  : null;
              const dates =
                isRound && w.returnDateFrom
                  ? `${shortDate(w.departDateFrom)} → ${shortDate(w.returnDateFrom)}`
                  : `${shortDate(w.departDateFrom)}${w.departDateTo !== w.departDateFrom ? ` ~ ${shortDate(w.departDateTo)}` : ''}`;
              const hasSignal = s.signal.recommendation !== 'NO_DATA' && s.signal.daysToDeparture >= 0;
              const atLow = hasSignal && s.latestAmount != null && s.latestAmount <= s.signal.lowestAmount;
              const overLowPct =
                hasSignal && s.latestAmount != null && s.signal.lowestAmount > 0
                  ? ((s.latestAmount - s.signal.lowestAmount) / s.signal.lowestAmount) * 100
                  : null;
              const ago = timeAgo(s.latestObservedAt);
              return (
                <li key={w.id}>
                  <Link href={`/watches/${w.id}`} className={`watch-card wcard${w.active ? '' : ' inactive'}`}>
                    <div className="wcard-top">
                      <div className="wcard-head">
                        <div className="route">
                          {w.origin}
                          <span className="arrow">→</span>
                          {w.destination}
                        </div>
                        {cities && <div className="meta wcard-cities">{cities}</div>}
                        <div className="meta">
                          {dates} · {isRound ? '왕복' : '편도'}
                        </div>
                      </div>
                      <button
                        type="button"
                        className="iconbtn"
                        title={w.active ? '추적 일시정지' : '추적 재개'}
                        aria-label={w.active ? '추적 일시정지' : '추적 재개'}
                        onClick={(e) => toggleActive(e, s)}
                      >
                        {w.active ? '⏸' : '▶'}
                      </button>
                    </div>

                    <div className="wcard-mid">
                      <div className="wcard-price">
                        {s.latestAmount != null ? (
                          <>
                            <b>{fmt(s.latestAmount)}</b>
                            <i>{w.currency}</i>
                            {atLow ? (
                              <span className="wcard-vs low">지금이 역대 최저가 🔥</span>
                            ) : overLowPct != null ? (
                              <span className="wcard-vs">
                                역대 최저 {fmt(s.signal.lowestAmount)} · +{overLowPct.toFixed(1)}%
                              </span>
                            ) : null}
                          </>
                        ) : (
                          <span className="wcard-collecting">첫 가격을 수집하고 있어요…</span>
                        )}
                      </div>
                      <Sparkline cells={s.spark} />
                    </div>

                    <div className="wcard-foot">
                      <SignalChip signal={s.signal} />
                      {!w.active && <span className="chip chip-paused">⏸ 일시정지</span>}
                      {ago && <span className="ago">{ago} 확인</span>}
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {feed.length > 0 && (
        <section>
          <div className="section-head">
            <h2>최근 알림</h2>
            <span className="muted">전체 워치</span>
          </div>
          <ul className="feed">
            {feed.map((a) => (
              <li key={a.id}>
                <Link href={`/watches/${a.watchId}`} className="feed-row">
                  <span className="feed-route">
                    {a.origin}
                    <span className="arrow">→</span>
                    {a.destination}
                  </span>
                  <span className="feed-main">
                    {a.mistakeFare && <span className="mistake-badge">🔥 에러요금 의심</span>}
                    🎉 {fmt(a.newLow)} {a.currency}
                    {a.previousLow != null && <i> (이전 {fmt(a.previousLow)})</i>}
                  </span>
                  <span className="feed-side">
                    <span className="chip-rule">{ruleShort(a.rule)}</span>
                    <span className="ago">{timeAgo(a.createdAt)}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
