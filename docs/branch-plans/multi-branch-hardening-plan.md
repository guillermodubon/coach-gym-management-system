# Branch Plan: `feature/multi-branch-hardening`

## 1. Branch identity

- **Branch:** `feature/multi-branch-hardening`
- **Capability:** Final integrated security, isolation, performance, resilience, scalability, and frontend-readiness hardening for the complete multisucursal backend
- **Previous completed branch:** `feature/multi-branch-reporting-audit`
- **Expected next branch:** `feature/frontend-foundation-auth`
- **Current migration baseline:** V1–V43, verified from the synchronized repository and Git history
- **Starting progress:** 0%
- **Target progress:** 100%
- **Execution model:** One explicitly authorized block per Codex response
- **Backend significance:** Final backend branch for the currently approved scope
- **Deployment policy:** Do not deploy external infrastructure in this branch. Deployment occurs only after backend and frontend are complete and locally integrated
- **Plan owner:** User
- **Implementation agent:** Codex
- **Git ownership:** The user controls staging, commits, pushes, rebases, merges, pull requests, branch deletion, and branch switching

## 2. Purpose

Finalize the Coach Gym backend after the multisucursal expansion.

This branch is not intended to add another large business feature. Its purpose is to independently review, test, measure, and harden everything introduced across the completed backend roadmap, especially:

- staff self-profile;
- organization and branch foundation;
- staff scopes, assignments, and active branch context;
- branch ownership and IDOR protection;
- membership-plan branch coverage;
- membership-period entitlement snapshots;
- manual and QR access across branches;
- branch-specific access-payment policy overrides;
- invitation-only staff provisioning;
- account lifecycle and password recovery;
- Gmail API delivery through OAuth 2.0;
- multi-branch dashboard and reporting;
- branch-aware audit query and CSV export.

At completion, the backend must be:

- functionally complete for the approved scope;
- secure for users and business data;
- safe under concurrency;
- branch-isolated;
- horizontally scalable within the documented modular-monolith model;
- efficient under representative portfolio traffic;
- operationally observable;
- reproducible locally;
- ready to support the frontend without contract guessing;
- honestly documented regarding free-tier and production limitations.

## 3. Execution protocol for Codex

This plan provides complete context but authorizes only the block explicitly requested in the current prompt.

For every block, Codex must:

1. Read the root and every applicable nested `AGENTS.md`.
2. Read this complete plan.
3. Read the current blueprint and relevant ADRs.
4. Inspect the current repository and run `git status --short` before editing.
5. Inspect the exact implementation and tests affected by the authorized block.
6. Produce a concise evidence-based finding list before broad changes.
7. Preserve unrelated local changes.
8. Implement only the authorized block.
9. Run focused validations.
10. Fix failures introduced by the block.
11. Perform a secondary consistency review.
12. Report evidence and stop.

Codex must not:

- implement later blocks early;
- add frontend code;
- deploy Supabase, Render, Vercel, Gmail, Google Cloud, or Stripe resources;
- introduce microservices, Kafka, RabbitMQ, Kubernetes, Redis, or a service mesh without a measured and separately approved need;
- weaken tests, authorization, CSRF, constraints, or error privacy;
- modify an applied Flyway migration;
- execute Flyway repair automatically;
- add indexes without query evidence;
- add caches without a correctness, security, and invalidation model;
- stage, commit, push, rebase, merge, switch branches, or open a pull request;
- rely on real external services in the normal quality gate;
- create temporary patch scripts, local database dumps, generated emails, reports, PDFs, PNGs, or CSVs in the repository;
- claim production readiness beyond the guarantees actually implemented.

## 4. Success definition

The branch reaches 100% only when the backend satisfies all of these outcomes:

1. Every approved business capability still works.
2. Branch isolation is verified across every affected endpoint, query, download, event, report, and export.
3. Role and scope authorization are consistent at HTTP, method, application, and persistence boundaries.
4. Sessions and active branch context cannot preserve stale authority indefinitely.
5. Invitation, recovery, account lifecycle, and last-admin protections pass concurrency tests.
6. Gmail OAuth, MIME, transport, uncertain outcomes, and secret handling pass security review.
7. Reporting reconciles organization and branch totals without double counting.
8. Audit query/detail/CSV apply exact scope and sanitization.
9. PostgreSQL migrations, constraints, indexes, and query plans pass clean and evolved database validation.
10. Critical queries and endpoints meet documented local performance targets or have explicit nonblocking limitations.
11. Logs, metrics, health, `ProblemDetail`, audit, exports, emails, and OpenAPI do not leak secrets or sensitive data.
12. OpenAPI and the frontend contract guide accurately describe final behavior.
13. Local development and demo startup remain reproducible.
14. The deployment path for Vercel, Render, Supabase, Gmail API, and Stripe Test Mode is fully documented but not executed.
15. `clean check` succeeds.

## 5. Explicit non-goals

This branch must not implement:

- frontend pages;
- multi-tenancy;
- organization switching;
- multiple independent gym brands;
- SaaS billing;
- general accounting, expenses, payroll, or tax reporting;
- client self-service accounts;
- mobile applications;
- MFA;
- OIDC or social login;
- Gmail inbox reading;
- transactional email delivery webhooks;
- bulk or marketing email;
- Stripe Live Mode;
- new payment methods;
- branch transfer workflows;
- new membership products;
- hardware turnstile integration;
- cloud provisioning;
- paid infrastructure;
- speculative partitioning, materialized views, or service extraction.

## 6. Architecture decision retained

The backend remains a modular monolith.

Final architecture:

```text
Spring Boot modular monolith
├── capability-oriented modules
├── public contracts and events
├── application transaction boundaries
├── ports and adapters
├── PostgreSQL + Flyway
├── provider-neutral storage
├── Gmail API behind EmailSender
├── Stripe Test Mode behind payment ports
└── Spring Modulith verification
```

This branch must revalidate the ADR that retains the modular monolith and update it only if multisucursal implementation changed any assumptions.

Future extraction remains evidence-driven. Candidates may include:

- background email processing;
- document generation;
- analytical reporting;
- physical access edge processing.

Extraction requires measurable need such as independent scaling, separate ownership, fault isolation, regulation, or deployment bottlenecks.

## 7. Final system scope to inventory

Before Block 1 is complete, Codex must inventory:

