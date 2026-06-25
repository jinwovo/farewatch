'use client';

import type { PricePoint } from '@/lib/api';

// Dependency-free SVG line chart of a watch's price time-series (vibrant light theme).
// Down-samples to keep the trend readable when there are hundreds of jittery polls,
// while always preserving the first, last, and all-time-low points.
function prepare(points: PricePoint[], max = 48): PricePoint[] {
  if (points.length <= max) return points;
  const minIdx = points.reduce((mi, p, i, arr) => (p.amount < arr[mi].amount ? i : mi), 0);
  const keep = new Set<number>([0, points.length - 1, minIdx]);
  const step = (points.length - 1) / (max - 1);
  for (let i = 0; i < max; i++) keep.add(Math.round(i * step));
  return [...keep].sort((a, b) => a - b).map((i) => points[i]);
}

export default function PriceChart({ points }: { points: PricePoint[] }) {
  if (points.length === 0) {
    return <p className="muted">아직 가격 데이터가 없어요. &quot;지금 폴&quot;을 눌러보세요.</p>;
  }

  const pts = prepare(points);
  const W = 760;
  const H = 260;
  const padL = 66;
  const padR = 20;
  const padT = 20;
  const padB = 32;

  const amounts = pts.map((p) => p.amount);
  const minY = Math.min(...amounts);
  const maxY = Math.max(...amounts);
  const spanY = maxY - minY || 1;
  const n = pts.length;

  const x = (i: number) => (n === 1 ? padL + (W - padL - padR) / 2 : padL + (i / (n - 1)) * (W - padL - padR));
  const y = (v: number) => H - padB - ((v - minY) / spanY) * (H - padT - padB);

  const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.amount).toFixed(1)}`).join(' ');
  const area = `${line} L${x(n - 1).toFixed(1)},${(H - padB).toFixed(1)} L${x(0).toFixed(1)},${(H - padB).toFixed(1)} Z`;
  const minIdx = amounts.indexOf(minY);
  const fmt = (v: number) => v.toLocaleString('ko-KR');

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="chart" role="img" aria-label="가격 추이 차트">
      <line x1={padL} y1={padT} x2={padL} y2={H - padB} className="axis" />
      <line x1={padL} y1={H - padB} x2={W - padR} y2={H - padB} className="axis" />
      <text x={padL - 8} y={padT + 10} className="lbl" textAnchor="end">{fmt(maxY)}</text>
      <text x={padL - 8} y={H - padB} className="lbl" textAnchor="end">{fmt(minY)}</text>
      <path d={area} className="area" />
      <path d={line} className="line" fill="none" strokeLinejoin="round" strokeLinecap="round" />
      {pts.map((p, i) => (
        <circle key={`${p.id}-${i}`} cx={x(i)} cy={y(p.amount)} r={i === minIdx ? 5 : 2.5} className={i === minIdx ? 'dot low' : 'dot'} />
      ))}
      <text x={x(minIdx)} y={y(minY) - 10} className="lowlbl" textAnchor="middle">최저 {fmt(minY)}</text>
    </svg>
  );
}
