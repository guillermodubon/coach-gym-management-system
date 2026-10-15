# Branch Plan: `feature/backend-hardening`

## 1. Branch identity

- **Branch:** `feature/backend-hardening`
- **Capability:** Final production-oriented hardening, scalability preparation, operational readiness, and frontend contract stabilization
- **Previous completed branch:** `feature/audit-csv-export`
- **Backend status before branch:** Functionally complete for the approved current scope
- **Backend status after branch:** Hardened, documented, regression-tested, prepared for the approved zero-cost deployment topology, and ready for frontend development
- **Expected next branch:** `feature/frontend-foundation-auth`
- **Starting progress:** 0%
- **Target progress:** 100%
- **Execution model:** One explicitly authorized block per Codex response
- **Plan owner:** User
- **Implementation agent:** Codex
- **Git ownership:** The user controls staging, commits, pushes, rebases, pull requests, merges, branch deletion, and synchronization with `main`

## 2. Purpose of this branch

This is the final backend branch for the currently approved product scope.

The branch must not add a large new business capability. Its purpose is to make the existing backend safer, faster, more consistent, easier to operate, easier to demonstrate, and easier to extend.

The final backend must remain appropriate for a personal portfolio project while following professional production-oriented practices. It must be capable of evolving into a real application without requiring a full rewrite.

The target architecture remains a Spring Boot modular monolith with:

- business-capability modules;
- explicit public module contracts;
- application-layer transaction boundaries;
- ports and adapters;
- PostgreSQL as the authoritative database;
- Flyway migrations;
- privacy-safe events and audit;
- external integrations behind interfaces;
- deterministic offline tests;
- horizontal-scaling readiness;
- measurable extraction criteria for future services.

## 3. Execution protocol for Codex

This document provides complete branch context. It does not authorize implementation of every block at once.

Codex must implement only the block explicitly requested in the current prompt.

For every block, Codex must:

1. Read the root and applicable nested `AGENTS.md` files.
2. Read this complete branch plan.
3. Read the current blueprint.
4. Inspect the current repository state before editing.
5. Inspect existing production code, tests, migrations, configuration, dependencies, Docker files, documentation, OpenAPI, audit, and module boundaries.
6. Produce a concise block-specific evidence report before making significant architectural changes.
7. Preserve unrelated user changes.
8. Implement only the authorized block.
9. Run the focused validation required by the block.
10. Fix failures introduced by the block.
11. Report evidence and stop.

Codex must not:

- implement frontend code;
- redesign the product;
- add business features not necessary for hardening;
- split the application into microservices;
- introduce Kafka, RabbitMQ, Kubernetes, service meshes, or distributed tracing platforms solely for portfolio appearance;
- add Redis unless a measured requirement and the authorized block justify it;
- add caching without correctness rules, invalidation policy, and evidence;
- add database indexes without query evidence;
- modify an applied Flyway migration;
- run Flyway repair automatically;
- weaken security or tests to simplify a failure;
- use real external services in the standard automated test suite;
- stage, commit, push, rebase, merge, switch branches, or open a pull request;
- create temporary patch scripts or generated data in the repository;
- claim production readiness without explicitly reporting remaining limitations.

## 4. Success definition

At branch completion, the backend must:

1. Preserve every approved business capability and regression test.
2. Have explicitly reviewed module boundaries and transaction boundaries.
3. Be safe to run as more than one backend instance where current infrastructure permits.
4. Avoid correctness that depends on process-local mutable state.
5. Use database-authoritative idempotency, locking, and lifecycle state.
6. Use bounded external I/O with timeouts and safe failures.
7. Have consistent authentication, authorization, CSRF, cookies, headers, and error responses.
8. Protect secrets and sensitive information in configuration, logs, events, audit, APIs, exports, files, and documentation.
9. Have justified PostgreSQL indexes and bounded queries.
10. Have health, readiness, operational metrics, and safe logging suitable for local demonstration and future deployment.
11. Have reproducible Docker-based development and demo startup.
12. Have final, stable OpenAPI contracts suitable for frontend generation or manual integration.
13. Have clear environment and deployment documentation.
14. Pass the full quality gate.

## 5. Explicit non-goals

This branch must not implement:

- frontend pages or frontend authentication;
- new payment methods;
- Stripe Live Mode activation;
- new membership rules;
- new access rules;
- new email delivery types;
- new audit export formats;
- new user roles;
- client self-service;
- public registration;
- multi-tenancy;
- multi-gym ownership;
- microservices;
- event brokers;
- distributed transactions;
- automatic cloud provisioning;
- executing the public deployment before both backend and frontend are complete;
- creating real Supabase, Render, Vercel, or Resend production resources during this branch;
- production cloud storage adapters other than the approved Supabase Storage adapter required to remove the Render ephemeral-filesystem blocker;
- Kubernetes manifests unless the blueprint explicitly requires them;
- speculative optimization without evidence;
- broad rewrites of stable modules;
- destructive schema redesign.

## 5.1 Approved final deployment topology

The approved zero-cost portfolio deployment target is:

```text
Vercel Hobby
Frontend SPA
      |
      | HTTPS, credentialed CORS, session cookie, CSRF
      v
Render Free Web Service
Spring Boot modular monolith
      |
      +-- Supabase PostgreSQL Free
      +-- Supabase Storage Free
      +-- Stripe Test Mode
      +-- Resend HTTP API Free
```

Local development and automated tests use:

```text
Frontend local development server
      -> Spring Boot local/test profile
            -> PostgreSQL/Testcontainers
            -> local filesystem storage or temporary test storage
            -> Stripe fakes/Test Mode contract seams
            -> Mailpit through SMTP for local demonstration
            -> fake email sender for deterministic tests
```

### Provider responsibilities

- **Vercel:** hosts only the frontend application under the personal, non-commercial Hobby plan.
- **Render:** hosts the Spring Boot backend as a free web service.
- **Supabase PostgreSQL:** stores authoritative relational and transactional data.
- **Supabase Storage:** stores client photos, canonical payment-receipt PDFs, and canonical access-credential PNGs.
- **Resend:** sends deployed transactional email through the HTTPS API.
- **Mailpit:** captures local/demo SMTP email only.
- **Stripe:** remains in Test Mode for the current product scope.

### Deployment timing rule

This branch prepares the backend for deployment but does not deploy it.

The database, object storage, backend, frontend, and deployed email transport will be provisioned and deployed together only after:

1. `feature/backend-hardening` is merged;
2. the approved frontend scope is complete;
3. frontend-backend integration tests pass locally;
4. the final deployment checklist is reviewed;
5. the user explicitly authorizes deployment.