- identity and authentication;
- staff self-profile and photos;
- organization and branches;
- staff scopes and assignments;
- active branch context;
- clients and photos;
- plans, promotions, memberships, and periods;
- manual payments, corrections, refunds, voids, and Stripe attempts;
- payment receipts;
- access credentials;
- manual and QR access;
- branch coverage and access-payment policies;
- staff invitations, activation, recovery, lifecycle, and bootstrap;
- Gmail OAuth and delivery;
- durable and ephemeral email paths;
- equipment, incidents, maintenance, and notifications;
- dashboards and reporting;
- audit query, detail, and CSV;
- settings;
- storage adapters;
- health, metrics, logs, correlation IDs, and operational controls;
- Docker Compose and local profiles;
- OpenAPI and frontend contract documentation;
- all Flyway migrations and indexes;
- all public events and module dependencies.

## 8. Final authorization model

Roles:

```text
ADMIN
RECEPTIONIST
```

Scopes:

```text
ORGANIZATION
BRANCH
```

Approved combinations:

```text
ADMIN + ORGANIZATION
ADMIN + BRANCH
RECEPTIONIST + BRANCH
```

Disallowed:

```text
RECEPTIONIST + ORGANIZATION
```

### `ADMIN + ORGANIZATION`

- full organization administration;
- all branches subject to explicit endpoint semantics;
- owner-level financial reporting;
- staff invitations and privilege administration;
- organization-wide audit and CSV;
- branch comparison.

### `ADMIN + BRANCH`

- only assigned branches;
- no organization-wide authority;
- no creation of organization admins;
- only delegated staff/reporting/audit operations explicitly approved.

### `RECEPTIONIST + BRANCH`

- only assigned branches;
- only currently approved operational actions;
- no owner-level financial reporting;
- no unrestricted staff management;
- no unrestricted audit or CSV.

Authorization must never depend solely on:

- role name;
- active branch sent by frontend;
- hidden UI controls;
- possession of a UUID;
- storage-key knowledge;
- session data not revalidated against persisted authority.

## 9. Security invariants

The final backend must guarantee:

1. No public staff registration.
2. Invitation and recovery tokens are hashed, expiring, single-use, and never logged.
3. Passwords and hashes never leave identity boundaries.
4. The last active organization administrator is protected.
5. Users cannot self-promote, self-suspend, or self-deactivate through administrative APIs.
6. Branch users cannot access another branch through UUID, query filter, download, report, audit, or CSV.
7. CSRF protects all authenticated mutations.
8. CORS allows only configured frontend origins with credentials.
9. Cookies and sessions use secure environment-specific attributes.
10. Gmail OAuth secrets remain external.
11. Stripe and Gmail payloads/signatures/tokens remain private.
12. PDF, PNG, photos, and CSV downloads are authorized before storage/query access.
13. File and message sizes are bounded.
14. `ProblemDetail` never exposes internals.
15. Logs and metrics remain low-cardinality and sanitized.
16. Actuator endpoints remain protected.
17. Standard tests use no real external network.

## 10. Horizontal-scaling invariants

Critical correctness must remain externalized and database-authoritative:

- payment and webhook idempotency;
- email delivery claims and attempts;
- duplicate QR scan prevention;
- staff invitation acceptance;
- password recovery consumption;
- last-admin checks;
- branch assignments;
- optimistic locking;
- report and audit authorization;
- canonical document metadata.

Process-local state may be used only for safe temporary concerns such as Gmail access-token caching.

The final documentation must state the actual session strategy and its multi-instance limitation or support level.

## 11. Performance objectives

Performance must be measured in a controlled local environment with a documented dataset.

Suggested non-SLA portfolio targets:

- indexed detail lookup p95 below 250 ms;
- ordinary paginated search p95 below 500 ms;
- manual/QR check-in p95 below 500 ms excluding environment startup;
- dashboard summary p95 below 750 ms;
- audit query p95 below 750 ms;
- CSV first-byte startup below 1 second for bounded test data;
- no unbounded memory growth;
- no avoidable N+1 queries;
- no pool saturation under controlled concurrent load;
- no external call without timeout.

Targets may be refined after baseline measurement. Final documentation must not present local figures as a universal production SLA.

## 12. Data-growth review

High-growth tables require explicit review:

- audit entries;
- access attempts;
- payment attempts and status history;
- email delivery attempts;
- staff invitations and recovery tokens;
- assignment history;
- membership periods and coverage snapshots;
- incidents and maintenance history.

For each, document:

- expected growth driver;
- primary queries;
- retention requirement;
- current indexes;
- archive/partition trigger for future work;
- why partitioning is or is not needed now.

## 13. Deployment target retained

Deferred final deployment:

```text
Vercel Hobby
    -> frontend

Render Free
    -> Spring Boot backend

Supabase Free
    -> PostgreSQL
    -> private object storage

Gmail API
    -> transactional and identity email over HTTPS

Stripe Test Mode
    -> payment-provider demonstration
```

Nothing is provisioned or deployed in this branch.

## 14. Block dependency graph

```text
Block 1: Full-system audit, threat model, ADR reconciliation, and hardening backlog
    -> Block 2: Authorization, branch isolation, IDOR, sessions, and account security
        -> Block 3: Database, migrations, transactions, constraints, and concurrency
            -> Block 4: Query performance, reporting reconciliation, CSV, and resource limits
                -> Block 5: Gmail, Stripe, storage, external I/O, and resilience
                    -> Block 6: Observability, health, logs, metrics, and operational readiness
                        -> Block 7: API/OpenAPI freeze, frontend contract, Docker, and deployment readiness
                            -> Block 8: Independent full-system quality gate and backend closure
```

## 15. Progress model

- Block 1: 10%
- Block 2: 24%
- Block 3: 39%
- Block 4: 54%
- Block 5: 67%
- Block 6: 78%
- Block 7: 90%
- Block 8: 100%

Progress is earned only after block completion criteria and validation pass.

---

# Block 1: Full-system audit, threat model, ADR reconciliation, and hardening backlog

- **Status:** `COMPLETED`
- **Target progress:** 10%

## Objective

Relearn the entire current backend, reconcile all ADRs and blueprint decisions, identify evidence-based risks introduced by multisucursal growth, and create the exact hardening backlog without broad production changes.

## Required work

