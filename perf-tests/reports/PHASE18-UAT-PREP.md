# PHASE 18 — Prepare for UAT/Production Testing

**Already satisfied by design, not a separate retrofit.** Every script built this
session (`k6/scenario.js`, `k6/tenant-isolation.js`) was written env-var-driven
from its very first version, not hardcoded and refactored later — confirmed by
grep just now: every URL, tenant code, and credential in both files only appears
as an `__ENV.X || 'default'` fallback, never a bare requirement.

## The same scripts, three environments, only the flags change

```bash
# LOCAL (what every phase in this report actually ran)
k6 run -e BASE_URL=http://localhost:8082 -e TENANT=PERFT01 \
  -e TEST_USER=admin@t01.perf.local -e TEST_PASSWORD=Password@1234 \
  -e USER_TEMPLATE='op{n}@t01.perf.local' -e USER_POOL_SIZE=44 \
  -e VUS=50 -e DURATION=2m k6/scenario.js

# UAT/STAGING — same file, different target and credentials
k6 run -e BASE_URL=https://uat.example.internal -e TENANT=UAT_TENANT_01 \
  -e TEST_USER=uat-loadtest@example.com -e TEST_PASSWORD="$UAT_LOADTEST_PASSWORD" \
  -e VUS=50 -e DURATION=5m k6/scenario.js

# PRODUCTION — start controlled, low load, only after explicit sign-off
k6 run -e BASE_URL=https://api.example.com -e TENANT=PROD_CANARY_TENANT \
  -e TEST_USER="$PROD_LOADTEST_USER" -e TEST_PASSWORD="$PROD_LOADTEST_PASSWORD" \
  -e VUS=5 -e DURATION=1m k6/scenario.js
```

`TEST_USER`/`TEST_PASSWORD` are read from the shell environment
(`$UAT_LOADTEST_PASSWORD` etc.) in the UAT/production examples above rather than
typed inline — the scripts themselves never care where the value came from, but a
real invocation should never put a real credential in shell history or a CI log
line. `-e VAR=value` on the command line is fine for `BASE_URL`/`TENANT` (not
secret); `TEST_PASSWORD` specifically should come from an env var already set in
the calling shell/CI secret store.

## What must never happen (per the brief's own instruction, restated as a hard
rule for whoever runs this next)

- **Production runs start low (single-digit VUs, ~1 minute) and require explicit
  authorization before scaling up** — never jump straight to the load levels this
  local pass used (up to 200 VUs).
- **Never point PHASE 6/PHASE 11's more aggressive scripts (stress ramp, MySQL/
  Redis-down simulation) at anything but local/throwaway infrastructure.** The
  MySQL-down test in this session specifically required user confirmation first
  even for the *local, shared-with-dev-only* instance — the same test against a
  real UAT or production database is a different, much higher-stakes decision
  that this document does not pre-authorize.
- **A fresh PHASE 2 data generation run is needed per target environment** — the
  `PERFT01`-`PERFT10` tenants are local-only fixtures; UAT/production need their
  own equivalent (real UAT test tenants, obviously never production customer
  data — see PHASE 1's own "never use production data" rule, still in force here).

## Known carry-overs into UAT (from PHASE 17's checklist)

`ISSUE-007` (MySQL-outage requests hang rather than fail fast) and `ISSUE-008`
(session cap can land at 6 instead of 5 under a race) should travel with the
release notes — both are characterized and low-risk, but a UAT pass should know
about them going in rather than "discover" them again.