No block may require a live Supabase, Render, Vercel, or Resend project to pass the standard build. All provider integrations require offline fakes, contract tests, or local controlled substitutes.

### Honest free-tier limitations

The final documentation must disclose:

- Render Free services can spin down after inactivity and lose local filesystem changes on restart, redeploy, or spin-down;
- Render Free blocks outbound SMTP ports, so deployed mail must use the Resend HTTPS API rather than SMTP;
- Supabase Free has bounded database and object-storage quotas and can pause inactive projects;
- Vercel Hobby is for personal, non-commercial use;
- Resend Free has daily and monthly limits;
- the free topology has no production SLA;
- this topology is suitable for a portfolio demonstration, not a critical commercial operation.

### Upgrade path

The business architecture must not depend on these vendors. Future paid or production migration should require adapter/configuration changes rather than rewriting use cases:

```text
PostgreSQL port/SQL -> Supabase PostgreSQL or another PostgreSQL provider
DocumentStorage     -> local, Supabase Storage, S3, Azure Blob, or equivalent
EmailSender         -> Mailpit SMTP, Resend HTTP API, SMTP, or another provider adapter
Frontend hosting    -> Vercel or another static host
Backend hosting     -> Render or another container/JVM platform
```

## 6. Architecture decision

The backend remains a modular monolith.

This branch must create or finalize an ADR documenting:

- why the modular monolith remains appropriate;
- current deployment assumptions;
- how horizontal scaling works;
- which state must be externalized;
- database and storage dependencies;
- how jobs and retries avoid duplicate processing;
- expected operational limits;
- future extraction candidates;
- measurable extraction triggers.

Potential future extraction candidates may include:

- transactional email workers;
- document generation;
- access-control edge integration;
- analytical reporting.

Extraction must be justified by one or more measurable triggers:

- independently high load;
- independent deployment requirement;
- separate team ownership;
- fault-isolation requirement;
- regulatory boundary;
- significantly different availability objective;
- deployment bottleneck demonstrated by metrics.

## 7. Quality principles

### 7.1 Correctness before optimization

- Preserve business invariants.
- Do not trade correctness for lower latency.
- Do not use cache as an authority.
- Do not hide failures through fallback values that change business meaning.

### 7.2 Bounded work

Every externally influenced collection, query, export, upload, request body, attachment, page, date range, and retry operation must have an explicit limit.

### 7.3 Database authority

Cross-instance correctness must rely on PostgreSQL constraints, transactions, optimistic locking, and persistent idempotency rather than process-local locks.

### 7.4 Privacy by default

Responses, logs, metrics, events, audit records, CSV files, emails, PDFs, PNGs, exceptions, and documentation must expose only approved information.

### 7.5 Operational honesty

Documentation must distinguish:

- what is implemented;
- what is demo-only;
- what is production-configurable;
- what remains a known limitation;
- what guarantees the system actually provides.

## 8. System-wide inventory required before Block 1

Codex must rebuild the current backend map from the repository and inspect:

- every Spring Modulith module and exposed package;
- module dependencies and cycles;
- public events and consumers;
- every controller and protected route;
- every application service and transaction annotation;
- every Flyway migration in numeric order;
- entities, JDBC adapters, repositories, native SQL, and indexes;
- all scheduled jobs and background processing;
- process-local maps, caches, locks, semaphores, random state, and temporary files;
- file/document storage implementations;
- Stripe Test Mode integration;
- webhook signature and idempotency behavior;
- SMTP and Mailpit integration;
- payment receipt and QR generation;
- audit query and CSV export;
- authentication, sessions, cookies, CSRF, CORS, headers, and method security;
- ProblemDetail handlers and error codes;
- Actuator and operational endpoints;
- metrics, logs, and correlation identifiers;
- dependency versions and known deprecations;
- Docker Compose and profiles;
- test architecture and Testcontainers;
- README, blueprint, ADRs, API documentation, and demo instructions;
- current `clean check` behavior and duration.

The inspection must produce a ranked finding list:

- **Critical:** security, data-loss, authorization, financial-integrity, or secret-exposure risk.
- **High:** cross-instance correctness, unbounded resource use, inconsistent failures, or major operability gap.
- **Medium:** maintainability, performance, documentation, or test-coverage weakness.
- **Low:** cleanup or optional improvement.

Only findings supported by repository evidence may become production changes.

## 9. Scalability target

This branch does not promise infinite scale. It prepares the backend for realistic growth within a modular-monolith deployment.

Target deployment shape:

```text
Reverse proxy or load balancer
    -> Backend instance 1
    -> Backend instance 2
    -> Backend instance N
          -> PostgreSQL
          -> shared production document storage
          -> Stripe
          -> SMTP provider
```

The backend must not rely on one instance for:

- idempotency;
- optimistic versioning;
- webhook de-duplication;
- email delivery ownership;
- duplicate QR scan prevention;
- sequence generation;
- canonical document identity;
- critical security state.

If current HTTP sessions are process-local, the branch must document and implement the approved scaling strategy where feasible:

- external/shared session storage;
- database-backed sessions;
- Redis-backed sessions if justified and approved;
- or explicit sticky-session limitation for the current deployment with a clear production migration path.

Do not add Redis automatically. First inspect current session behavior and choose the simplest correct strategy.

## 10. Performance target

The branch must establish measurable local targets, not vague claims.

Suggested baseline targets for the documented demo environment, to be refined after measurement:

- ordinary indexed detail query: p95 below 250 ms under local controlled load;
- ordinary paginated list: p95 below 500 ms;
- manual/QR access decision excluding environment startup: p95 below 500 ms;
- administrative mutation: p95 below 750 ms excluding external I/O;
- CSV export startup: first bytes emitted without loading the complete result set;
- no unbounded page size;
- no unbounded export;
- no N+1 query in primary flows;
- database pool remains below saturation during controlled load;
- external SMTP/Stripe calls use strict timeouts and do not consume all request threads indefinitely.

These are portfolio/demo acceptance targets, not universal production SLAs. The final documentation must state the test environment and limitations.

## 11. Security target

The branch must complete a system-wide review of:

- authentication;
- password handling;
- session fixation;
- session invalidation;
- cookie attributes;
- CSRF;
- CORS;
- method security;
- endpoint authorization;
- rate and abuse controls;
- file upload/download;
- webhook verification;
- SMTP secrets;
- Stripe secrets;
- actuator exposure;
- error detail;
- log redaction;
- dependency vulnerabilities;
- CSV injection;
- QR-token privacy;
- path traversal;
- content-type validation;
- request-size limits;
- security headers.

