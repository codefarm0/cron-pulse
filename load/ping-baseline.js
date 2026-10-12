// Phase 2 baseline: how fast is the ping endpoint on one instance?
//
// Run (from the repo root, app running in Docker):
//   APP_PORT=8081 docker compose --profile app up -d --build
//   docker compose --profile load run --rm k6 run /load/ping-baseline.js
//   docker compose --profile load run --rm -e RATE=2000 k6 run /load/ping-baseline.js
//
// It creates CHECKS checks through the REST API, then pings them at a constant RATE (requests per
// second) for DURATION, picking a random check each time, and deletes the checks afterwards.
// A constant arrival rate (not a fixed number of virtual users) keeps sending at RATE even when the
// server slows down, so slowness shows up as latency instead of quietly lowering the load.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CHECKS = Number(__ENV.CHECKS || 2000);
const RATE = Number(__ENV.RATE || 1000);
const DURATION = __ENV.DURATION || '60s';

const rateLimited = new Counter('pings_rate_limited');

export const options = {
  setupTimeout: '180s',
  teardownTimeout: '180s',
  scenarios: {
    pings: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 200,
      maxVUs: 2000,
    },
  },
  // The Phase 5 scale target (ROADMAP §2.1). Phase 2 only records where we stand.
  thresholds: {
    'http_req_duration{endpoint:ping}': ['p(99)<50'],
    'http_req_failed{endpoint:ping}': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const pingPaths = [];
  // Create checks in parallel batches of 50 to keep setup short.
  for (let start = 0; start < CHECKS; start += 50) {
    const requests = [];
    for (let i = start; i < Math.min(start + 50, CHECKS); i++) {
      requests.push(['POST', `${BASE_URL}/api/v1/checks`, JSON.stringify({
        name: `k6 load ${Date.now()}-${i}`,
        schedule: { type: 'SIMPLE', periodSeconds: 3600 },
        graceSeconds: 300,
      }), { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'setup' } }]);
    }
    for (const response of http.batch(requests)) {
      if (response.status !== 201) {
        throw new Error(`Creating a check failed: ${response.status} ${response.body}`);
      }
      const body = response.json();
      // pingUrl uses the public base URL; k6 reaches the app on BASE_URL, so keep only the path.
      // (k6 has no URL class, hence the regex.)
      pingPaths.push({ id: body.id, path: body.pingUrl.replace(/^https?:\/\/[^/]+/, '') });
    }
  }
  return { checks: pingPaths };
}

export default function (data) {
  const target = data.checks[Math.floor(Math.random() * data.checks.length)];
  const response = http.get(`${BASE_URL}${target.path}`, {
    headers: { 'User-Agent': 'k6-baseline' },
    tags: { endpoint: 'ping' },
    // 429 is the rate limiter working, not a server failure.
    responseCallback: http.expectedStatuses(200, 429),
  });
  if (response.status === 429) {
    rateLimited.add(1);
  }
  check(response, { 'ping accepted': (r) => r.status === 200 });
}

export function teardown(data) {
  for (let start = 0; start < data.checks.length; start += 50) {
    http.batch(data.checks.slice(start, start + 50).map((c) =>
      ['DELETE', `${BASE_URL}/api/v1/checks/${c.id}`, null, { tags: { endpoint: 'teardown' } }]));
  }
}
