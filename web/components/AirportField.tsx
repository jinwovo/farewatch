'use client';

import { useEffect, useRef, useState } from 'react';
import { api } from '@/lib/api';
import type { Airport, NearbyAirport } from '@/lib/api';

export default function AirportField({
  label,
  value,
  onSelect,
  placeholder,
}: {
  label: string;
  value: Airport | null;
  onSelect: (a: Airport) => void;
  placeholder?: string;
}) {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const [results, setResults] = useState<Airport[]>([]);
  const [nearby, setNearby] = useState<NearbyAirport[]>([]);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open || q.trim().length === 0) {
      setResults([]);
      return;
    }
    const t = setTimeout(() => {
      api.searchAirports(q.trim()).then(setResults).catch(() => setResults([]));
    }, 180);
    return () => clearTimeout(t);
  }, [q, open]);

  useEffect(() => {
    if (open && value && q.trim().length === 0) {
      api.nearbyAirports(value.iata).then(setNearby).catch(() => setNearby([]));
    }
  }, [open, value, q]);

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  function pick(a: Airport) {
    onSelect(a);
    setQ('');
    setOpen(false);
  }

  const display = value ? `${value.municipality || value.name} (${value.iata})` : '';

  return (
    <div className="airport-field" ref={ref}>
      <div className="seg-label">{label}</div>
      <input
        className="seg-input"
        value={open ? q : display}
        placeholder={placeholder ?? '도시 · 공항'}
        onFocus={() => {
          setOpen(true);
          setQ('');
        }}
        onChange={(e) => setQ(e.target.value)}
      />
      {open && (
        <div className="airport-pop">
          {q.trim().length > 0 &&
            results.map((a) => (
              <button type="button" key={a.iata} className="airport-row" onMouseDown={() => pick(a)}>
                <span className="ic">✈</span>
                <span className="lines">
                  <span className="t1">
                    {a.municipality || a.name} <span className="iata">({a.iata})</span>
                  </span>
                  <span className="t2">{a.name} · {a.country}</span>
                </span>
              </button>
            ))}
          {q.trim().length > 0 && results.length === 0 && <div className="airport-empty">검색 결과 없음</div>}
          {q.trim().length === 0 && value && nearby.length > 0 && (
            <>
              <div className="airport-hd">{value.municipality || value.name} 근처 공항</div>
              {nearby.map((a) => (
                <button type="button" key={a.iata} className="airport-row" onMouseDown={() => pick(a)}>
                  <span className="ic">✈</span>
                  <span className="lines">
                    <span className="t1">
                      {a.municipality || a.name} <span className="iata">({a.iata})</span>
                    </span>
                    <span className="t2">
                      {a.country} · {value.municipality || value.name}에서 {a.distanceKm}km
                    </span>
                  </span>
                </button>
              ))}
            </>
          )}
          {q.trim().length === 0 && !value && <div className="airport-empty">도시나 공항을 검색하세요</div>}
        </div>
      )}
    </div>
  );
}