1. Map every module and exposed package.
2. Map every controller, route, role, scope, CSRF rule, and public/token-protected endpoint.
3. Map every branch-owned resource and query.
4. Map every user/session/context lifecycle.
5. Map every public event and consumer.
6. Map every external I/O path.
7. Map every migration, constraint, trigger, and index.
8. Map every report, audit query, and CSV query.
9. Find process-local correctness state.
10. Find unbounded inputs, queries, ranges, collections, payloads, exports, retries, and responses.
11. Find sensitive-data logging or metric risks.
12. Inspect dependency versions and supported security tooling.
13. Run baseline architecture, compile, and selected test/performance checks.

## Threat model

Create or update a concise threat model covering at least:

- authentication and session theft;
- CSRF and CORS misuse;
- branch IDOR;
- privilege escalation;
- invitation/recovery token theft;
- last-admin removal;
- Gmail OAuth secret leakage;
- Gmail relay abuse;
- Stripe webhook forgery/duplication;
- file path/object authorization;
- CSV injection;
- audit-data leakage;
- report side-channel leakage;
- denial of service through unbounded queries/exports;
- concurrency races;
- stale branch/session authority;
- free-tier resource exhaustion.

## ADR reconciliation

Review all relevant accepted ADRs and identify contradictions, obsolete transitional statements, and final target decisions.

At minimum reconcile:

- modular-monolith decision;
- organization/branch ownership;
- staff scope and active branch context;
- membership coverage/access policy;
- invitation/provisioning;
- Gmail transport and identity-email durability boundaries;
- reporting/audit branch visibility;
- deployment deferral and free-tier topology.

Do not silently rewrite accepted decisions. Amend only with explicit evidence.

## Finding report

Create a tracked report under the repository's documentation convention containing:

```text
Finding ID
Severity
Affected capability
Evidence
Risk
Authorized remediation block
Verification method
Status
```

Severity:

```text
CRITICAL
HIGH
MEDIUM
LOW
```

No secret or personal runtime data belongs in the report.

## Allowed production scope

Only minimal corrections needed to make discovery, contracts, or existing tests accurate. Broad remediations belong to later blocks.

## Required validation

```powershell
.\gradlew compileJava compileTestJava
```

Run:

```powershell
.\gradlew test `
  --tests "*ApplicationModuleStructureTests.verifiesModuleBoundaries" `
  --rerun-tasks
```

Run focused current security/reporting/email/database smoke tests and:

```powershell
git diff --check
```

Record current `clean check` result and duration, but do not broaden scope solely to fix unrelated low-priority findings.

## Explicit exclusions

- no speculative indexes;
- no new cache;
- no new infrastructure;
- no mass refactor;
- no frontend;
- no deployment.

## Completion criteria

- complete current-state map exists;
- threat model is current;
- ADR contradictions are resolved or logged;
- evidence-based backlog assigns every remediation to a later block;
- no unsupported production claim is made;
- Codex reports and stops.

---

# Block 2: Authorization, branch isolation, IDOR, sessions, and account security

- **Status:** `COMPLETED`
- **Depends on:** Block 1
- **Target progress:** 24%

## Objective

Close authorization and identity risks across all routes, services, reports, downloads, invitations, recovery, sessions, branch context, audit, and CSV.

## Complete authorization matrix

Validate every approved actor against every resource family:

```text
anonymous
ADMIN + ORGANIZATION
ADMIN + BRANCH
RECEPTIONIST + BRANCH
suspended account
deactivated account
invalid/missing scope
ended assignment
inactive branch
stale active branch context
```

Resource families:

- staff profile;
- organizations and branches;
- invitations and staff lifecycle;
- staff assignments and scope;
- clients and photos;
- plans, memberships, and periods;
- payments and corrections;
- receipts;
- credentials;
- manual and QR access;
- equipment, incidents, maintenance, notifications;
- email deliveries;
- dashboards and reports;
- audit list/detail/CSV;
- settings and access policies;
- downloads.

## Branch IDOR review

For every branch-owned resource, test:

- detail by foreign UUID;
- update by foreign UUID;
- lifecycle action by foreign UUID;
- history by foreign UUID;
- download by foreign UUID;
- email action by foreign UUID;
- search/filter with foreign branch;
- report with foreign branch;
- audit detail/export with foreign branch;
- child-resource mismatch.

Authorization must happen before sensitive content is loaded or streamed.

## Sessions and active branch

Verify:

- login session fixation protection;
- logout invalidation;
- suspended/deactivated account rejection;
- password change/reset invalidation;
- scope/assignment changes invalidate stale context;
- branch deactivation invalidates active context;
- session expiration;
- current-user response refreshes authority;
- actual horizontal-scaling session behavior is documented;
- no session ID appears in logs or APIs.

## Invitation and recovery

Verify:

- no public signup;
- token hashing/expiry/single use;
- resend rotates tokens;
- generic recovery response;
- rate limits;
- no token in durable email outbox, audit, logs, metrics, events, or delivery APIs;
- concurrent acceptance/reset safety;
- bootstrap one-time behavior;
- admin reauthentication;
- last-admin protection.

## CSRF, CORS, cookies, and headers

Revalidate:

- every authenticated mutation has CSRF;
- only legitimate external webhook/token routes are exempt under explicit policy;
- exact frontend-origin allowlist;
- credentials with no wildcard origin;
- cookie `HttpOnly`;
- cookie `Secure` in deployment;
- appropriate `SameSite`;
- no-store on sensitive/download/token responses;
- HSTS only under HTTPS deployment;
- `nosniff`, frame, referrer, and CSP policies where appropriate.

## Abuse controls

Review:

- login attempts;
- invitation create/resend/accept;
- password recovery;
- Gmail sending/retry;
- QR check-in;
- audit query/export;
- reporting ranges;
- file uploads/downloads;
- Stripe webhook size/rate.

Rate limits must not become the sole authorization control.

## Required tests

- complete role/scope matrix;
- complete cross-branch IDOR matrix;
- session lifecycle;
- invitation/recovery security;
- last-admin concurrency;
- CSRF/CORS/cookies/headers;
- abuse limits;
- safe 401/403/404 behavior;
- no unauthorized database/query execution where testable;
- no sensitive leak through errors/logs/audit/metrics.

## Explicit exclusions

- no new identity feature;
- no MFA;
- no OIDC;
- no frontend;
- no multi-tenant logic.

## Validation

