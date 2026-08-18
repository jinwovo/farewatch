import type { SparkCell } from '@/lib/api';

/**
 * Tiny dependency-free price sparkline for dashboard cards: one point per day
 * (that day's cheapest observation), coral dot on the window's minimum.
 */
export default function Sparkline({
  cells,
  width = 110,
  height = 34,
}: {
  cells: SparkCell[];
  width?: number;
  height?: number;
}) {
  if (cells.length === 0) {
    return null;
  }
  const pad = 4;
  const amounts = cells.map((c) => c.amount);
  const min = Math.min(...amounts);
  const max = Math.max(...amounts);
  const x = (i: number) =>
    cells.length === 1 ? width / 2 : pad + (i * (width - pad * 2)) / (cells.length - 1);
  const y = (v: number) =>
    max === min ? height / 2 : pad + ((max - v) * (height - pad * 2)) / (max - min);
  const minIdx = amounts.indexOf(min);
  return (
    <svg className="spark" viewBox={`0 0 ${width} ${height}`} width={width} height={height} aria-hidden>
      {cells.length > 1 && (
        <polyline
          className="spark-line"
          points={cells.map((c, i) => `${x(i).toFixed(1)},${y(c.amount).toFixed(1)}`).join(' ')}
        />
      )}
      <circle className="spark-dot" cx={x(minIdx)} cy={y(min)} r="3" />
    </svg>
  );
}
