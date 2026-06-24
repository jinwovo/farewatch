'use client';

import { useState } from 'react';

const DOW = ['일', '월', '화', '수', '목', '금', '토'];
const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
const fmt = (y: number, m: number, d: number) => `${y}-${pad(m + 1)}-${pad(d)}`; // m is 0-based

function todayStr() {
  const t = new Date();
  return fmt(t.getFullYear(), t.getMonth(), t.getDate());
}

export default function DateRangeField({
  from,
  to,
  onChange,
}: {
  from: string;
  to: string;
  onChange: (from: string, to: string) => void;
}) {
  const now = new Date();
  const [vy, setVy] = useState(now.getFullYear());
  const [vm, setVm] = useState(now.getMonth());
  const [flexible, setFlexible] = useState(to !== '' && to !== from);
  const today = todayStr();

  function clickDay(ds: string) {
    if (ds < today) return;
    if (!flexible) {
      onChange(ds, ds);
      return;
    }
    if (!from || (from && to)) {
      onChange(ds, '');
      return;
    }
    if (ds >= from) onChange(from, ds);
    else onChange(ds, '');
  }

  function shift(delta: number) {
    let m = vm + delta;
    let y = vy;
    while (m < 0) {
      m += 12;
      y--;
    }
    while (m > 11) {
      m -= 12;
      y++;
    }
    setVy(y);
    setVm(m);
  }

  function grid(y: number, m: number) {
    const first = new Date(y, m, 1).getDay();
    const days = new Date(y, m + 1, 0).getDate();
    const cells: (number | null)[] = [];
    for (let i = 0; i < first; i++) cells.push(null);
    for (let d = 1; d <= days; d++) cells.push(d);
    return cells;
  }

  function Month({ y, m }: { y: number; m: number }) {
    return (
      <div className="cal-month">
        <div className="cal-title">{y}년 {m + 1}월</div>
        <div className="cal-dow">{DOW.map((d) => <span key={d}>{d}</span>)}</div>
        <div className="cal-grid">
          {grid(y, m).map((d, i) => {
            if (d === null) return <span key={i} className="cal-cell empty" />;
            const ds = fmt(y, m, d);
            const past = ds < today;
            const sel = ds === from || ds === to;
            const inRange = flexible && !!from && !!to && ds > from && ds < to;
            const cls = ['cal-cell'];
            if (past) cls.push('past');
            if (sel) cls.push('sel');
            if (inRange) cls.push('inrange');
            return (
              <button type="button" key={i} className={cls.join(' ')} disabled={past} onClick={() => clickDay(ds)}>
                {d}
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  const ny = vm === 11 ? vy + 1 : vy;
  const nm = (vm + 1) % 12;
  const label = from ? (to && to !== from ? `${from} ~ ${to}` : from) : '날짜를 선택하세요';

  return (
    <div className="daterange">
      <div className="cal-head">
        <div className="cal-tabs">
          <button
            type="button"
            className={!flexible ? 'active' : ''}
            onClick={() => {
              setFlexible(false);
              if (from) onChange(from, from);
            }}
          >
            특정 날짜
          </button>
          <button type="button" className={flexible ? 'active' : ''} onClick={() => setFlexible(true)}>
            날짜 조정 가능
          </button>
        </div>
        <div className="cal-sel">{label}</div>
      </div>
      <div className="cal-body">
        <button type="button" className="cal-nav" onClick={() => shift(-1)} aria-label="이전 달">‹</button>
        <Month y={vy} m={vm} />
        <Month y={ny} m={nm} />
        <button type="button" className="cal-nav right" onClick={() => shift(1)} aria-label="다음 달">›</button>
      </div>
      {flexible && <div className="cal-hint">시작일을 누른 뒤 종료일을 누르면 유연한 날짜 범위가 됩니다.</div>}
    </div>
  );
}