Only `ADMIN` and `RECEPTIONIST` remain supported staff roles.

## 12. Data and migration target

The final Flyway chain must be:

- complete;
- numerically unique;
- reproducible on a clean database;
- valid against the current development database;
- free of modified applied migrations;
- documented.

Database review must cover:

- constraints;
- foreign-key delete rules;
- unique indexes;
- redundant indexes;
- missing indexes for measured queries;
- append-only history;
- optimistic versions;
- idempotency keys;
- timestamp consistency;
- query plans for critical paths;
- data-retention growth points;
- backup/restore documentation.

No migration may be added solely to make a test pass without a durable production reason.

For the approved deployment topology, the branch must also prepare:

- an externalized Supabase-compatible JDBC URL with SSL;
- a separately configurable Flyway URL when a direct migration connection is preferable to the runtime pooler;
- a small, bounded Hikari pool suitable for free-tier connection limits;
- documented connection budgeting as `backend instances x maximumPoolSize`;
- no browser use of Supabase Data APIs for business operations;
- Spring Boot remains the only business API and authorization authority.

## 13. Observability target

The backend must expose safe operational signals for:

- application liveness;
- application readiness;
- database availability;
- migration success at startup;
- external integration readiness where safe;
- request counts and latency by bounded route/result dimensions;
- access decisions by bounded result/reason/source;
- email delivery status/failure code;
- payment webhook outcomes;
- JVM and connection-pool health;
- stale pending operational records where already modeled.

Metrics must not use high-cardinality identifiers such as:

- client ID;
- payment ID;
- credential ID;
- delivery ID;
- email address;
- external reference;
- token;
- audit-entry ID.

Logs must be structured or consistently formatted, correlation-aware, bounded, and redacted.

## 14. Frontend-readiness target

Before the first frontend branch, the backend must provide:

- stable API routes;
- stable request and response DTOs;
- consistent pagination;
- consistent date/time and currency formats;
- complete session-authentication behavior;
- explicit CSRF acquisition/usage instructions;
- standardized `ProblemDetail` responses;
- complete OpenAPI security and schemas;
- CORS strategy for local frontend development;
- demo users and safe seed data;
- no backend contract that requires frontend guessing;
- an API change policy for future frontend work.

## 15. Block dependency graph

```text
Block 1: Current-state audit, ADRs, and hardening backlog
    -> Block 2: Security, secrets, sessions, and HTTP boundary hardening
        -> Block 3: Database, queries, indexes, and transaction hardening
            -> Block 4: Cross-instance correctness, external I/O, and resilience
                -> Block 5: Observability, health, logging, and operational controls
                    -> Block 6: API consistency, OpenAPI, and frontend-readiness contract
                        -> Block 7: Docker, deployment, backup, demo, and scalability documentation
                            -> Block 8: Full-system performance, regression, security, and final gate
```

## 16. Progress model

- Block 1: 10%
- Block 2: 24%
- Block 3: 38%
- Block 4: 53%
- Block 5: 66%
- Block 6: 78%
- Block 7: 89%
- Block 8: 100%

Progress is earned only after the block completion criteria and validation pass.

---

# Block 1: Current-state audit, ADRs, and hardening backlog

- **Status:** `NOT_STARTED`
- **Target progress:** 10%

## Objective

Relearn and document the complete current backend, identify evidence-based risks, establish production-oriented architecture decisions, and create the exact hardening backlog for Blocks 2 through 8 without changing broad production behavior.

## Required work

1. Map all modules and public contracts.
2. Run Spring Modulith verification.
3. Inventory controllers and role/CSRF rules.
4. Inventory transactions and persistence patterns.
5. List all migrations and verify Git history.
6. Inventory external integrations and timeout/retry behavior.
7. Find process-local correctness state.
8. Inventory file storage implementations and scaling assumptions.
9. Inventory operational endpoints, metrics, and logging.
10. Inventory API inconsistencies relevant to the frontend.
11. Run dependency and build reports already supported by the project.
12. Measure baseline test duration and selected endpoint/query performance in a controlled environment.

## Required ADRs

Create or update concise ADRs for:

- retaining the modular monolith;
- horizontal-scaling strategy;
- session strategy;
- shared document-storage production strategy;
- background/retry ownership strategy;
- API compatibility policy for frontend development;
- future service-extraction triggers.

Do not add infrastructure solely because an ADR mentions a future option.

## Finding report

Create a tracked hardening report under the project's documentation convention containing:

- finding ID;
- severity;
- affected module/file;
- evidence;
- risk;
- authorized remediation block;
- verification method;
- status.

Do not include secrets or sensitive runtime data in the report.

## Allowed production changes

Only narrowly scoped corrections required to make the inventory/tests accurate. Broad hardening changes belong to later blocks.

## Required tests and validation

```powershell
.\gradlew compileJava compileTestJava
```

Run:

```powershell
.\gradlew test `
  --tests "*ApplicationModuleStructureTests.verifiesModuleBoundaries" `
  --rerun-tasks
```

Run repository hygiene and read-only Git inspections.

Record baseline `clean check` result, but do not fix unrelated findings outside this block unless critical and explicitly reported.

## Explicit exclusions

- no speculative caching;
- no new database indexes;
- no session migration;
- no new rate limiter;
- no broad controller rewrite;
- no deployment platform introduction;
- no frontend.

## Completion criteria

- current architecture is documented from repository evidence;
- risks are ranked;
- ADRs define the approved direction;
- later blocks have exact, evidence-based scope;
- no unsupported production claim is made;
- Codex reports and stops.

---

# Block 2: Security, secrets, sessions, and HTTP boundary hardening

- **Status:** `NOT_STARTED`
- **Depends on:** Block 1
- **Target progress:** 24%

## Objective

Close verified security gaps in authentication, authorization, sessions, cookies, CSRF, CORS, headers, rate/abuse controls, request limits, secrets, files, actuator exposure, and safe error handling.

## Required review and implementation

### Authentication and sessions

- session fixation protection;
- login success/failure behavior;
- logout invalidation;
- idle and absolute timeout strategy;
- concurrent-session behavior if required;
- process-local versus shared-session readiness;
- safe remember-me behavior, preferably absent unless approved;
- no session identifiers in logs.

### Cookies

Verify environment-specific configuration for:

- `HttpOnly`;
- `Secure` in HTTPS environments;
- `SameSite` compatible with the frontend deployment;
- bounded lifetime;
- appropriate path/domain;
- no sensitive secondary cookies.

### Authorization

