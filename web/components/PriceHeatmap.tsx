'use client';

import type { CalendarCell } from '@/lib/api';

// Cheapest-date heatmap: each observed departure date colored by its lowest price
// (green = cheap → coral = pricey), with the overall cheapest tagged.
export default function PriceHeatmap({ cells }: { cells: CalendarCell[] }) {
  if (cells.length === 0) {
    return <p className="muted">&quot;지금 폴&quot;을 여러 번 돌리면 날짜별 최저가가 색지도로 표시돼요.</p>;
  }
  const amounts = cells.map((c) => c.lowestAmount);
  const min = Math.min(...amounts);
  const max = Math.max(...amounts);
  const span = max - min || 1;
  const fmt = (v: number) => v.toLocaleString('ko-KR');
  const color = (v: number) => {
    const t = (v - min) / span; // 0 cheap → 1 pricey
    const r = Math.round(34 + t * (255 - 34));
    const g = Math.round(197 - t * (197 - 85));
    const b = Math.round(94 - t * (94 - 48));
    return `rgb(${r}, ${g}, ${b})`;
  };

  return (
    <div className="heatmap">
      {cells.map((c) => {
        const cheapest = c.lowestAmount === min;
        return (
          <div key={c.date} className={cheapest ? 'hm-cell best' : 'hm-cell'} style={{ background: color(c.lowestAmount) }}>
            <span className="hm-date">{c.date.slice(5)}</span>
            <span className="hm-amt">{fmt(c.lowestAmount)}</span>
            {cheapest && <span className="hm-tag">최저</span>}
          </div>
        );
      })}
    </div>
  );
}
