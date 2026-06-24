'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import type { CreateWatchInput, TripType, Watch } from '@/lib/api';

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
      <CreateForm onCreated={refresh} />
      <section>
        <h2>내 워치</h2>
        {loading && <p className="muted">불러오는 중…</p>}
        {error && <p className="error">백엔드 연결 실패: {error}</p>}
        {!loading && !error && watches.length === 0 && (
          <p className="muted">아직 워치가 없어요. 위에서 하나 만들어 보세요.</p>
        )}
        <ul className="cards">
          {watches.map((w) => (
            <li key={w.id} className="card">
              <Link href={`/watches/${w.id}`} className="route">
                {w.origin} → {w.destination}
              </Link>
              <div className="meta">
                {w.departDateFrom}
                {w.departDateTo !== w.departDateFrom ? ` ~ ${w.departDateTo}` : ''} ·{' '}
                {w.tripType === 'ROUND_TRIP' ? '왕복' : '편도'} · {w.cabin} · 알림 {w.alertRule}
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function CreateForm({ onCreated }: { onCreated: () => void }) {
  const [form, setForm] = useState<CreateWatchInput>({
    userRef: 'demo-user',
    origin: 'ICN',
    destination: 'NRT',
    tripType: 'ONE_WAY',
    departDateFrom: '',
    departDateTo: '',
  });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await api.createWatch({ ...form, departDateTo: form.departDateTo || form.departDateFrom });
      onCreated();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="panel" onSubmit={submit}>
      <h2>워치 만들기</h2>
      <div className="row">
        <label>
          출발 (IATA)
          <input
            value={form.origin}
            onChange={(e) => setForm({ ...form, origin: e.target.value })}
            maxLength={3}
            placeholder="ICN"
          />
        </label>
        <label>
          도착 (IATA)
          <input
            value={form.destination}
            onChange={(e) => setForm({ ...form, destination: e.target.value })}
            maxLength={3}
            placeholder="NRT"
          />
        </label>
        <label>
          여정
          <select
            value={form.tripType}
            onChange={(e) => setForm({ ...form, tripType: e.target.value as TripType })}
          >
            <option value="ONE_WAY">편도</option>
            <option value="ROUND_TRIP">왕복</option>
          </select>
        </label>
      </div>
      <div className="row">
        <label>
          출발일 (부터)
          <input
            type="date"
            value={form.departDateFrom}
            onChange={(e) => setForm({ ...form, departDateFrom: e.target.value })}
            required
          />
        </label>
        <label>
          출발일 (까지 · 유연)
          <input
            type="date"
            value={form.departDateTo}
            onChange={(e) => setForm({ ...form, departDateTo: e.target.value })}
          />
        </label>
      </div>
      {err && <p className="error">{err}</p>}
      <button disabled={busy || !form.departDateFrom}>{busy ? '만드는 중…' : '워치 생성'}</button>
    </form>
  );
}