- inventory every route;
- compare HTTP and method security;
- test anonymous, ADMIN, and RECEPTIONIST behavior;
- remove accidental broad permits;
- ensure actuator and documentation exposure follows policy;
- ensure no `MAINTENANCE` role returns.

### CSRF

- all staff-initiated mutations protected;
- only verified third-party webhook routes excluded;
- frontend-compatible CSRF token strategy documented;
- no wildcard exemption.

### CORS

- deny-by-default production configuration;
- explicit local frontend origins;
- explicit externalized Vercel frontend origin for the future deployed profile;
- credentials support only for allowlisted origins;
- no wildcard origin with credentials;
- typed externalized configuration;
- preflight tests.

### HTTP headers

Review and add as appropriate:

- `X-Content-Type-Options: nosniff`;
- frame protection or CSP `frame-ancestors`;
- Referrer Policy;
- Content Security Policy relevant to API/docs endpoints;
- cache controls for sensitive responses;
- HSTS only in HTTPS production profile;
- safe download headers.

### Request and abuse limits

Use the simplest evidence-based mechanism for:

- login attempts;
- Stripe webhook request size and processing;
- QR check-in abuse;
- email request/retry abuse;
- audit query/export bounds;
- file upload sizes;
- JSON/body limits.

Do not add a distributed rate-limit dependency unless required. If limits are process-local, document the multi-instance limitation. Prefer database-authoritative business limits for correctness.

### Secrets

- no secrets in Git;
- typed configuration;
- startup validation;
- redacted `toString`;
- safe error mapping;
- `.env.example` contains placeholders only;
- production secret-manager path documented without requiring cloud implementation.

### Files and downloads

- path traversal prevention;
- server-generated storage keys;
- MIME/signature validation;
- size bounds;
- safe download filenames;
- no absolute paths;
- no cache for sensitive artifacts;
- local storage limitations documented.

### Error handling

- standardized safe `ProblemDetail`;
- no stack traces, SQL, paths, provider payloads, or secrets;
- consistent status/code mapping;
- unknown errors use correlation ID where established.

## Required tests

- complete role matrix across protected controllers;
- CSRF matrix;
- CORS allow/deny/preflight;
- cookie/session behavior;
- logout/session invalidation;
- security headers;
- actuator exposure;
- oversized request/file rejection;
- rate/abuse behavior where implemented;
- secret redaction;
- path traversal and content-type tests;
- ProblemDetail privacy;
- webhook signature regression;
- CSV-injection regression.

## Explicit exclusions

- no new authentication product feature;
- no OAuth/OIDC migration unless already approved;
- no unsupported role;
- no frontend;
- no cloud-secret-manager dependency.

## Validation

Compile, focused security tests, all web security regressions, file/integration security tests, OpenAPI security smoke tests, and Spring Modulith verification.

## Completion criteria

- verified security findings are closed;
- session/frontend interaction is documented;
- no staff mutation lacks CSRF;
- no secret or internal detail leaks;
- production/local security differences are explicit;
- Codex reports and stops.

---

# Block 3: Database, queries, indexes, and transaction hardening

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 and 2
- **Target progress:** 38%

## Objective

Make PostgreSQL usage reliable and efficient under realistic load by reviewing constraints, query plans, indexes, pagination, transactions, locks, idempotency, migration integrity, and growth tables.

## Required migration safety workflow

Before adding any migration:

1. list versions numerically;
2. search all Git history and branches;
3. inspect original and subsequent DDL;
4. inspect actual query shape;
5. use `EXPLAIN` or equivalent evidence in controlled tests;
6. verify index nonredundancy;
7. verify write cost and migration compatibility.

## Critical query review

At minimum inspect:

- client search/profile;
- membership and period lookup;
- payment registration and confirmed-payment checks;
- payment corrections and history;
- Stripe attempt/event idempotency;
- access manual and QR decisions;
- duplicate QR scan detection;
- receipt queries;
- credential active lookup and history;
- email delivery status/idempotency/retry;
- audit paginated search/detail;
- audit CSV streaming export;
- dashboard/reporting queries.

## Required hardening

### Pagination and sorting

- deterministic tie-breaker;
- maximum page size;
- bounded date ranges;
- allowlisted sort fields/directions;
- stable empty-page behavior;
- no unbounded repository `findAll` in web flows.

### Transactions

- application-layer ownership;
- read-only for reads;
- no external network while holding avoidable locks;
- documented isolation assumptions;
- safe flush ordering with triggers;
- no broad transaction spanning PDF/SMTP/Stripe unnecessarily;
- rollback tests.

### Concurrency and idempotency

- database unique constraints;
- optimistic versions;
- safe duplicate resolution;
- no process-local correctness locks;
- concurrency tests for critical mutations;
- no hidden last-write-wins.

### Historical data

- append-only tables remain immutable;
- restrictive foreign keys;
- no destructive cascades;
- retention and archiving candidates documented;
- no premature partitioning without evidence.

### Connection pool

- externalized bounded pool configuration;
- acquisition and idle timeouts;
- leak detection only if appropriate for nonproduction diagnostics;
- documented relationship between backend instances and database capacity;
- Supabase runtime connection versus Flyway migration connection strategy;
- SSL verification in the deployed profile;
- a default free-tier pool target of approximately 3 to 5 maximum connections per backend instance, subject to measured limits;
- tests/configuration contracts.

## Required tests

- Flyway clean-current validation;
- duplicate migration detection;
- query-plan/index contract where practical;
- pagination determinism;
- maximum limits;
- concurrency and stale versions;
- transaction rollback;
- no partial writes;
- no external I/O inside critical transaction where contract-testable;
- restrictive FK and append-only regression;
- connection-pool property validation;
- full persistence regression for affected modules.

## Explicit exclusions

- no speculative partitioning;
- no read replica implementation;
- no database sharding;
- no cache introduction without evidence;
- no destructive migration rewrite.

## Validation

Compile, focused PostgreSQL tests, affected persistence regressions, full Flyway validation, and Spring Modulith verification.

## Completion criteria

- critical queries are bounded and indexed when justified;
- redundant indexes are not introduced;
- transaction boundaries are explicit;
- cross-instance correctness remains database-authoritative;
- migration chain is reproducible;
- Codex reports and stops.

---

# Block 4: Cross-instance correctness, external I/O, and resilience

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 3
- **Target progress:** 53%

## Objective

Ensure critical behavior remains correct with multiple backend instances and that Stripe, SMTP, document storage, jobs, and retries fail safely within bounded resource limits.

## Cross-instance review

Find and classify:

- static mutable state;
- in-memory locks;
- in-memory idempotency;
- scheduled job ownership;
- temporary filesystem assumptions;
- local-only session state;
- local-only canonical documents;
- random generators shared unsafely;
- caches used as authority.

Correct critical findings using the simplest architecture consistent with the ADRs.

## External I/O hardening

### Stripe

- strict connection/read/write timeouts where supported;
- Test Mode only in current product scope;
- Live key rejection where established;
- signed webhook verification;
- persistent event idempotency;
- out-of-order/duplicate event behavior;
- no provider SDK beyond infrastructure;
- safe logs and errors;
- no HTTP thread blocked indefinitely.

### SMTP

- bounded timeouts;
- persistent delivery intent;
- cross-instance attempt ownership;
- retry limit;
- ambiguous outcome handling;
- no exactly-once false claim;
- SMTP failure isolated from source aggregates;
- Mailpit local/demo only.

### Document storage

- local adapter remains for development;
- production shared-storage port contract documented;
- no correctness assumption that all instances share local disk;
- canonical artifact metadata/checksum validation;
- atomic/staged writes and compensation;
- missing/corrupt artifact behavior;
- startup/application should not silently claim multi-instance document readiness with local disk.

### Scheduled/background work

If background jobs exist:

- database-backed ownership or leader strategy;
- safe retry;
- idempotency;
- graceful shutdown;
- no duplicate processing across instances;
- bounded batch size;
- failure metrics.

Do not introduce a scheduler only for hardening appearance.

## Resilience policy

Use narrow, justified resilience behavior:

- timeout;
- bounded retry only for safe/idempotent operations;
- no retry for validation/authentication failures;
- no generic retry around non-idempotent effects;
- circuit breaker only if current traffic/integration evidence warrants it;
- bulkhead only if external integration can exhaust shared request resources;
- safe fallback must not change business truth.

## Required tests

- duplicate webhooks across simulated instances;
- duplicate email delivery/retry ownership;
- concurrent receipt/credential generation regression;
- duplicate QR scan regression;
- timeout behavior;
- no retry for unsafe effects;
- source-state isolation;
- storage corruption/missing file;
- graceful interruption/recovery where testable;
- deployed-profile rejection of local filesystem storage;
- Supabase Storage adapter contract through an offline fake HTTP server or provider seam;
- Resend HTTP adapter contract through an offline fake HTTP server or provider seam;
- provider timeout, authentication, quota/rejection, and safe error mapping;
- no SDK leakage;
- no real external network.

## Explicit exclusions

- no message broker;
- no service extraction;
- no distributed cache unless explicitly justified;
- no cloud-storage adapter solely for demonstration;
- no generic resilience annotations on every method.

## Validation

Compile, focused resilience/concurrency tests, Stripe regression, email regression, document/credential regression, access regression, and Spring Modulith verification.

## Completion criteria

- critical correctness is not process-local;
- all external I/O is bounded;
- retry behavior is safe and specific;
- multi-instance limitations are explicit;
- source aggregates survive secondary failures;
- Codex reports and stops.

---

# Block 5: Observability, health, logging, and operational controls

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 4
- **Target progress:** 66%

## Objective

Provide safe, useful operational visibility and startup/runtime diagnostics without leaking sensitive data or creating high-cardinality telemetry.

## Health and readiness

Review and implement:

- liveness endpoint;
- readiness endpoint;
- database readiness;
- disk/storage readiness where meaningful;
- safe SMTP/Stripe configuration readiness without making external calls on every probe;
- migration failure prevents readiness;
- management endpoint access policy;
- separate health groups if current Spring Boot version supports established conventions.

Do not expose full environment, beans, config properties, heap dumps, loggers, or mappings publicly.

## Metrics

Use existing metrics infrastructure when present.

Safe bounded metrics may include:

- HTTP count/latency by normalized route and status;
- access result/reason/source;
- Stripe webhook result/event type allowlist;
- email delivery status/failure code/type;
- receipt/credential generation outcome;
- audit export outcome;
- active/pending operational counts from bounded queries;
- Hikari pool metrics;
- JVM/process metrics.

Do not tag with user IDs, resource IDs, emails, tokens, external references, receipt numbers, or free-form exception messages.

## Logging

- correlation/request ID;
- structured or consistent key-value format;
- server-generated operation IDs;
- no request-body logging on sensitive paths;
- no response-body logging for sensitive resources;
- no credentials, tokens, signatures, card data, email bodies, attachment bytes, SQL parameters, or absolute paths;
- expected business denials at appropriate level;
- unexpected failures with safe context;
- bounded log message length;
- stack traces remain server-side only where useful.

## Operational controls

Where current models support them, expose safe indicators for:

- stale pending email delivery;
- failed email delivery;
- unresolved payment attempt/webhook inconsistency;
- missing canonical artifact;
- excessive denied access patterns through metrics, not PII-heavy logs.

Do not create a new administrative feature if the blueprint does not require it. Metrics and documentation may be sufficient.

## Required tests

- actuator exposure and authorization;
- liveness/readiness behavior;
- database readiness failure;
- safe metric tag cardinality contracts;
- expected metric increments;
- correlation ID propagation;
- sensitive log redaction/capture;
- no body logging;
- no secret in startup/config logs;
- operational query bounds;
- health does not mutate state.

## Explicit exclusions

- no external APM dependency required;
- no centralized logging stack;
- no distributed tracing platform;
- no alerting vendor integration;
- no PII metrics.

## Validation

Compile, focused actuator/metrics/logging tests, security regression, integration regressions, and architecture verification.

## Completion criteria

- operators can distinguish live, ready, degraded, and failed states safely;
- metrics are useful and bounded;
- logs support investigation without leaking secrets;
- management endpoints are protected;
- Codex reports and stops.

---

# Block 6: API consistency, OpenAPI, and frontend-readiness contract

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 5
- **Target progress:** 78%

## Objective

Stabilize the backend contract for frontend implementation by standardizing security, pagination, errors, timestamps, enums, downloads, OpenAPI, and local development interaction.

## API inventory

Review every public endpoint for:

- path/version consistency;
- HTTP method semantics;
- status codes;
- request validation;
- response DTOs;
- role rules;
- CSRF;
- pagination;
- sorting/filter allowlists;
- date/time formats;
- monetary formats;
- enum serialization;
- empty/null semantics;
- content-type and download headers;
- ProblemDetail;
- OpenAPI completeness.

Do not make breaking changes without documenting them in the frontend contract report and updating all tests.

## Standardization targets

### Errors

Standardize safe `ProblemDetail` fields, expected to include established equivalents of:

- type;
- title;
- status;
- detail;
- instance;
- stable application code;
- timestamp;
- correlation ID;
- field errors for validation.

Avoid leaking exception class names or internals.

### Pagination

- consistent page numbering;
- consistent `size` limits;
- total counts only where current APIs support them efficiently;
- deterministic order;
- consistent page response shape;
- allowlisted sort fields.

### Dates and money

- ISO-8601 timestamps;
- explicit timezone policy;
- date-only fields remain date-only;
- monetary amounts use exact decimal representation;
- currency explicit;
- no floating-point business money.

### Authentication/CSRF frontend contract

Document:

- login request/response;
- session cookie behavior;
- current-user endpoint;
- logout;
- unauthorized/forbidden semantics;
- CSRF token acquisition and submission;
- CORS origins for local development;
- credentials mode expected by frontend;
- session expiration behavior.

### Downloads

- PDF, PNG, and CSV media types;
- safe `Content-Disposition`;
- no-store policy where required;
- error behavior for missing artifacts;
- no frontend need to know storage paths.

## OpenAPI finalization

Verify all modules and operations:

- authentication;
- clients;
- memberships/plans/promotions;
- payments and corrections;
- Stripe Test Mode attempts/webhooks as appropriately documented;
- receipts;
- access credentials;
- manual/QR access;
- access payment policy;
- email delivery;
- audit query/export;
- settings;
- reporting/dashboard;
- equipment/incidents/maintenance/notifications.

OpenAPI must document only supported behavior and current roles.

## Frontend contract artifact

Create a concise tracked document containing:

- API base URL;
- auth/session flow;
- CSRF flow;
- role matrix summary;
- common error model;
- pagination model;
- date/money conventions;
- file download behavior;
- local environment URLs;
- OpenAPI path;
- known non-breaking extension policy;
- explicitly unsupported frontend assumptions.

## Required tests

- OpenAPI all-path inventory;
- security scheme coverage;
- request/response schema contracts;
- stable ProblemDetail shape;
- pagination consistency contracts;
- timestamp/currency serialization;
- CORS/frontend-origin behavior;
- CSRF flow integration;
- downloads;
- no unsupported role/path;
- no internal/infrastructure fields in schemas.

## Explicit exclusions

- no frontend code;
- no generated TypeScript client required unless blueprint explicitly requests it;
- no cosmetic API rewrite;
- no unnecessary breaking route changes.

## Validation

Compile, all OpenAPI tests, web/security contract tests, representative end-to-end flows, and Spring Modulith verification.

## Completion criteria

- frontend no longer needs to infer backend behavior;
- API conventions are documented and tested;
- OpenAPI matches execution;
- authentication and CSRF are ready for frontend use;
- Codex reports and stops.

---

# Block 7: Docker, deployment, backup, demo, and scalability documentation

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 6
- **Target progress:** 89%

## Objective

Make local/demo startup reproducible and prepare, but do not execute, the approved future deployment to Vercel, Render, Supabase, and Resend.

## Docker Compose

Review and harden services as applicable:

- PostgreSQL;
- Mailpit;
- backend if Compose-managed;
- health checks;
- service dependencies based on health, not only start order;
- named volumes;
- explicit ports;
- environment placeholders;
- no secrets committed;
- resource guidance;
- restart behavior appropriate for local/demo;
- graceful shutdown.

Do not add unnecessary infrastructure.

### Final deployment preparation, without provisioning

Prepare tracked templates and documentation for the deployment that will occur only after the frontend is complete:

- Vercel frontend environment-variable template;
- Render backend build/start/health configuration guidance;
- Supabase PostgreSQL runtime and Flyway connection variables;
- Supabase Storage URL, private bucket names, and service-role secret placeholders;
- Resend API URL, API-key placeholder, verified-sender placeholder, and timeout variables;
- exact Vercel origin allowlist placeholder for backend CORS;
- backend public URL placeholder for frontend configuration;
- cookie, `Secure`, `SameSite`, CORS, and CSRF deployment checklist;
- one-time Flyway deployment procedure;
- post-deployment smoke-test and rollback checklist.

Do not create provider accounts, projects, buckets, domains, databases, or deployed services in this branch.

## Environment profiles

Document and validate:

- test;
- local/demo;
- production template.

Production template must externalize:

- database URL and credentials;
- session/cookie settings;
- allowed origins;
- Stripe keys/webhook secret;
- SMTP settings for local Mailpit and Resend HTTPS settings for deployment;
- storage root/provider selection, including Supabase Storage private buckets;
- log level;
- management endpoint policy;
- pool sizes/timeouts;
- public application URLs.

No real secrets belong in source control.

## Data and demo readiness

- deterministic safe demo users;
- supported ADMIN and RECEPTIONIST credentials documented through secure local/demo mechanism;
- representative clients, memberships, payments, receipts, credentials, access attempts, email, and audit flow instructions;
- no live-looking personal data;
- no generated binary committed unless explicitly intentional and harmless;
- clear reset procedure for local demo.

## Backup and recovery documentation

Document:

- PostgreSQL backup command/policy;
- restore validation procedure;
- document-storage backup relation;
- order for restoring database and artifacts;
- secret/config backup responsibility;
- Flyway startup behavior after restore;
- recovery limitations for local storage;
- periodic restore testing recommendation.

Do not claim backups are implemented automatically if only documentation exists.

## Production deployment reference

Document a simple reference topology:

```text
Vercel frontend
    -> Render Spring Boot backend
    -> Supabase PostgreSQL
    -> Supabase Storage
    -> Resend HTTPS API
    -> Stripe Test Mode
```

The guide must also show the future paid/multi-instance evolution behind a load balancer without changing business modules.

Include:

- session strategy;
- load-balancer health paths;
- readiness/liveness;
- graceful rolling deployment;
- Flyway single-owner/startup policy;
- connection-pool sizing guidance;
- storage-sharing requirement;
- scaling limits;
- extraction triggers.

## Recruiter demo

Provide a short reproducible sequence covering:

- startup;
- login;
- client/membership;
- payment;
- receipt;
- credential;
- QR/manual access;
- payment access policy;
- email through Mailpit;
- audit search;
- CSV export;
- OpenAPI;
- health.

The demo must state which components are local simulations and how production replacements work.

## Required tests or contracts

- Compose syntax/config validation;
- required service health checks;
- profile binding tests;
- placeholder-secret scans;
- local/demo startup smoke test where environment permits;
- no production profile using Mailpit by default;
- no local filesystem incorrectly documented as shared production storage;
- README link/path contracts.

## Explicit exclusions