Compile, focused security suites, MockMvc integration, PostgreSQL authorization tests, identity/email/audit regressions, and Spring Modulith verification.

**Completion evidence (2026-09-29):** `compileJava compileTestJava`, focused
authorization/session/identity/audit/Flyway/Actuator regressions, the HTTP
login rate-limit contract, Spring Modulith verification, and `clean check`
passed. The full check reported 2,933 tests, 0 failures, 0 errors and 0
skipped tests. V43 is present exactly once in the 43-version Flyway chain.
The login limiter is bounded, safely reclaims expired identifiers, preserves
active blocked identifiers at capacity, and explicitly remains process-local.

## Completion criteria

- every route has explicit authorization behavior;
- cross-branch IDOR is closed;
- stale authority cannot persist indefinitely;
- account and token flows are secure;
- CSRF/CORS/session behavior is frontend-ready;
- Codex reports and stops.

---

# Block 3: Database, migrations, transactions, constraints, and concurrency

- **Status:** `COMPLETED`
- **Depends on:** Blocks 1 and 2
- **Target progress:** 39%

## Objective

Verify and harden the complete PostgreSQL model, Flyway chain, transaction boundaries, constraints, immutable history, idempotency, optimistic locking, and concurrency behavior.

## Flyway review

1. List every migration numerically.
2. Search all Git history and relevant branches.
3. Verify no applied migration changed.
4. Validate clean database migration.
5. Validate evolved/current database migration.
6. Inspect migration checksums.
7. Inspect branch backfills and canonical seeds.
8. Inspect all triggers and append-only protections.
9. Verify no repair workaround exists.
10. Document the actual latest migration.

## Constraint review

Verify database enforcement for:

- unique users/emails according to identity rules;
- invitation/recovery token uniqueness;
- one active invitation/token policy;
- role/scope combinations where feasible;
- staff active-assignment uniqueness;
- branch and organization code uniqueness;
- branch ownership foreign keys;
- membership coverage cardinality and snapshots;
- payment/access/email idempotency;
- append-only histories;
- status metadata consistency;
- nonnegative versions;
- restrictive deletion;
- no destructive cascades.

## Transaction review

Verify:

- application-layer transaction ownership;
- read-only reporting/query transactions;
- no avoidable external I/O under database locks;
- payment/receipt/credential consistency;
- provisioning transaction;
- membership snapshot transaction;
- access-attempt transaction;
- email send intent/finalization separation;
- audit event timing;
- rollback and compensation.

## Concurrency matrix

At minimum test:

- duplicate payments/provider events;
- payment correction races;
- receipt generation;
- credential issue/replace/revoke;
- duplicate QR scans across branches;
- plan coverage update versus membership sale;
- policy override versus access;
- duplicate staff invitation;
- concurrent invitation acceptance;
- last-admin demotion/deactivation;
- assignment ending versus operation;
- password reset concurrency;
- email claim/retry/finalization;
- audit export during new entries;
- stale optimistic versions.

## Connection-pool review

- bounded Hikari configuration;
- connection acquisition timeout;
- idle/lifetime settings;
- Supabase free-tier connection guidance;
- total connections per Render instance;
- no pool exhaustion under controlled tests;
- no leak-detection setting in production unless justified.

## Data-growth and retention documentation

Document future retention/archival triggers. Do not delete records or add partitioning without an approved policy.

## Required tests

- clean/current Flyway;
- constraints and restrictive FKs;
- append-only triggers;
- rollback injection;
- concurrency/races;
- optimistic locking;
- idempotency;
- no partial rows;
- no deadlocks under controlled tests;
- pool property validation.

## Explicit exclusions

- no speculative partitioning;
- no sharding;
- no read replicas;
- no destructive migration rewrite;
- no cache as authority.

## Validation

Compile, migration and PostgreSQL suites, module persistence regressions, concurrency suites, and Spring Modulith verification.

## Completion criteria

- Flyway chain is reproducible;
- critical invariants are database or transaction protected;
- races have deterministic outcomes;
- no partial business state survives failures;
- pool strategy is documented and bounded;
- Codex reports and stops.

## Completion evidence (2026-09-30)

- The current chain is V1–V43, contiguous and unique; V43 is the actual latest
  migration. No applied migration changed and no new migration was necessary.
- PostgreSQL migration, schema, constraint, history, restrictive-delete,
  rollback, and existing-data tests passed.
- PostgreSQL concurrency coverage passed for payment correction, duplicate
  provider events, receipt generation, credential lifecycle, duplicate QR
  scans, staff invitation/acceptance, last-administrator and assignment
  behavior, password recovery, plan coverage versus membership sale, access
  policy versus check-in, competing durable email lease claims, and audit
  export while a new row is committed during streaming.
- Hikari remains bounded to 5 connections by default and 10 maximum per
  application instance; twelve concurrent requests completed under a
  five-connection test pool without a leaked active connection.
- Retention periods remain unapproved. No deletion, archival, partitioning, or
  schema change was introduced; growth and recovery-budget signals are recorded
  in the findings report as triggers for a future approved policy.
- Spring Modulith boundary verification passed. Block 4 was not started.

---

# Block 4: Query performance, reporting reconciliation, CSV, and resource limits

- **Status:** `COMPLETED`
- **Depends on:** Blocks 1 to 3
- **Target progress:** 54%

## Objective

Validate critical PostgreSQL queries, indexes, pagination, reporting correctness, audit/CSV safety, memory behavior, and all externally influenced limits.

## Critical query review

Inspect actual SQL and plans for:

- client search/profile;
- plans and memberships;
- membership coverage eligibility;
- payment lists and confirmed-payment checks;
- Stripe attempt/event lookup;
- manual and QR access;
- duplicate-scan detection;
- equipment/incidents/maintenance;
- email delivery claims/history;
- staff invitations and recovery;
- dashboard summaries and trends;
- branch comparisons;
- audit list/detail;
- audit CSV export.

## Index rules

- add only evidence-supported indexes;
- avoid redundant left-prefix indexes;
- account for write overhead;
- use partial indexes only for stable predicates;
- validate through PostgreSQL query plans;
- document why each new index exists.

## Pagination and range rules

- maximum page size;
- deterministic tie-breaker;
- bounded date range;
- bounded branch comparison set;
- bounded time-series buckets;
- allowlisted sorts;
- parameterized SQL;
- no global load then Java filter;
- no unbounded `findAll` in web paths.

