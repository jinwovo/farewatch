// farewatch load test — concurrent polling.
//
// Ramps to 30 virtual users hammering POST /api/watches/{id}/poll, which exercises
// the full poll path: aggregator → per-source circuit breaker + token-bucket rate
// limiter → simulator → price_point write → change detection. Reports poll latency
// and error rate with pass/fail thresholds.
//
//   k6 run load/k6-poll.js                 (backend on :8101)
//   k6 run -e BASE=http://host:8101 ...    (override target)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8101';
const pollLatency = new Trend('poll_latency', true);
const errors = new Rate('errors');

export const options = {
  scenarios: {
    polls: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 30 },
        { duration: '30s', target: 30 },
        { duration: '5s', target: 0 },
      ],
    },
  },
  thresholds: {
    poll_latency: ['p(95)<800'],
    errors: ['rate<0.01'],
  },
};

export function setup() {
  const routes = [
    ['ICN', 'NRT'], ['ICN', 'KIX'], ['GMP', 'TAO'], ['ICN', 'CDG'], ['ICN', 'BKK'],
  ];
  const ids = [];
  for (const [origin, destination] of routes) {
    const body = JSON.stringify({
      userRef: 'k6', origin, destination, tripType: 'ONE_WAY',
      departDateFrom: '2026-09-01', departDateTo: '2026-09-05',
      departTimeFrom: '06:00', departTimeTo: '12:00',
    });
    const res = http.post(`${BASE}/api/watches`, body, { headers: { 'Content-Type': 'application/json' } });
    if (res.status === 201) ids.push(JSON.parse(res.body).id);
  }
  return { ids };
}

export default function (data) {
  if (!data.ids.length) return;
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const res = http.post(`${BASE}/api/watches/${id}/poll`);
  pollLatency.add(res.timings.duration);
  errors.add(!check(res, { 'poll 200': (r) => r.status === 200 }));
  sleep(0.1);
}