- no cloud deployment automation;
- no Kubernetes;
- no Terraform;
- no real production credentials;
- no provider provisioning or public deployment;
- no frontend startup.

## Validation

Compile where config binding is affected, run configuration/Compose contract tests, local smoke tests where practical, security scans, and documentation link checks.

## Completion criteria

- local demo is reproducible;
- the combined post-frontend deployment runbook is complete;
- production configuration path is credible and honest;
- horizontal scaling requirements are explicit;
- backup/restore and limitations are documented;
- secrets remain external;
- Codex reports and stops.

---

# Block 8: Full-system performance, regression, security, and final gate

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 7
- **Target progress:** 100%

## Objective

Perform a complete independent quality review, close remaining approved findings, validate performance and concurrency under controlled load, verify every backend capability, finalize the blueprint, and establish the backend as ready for frontend development.

## Full functional regression

Run and verify all approved backend capabilities:

- identity and sessions;
- clients and profiles;
- plans, promotions, memberships, and periods;
- payments;
- payment corrections;
- Stripe Test Mode;
- payment receipts;
- access credentials;
- manual and QR access;
- access payment policy;
- transactional email;
- audit query;
- CSV export;
- reporting/dashboard;
- equipment, incidents, maintenance, and notifications;
- settings;
- module architecture.

## Controlled performance tests

Use a reproducible nonproduction dataset and controlled local environment.

Measure representative operations:

- login/current user;
- client search;
- membership lookup;
- payment list/detail;
- access check-in manual and QR;
- audit query;
- CSV export startup and bounded completion;
- reporting/dashboard;
- email request using fake/local transport.

Requirements:

- document dataset size, machine/environment, concurrency, and limitations;
- no real external provider load test;
- verify no unbounded memory growth;
- verify pool behavior;
- inspect slow queries;
- fix only evidence-supported bottlenecks;
- do not present local results as universal production SLA.

## Concurrency and resilience regression

- duplicate payment/provider events;
- payment corrections;
- receipt generation;
- credential issue/replace/revoke;
- duplicate QR scan;
- access/payment-policy races;
- email delivery and retry;
- policy version conflicts;
- audit export boundaries;
- graceful shutdown affecting active operations where testable.

## Security regression

- complete endpoint role matrix;
- CSRF matrix;
- CORS;
- sessions/cookies/logout;
- webhook signatures;
- file/path/content security;
- CSV injection;
- QR token privacy;
- SMTP/Stripe secrets;
- Actuator exposure;
- error privacy;
- log redaction;
- dependency scan using project-supported tools;
- no unsupported role.

## Approved deployment-readiness verification

Without deploying publicly, verify:

- production profile selects Supabase Storage rather than local disk;
- production profile selects Resend HTTPS rather than SMTP;
- Supabase JDBC and Flyway properties bind safely with SSL placeholders;
- Hikari pool limits are bounded;
- Vercel origin and Render backend URL are externalized;
- credentialed CORS and CSRF behavior are contract-tested;
- no provider secret is present in Git;
- all provider adapters pass offline contract tests;
- final deployment order and smoke-test checklist are documented;
- deployment remains deferred until frontend completion and user authorization.

## Database final verification

- complete migration list and Git-history check;
- clean database migration;
- current database validation;
- constraints and append-only triggers;
- index redundancy review;
- critical query plans;
- backup/restore documentation check;
- no migration placeholders or repair workarounds.

## Architecture final verification

- no module cycle;
- no internal cross-module import;
- public events/contracts only;
- infrastructure libraries remain confined;
- transactions remain in application layer;
- controllers thin;
- no process-local critical correctness;
- modular-monolith ADR accurate;
- future extraction triggers documented;
- Spring Modulith passes.

## Repository hygiene

Reject:

- `data/` artifacts;
- generated PDFs/PNGs/emails;
- local database dumps unless expressly documented and safe;
- `.env` files;
- secrets;
- temporary scripts;
- ZIP files;
- build output;
- BOM characters;
- HTML entities in source;
- debug prints;
- commented-out code;
- obsolete deferral flags/tests;
- accidental broad line-ending rewrites;
- unsupported dependencies.

## Required validation sequence

Start with:

```powershell
.\gradlew compileJava compileTestJava
```

Run focused hardening tests.

Run module regressions in manageable groups.

Run:

```powershell
.\gradlew test `
  --tests "*ApplicationModuleStructureTests.verifiesModuleBoundaries" `
  --rerun-tasks
```

Run:

```powershell
git diff --check
```

Run project-supported dependency/security analysis.

Finally run:

```powershell
.\gradlew clean check
```

The final status must be based on executed evidence.

## Blueprint closure

Update or identify exact updates to the blueprint so it states:

- every approved backend branch is complete;
- `feature/backend-hardening` is complete;
- backend current scope is finalized;
- next branch is `feature/frontend-foundation-auth`;
- deployment and scale assumptions;
- known production limitations;
- future extraction criteria;
- deferred backend enhancements.

## Final deliverables

- final hardening report with all critical/high findings closed or explicitly blocked;
- modular-monolith ADR;
- deployment/scaling guide;
- backup/restore guide;
- local/recruiter demo guide;
- frontend backend-contract guide;
- final OpenAPI contract;
- final quality-gate report;
- updated blueprint status.

## Completion criteria

The backend reaches its current 100% completion only when:

- all eight blocks pass;
- no critical finding remains open;
- any remaining medium/low findings are documented and nonblocking;
- full regression passes;
- security regression passes;
- Spring Modulith passes;
- migration chain is valid;
- controlled performance targets are measured and acceptable or honestly documented;
- local/demo startup is reproducible;
- production scaling path is documented;
- frontend contract is stable;
- repository hygiene passes;
- `clean check` succeeds;
- final verdict is `PASS` or `PASS WITH NON-BLOCKING NOTES`;
- Codex stops.

---

# 17. Complete test and review matrix

## Architecture

- module dependency inventory;
- no cycles;
- public contract boundaries;
- provider-library isolation;
- storage-port isolation;
- application transactions;
- thin controllers;
- Spring Modulith verification.

## Authentication and authorization

- login success/failure;
- current user;
- logout;
- session invalidation/fixation;
- ADMIN;
- RECEPTIONIST;
- anonymous;
- no unsupported role;
- HTTP and method security consistency.

## HTTP security

- CSRF all mutations;
- webhook-only exemptions;
- CORS allow/deny/preflight;
- cookie attributes;
- security headers;
- request limits;
- actuator protection;
- safe ProblemDetail.

## Persistence