## Reporting reconciliation

Verify with deterministic fixtures:

- organization revenue equals qualifying branch revenue sum;
- no payment or membership join multiplication;
- `PaymentAttempt` excluded from revenue;
- `VOIDED` and `REFUNDED` semantics correct;
- membership coverage rows do not multiply memberships;
- access counts equal canonical attempts;
- email attempts do not multiply logical deliveries;
- inactive branch history remains visible to authorized users;
- currencies remain separate.

## CSV security and performance

Verify:

- exact branch scope;
- row limits;
- mandatory ranges;
- no silent truncation;
- streaming;
- bounded memory;
- no temp files;
- fixed columns;
- UTF-8;
- formula injection defense including leading whitespace;
- metadata sanitization;
- safe headers and filenames;
- export audit without recursion.

## General resource limits

Review and test:

- JSON/body size;
- multipart/image size/dimensions;
- PDF/PNG/email attachment size;
- Gmail raw-message size;
- OAuth/Gmail response size;
- CSV rows/range;
- report range/branches/buckets;
- retries;
- page sizes;
- password/token lengths;
- collection fields.

## Performance baseline

Create or reuse a repository-approved controlled test harness. Record:

- dataset size;
- hardware/environment;
- concurrency;
- warmup;
- p50/p95;
- database pool behavior;
- memory behavior;
- limitations.

Do not depend on real Gmail, Stripe, Supabase, Render, or Vercel.

## Required tests

- query plans;
- index use/nonredundancy;
- pagination/ranges;
- reporting reconciliation;
- CSV injection/streaming/memory;
- no N+1;
- representative p95;
- resource-limit rejection;
- safe errors.

## Explicit exclusions

- no materialized views without evidence;
- no cache without amended ADR;
- no external load test against free-tier services;
- no frontend charts.

## Validation

Compile, PostgreSQL query-plan suites, reporting and audit regressions, resource-limit tests, controlled performance checks, and architecture verification.

## Completion criteria

- critical queries are bounded and index-supported;
- reporting totals reconcile;
- CSV is secure and memory-safe;
- resource limits are explicit;
- performance is measured honestly;
- Codex reports and stops.

## Completion evidence (2026-09-30)

- PostgreSQL query-plan and reconciliation suites passed for client search,
  access, reporting aggregates, operations, payment correction reports, and
  branch-scoped audit queries. Existing indexes were sufficient; no migration
  or production SQL change was warranted.
- CSV security and bounded streaming tests passed, including a 4,096-row
  forward-only export consumed by a counter-only sink, fetch size 256, and
  overflow rejection before any row is written. Heap usage was not directly
  instrumented and is explicitly recorded as a measurement limitation.
- Request-body, upload/document, Gmail/OAuth, page/range, and report limits
  passed focused tests, including oversized chunked JSON with unknown content
  length returning a generic 413 and configuration bounds/defaults tests.
- The performance harness now measures the actual access-trend route and
  records local p50/p95 with dataset, environment, pool, concurrency, and
  warmup metadata. These Testcontainers results are not production SLAs.
- A client-search query-budget test confirms the JDBC projection makes one
  count query and one paged data query, with no per-row lookup.
- `compileJava compileTestJava`, focused suites, diff hygiene, and the
  independent Spring Modulith boundary check passed. No module contract or
  dependency changed in Block 4.

---

# Block 5: Gmail, Stripe, storage, external I/O, and resilience

- **Status:** `COMPLETED`
- **Depends on:** Blocks 1 to 4
- **Target progress:** 67%

## Objective

Harden every external integration and file path for timeouts, retries, idempotency, secrets, failure isolation, and future multi-instance deployment.

## Gmail API

Verify:

- Gmail is the only real email transport;
- OAuth refresh-token secret externalized;
- access-token cache memory-only;
- concurrent token refresh single-flight;
- HTTPS restrictions;
- MIME text/HTML/PDF/PNG correctness;
- message-size limits;
- one safe auth refresh retry;
- no general unbounded retry;
- ambiguous outcome handling;
- `SENT` means Gmail acceptance;
- optional live task excluded from `clean check`;
- invitation/recovery token confidentiality;
- durable versus ephemeral email boundaries;
- account-activation metadata does not persist token/content;
- no old Mailpit/SMTP/Resend runtime remnants.

## Stripe Test Mode

Verify:

- Test Mode only;
- live credentials rejected if policy requires;
- webhook signature verification;
- request-size bounds;
- persistent event idempotency;
- duplicate/out-of-order behavior;
- timeouts;
- no provider SDK outside infrastructure;
- no sensitive payload logging;
- payment truth remains persisted `Payment` state;
- no access-time Stripe call.

## Storage

Verify local and Supabase adapters:

- server-generated keys;
- private objects;
- path traversal prevention;
- content type/signature/checksum/size;
- atomic/staged writes;
- compensation;
- authorization before read;
- no public permanent URL required;
- no local-disk assumption for deployed multi-instance mode;
- old storage keys remain compatible;
- no storage path in APIs/logs/audit.

## External I/O resilience

- strict connect/read/write timeouts;
- response-size limits;
- bounded safe retries only for idempotent/definitively rejected operations;
- no fallback that changes business truth;
- no external I/O under avoidable DB locks;
- partial failures preserve source aggregates;
- readiness does not perform constant external sends/calls;
- graceful shutdown behavior for active operations;
- multi-instance ownership through DB claims/idempotency.

## Required tests

- Gmail OAuth/MIME/errors/concurrency/privacy;
- Stripe signature/duplicate/order/timeouts/privacy;
- storage integrity/path/auth/compensation;
- failure isolation;
- ambiguous outcomes;
- no real network;
- no secret leakage;
- optional live task isolation;
- external dependency outage behavior.

## Explicit exclusions

- no commercial email provider;
- no Stripe Live Mode;
- no cloud provisioning;
- no message broker;
- no service extraction.

## Validation

Compile, Gmail/Stripe/storage/resilience regressions, secret scans, architecture tests, and Spring Modulith verification.

## Completion criteria

- all external I/O is bounded and private;
- provider failures do not corrupt business state;
- multi-instance ownership is safe;
- standard tests remain offline;
- Codex reports and stops.

## Completion evidence (2026-09-30)

