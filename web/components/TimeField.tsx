'use client';

const PRESETS = [
  { key: 'any', label: '아무때나', from: '', to: '' },
  { key: 'dawn', label: '새벽 0–6', from: '00:00', to: '06:00' },
  { key: 'morning', label: '아침 6–12', from: '06:00', to: '12:00' },
  { key: 'afternoon', label: '오후 12–18', from: '12:00', to: '18:00' },
  { key: 'evening', label: '저녁 18–24', from: '18:00', to: '23:59' },
];

export default function TimeField({
  from,
  to,
  onChange,
}: {
  from: string;
  to: string;
  onChange: (from: string, to: string) => void;
}) {
  const active = PRESETS.find((p) => p.from === from && p.to === to)?.key ?? 'custom';
  return (
    <div className="timefield">
      <div className="time-pills">
        {PRESETS.map((p) => (
          <button
            type="button"
            key={p.key}
            className={active === p.key ? 'time-pill active' : 'time-pill'}
            onClick={() => onChange(p.from, p.to)}
          >
            {p.label}
          </button>
        ))}
      </div>
      <div className="time-custom">
        <span>직접 지정</span>
        <input type="time" value={from} onChange={(e) => onChange(e.target.value, to || e.target.value)} />
        <span>~</span>
        <input type="time" value={to} onChange={(e) => onChange(from, e.target.value)} />
      </div>
    </div>
  );
}
