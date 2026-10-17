// 1,000 users arrive within one second, each request waits ~1 s on the "database".
// Arrivals are spread over 1 s because macOS caps the socket backlog at 128 (kern.ipc.somaxconn),
// so a single-instant burst of 1,000 connects gets "connection reset by peer" from the kernel.
// Run: k6 run k6-load-test.js            (BASE_URL defaults to http://localhost:8080)
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        thousand_users: {
            executor: 'constant-arrival-rate',
            rate: 1000,
            timeUnit: '1s',
            duration: '1s',
            preAllocatedVUs: 1000,
        },
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/api/users/${__VU}`);
    check(res, { 'status is 200': (r) => r.status === 200 });
}
