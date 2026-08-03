import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const throttled = new Rate('throttled');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 200),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = __ENV.GATEWAY_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;

export default function () {
  const res = http.get(`${BASE}/api/v1/query/exposures`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });

  throttled.add(res.status === 429);

  check(res, {
    'not a server error': (r) => r.status < 500,
  });
}
