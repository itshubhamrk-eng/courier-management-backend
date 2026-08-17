// PHASE 8 — Multi-Tenant Isolation Test (mandatory per the test brief).
//
// Tenant A (TENANT_A) attempts to read Tenant B (TENANT_B)'s data by id, across
// every resource type that has a by-id endpoint. Every attempt must be denied
// (404/403) at the BACKEND — this deliberately never touches the frontend, since a
// frontend-only isolation check proves nothing about what the API itself allows.
//
// This is a correctness/security check, not a load test — VUS=1, one pass. Run it
// after every PHASE 2 data-generation pass (a fresh cohort needs fresh resource ids)
// and after any change touching CompanyFilterAspect/CompanyContext/a repository's
// company-scoping.
//
// Usage:
//   k6 run -e BASE_URL=http://localhost:8082 \
//      -e TENANT_A=PERFT01 -e TENANT_A_USER=admin@t01.perf.local \
//      -e TENANT_B=PERFT02 -e TENANT_B_USER=admin@t02.perf.local \
//      -e PASSWORD=Password@1234 \
//      perf-tests/k6/tenant-isolation.js
//
// Env vars (all optional, default to this project's own PERFT01/PERFT02 fixtures):
//   BASE_URL        backend root                           (default http://localhost:8082)
//   TENANT_A/_USER  the attacking tenant + its login email  (default PERFT01 / admin@t01.perf.local)
//   TENANT_B/_USER  the victim tenant + its login email     (default PERFT02 / admin@t02.perf.local)
//   PASSWORD        shared password for both                (default Password@1234)

import http from 'k6/http';
import { check, group } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const TENANT_A = __ENV.TENANT_A || 'PERFT01';
const TENANT_A_USER = __ENV.TENANT_A_USER || 'admin@t01.perf.local';
const TENANT_B = __ENV.TENANT_B || 'PERFT02';
const TENANT_B_USER = __ENV.TENANT_B_USER || 'admin@t02.perf.local';
const PASSWORD = __ENV.PASSWORD || 'Password@1234';

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        // Every isolation check must pass — this is a security gate, not a
        // performance one. Any failure here should fail the whole run loudly.
        checks: ['rate==1.0'],
    },
};

function login(companyCode, email) {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ companyCode, email, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } },
    );
    if (res.status !== 200) {
        throw new Error(`login failed for ${email}@${companyCode} (${res.status}): ${res.body}`);
    }
    return res.json('data.accessToken');
}

function auth(token) {
    return { headers: { Authorization: `Bearer ${token}` } };
}

/** Confirms a cross-tenant GET-by-id is refused. Accepts 404 (the app's own
 * convention — findByIdWithinCompany throws ResourceNotFoundException, never a 403,
 * so a foreign resource looks identical to a nonexistent one) or 403. */
function assertDenied(name, tokenA, path) {
    const res = http.get(`${BASE_URL}${path}`, auth(tokenA));
    const denied = res.status === 404 || res.status === 403;
    check(res, { [`${name}: cross-tenant GET denied (404/403)`]: () => denied });
    if (!denied) {
        console.error(`LEAK: ${name} — GET ${path} as ${TENANT_A} returned ${res.status}: ${res.body}`);
    }
}

/** Same as assertDenied, but for a lookup keyed by a value that is only unique
 * *per company* (a tracking number: `uk_shipments_company_tracking (company_id,
 * tracking_number)`, not globally) — two tenants can legitimately share the same
 * value by coincidence, in which case a 200 is correct as long as it's tenant A's
 * *own* row, not tenant B's. Only a 200 carrying tenant B's actual id is a real leak. */
function assertDeniedOrOwnRecord(name, tokenA, path, foreignId) {
    const res = http.get(`${BASE_URL}${path}`, auth(tokenA));
    if (res.status === 404 || res.status === 403) {
        check(res, { [`${name}: cross-tenant lookup denied or resolved to caller's own record`]: () => true });
        return;
    }
    const returnedId = res.status === 200 ? res.json('data.id') : null;
    const leaked = returnedId === foreignId;
    check(res, { [`${name}: cross-tenant lookup denied or resolved to caller's own record`]: () => !leaked });
    if (leaked) {
        console.error(`LEAK: ${name} — GET ${path} as ${TENANT_A} returned tenant B's own record ${returnedId}`);
    }
}

/** Confirms a tenant's own list/search endpoint never contains the other tenant's
 * rows — belt-and-suspenders beyond the by-id checks above (this is exactly the
 * shape of check that caught ISSUE-001: a leak in an aggregate, not a single lookup). */
