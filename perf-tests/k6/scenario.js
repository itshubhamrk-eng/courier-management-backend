// Realistic user-journey script for the local Courier SaaS backend:
//   Login -> Dashboard -> Search Shipments -> Open Shipment -> Track -> Create Shipment
//   -> Update Shipment
//
// Doubles as PHASE 3 (functional smoke — every `check()` failing is a functional bug,
// not a performance one) and PHASE 4/5 (baseline/load — same script, more VUs). Never
// hard-codes environment: every value that differs between LOCAL/UAT/STAGING/PRODUCTION
// (PHASE 18) is a `-e` flag or env var below, so this file itself never changes when the
// target environment does.
//
// Usage:
//   Functional check (few iterations, 1 VU):
//     k6 run -e BASE_URL=http://localhost:8082 -e TENANT=PERFT01 \
//        -e TEST_USER=admin@t01.perf.local -e TEST_PASSWORD=Password@1234 \
//        -e VUS=1 -e DURATION=15s perf-tests/k6/scenario.js
//   Baseline (PHASE 4 — run once each at 10/20/50). USER_TEMPLATE required once VUS
//   exceeds a handful — see the USER_TEMPLATE note below:
//     k6 run -e VUS=10 -e DURATION=2m \
//        -e USER_TEMPLATE='op{n}@t01.perf.local' -e USER_POOL_SIZE=44 ... scenario.js
//   Load/stress (PHASE 5/6 — same script, higher VUS):
//     k6 run -e VUS=500 -e DURATION=5m \
//        -e USER_TEMPLATE='op{n}@t01.perf.local' -e USER_POOL_SIZE=44 ... scenario.js
//
// Env vars (all optional, defaults match this project's own PERFT01 fixture — see
// perf-tests/README.md):
//   BASE_URL          backend root, no trailing slash       (default http://localhost:8082)
//   TENANT            companyCode to sign in to             (default PERFT01)
//   TEST_USER         login email, used when USER_TEMPLATE is unset (default admin@t01.perf.local)
//   TEST_PASSWORD     login password, shared by every generated user (default Password@1234)
//   USER_TEMPLATE     email template with a `{n}` placeholder, e.g. 'op{n}@t01.perf.local' —
//                     when set, each VU logs in as its own account instead of every VU
//                     sharing TEST_USER. REQUIRED once VUS is large enough to matter: the
//                     app's own login throttle (app.auth.throttle-max-attempts, per
//                     email+IP) will 429 a shared single account under concurrent VUs —
//                     that's the throttle correctly doing its job, not a bug, and every VU
//                     hammering one account is not a realistic traffic shape anyway.
//   USER_POOL_SIZE    how many distinct accounts USER_TEMPLATE cycles through (default 44 —
//                     matches the perf data generator's own default OPERATOR headcount per
//                     company: usersPerCompany(50) - 1 admin - branchesPerCompany(5) managers)
//   USER_PAD          zero-pad width for `{n}`                (default 4, matches generator's op%04d)
//   VUS               virtual users                           (default 1)
//   DURATION          k6 duration string                      (default 30s)

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const TENANT = __ENV.TENANT || 'PERFT01';
const TEST_USER = __ENV.TEST_USER || 'admin@t01.perf.local';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Password@1234';
const USER_TEMPLATE = __ENV.USER_TEMPLATE || '';
const USER_POOL_SIZE = Number(__ENV.USER_POOL_SIZE || 44);
const USER_PAD = Number(__ENV.USER_PAD || 4);

function currentUserEmail() {
    if (!USER_TEMPLATE) {
        return TEST_USER;
    }
    const idx = ((__VU - 1) % USER_POOL_SIZE) + 1;
    return USER_TEMPLATE.replace('{n}', String(idx).padStart(USER_PAD, '0'));
}