- Gmail, Stripe Test Mode, durable and ephemeral email, payment attempt/event,
  receipt, access credential, storage, and PostgreSQL-backed delivery/storage
  regressions passed using offline stubs and Testcontainers. No live provider
  request was made.
- Supabase Storage now consumes successful object responses incrementally and
  cancels once the expected object size or 12 MiB object ceiling is exceeded;
  provider error bodies are capped at 8 KiB. An offline streaming-server test
  proves the client stops before receiving a 32 MiB response. Dot-segment
  bucket names are rejected.
- Gmail's JDK HTTP client has one whole-exchange deadline, not independent
  read/write socket deadlines. The adapter now uses the stricter of the
  configured read and write bounds; configuration tests cover either one being
  stricter. Connection establishment remains separately bounded.
- The Stripe adapter retains Test Mode, disabled automatic SDK retries, and
  per-attempt idempotency keys; verified provider events remain persisted and
  idempotent. No new retry path or payment-truth shortcut was introduced.
- `compileJava compileTestJava`, the focused Gmail/storage suite, the broader
  Gmail/Stripe/storage/payment/email regression scope, the Spring Modulith
  boundary check, and `git diff --check` passed. No migration, module contract,
  or module dependency was added or changed.
- Optional live Gmail tests remain opt-in and were not run. Production
  credentials, provider quotas, and deployed multi-instance topology remain
  operational verification items, not local-test claims.

---

# Block 6: Observability, health, logs, metrics, and operational readiness

- **Status:** `COMPLETED`
- **Depends on:** Blocks 1 to 5
- **Target progress:** 78%

## Objective

Provide final safe observability and operational controls without leaking personal, financial, identity, branch, provider, or token data.

## Health and readiness

Review:

- liveness;
- readiness;
- PostgreSQL;
- Flyway startup;
- storage configuration;
- Gmail configuration;
- Stripe configuration;
- disk/temp requirements;
- management endpoint security;
- no external email/payment action in health probes;
- degraded versus not-ready semantics.

Do not publicly expose sensitive actuator endpoints such as environment, beans, heap dumps, mappings, config props, or loggers.

## Metrics

Safe bounded metrics may include:

- normalized HTTP route/status/latency;
- login result;
- access result/reason/source;
- payment/webhook bounded outcome;
- email delivery type/outcome/failure code;
- OAuth refresh outcome;
- invitation/recovery outcome;
- audit export outcome;
- report endpoint latency/result;
- Hikari/JVM/process metrics.

Do not tag with:

- user/client/branch/resource IDs;
- email address;
- delivery ID;
- provider message ID;
- subject;
- token;
- exception text;
- free-form metadata.

## Logging

- correlation/request ID;
- consistent structured/key-value style;
- no request bodies on sensitive routes;
- no response bodies for confidential resources;
- no passwords/tokens/signatures/provider payloads/MIME/attachment bytes;
- no SQL parameters containing private data;
- bounded message length;
- expected business denials at appropriate level;
- unexpected failures with safe context;
- no branch/user IDs as indiscriminate log noise.

## Operational indicators

Where data already exists, provide or document bounded detection for:

- stale email claims/pending deliveries;
- repeated Gmail/Stripe failures;
- invalid or expiring invitation/recovery patterns;
- missing/corrupt artifacts;
- excessive denied access;
- slow report/audit queries;
- pool saturation.

Do not create a large new admin feature. Metrics, health, and runbooks may be sufficient.

## Required tests

- actuator authorization;
- liveness/readiness;
- failed DB/config/storage state;
- no mutation in health probes;
- metric names and bounded tags;
- expected metric increments;
- correlation propagation;
- sensitive log capture;
- no startup secret exposure;
- no PII or high-cardinality metrics.

## Explicit exclusions

- no external APM vendor;
- no distributed tracing platform;
- no centralized log stack;
- no alerting vendor integration.

## Validation

Compile, actuator/metrics/logging tests, security regressions, and architecture verification.

## Completion criteria

- operators can distinguish live, ready, degraded, and failed states;
- logs and metrics support investigation safely;
- management endpoints are protected;
- no sensitive telemetry leakage remains;
- Codex reports and stops.

---

# Block 7: API/OpenAPI freeze, frontend contract, Docker, and deployment readiness

- **Status:** `COMPLETED`
- **Depends on:** Blocks 1 to 6
- **Target progress:** 90%

## Objective

Freeze and document the final backend contract for frontend implementation, clean the local stack, and prepare the deferred zero-budget deployment runbook without deploying.

## API inventory and consistency

Review every public endpoint for:

- route/version consistency;
- HTTP method/status semantics;
- request validation;
- response DTOs;
- pagination;
- filters/sorts;
- dates/timezones;
- money/currency;
- enum values;
- null/empty behavior;
- downloads;
- role/scope/branch authorization;
- CSRF;
- `ProblemDetail`;
- OpenAPI coverage.

Breaking changes require explicit documentation and complete test updates.

## Authentication/frontend contract

Document final flows:

- no public signup;
- login;
- current user;
- logout;
- session expiration;
- CSRF acquisition/use;
- invitations;
- activation;
- password recovery;
- staff self-profile;
- role/scope;
- available and active branches;
- account state;
- permission-denied behavior.

## Domain frontend contract

Document:

- clients;
- plans and membership coverage;
- memberships/periods;
- payments/corrections/receipts;
- credentials and check-in;
- branch policies;
- equipment/incidents/maintenance;
- email deliveries;
- dashboards/reports;
- audit and CSV;
- download headers;
- `ProblemDetail` structure.

The frontend must not guess hidden rules.

## OpenAPI freeze

Verify every implemented operation and schema. Public schemas must not expose:

- persistence entities;
- storage keys;
- OAuth/Gmail/Stripe internals;
- password hashes;
- tokens;
- internal audit metadata;
- unsupported roles;
- multi-tenant fields.

## Docker Compose/local development

Final local stack should include only necessary services, expected to include PostgreSQL if Compose-managed.

Mailpit, SMTP, and Resend must not reappear.

Validate:

- health checks;
- service dependencies;
- named volumes;
- ports;
- no secrets;
- deterministic reset/start instructions;
- Gmail disabled by default;
- standard tests independent of Compose external services where intended.

## Deployment runbook

Document the deferred process for:

