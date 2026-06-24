'use client';

import { useState } from 'react';
import { api } from '@/lib/api';
import type { Airport, Cabin, TripType } from '@/lib/api';
import AirportField from './AirportField';
import DateRangeField from './DateRangeField';
import TimeField from './TimeField';
import PaxCabinField from './PaxCabinField';

export default function SearchBar({ onCreated }: { onCreated: () => void }) {
  const [origin, setOrigin] = useState<Airport | null>(null);
  const [dest, setDest] = useState<Airport | null>(null);
  const [tripType, setTripType] = useState<TripType>('ONE_WAY');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [timeFrom, setTimeFrom] = useState('');
  const [timeTo, setTimeTo] = useState('');
  const [retTimeFrom, setRetTimeFrom] = useState('');
  const [retTimeTo, setRetTimeTo] = useState('');
  const [passengers, setPassengers] = useState(1);
  const [cabin, setCabin] = useState<Cabin>('ECONOMY');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const round = tripType === 'ROUND_TRIP';

  function changeTrip(t: TripType) {
    setTripType(t);
    setFrom('');
    setTo('');
    if (t === 'ONE_WAY') {
      setRetTimeFrom('');
      setRetTimeTo('');
    }
  }

  function swap() {
    setOrigin(dest);
    setDest(origin);
  }

  async function submit() {
    if (!origin || !dest || !from) {
      setErr('출발·도착 공항과 가는 날짜를 선택하세요.');
      return;
    }
    if (round && !to) {
      setErr('오는 날짜도 선택하세요.');
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      await api.createWatch({
        userRef: 'demo-user',
        origin: origin.iata,
        destination: dest.iata,
        tripType,
        departDateFrom: from,
        departDateTo: round ? from : to || from,
        returnDateFrom: round ? to : undefined,
        returnDateTo: round ? to : undefined,
        departTimeFrom: timeFrom || undefined,
        departTimeTo: timeTo || undefined,
        returnTimeFrom: round ? retTimeFrom || undefined : undefined,
        returnTimeTo: round ? retTimeTo || undefined : undefined,
        passengers,
        cabin,
      });
      setDest(null);
      setFrom('');
      setTo('');
      onCreated();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="searchbar">
      <div className="sb-row">
        <AirportField label="출발지" value={origin} onSelect={setOrigin} placeholder="서울 ICN" />
        <button type="button" className="swap" onClick={swap} aria-label="출도착 바꾸기">⇄</button>
        <AirportField label="도착지" value={dest} onSelect={setDest} placeholder="도쿄 NRT" />
        <div className="sb-trip">
          <button type="button" className={!round ? 'active' : ''} onClick={() => changeTrip('ONE_WAY')}>
            편도
          </button>
          <button type="button" className={round ? 'active' : ''} onClick={() => changeTrip('ROUND_TRIP')}>
            왕복
          </button>
        </div>
      </div>

      <DateRangeField
        from={from}
        to={to}
        roundTrip={round}
        onChange={(f, t) => {
          setFrom(f);
          setTo(t);
        }}
      />

      <div className="sb-row2">
        <div className="sb-field">
          <div className="seg-label">{round ? '가는 편 출발 시간대' : '출발 시간대'}</div>
          <TimeField
            from={timeFrom}
            to={timeTo}
            onChange={(f, t) => {
              setTimeFrom(f);
              setTimeTo(t);
            }}
          />
        </div>
        {round && (
          <div className="sb-field">
            <div className="seg-label">오는 편 출발 시간대</div>
            <TimeField
              from={retTimeFrom}
              to={retTimeTo}
              onChange={(f, t) => {
                setRetTimeFrom(f);
                setRetTimeTo(t);
              }}
            />
          </div>
        )}
        <div className="sb-field">
          <div className="seg-label">여행자 · 좌석</div>
          <PaxCabinField
            passengers={passengers}
            cabin={cabin}
            onChange={(p, c) => {
              setPassengers(p);
              setCabin(c);
            }}
          />
        </div>
      </div>

      {err && <p className="error">{err}</p>}
      <div className="sb-actions">
        <button className="btn btn-primary lg" onClick={submit} disabled={busy}>
          {busy ? '만드는 중…' : '＋ 워치 만들기'}
        </button>
      </div>
    </div>
  );
}