export const options = {
    vus: Number(__ENV.VUS || 1),
    duration: __ENV.DURATION || '30s',
    thresholds: {
        // PHASE 16 starting acceptance targets — not production guarantees.
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.01'],
        login_duration: ['p(95)<1000'],
        dashboard_duration: ['p(95)<1000'],
        search_duration: ['p(95)<1000'],
        create_shipment_duration: ['p(95)<1000'],
    },
};

const loginTrend = new Trend('login_duration');
const dashboardTrend = new Trend('dashboard_duration');
const searchTrend = new Trend('search_duration');
const createTrend = new Trend('create_shipment_duration');

function authHeaders(token) {
    return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

/** @returns {{token: string, branchId: string|null}} — `branchId` is the field
 * `LoginResponse` itself carries ("the branch this account is staffed at, if any —
 * null for a company admin"). A non-admin caller may only *book* at their own
 * branch (`BranchServiceImpl.requireVisible` 404s anyone else's) — found the hard
 * way running the first real PHASE 4 baseline (2026-08-17): a script that hands
 * every VU the same fixed `bookingBranchId` regardless of which account is logged
 * in gets ~80% of OPERATOR-driven bookings rejected 404 "Branch not found", not
 * because the branch doesn't exist but because that caller isn't staffed there. */
function loginAs(email) {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ companyCode: TENANT, email, password: TEST_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } },
    );
    loginTrend.add(res.timings.duration);
    if (!check(res, { 'login 200': (r) => r.status === 200 })) {
        throw new Error(`login failed for ${email} (${res.status}): ${res.body}`);
    }
    return { token: res.json('data.accessToken'), branchId: res.json('data.branchId') };
}

/** Runs once before VUs start: logs in (always as TEST_USER, never the per-VU
 * USER_TEMPLATE pool — this is a single one-off call, not the VU loop, so there's no
 * throttle risk and no reason to burn a pool slot) and resolves the master-data ids
 * every booking needs, so every VU iteration reuses the same real ids rather than
 * each VU re-discovering them on every single iteration. */
export function setup() {
    const auth = authHeaders(loginAs(TEST_USER).token);

    const branches = http.get(`${BASE_URL}/api/v1/branches?page=0&size=10`, auth).json('data.content');
    if (!branches || branches.length < 1) {
        throw new Error('setup(): no branches found — run the perf data generator first');
    }
    const serviceTypes = http.get(`${BASE_URL}/api/v1/master/service-types?page=0&size=5`, auth).json('data.content');
    const packageTypes = http.get(`${BASE_URL}/api/v1/master/package-types?page=0&size=5`, auth).json('data.content');
    const paymentModes = http.get(`${BASE_URL}/api/v1/master/payment-modes?page=0&size=5`, auth).json('data.content');
    // TOPAY (collect at delivery), not PAID (collect at booking): these synthetic
    // wallets were already run down close to empty by the data generator's own
    // booking-debit backfill, so defaulting to PAID would make every create-shipment
    // call fail on wallet balance once a run's VUs collectively exhaust it — a wallet-
    // balance artifact unrelated to what PHASE 4/5/6 are actually measuring. Wallet-
    // specific behavior (including PAID) belongs to PHASE 7's dedicated concurrency
    // scenario, not this general-purpose journey script.
    const topay = paymentModes.find((p) => p.code === 'TOPAY') || paymentModes[0];

    return {
        branchIds: branches.map((b) => b.id),
        serviceTypeId: serviceTypes[0].id,
        packageTypeId: packageTypes[0].id,
        paymentModeId: topay.id,
    };
}

// Module-scope, not shared across VUs: each k6 VU runs its own JS VM instance, so this
// caches one real login per virtual user — "User Login" happens once per simulated
// session, then every iteration of that VU reuses it, the same as a real logged-in user
// repeating actions rather than re-authenticating on every single request.
let vuSession = null;
let createFailuresLogged = 0;