1. Supabase PostgreSQL project.
2. Supabase private storage buckets.
3. Google Cloud project and Gmail API.
4. Dedicated Gmail account and OAuth secrets.
5. Render backend service.
6. Vercel frontend.
7. Stripe Test Mode secrets/webhook.
8. CORS/cookies/CSRF/public URLs.
9. Flyway migration strategy.
10. Initial administrator bootstrap.
11. Smoke tests.
12. Secret rotation.
13. Rollback.
14. Backup/restore.

No resource is created in this branch.

## Backup and recovery guide

Document:

- PostgreSQL backup;
- storage-object backup relationship;
- restore order;
- Flyway behavior after restore;
- secrets responsibility;
- recovery validation;
- free-tier limitations;
- recommendation to test restore periodically.

## Required tests/contracts

- OpenAPI path/schema/security inventory;
- common error structure;
- frontend CORS/CSRF integration tests;
- download behavior;
- Compose syntax/configuration;
- profile binding;
- Gmail disabled default;
- no obsolete transport configuration;
- documentation links/config names;
- no secret placeholders that resemble real credentials.

## Explicit exclusions

- no frontend implementation;
- no deployment;
- no cloud automation;
- no Kubernetes/Terraform;
- no real secrets.

## Validation

Compile, OpenAPI/security/contract tests, Compose/configuration validation, documentation checks, and Spring Modulith verification.

## Completion criteria

- backend API is frozen and documented for frontend work;
- local setup is reproducible;
- deferred deployment is fully specified;
- no old email infrastructure remains;
- frontend can begin without contract guessing;
- Codex reports and stops.

---

# Block 8: Independent full-system quality gate and backend closure

- **Status:** `COMPLETED`
- **Depends on:** Blocks 1 to 7
- **Target progress:** 100%

## Objective

Perform an independent final review of the entire backend, close all critical/high findings, execute comprehensive regression and performance validations, update the blueprint, and formally declare the backend ready for frontend development.

## Full functional regression

Verify:

- authentication/session/logout;
- staff self-profile/photo/password;
- organization/branches;
- staff scope/assignments/active branch;
- clients/photos;
- plans/promotions/membership coverage;
- memberships/periods;
- payments/corrections/Stripe;
- receipts;
- credentials;
- manual/QR access;
- branch access-payment policy;
- invitations/activation/recovery/lifecycle/bootstrap;
- Gmail email delivery;
- equipment/incidents/maintenance/notifications;
- dashboards/reporting;
- audit query/detail/CSV;
- settings/storage;
- health/metrics/OpenAPI.

## Two-branch and organization-wide end-to-end dataset

Create controlled fixtures for:

- organization ADMIN;
- branch ADMINs;
- receptionists;
- active/inactive branches;
- cross-branch clients/resources;
- all plan coverage types;
- all payment states;
- manual/QR access outcomes;
- identity/account states;
- email success/failure/ambiguous outcomes;
- audit global/branch/unknown entries;
- reporting reconciliation.

## Final security gate

- endpoint authorization inventory;
- IDOR;
- CSRF/CORS/cookies/headers;
- invitations/recovery;
- last-admin;
- sessions;
- Gmail/Stripe/storage secrets;
- files/downloads;
- CSV injection;
- audit sanitization;
- `ProblemDetail` privacy;
- logs/metrics/health;
- dependency analysis using project-supported tooling;
- no unsupported role;
- no public registration;
- no multi-tenant leak.

## Final database gate

- migration list and Git-history integrity;
- clean database;
- evolved database;
- constraints/triggers;
- indexes/nonredundancy;
- critical query plans;
- backup/restore documentation;
- no placeholders or repair workarounds.

## Final performance gate

Measure and report:

- representative searches;
- payment detail/list;
- manual/QR access;
- dashboard;
- financial/access trends;
- audit query/detail;
- CSV startup/completion;
- email delivery through HTTP stub;
- pool and memory behavior.

Document environment and limitations.

## Final architecture gate

- no module cycle;
- no internal cross-module imports;
- public contracts/events only;
- infrastructure libraries confined;
- application transactions explicit;
- controllers thin;
- source-owned reporting queries;
- provider-neutral external boundaries;
- no process-local critical authority;
- Spring Modulith passes.

## Repository hygiene

Reject:

- secrets/tokens/credentials;
- `.env` files;
- generated emails/PDFs/PNGs/CSVs;
- data directories;
- database dumps;
- temporary scripts;
- ZIP files;
- build output;
- BOM characters;
- HTML entities in source;
- debug prints;
- obsolete provider code/config;
- obsolete deferral tests/flags;
- commented-out code;
- broad unrelated formatting;
- frontend code.

## Required validation sequence

```powershell
.\gradlew compileJava compileTestJava
```

Run focused hardening tests and module regressions in manageable groups.

Run:

```powershell
.\gradlew test `
  --tests "*ApplicationModuleStructureTests.verifiesModuleBoundaries" `
  --rerun-tasks
```

Run project-supported dependency/security analysis.

Run query-plan/performance checks.

Run:

```powershell
git diff --check
```

Finally:

```powershell
.\gradlew clean check
```

Do not run live Gmail or other real-provider tests automatically. Report optional live-test status separately.

## Blueprint closure

Update or report exact changes so the blueprint states:

- `feature/multi-branch-hardening` complete;
- backend complete for current approved scope;
- no backend branches pending before frontend;
- next branch `feature/frontend-foundation-auth`;
- final migration version;
- final architecture and deployment assumptions;
- known limitations;
- deferred commercial evolution.

## Final deliverables

- final hardening findings report;
- updated threat model;
- reconciled ADRs;
- final authorization matrix;
- final metric catalog/reconciliation report;
- performance baseline;
- deployment runbook;
- backup/restore guide;
- recruiter demo guide;
- frontend backend-contract guide;
- final OpenAPI contract;
- final quality-gate report;
- blueprint update.

## Completion criteria

The backend reaches current-scope 100% only when:

- all eight blocks pass;
- no critical finding remains;
- no high finding remains unless externally blocked and explicitly accepted;
- medium/low findings are documented and nonblocking;
- all functional regressions pass;
- security gate passes;
- migration/database gate passes;
- reporting reconciliation passes;
- performance is measured and acceptable or honestly documented;
- OpenAPI/frontend contract is stable;
- local startup is reproducible;
- deployment path is documented;
- Spring Modulith passes;
- repository hygiene passes;
- `clean check` succeeds;
- final verdict is `PASS` or `PASS WITH NON-BLOCKING NOTES`;
- Codex stops.