function assertSearchNotContaining(name, token, path, foreignId) {
    const res = http.get(`${BASE_URL}${path}`, auth(token));
    const ok = check(res, { [`${name}: search 200`]: (r) => r.status === 200 });
    if (!ok) return;
    const body = res.body;
    const contaminated = foreignId && body.includes(foreignId);
    check(res, { [`${name}: search results contain no foreign-tenant id`]: () => !contaminated });
    if (contaminated) {
        console.error(`LEAK: ${name} — GET ${path} as caller's own tenant contains foreign id ${foreignId}`);
    }
}

export default function () {
    const tokenA = login(TENANT_A, TENANT_A_USER);
    const tokenB = login(TENANT_B, TENANT_B_USER);
    const authB = auth(tokenB);

    // ---- Resolve real Tenant B resource ids, as Tenant B itself ----
    const shipment = http.get(`${BASE_URL}/api/v1/shipments?page=0&size=1`, authB).json('data.content')[0];
    const customer = http.get(`${BASE_URL}/api/v1/customers?page=0&size=1`, authB).json('data.content')[0];
    const branch = http.get(`${BASE_URL}/api/v1/branches?page=0&size=1`, authB).json('data.content')[0];
    const ticket = http.get(`${BASE_URL}/api/v1/support/tickets?page=0&size=1`, authB).json('data.content')[0];
    const user = http.get(`${BASE_URL}/api/v1/users?page=0&size=1`, authB).json('data.content')[0];
    const manifests = http.get(`${BASE_URL}/api/v1/manifests?page=0&size=1`, authB).json('data.content');
    const manifest = manifests && manifests.length > 0 ? manifests[0] : null;

    group('Shipments', function () {
        if (shipment) {
            assertDenied('Shipment by id', tokenA, `/api/v1/shipments/${shipment.id}`);
            assertDeniedOrOwnRecord('Shipment by tracking number', tokenA,
                `/api/v1/shipments/track/${shipment.trackingNumber}`, shipment.id);
            assertDenied('Shipment timeline', tokenA, `/api/v1/shipments/${shipment.id}/timeline`);
            assertSearchNotContaining('Shipment search', tokenA, '/api/v1/shipments?page=0&size=50', shipment.id);
        }
    });

    group('Customers', function () {
        if (customer) {
            assertDenied('Customer by id', tokenA, `/api/v1/customers/${customer.id}`);
            assertSearchNotContaining('Customer search', tokenA, '/api/v1/customers?page=0&size=50', customer.id);
        }
    });

    group('Branches', function () {
        if (branch) {
            assertDenied('Branch by id', tokenA, `/api/v1/branches/${branch.id}`);
        }
    });

    group('Tickets', function () {
        if (ticket) {
            assertDenied('Ticket by id', tokenA, `/api/v1/support/tickets/${ticket.id}`);
            assertSearchNotContaining('Ticket search', tokenA, '/api/v1/support/tickets?page=0&size=50', ticket.id);
        }
    });

    group('Users', function () {
        if (user) {
            assertDenied('User by id', tokenA, `/api/v1/users/${user.id}`);
        }
    });

    group('Manifests', function () {
        if (manifest) {
            assertDenied('Manifest by id', tokenA, `/api/v1/manifests/${manifest.id}`);
        }
    });

    group('Dashboard', function () {
        // ISSUE-001's own regression check, run live rather than just unit-tested.
        // NOT "tenant A's total != tenant B's total" — two tenants generated from the
        // same PHASE 2 template legitimately start with identical counts (10,000
        // each) with zero traffic against either, so equality alone proves nothing
        // (caught this the hard way: a first version of this check false-positived on
        // exactly that). ISSUE-001's actual bug summed *every* tenant's rows together
        // (~100K+ across all 10 PERFT companies) — a per-tenant total anywhere near
        // that combined scale is the real signal, not equality between two peers.
        const dashA = http.get(`${BASE_URL}/api/v1/dashboard/summary`, auth(tokenA)).json('data.statistics');
        const PER_TENANT_CEILING = 50000; // well above one tenant's own ~10-20K, well below the ~100K+ cross-tenant sum
        check(null, {
            [`Dashboard: tenant A's own total (${dashA.totalShipments}) is within one-tenant scale, not the cross-tenant sum`]:
                () => dashA.totalShipments < PER_TENANT_CEILING,
        });
    });
}
