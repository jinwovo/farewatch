'use client';

import type { Cabin } from '@/lib/api';

const CABINS: { v: Cabin; l: string }[] = [
  { v: 'ECONOMY', l: '일반석' },
  { v: 'PREMIUM_ECONOMY', l: '프리미엄' },
  { v: 'BUSINESS', l: '비즈니스' },
  { v: 'FIRST', l: '일등석' },
];

export default function PaxCabinField({
  passengers,
  cabin,
  onChange,
}: {
  passengers: number;
  cabin: Cabin;
  onChange: (passengers: number, cabin: Cabin) => void;
}) {
  return (
    <div className="paxcabin">
      <div className="stepper">
        <span className="lbl">여행자</span>
        <button type="button" onClick={() => onChange(Math.max(1, passengers - 1), cabin)} aria-label="줄이기">
          −
        </button>
        <b>{passengers}</b>
        <button type="button" onClick={() => onChange(Math.min(9, passengers + 1), cabin)} aria-label="늘리기">
          +
        </button>
      </div>
      <select value={cabin} onChange={(e) => onChange(passengers, e.target.value as Cabin)}>
        {CABINS.map((c) => (
          <option key={c.v} value={c.v}>
            {c.l}
          </option>
        ))}
      </select>
    </div>
  );
}