- Flyway clean/current;
- constraints;
- indexes;
- query plans;
- pagination;
- deterministic sorting;
- optimistic locking;
- idempotency;
- append-only history;
- rollback;
- concurrency;
- pool configuration.

## External integrations

- Stripe timeout/signature/idempotency/privacy;
- SMTP timeout/retry/ambiguity/privacy;
- Mailpit local-only behavior;
- PDF/QR generation bounds;
- artifact integrity;
- no real network in standard tests.

## Observability

- liveness;
- readiness;
- database health;
- safe management exposure;
- bounded metrics;
- correlation ID;
- log redaction;
- no high-cardinality sensitive tags.

## API/frontend readiness

- complete OpenAPI;
- auth and CSRF contract;
- pagination consistency;
- date/time consistency;
- exact money;
- enum serialization;
- downloads;
- errors;
- local frontend CORS.

## Performance

- representative endpoint latency;
- critical query plans;
- bounded memory for export;
- pool saturation behavior;
- controlled concurrency;
- documented environment and dataset.

## Deployment and recovery

- Compose validation;
- service health checks;
- profiles;
- externalized secrets;
- graceful shutdown;
- backup guide;
- restore guide;
- shared-storage requirement;
- multi-instance session strategy.

# 18. Final Definition of Done

- [ ] Block 1 completed and validated
- [ ] Block 2 completed and validated
- [ ] Block 3 completed and validated
- [ ] Block 4 completed and validated
- [ ] Block 5 completed and validated
- [ ] Block 6 completed and validated
- [ ] Block 7 completed and validated
- [ ] Block 8 completed and validated
- [ ] Modular-monolith ADR finalized
- [ ] Horizontal-scaling strategy documented
- [ ] Session strategy documented and safely configured
- [ ] Production storage limitation/path documented
- [ ] No critical process-local correctness state
- [ ] Security role matrix verified
- [ ] CSRF matrix verified
- [ ] CORS ready for local frontend and safe for production
- [ ] Cookies and sessions hardened
- [ ] Security headers verified
- [ ] Request/file/export limits verified
- [ ] Secrets externalized and redacted
- [ ] ProblemDetail standardized and safe
- [ ] Flyway chain complete
- [ ] Critical PostgreSQL queries reviewed
- [ ] Indexes justified and nonredundant
- [ ] Transactions and concurrency verified
- [ ] External I/O bounded by timeouts
- [ ] Retry/idempotency behavior verified
- [ ] Audit and logs protect sensitive data
- [ ] Health/readiness available and protected
- [ ] Metrics bounded and low-cardinality
- [ ] OpenAPI complete and accurate
- [ ] Frontend backend-contract guide complete
- [ ] Docker/local demo reproducible
- [ ] Recruiter demo documented
- [ ] Backup/restore guidance complete
- [ ] Controlled performance baseline recorded
- [ ] No real external network in standard tests
- [ ] Spring Modulith passes
- [ ] Repository hygiene passes
- [ ] `clean check` passes
- [ ] Supabase PostgreSQL configuration and Flyway strategy verified offline
- [ ] Supabase Storage adapter and private-bucket policy verified offline
- [ ] Resend HTTPS adapter verified offline
- [ ] Render free-tier SMTP and ephemeral-filesystem limitations addressed
- [ ] Vercel-to-Render CORS, cookie, and CSRF contract documented and tested
- [ ] Combined deployment explicitly deferred until frontend completion
- [ ] Final Vercel + Render + Supabase + Resend deployment runbook completed
- [ ] Blueprint updated
- [ ] Next branch identified as `feature/frontend-foundation-auth`

# 19. Known limitations that may remain acceptable

The final report may retain these limitations if documented accurately and noncritical for current scope:

- local filesystem storage in local/demo profile, with Supabase Storage selected for future deployment;
- Mailpit only for local/demo email capture, with Resend HTTPS selected for future deployment;
- Stripe Test Mode only;
- no automatic cloud deployment;
- no distributed trace backend;
- no message broker;
- no multi-tenancy;
- no automatic database partitioning;
- no guaranteed exactly-once external SMTP delivery;
- local performance baselines are not production SLAs;
- the zero-cost deployment has cold starts, inactivity pauses, quotas, and no SLA;
- real public deployment is intentionally deferred until the frontend is complete;
- production-grade secret management beyond provider environment variables remains deployment-specific.

These limitations must not be disguised as completed production infrastructure.

# 20. Future evolution after frontend

Potential future backend branches may be created only when justified, for example:

- production object-storage adapter;
- production secret-manager integration;
- OIDC/enterprise authentication;
- multi-location support;
- multi-tenancy;
- background worker extraction;
- provider webhook reconciliation tools;
- audit retention/archiving;
- database partitioning;
- dedicated analytics pipeline;
- selective module extraction.

They are not part of current backend completion.

# 20.1 Final deployment rule

Completing this branch does not mean deploying the backend immediately.

The required sequence is:

```text
complete backend hardening
    -> merge backend
    -> implement and complete frontend
    -> run local full-stack integration
    -> review final deployment checklist
    -> provision Supabase, Render, Vercel, and Resend
    -> deploy database/storage/backend/frontend together
    -> run post-deployment smoke tests
```

Codex must prepare all adapters, properties, templates, tests, and runbooks required by this sequence, but must stop before external provisioning or deployment.

# 21. Short prompts for Codex

## Start Block 1

```text
Read the active plan:
docs/branch-plans/backend-hardening-plan.md

Implement only Block 1: Current-state audit, ADRs, and hardening backlog.
Do not implement Blocks 2 through 8.
Follow AGENTS.md and the applicable skills, validate the authorized block, report evidence, and stop.
```

## Continue with a later block

```text
Review the current repository, the Block 1 hardening report, and all completed previous blocks.
Implement only Block <N>: <EXACT BLOCK NAME> from:
docs/branch-plans/backend-hardening-plan.md

Do not implement later blocks. Apply only evidence-supported hardening changes, validate, report, and stop.
```

## Repair without advancing

```text
The authorized scope remains Block <N>.
Diagnose and fix the failure below without starting the next block:

<PASTE FAILURE>

Classify the root cause, rerun the smallest failing scope, then rerun the block validation. Report evidence and stop.
```

## Final independent review

```text
Use the quality-gate and security-review skills.
Do not modify files initially.
Review all changes in feature/backend-hardening against:

- docs/branch-plans/backend-hardening-plan.md
- the current blueprint
- AGENTS.md
- the current repository evidence

Return PASS, PASS WITH NON-BLOCKING NOTES, BLOCKED, or FAIL.
If corrections are required, list exact files, evidence, severity, and required remediation without starting frontend work.
```