---

# 16. Complete final test matrix

## Identity and account security

- no public signup;
- invitations;
- activation;
- recovery;
- lifecycle;
- role/scope;
- branch assignments;
- bootstrap;
- session invalidation;
- last admin;
- abuse limits.

## Branch isolation

- create/read/update/history/download/email/report/audit/export IDOR;
- active/inactive branch;
- ended assignment;
- stale context;
- organization/global behavior;
- safe errors.

## Clients, memberships, and plans

- home branch;
- registration branch;
- coverage scopes;
- snapshots;
- renewals;
- concurrent plan changes;
- branch eligibility.

## Payments and Stripe

- manual payment;
- attempts/webhooks;
- corrections;
- receipts;
- confirmed-payment access policy;
- idempotency;
- privacy;
- branch ownership.

## Access and credentials

- credential lifecycle;
- manual/QR access;
- payment/coverage denial;
- duplicate scans across branches;
- immutable attempts;
- performance.

## Operations

- equipment;
- incidents;
- maintenance;
- notifications;
- branch consistency;
- role visibility.

## Gmail and email domain

- OAuth;
- MIME;
- durable deliveries;
- ephemeral identity emails;
- activation ledger;
- retries/ambiguous outcomes;
- secrets;
- metrics/health;
- no real network.

## Reporting and audit

- financial reconciliation;
- memberships/access/operations;
- branch comparison;
- role visibility;
- audit list/detail;
- CSV streaming/injection/limits;
- history attribution;
- query performance.

## Platform/security

- CSRF;
- CORS;
- cookies;
- headers;
- ProblemDetail;
- Actuator;
- logs;
- metrics;
- request/file limits;
- dependencies;
- OpenAPI.

## Database/architecture

- Flyway clean/current;
- constraints/triggers;
- indexes/query plans;
- concurrency/rollback;
- pool;
- module boundaries;
- public contracts/events;
- Spring Modulith.

# 17. Final Definition of Done

- [x] Block 1 completed and validated
- [x] Block 2 completed and validated
- [x] Block 3 completed and validated
- [x] Block 4 completed and validated
- [x] Block 5 completed and validated
- [x] Block 6 completed and validated
- [x] Block 7 completed and validated
- [x] Block 8 completed and validated
- [x] Full-system inventory completed
- [x] Threat model updated
- [x] ADRs reconciled against available canonical source and accepted plan decisions; missing ADR-011/012 source files are recorded as a low documentation follow-up
- [x] No critical findings open
- [x] Authorization matrix verified
- [x] Cross-branch IDOR verified
- [x] Session and active-branch invalidation verified
- [x] Invitation/recovery/account security verified
- [x] Last-admin protection verified
- [x] CSRF/CORS/cookies/headers verified
- [x] Flyway clean/current verified
- [x] Constraints/triggers/FKs verified
- [x] Concurrency/idempotency verified
- [x] Query plans and indexes verified
- [x] Reporting reconciliation verified
- [x] CSV security and streaming verified
- [x] Gmail OAuth/MIME/privacy verified
- [x] Stripe Test Mode security verified
- [x] Storage privacy/integrity verified
- [x] Health/readiness protected
- [x] Logs and metrics sanitized
- [x] Performance baseline recorded
- [x] OpenAPI frozen and accurate
- [x] Frontend contract guide finalized
- [x] Docker/local setup reproducible
- [x] Deployment runbook completed
- [x] Backup/restore guide completed
- [x] Recruiter demo documented
- [x] No real external network in standard tests
- [x] Spring Modulith passes
- [x] Repository hygiene passes
- [x] `clean check` passes
- [x] Blueprint updated
- [x] Next branch set to `feature/frontend-foundation-auth`

# 18. Acceptable documented limitations

The following may remain if accurately documented and noncritical:

- Render Free cold starts;
- Supabase Free quotas and inactivity behavior;
- Gmail API acceptance does not guarantee inbox placement;
- Stripe remains Test Mode;
- local performance results are not production SLAs;
- no automatic cloud backup implementation;
- no paid monitoring/APM;
- no multi-tenancy;
- no full accounting;
- no automatic partitioning;
- optional live Gmail tests are outside `clean check`;
- deployment has not occurred yet;
- future production storage/session enhancements may depend on deployment-specific configuration.

## 19. Deferred future evolution

Potential future work after frontend and deployment feedback:

- production custom domain;
- transactional email provider migration;
- Stripe Live Mode;
- MFA/OIDC;
- client self-service;
- multi-tenancy;
- cloud secret manager;
- object-storage vendor-specific optimization;
- report scheduling;
- data retention/archiving;
- database partitioning;
- dedicated analytics;
- selective worker/service extraction;
- mobile/physical access integration.

## 20. Short prompts for Codex

### Start Block 1

```text
Read the active plan:
docs/branch-plans/multi-branch-hardening-plan.md

Implement only Block 1: Full-system audit, threat model, ADR reconciliation, and hardening backlog.
Do not implement Blocks 2 through 8.
Follow AGENTS.md and the applicable skills, validate the authorized block, report evidence, and stop.
```

### Continue with a later block

```text
Review the current repository, the Block 1 finding report, the threat model, the reconciled ADRs, and all completed previous blocks.
Implement only Block <N>: <EXACT BLOCK NAME> from:
docs/branch-plans/multi-branch-hardening-plan.md

Do not implement later blocks. Apply only evidence-supported changes, validate, report, and stop.
```

### Repair without advancing

```text
The authorized scope remains Block <N>.
Diagnose and fix the failure below without starting the next block:

<PASTE FAILURE>

Classify the root cause, rerun the smallest failing scope, then rerun the block validation. Report evidence and stop.
```

### Final independent review

```text
Do not modify files initially.
Review feature/multi-branch-hardening against:

- docs/branch-plans/multi-branch-hardening-plan.md
- the current blueprint
- AGENTS.md
- the final hardening findings report
- the current repository evidence

Return one verdict:

PASS
PASS WITH NON-BLOCKING NOTES
BLOCKED
FAIL

For every blocking issue, provide:

- severity;
- exact evidence;
- affected files/modules;
- security or correctness impact;
- required remediation;
- validation command.

Do not start frontend work.
```
