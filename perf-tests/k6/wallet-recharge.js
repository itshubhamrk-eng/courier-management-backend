// Wallet recharge-order load test: Login -> POST /branch-wallet/recharge/order, repeated.
// Scoped to the order-open step only (creates a real Razorpay TEST-mode order, credits
// nothing) — completing a payment needs Razorpay's actual checkout/signature flow, not
// something safe to fabricate server-side. Still exercises the real wallet lookup + gateway
// call + order-tracking DB write under concurrency.
//
// Usage:
//   k6 run -e BASE_URL=... -e TENANT=... -e USER_TEMPLATE='op{n}@...' -e USER_POOL_SIZE=15 \
//      -e TEST_PASSWORD=... -e VUS=50 -e DURATION=2m k6/wallet-recharge.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const TENANT = __ENV.TENANT || 'PERFT01';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Password@1234';
const USER_TEMPLATE = __ENV.USER_TEMPLATE || '';
const USER_POOL_SIZE = Number(__ENV.USER_POOL_SIZE || 15);
const USER_PAD = Number(__ENV.USER_PAD || 2);

function currentUserEmail() {
    const idx = ((__VU - 1) % USER_POOL_SIZE) + 1;
    return USER_TEMPLATE.replace('{n}', String(idx).padStart(USER_PAD, '0'));
}

export const options = {
    vus: Number(__ENV.VUS || 1),
    duration: __ENV.DURATION || '30s',
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        http_req_failed: ['rate<0.01'],
        recharge_order_duration: ['p(95)<2000'],
    },
};

const rechargeTrend = new Trend('recharge_order_duration');

function authHeaders(token) {
    return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

function loginAs(email) {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ companyCode: TENANT, email, password: TEST_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } },
    );
    if (!check(res, { 'login 200': (r) => r.status === 200 })) {
        throw new Error(`login failed for ${email} (${res.status}): ${res.body}`);
    }
    return res.json('data.accessToken');
}

let vuToken = null;
let failuresLogged = 0;

export default function () {
    if (!vuToken) {
        vuToken = loginAs(currentUserEmail());
    }
    const auth = authHeaders(vuToken);
    const amount = (Math.floor(Math.random() * 500) + 100).toFixed(2); // 100.00-599.00

    const res = http.post(
        `${BASE_URL}/api/v1/branch-wallet/recharge/order`,
        JSON.stringify({ amount: Number(amount) }),
        auth,
    );
    rechargeTrend.add(res.timings.duration);
    const ok = check(res, { 'recharge order 200': (r) => r.status === 200 || r.status === 201 });
    if (!ok && failuresLogged < 5) {
        failuresLogged++;
        console.error(`Recharge order failed (${res.status}): ${res.body}`);
    }

    sleep(1);
}
