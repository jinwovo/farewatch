'use client';

import type { PricePoint } from '@/lib/api';

// Dependency-free SVG line chart of a watch's price time-series.
export default function PriceChart({ points }: { points: PricePoint[] }) {
  if (points.length === 0) {
    return <p className="muted">아직 가격 데이터가 없어요. &quot;지금 폴&quot;을 눌러보세요.</p>;
  }

  const W = 720;
  const H = 240;
  const pad = 40;
  const amounts = points.map((p) => p.amount);
  const minY = Math.min(...amounts);
  const maxY = Math.max(...amounts);
  const spanY = maxY - minY || 1;
  const n = points.length;

  const x = (i: number) => (n === 1 ? W / 2 : pad + (i / (n - 1)) * (W - 2 * pad));
  const y = (v: number) => H - pad - ((v - minY) / spanY) * (H - 2 * pad);

  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.amount).toFixed(1)}`).join(' ');
  const minIdx = amounts.indexOf(minY);
  const fmt = (v: number) => v.toLocaleString('ko-KR');

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="chart" role="img" aria-label="가격 추이 차트">
      <line x1={pad} y1={H - pad} x2={W - pad} y2={H - pad} className="axis" />
      <line x1={pad} y1={pad} x2={pad} y2={H - pad} className="axis" />
      <text x={6} y={pad + 4} className="lbl">{fmt(maxY)}</text>
      <text x={6} y={H - pad} className="lbl">{fmt(minY)}</text>
      <path d={path} className="line" fill="none" strokeLinejoin="round" strokeLinecap="round" />
      {points.map((p, i) => (
        <circle key={p.id} cx={x(i)} cy={y(p.amount)} r={i === minIdx ? 4.5 : 2.5} className={i === minIdx ? 'dot low' : 'dot'} />
      ))}
      <text x={x(minIdx)} y={y(minY) - 9} className="lowlbl" textAnchor="middle">최저 {fmt(minY)}</text>
    </svg>
  );
}