export default function (data) {
    if (!vuSession) {
        vuSession = loginAs(currentUserEmail());
    }
    const auth = authHeaders(vuSession.token);
    // Book at the caller's own branch when they have one (every non-admin account
    // does); fall back to the shared directory's first branch for admin/no-branch
    // callers. Deliver to a different branch from the shared directory.
    const bookingBranchId = vuSession.branchId || data.branchIds[0];
    const otherBranches = data.branchIds.filter((id) => id !== bookingBranchId);
    const deliveryBranchId = otherBranches.length > 0
        ? otherBranches[Math.floor(Math.random() * otherBranches.length)]
        : bookingBranchId;

    group('Dashboard', function () {
        const res = http.get(`${BASE_URL}/api/v1/dashboard/summary`, auth);
        dashboardTrend.add(res.timings.duration);
        check(res, { 'dashboard 200': (r) => r.status === 200 });
    });
    sleep(1);

    let trackingNumber = null;
    let shipmentId = null;

    group('Search Shipment', function () {
        const res = http.get(`${BASE_URL}/api/v1/shipments?page=0&size=20&sort=createdAt,desc`, auth);
        searchTrend.add(res.timings.duration);
        const ok = check(res, { 'search 200': (r) => r.status === 200 });
        if (ok) {
            const content = res.json('data.content');
            if (content && content.length > 0) {
                const pick = content[Math.floor(Math.random() * content.length)];
                shipmentId = pick.id;
                trackingNumber = pick.trackingNumber;
            }
        }
    });
    sleep(1);

    if (shipmentId) {
        group('Open Shipment', function () {
            const res = http.get(`${BASE_URL}/api/v1/shipments/${shipmentId}`, auth);
            check(res, { 'open shipment 200': (r) => r.status === 200 });
        });
        sleep(1);
    }

    if (trackingNumber) {
        group('Track Shipment', function () {
            const res = http.get(`${BASE_URL}/api/v1/shipments/track/${trackingNumber}`, auth);
            check(res, { 'track 200': (r) => r.status === 200 });
        });
        sleep(1);
    }

    let createdId = null;
    let createdVersion = null;
    const createBody = {
        bookingBranchId: bookingBranchId,
        deliveryBranchId: deliveryBranchId,
        pickupPincode: '411001',
        deliveryPincode: '413512',
        senderName: `k6 Sender ${__VU}-${__ITER}`,
        senderAddress: '1 Load Test Lane',
        senderContact: '9800000000',
        receiverName: `k6 Receiver ${__VU}-${__ITER}`,
        receiverAddress: '2 Load Test Lane',
        receiverContact: '9800000001',
        serviceTypeId: data.serviceTypeId,
        packageTypeId: data.packageTypeId,
        paymentModeId: data.paymentModeId,
        numberOfPackages: 1,
        items: [{ itemName: 'Package', quantity: 1, weight: 2.5 }],
    };

    group('Create Shipment', function () {
        const res = http.post(`${BASE_URL}/api/v1/shipments`, JSON.stringify(createBody), auth);
        createTrend.add(res.timings.duration);
        const ok = check(res, { 'create shipment 201/200': (r) => r.status === 201 || r.status === 200 });
        if (ok) {
            createdId = res.json('data.id');
            createdVersion = res.json('data.version');
        } else if (createFailuresLogged < 5) {
            createFailuresLogged++;
            console.error(`Create Shipment failed (${res.status}): ${res.body}`);
        }
    });
    sleep(1);

    if (createdId) {
        group('Update Shipment', function () {
            const updateBody = { ...createBody, version: createdVersion, remarks: 'Updated by k6 scenario' };
            delete updateBody.bookingBranchId; // immutable once booked, not part of UpdateShipmentRequest
            const res = http.put(`${BASE_URL}/api/v1/shipments/${createdId}`, JSON.stringify(updateBody), auth);
            check(res, { 'update shipment 200': (r) => r.status === 200 });
        });
    }

    sleep(1);
}
