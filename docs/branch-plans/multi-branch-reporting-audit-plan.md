# Branch Plan: `feature/multi-branch-reporting-audit`

## 1. Branch identity

- **Branch:** `feature/multi-branch-reporting-audit`
- **Capability:** Complete secure, accurate, and efficient organization-wide and branch-scoped reporting, dashboards, audit queries, and CSV exports
- **Previous completed branch:** `feature/gmail-api-email-delivery`
- **Expected next branch:** `feature/multi-branch-hardening`
- **Current migration baseline:** Verify from repository, expected through `V42`
- **Starting progress:** 0%
- **Target progress:** 100%
- **Execution model:** One explicitly authorized block per Codex response
- **Plan owner:** User
- **Implementation agent:** Codex
- **Git ownership:** The user controls staging, commits, pushes, rebases, pull requests, merges, and branch changes

## 2. Purpose

Complete the multisucursal visibility layer of Coach Gym.

The backend already provides:

- one canonical organization;
- multiple branches;
- organization-wide and branch-scoped staff authority;
- active branch context;
- branch ownership for operational resources;
- branch-aware membership coverage;
- branch-aware manual and QR access;
- branch-specific access-payment policy overrides;
- branch-aware public events and audit metadata;
- administrative audit query and secure CSV export;
- durable email delivery for receipts and credentials;
- Gmail API transport for real email delivery.

This branch must turn those foundations into reliable dashboards, reports, audit searches, and exports that respect the authenticated user's exact organizational and branch scope.

The branch must guarantee that:

- organization totals are financially and operationally correct;
- branch totals reconcile with organization totals where mathematically applicable;
- branch-scoped users never view another branch's data;
- historical records remain attributed to their stored branch snapshot;
- filters, pagination, ranges, grouping, and exports are bounded;
- report queries are efficient and index-supported;
- audit and CSV preserve privacy and prevent formula injection;
- frontend consumers receive stable, explicit contracts.

## 3. Execution protocol for Codex

This plan provides complete context but authorizes only the block explicitly requested in the current prompt.

For every block, Codex must:

1. Read the root and applicable nested `AGENTS.md` files.
2. Read this complete branch plan.
3. Read the current blueprint sections for reporting, dashboards, branches, audit, CSV, payments, memberships, access, operations, email, and security.
4. Inspect the current repository and `git status --short` before editing.
5. Inspect existing report queries, controllers, OpenAPI, events, branch authorization, audit sanitization, CSV encoder, migrations, indexes, and integration tests.
6. Prepare a block-specific impact map before broad edits.
7. Preserve unrelated user changes.
8. Implement only the authorized block.
9. Run focused validation.
10. Fix failures introduced by the block.
11. Perform a secondary consistency check.
12. Report evidence and stop.

Codex must not:

- implement later blocks early;
- stage, commit, push, rebase, merge, switch branches, or open a pull request;
- modify an applied Flyway migration;
- execute Flyway repair automatically;
- introduce multi-tenancy;
- create new roles;
- weaken branch authorization;
- trust request-supplied branch IDs without server-side scope validation;
- calculate authoritative financial totals in Java after loading unbounded rows;
- silently truncate exports;
- expose audit secrets or unsanitized metadata;
- introduce speculative indexes without query evidence;
- add frontend code;
- deploy external infrastructure;
- use real external services in standard tests;
- create temporary patch scripts or generated reports in the repository.

## 4. Goals

1. Provide organization-wide and branch-specific dashboard summaries.
2. Provide time-series and grouped financial reporting by branch.
3. Provide membership, client, access, equipment, incident, maintenance, notification, and email operational reporting by branch.
4. Apply exact role/scope visibility rules to every report.
5. Add secure branch filters to audit query and audit CSV export.
6. Preserve deterministic historical branch attribution.
7. Define explicit timezone, date-range, currency, and aggregation semantics.
8. Prevent double counting across joins and historical tables.
9. Use bounded PostgreSQL aggregation queries with justified indexes.
10. Provide CSV exports safe for spreadsheet applications.
11. Expose stable REST and OpenAPI contracts for the future frontend.
12. Preserve all current business, security, and performance invariants.

## 5. Non-goals

This branch must not implement:

- multi-tenant reporting;
- organization switching;
- accounting ledger or general ledger;
- expenses, payroll, taxes, accounts payable, or bank reconciliation;
- revenue forecasting or machine-learning predictions;
- arbitrary custom report builders;
- user-authored SQL;
- scheduled report emails;
- PDF report exports;
- Excel `.xlsx` exports;
- external BI integrations;
- data warehouse or OLAP infrastructure;
- materialized views without measured need;
- table partitioning without measured need;
- frontend dashboards;
- public reports;
- client self-service analytics;
- final multisucursal hardening.

## 6. Required repository inspection before Block 1

Codex must inspect and confirm:

- current dashboard/reporting module ownership;
- all existing financial and operational aggregate queries;
- current dashboard endpoints and DTOs;
- current date, timezone, money, and currency conventions;
- payment states and correction semantics;
- membership and period statuses;
- client home branch and registration branch fields;
- access attempts, physical branch, sources, results, and denial reasons;
- equipment, incident, maintenance, and notification branch ownership;
- email delivery and attempt branch ownership/statuses;
- current audit-entry branch metadata or columns;
- current audit query filters, pagination, detail response, and sanitization;
- current CSV export query, encoder, formula-injection defense, row limits, and streaming behavior;
- authenticated staff scope and active-branch authorization contracts;
- current ProblemDetail, security, CSRF, and OpenAPI conventions;
- current PostgreSQL indexes and query plans;
- current Flyway sequence and Git history;
- current performance baselines and test datasets;
- Spring Modulith exposed packages and allowed dependencies.

The inspection must produce a metric catalog identifying:

- metric name;
- authoritative source table/query;
- aggregation unit;
- branch attribution field;
- date/time field;
- included statuses;
- excluded statuses;
- reconciliation rule;
- authorized audience;
- required filters;
- expected index/query plan;
- privacy classification.

## 7. Reporting access model

### `ADMIN + ORGANIZATION`

May:

- view organization-wide totals;
- view one authorized branch;
- compare multiple branches;
- export organization-wide or branch-filtered reporting data where approved;
- query organization-wide audit;
- export organization-wide audit CSV.

### `ADMIN + BRANCH`

May:

- view only assigned active branches;
- compare assigned branches only if assigned to more than one branch and the endpoint permits comparison;
- query audit only for assigned branches;
- export only authorized branch data.

### `RECEPTIONIST + BRANCH`

May view only approved operational metrics for the active or assigned branch.

Recommended visibility:

- clients registered;
- memberships and periods;
- today's access activity;
- operational incidents/equipment status;
- email delivery status directly relevant to allowed work.

Recommended restrictions:

- no organization-wide financial totals;
- no owner-level financial comparisons;
- no unrestricted audit query;
- no organization-wide CSV export;
- no cross-branch comparison.

The exact matrix must be finalized by the ADR and blueprint.

## 8. Branch-filter semantics

Every reporting and audit request must resolve one of these server-authoritative scopes:

```text
ORGANIZATION
SINGLE_BRANCH
AUTHORIZED_BRANCH_SET
ACTIVE_BRANCH
```

Rules:

1. A request-supplied `branchId` is only a filter request, not authority.
2. The backend validates branch ownership and actor scope.
3. Branch-scoped users cannot request organization scope.
4. Organization ADMIN may request organization scope or one/more branch filters if endpoint permits.
5. Inactive branches remain reportable historically to authorized administrators.
6. Operational views may default to the active branch.
7. Historical reporting uses persisted branch snapshots, not current assignments.
8. SQL applies the authorized branch predicate before aggregation.
9. No endpoint loads organization data and filters unauthorized branches in Java.
10. Cross-branch denial follows the project's safe IDOR semantics.

## 9. Timezone and date-range policy

Reporting must distinguish stored instants from branch-local business dates.

Rules:

1. Database timestamps remain authoritative instants/timestamptz.
2. Organization-wide reports use an explicitly documented timezone, normally the organization default.
3. Single-branch reports use the branch timezone unless the API explicitly accepts an authorized reporting timezone.
4. Multi-branch comparison must use a consistent comparison timezone chosen by contract, preferably organization default.
5. Date-only filters are converted to precise half-open instant ranges:

```text
[fromInclusive, toExclusive)
```

6. Daylight-saving and offset boundaries must be tested even if the initial deployment timezone has no DST.
7. Maximum date ranges are bounded by report type.
8. Defaults are explicit and stable.
9. No browser-local implicit timezone logic is used by the backend.
10. Responses include the timezone used for aggregation.

Recommended bounds, subject to current conventions:

- dashboard summary: up to 366 days;
- time-series trend: up to 366 days;
- branch comparison: up to 366 days;
- detailed export: smaller bounded period based on row count;
- audit query/export: preserve current strict range limits.

## 10. Currency and money policy

1. Financial amounts use exact decimal arithmetic.
2. Reports group only currencies that can be meaningfully aggregated.
3. Current deployment uses the organization default currency unless historical payments persist another currency.
4. Different currencies must never be silently summed.
5. Every financial response includes currency.
6. `PAID`, `VOIDED`, and `REFUNDED` semantics follow existing financial rules.
7. `PaymentAttempt` is never treated as revenue.
8. Receipts and email deliveries do not create financial value.
9. Refund/void reporting must use current authoritative payment/correction data without duplicating the original payment.
10. Rounding occurs only at established presentation boundaries, not in aggregation logic.

## 11. Financial metric catalog

Minimum required metrics, subject to repository evidence:

### Confirmed revenue

- source: persisted payments;
- include status `PAID` only according to current authoritative semantics;
- group by payment's immutable branch of registration;
- group by payment date/confirmed date according to current reporting contract;
- exclude attempts, voided, and refunded.

### Voided amount/count

- source: current payment/correction state;
- branch: payment branch;
- no double counting through status history joins.

### Refunded amount/count

- source: current payment/correction state;
- full-refund semantics only, unless current model supports more;
- branch: payment branch.

### Payment count

- count canonical payments, not status-history rows or attempts.

### Payment method distribution

- group by bounded method enum;
- exclude noncanonical attempts.

### Average payment value

- numerator and denominator based on the same qualifying `PAID` set;
- exact decimal handling.

### Trend

- daily, weekly, or monthly buckets according to requested granularity;
- bounded bucket count;
- gaps represented explicitly or documented.

This application provides revenue management and operational financial reporting, not full accounting.

## 12. Client and membership metric catalog

Minimum metrics:

- total clients by home branch;
- clients registered during range;
- active/inactive client counts according to current status model;
- active memberships;
- memberships created during range;
- active membership periods;
- new membership periods;
- expirations during range;
- plan distribution;
- membership coverage-scope distribution;
- registrations by branch;
- optional renewal count if current model can distinguish renewal safely.

Rules:

- client home branch and membership registration branch are distinct concepts;
- use the metric's documented attribution;
- do not duplicate memberships through covered-branch association joins;
- `ALL_BRANCHES` and selected-branch coverage must not multiply counts;
- snapshot tables require `EXISTS` or distinct-safe queries when filtering by covered branch.

## 13. Access metric catalog

Minimum metrics:

- total access attempts;
- allowed count;
- denied count;
- allowed rate;
- manual versus QR source;
- denial reasons;
- access attempts by physical branch;
- hourly/day trend;
- duplicate-scan denials;
- membership-not-valid-at-branch denials;
- payment-required denials;
- active credential usage where safely derivable.

Rules:

- use physical `access_attempt.branch_id`;
- count canonical attempt rows only;
- do not reveal QR token/fingerprint;
- denial reasons remain bounded enums;
- branch comparisons use the same time basis;
- access result changes are not inferred from audit entries.

## 14. Equipment and operations metric catalog

Minimum metrics:

- equipment count by branch and status;
- equipment out of service;
- open incidents;
- incidents created/resolved during range;
- incident severity/type distribution where modeled;
- scheduled/active/completed maintenance counts;
- overdue maintenance where current dates/status allow accurate derivation;
- branch-related operational notifications by status/type where useful.

Rules:

- equipment, incident, and maintenance branch relationships remain consistent;
- no double counting through many-to-many joins;
- overdue logic uses branch/report timezone and documented boundary;
- historical inactive branches remain visible to authorized ADMINs.

## 15. Email metric catalog

Minimum safe metrics:

- durable email deliveries requested;
- `SENT` and `FAILED` counts;
- delivery type distribution;
- retry count;
- safe failure-code distribution;
- identity-email best-effort outcomes only if persisted/observable safely;
- account-activation delivery ledger outcomes if current model supports it.

Rules:

- branch attribution comes from persisted source/delivery branch;
- identity emails without durable records are not invented as historical metrics;
- recipient addresses never appear in aggregate labels;
- Gmail provider message IDs are not reported;
- `SENT` means Gmail accepted for sending, not inbox delivery.

## 16. Organization dashboard contract

Recommended organization dashboard sections:

```text
Financial summary
Client and membership summary
Access summary
Branch comparison summary
Operations summary
Email/notification health summary
```

Each response must include:

- applied scope;
- branch filter summary;
- range;
- timezone;
- currency where applicable;
- generated-at timestamp;
- metric values;
- optional comparison with previous equivalent period only if current scope permits and query cost is bounded.

Avoid one unbounded endpoint that issues dozens of unrelated queries without limits. The ADR must choose between:

- one composed dashboard endpoint with bounded parallel/read-only queries; or
- several focused endpoints consumed by the frontend.

Recommended direction is a small summary endpoint plus focused detail/trend endpoints.

## 17. Branch comparison contract

Organization ADMIN may compare branches using consistent metrics.

Requirements:

- explicit allowlisted branch IDs or all authorized branches;
- maximum number of compared branches;
- deterministic branch ordering;
- same date range/timezone/currency/granularity;
- inactive branches included only when explicitly requested and authorized;
- no ranking that leaks unauthorized branches;
- metrics absent versus zero distinguished where necessary;
- no branch contact/private data included unnecessarily.

## 18. Audit query branch scope

The current audit query must be extended to support branch-aware authorization.

Requirements:

1. Organization ADMIN can query organization-wide or branch-filtered audit.
2. Branch ADMIN can query only assigned branches if audit access is approved by blueprint.
3. RECEPTIONIST remains denied unless a narrowly scoped operational audit view is explicitly approved.
4. Branch filter is applied in SQL.
5. Entries without branch metadata are treated as organization-global and visible only according to action/resource policy.
6. Branch-owned actions carry persisted branch snapshots.
7. Historical entries are never rewritten.
8. Query remains paginated, bounded, deterministic, and sanitized.
9. Cross-branch actors/resources do not leak through metadata.
10. Detail endpoint revalidates branch authorization independently.

## 19. Audit branch attribution

Branch attribution should use the most authoritative available representation:

1. dedicated audit branch column if current schema already has one;
2. approved normalized JSONB metadata field;
3. resource lookup only when safe, bounded, immutable, and explicitly approved.

Do not parse arbitrary JSON text in Java after loading all entries.

If current schema cannot support efficient/safe branch filtering, add a migration with:

- nullable `branch_id` for organization-global entries;
- restrictive branch FK or snapshot strategy according to historical requirements;
- backfill only when deterministic;
- index for branch/time/action query;
- no rewrite of ambiguous historical entries.

A migration is evidence-driven, not automatic.

## 20. Audit metadata sanitization

Sanitization remains deny-by-default.

Approved branch metadata may include:

- branch ID;
- safe branch code snapshot;
- scope mode;
- bounded comparison/export filters.

Never expose:

- passwords;
- invitation/recovery tokens;
- tokenized links;
- OAuth tokens/secrets;
- Gmail raw/message bodies;
- Stripe payloads/signatures;
- card data;
- QR token/fingerprint;
- storage paths;
- stack traces;
- SQL;
- full email bodies;
- attachment bytes;
- session IDs;
- unnecessary address/contact data.

Summary and detail projections may expose different sanitized allowlists.

## 21. Audit CSV branch scope

CSV export must reuse the same authorization and filters as JSON audit query.

Requirements:

- ADMIN-only or exact approved role/scope matrix;
- mandatory bounded date range;
- branch scope enforced server-side;
- organization export only for organization ADMIN;
- branch ADMIN export only for authorized branches if allowed;
- stable fixed columns;
- UTF-8;
- streaming;
- no temporary server file;
- maximum row count;
- reject overflow rather than silently truncate;
- CSV injection neutralization;
- sanitized metadata only;
- safe filename;
- secure download headers;
- export operation audited without recursive export loops.

## 22. CSV injection defense

Every textual CSV cell that begins, after leading whitespace, with a spreadsheet formula indicator must be neutralized.

At minimum protect:

```text
=
+
-
@
```

Follow the current encoder's approved strategy, such as prefixing an apostrophe.

Also handle:

- tabs/newlines/carriage returns;
- quotes and delimiters;
- UTF-8;
- nulls;
- long bounded metadata;
- nested sanitized JSON representation.

Do not weaken existing CSV-security tests.

## 23. Reporting export direction

This branch may add CSV export for selected reporting datasets only if the blueprint explicitly requires it.

Recommended required export remains audit CSV.

Optional financial/operational CSV exports should be deferred unless they provide clear current product value and can be implemented without duplicating the audit exporter unsafely.

Do not add arbitrary report export endpoints merely for completeness.

## 24. Query architecture

Each owning module should expose a minimal reporting projection or query contract.

Preferred architecture:

```text
reporting application service
    -> payment reporting query
    -> membership reporting query
    -> access reporting query
    -> operations reporting query
    -> notification reporting query
```

Rules:

- reporting does not import another module's infrastructure/entity classes;
- source modules own SQL against their tables;
- reporting composes technology-neutral projections;
- no giant cross-module SQL joining every business table;
- no duplicate business truth in reporting tables without explicit design;
- no report query mutates state;
- all report transactions are read-only.

## 25. Aggregation correctness

Prevent common reporting errors:

- counting status-history rows instead of canonical resources;
- multiplying payments through receipt/delivery joins;
- multiplying membership periods through coverage rows;
- counting Stripe attempts as payments;
- summing refunded/voided payments as revenue;
- using current branch instead of historical branch snapshot;
- grouping by current client branch when metric requires registration branch;
- counting email attempts as logical deliveries;
- counting multiple maintenance-history rows as multiple equipment items.

Every metric must have a reconciliation test against canonical fixtures.

## 26. Pagination, sorting, and filters

Detailed reporting/audit lists must use:

- bounded page sizes;
- deterministic tie-breakers;
- allowlisted sort fields/directions;
- bounded date ranges;
- validated granularity;
- authorized branch scope;
- escaped search patterns;
- parameterized SQL;
- stable empty results;
- no unbounded `findAll`.

Aggregate endpoints may return bounded fixed-size series rather than page objects.

## 27. Performance and indexes

Codex must inspect actual query plans before adding indexes.

Potential index patterns, only when justified:

```text
branch_id + occurred_at
branch_id + status + occurred_at
branch_id + payment_date/status
branch_id + result + occurred_at
branch_id + action_code + occurred_at
```

Requirements:

- inspect existing branch indexes from operational scoping;
- avoid redundant left-prefix indexes;
- consider write overhead;
- use partial indexes only when stable predicates justify them;
- verify query-plan use in PostgreSQL tests;
- establish representative dataset size;
- record local p95 baseline for critical dashboard/audit queries;
- do not introduce materialized views without evidence.

## 28. Caching policy

Default: no authoritative report cache.

A short-lived in-process cache may be considered only if:

- correctness is unaffected;
- cache key includes scope, branches, range, timezone, currency, and granularity;
- stale tolerance is documented;
- multi-instance behavior is understood;
- invalidation is not relied on for security;
- tests prove no cross-user/branch leakage.

Recommended first version uses direct indexed PostgreSQL queries and no new cache.

## 29. Concurrency and consistency

Reports use committed database state under the project's established isolation level.

Rules:

- no report claims serializable point-in-time consistency across separate module queries unless implemented;
- composed dashboard `generatedAt` documents request time, not a global database snapshot guarantee;
- read-only transactions where appropriate;
- exports use a stable query/order and documented consistency behavior;
- concurrent payment/access changes may appear according to committed-state timing;
- no report locks operational tables unnecessarily;
- no long database transaction around network I/O.

## 30. ProblemDetail direction

Recommended stable concepts:

```text
REPORT_VALIDATION_FAILED
REPORT_RANGE_TOO_LARGE
REPORT_BRANCH_ACCESS_DENIED
REPORT_UNSUPPORTED_GRANULARITY
REPORT_CURRENCY_CONFLICT
REPORT_DATA_ACCESS_FAILED
AUDIT_BRANCH_ACCESS_DENIED
AUDIT_EXPORT_LIMIT_EXCEEDED
AUDIT_EXPORT_DATA_ACCESS_FAILED
```

Follow repository naming conventions.

Errors must not expose SQL, plans, internal branch assignments, stack traces, or unauthorized branch existence.

## 31. API direction

Exact routes must follow current conventions.

Recommended capabilities:

### Dashboard summary

```http
GET /api/v1/reporting/dashboard
```

Filters may include:

- date range;
- authorized branch ID for organization ADMIN;
- organization scope flag only for organization ADMIN.

### Financial trend

```http
GET /api/v1/reporting/financial-trend
```

### Branch comparison

```http
GET /api/v1/reporting/branches/comparison
```

### Membership/access/operations insights

Use focused endpoints or sections according to ADR.

### Audit

Extend existing endpoints with authorized branch filters rather than creating parallel audit APIs.

### Audit CSV

Extend existing export endpoint with authorized branch scope.

Do not add branch IDs to request bodies where query parameters and active context are more appropriate.

## 32. Response contracts

Every report response should include enough context to prevent frontend ambiguity:

```text
scope
branches
from
untilExclusive
timezone
currency
granularity
generatedAt
metrics/series
```

Do not expose:

- raw SQL;
- internal query diagnostics;
- unauthorized branch summaries;
- personal client lists in aggregate responses;
- payment-provider details;
- audit secrets.

## 33. Events and audit of reporting actions

Reading dashboards generally should not create high-volume audit entries unless current policy requires it.

Security-sensitive exports should be audited.

Recommended export audit metadata:

- requested scope;
- branch IDs/codes within authorized scope;
- date range;
- filter names, not sensitive values where avoidable;
- row count;
- actor;
- occurred-at.

Do not audit exported CSV content.

Prevent recursive behavior where auditing an audit export causes the same export to include or repeatedly generate itself unexpectedly. Define cutoff/generated-at semantics.

## 34. OpenAPI and frontend contract

OpenAPI must document:

- role/scope visibility;
- branch authorization;
- default and maximum ranges;
- half-open date intervals;
- timezone used;
- currency semantics;
- metric definitions at a useful level;
- trend granularity;
- organization versus branch mode;
- comparison limits;
- audit branch filters;
- CSV limits and headers;
- safe errors;
- no full-accounting claim.

Frontend contract guide must explain:

- which dashboard sections each role may see;
- organization and branch filters;
- active branch defaults;
- empty/zero semantics;
- date/time/currency formatting;
- CSV download behavior;
- permission-denied behavior;
- no client-side security assumptions.

## 35. Migration decision

A migration is not automatic.

Possible justified changes:

- dedicated `branch_id` on audit entries for efficient secure filtering;
- missing indexes proven necessary by query plans;
- bounded reporting-support index.

Do not add:

- denormalized summary tables;
- materialized views;
- duplicated reporting facts;
- partitioning;

unless evidence demonstrates necessity and the plan is explicitly amended.

Any migration must use the database migration safety workflow, verify the next version after `V42`, and include clean/current PostgreSQL tests.

## 36. Block dependency graph

```text
Block 1: Discovery, metric catalog, ADR, access matrix, and report contracts
    -> Block 2: Financial and membership reporting queries
        -> Block 3: Access and operations reporting queries
            -> Block 4: Dashboard composition and branch comparison
                -> Block 5: Branch-aware audit query and detail authorization
                    -> Block 6: Branch-aware secure audit CSV streaming export
                        -> Block 7: REST API, security, OpenAPI, indexes, and performance
                            -> Block 8: End-to-end reconciliation, isolation, regression, and final gate
```

## 37. Progress model

- Block 1: 11%
- Block 2: 25%
- Block 3: 39%
- Block 4: 54%
- Block 5: 67%
- Block 6: 78%
- Block 7: 91%
- Block 8: 100%

Progress is earned only after each block's completion criteria and validation pass.

---

# Block 1: Discovery, metric catalog, ADR, access matrix, and report contracts

- **Status:** `NOT_STARTED`
- **Target progress:** 11%

## Objective

Define authoritative metrics, attribution, access visibility, date/time/currency semantics, API decomposition, audit branch behavior, and minimal technology-neutral contracts without production SQL or endpoints.

## Required work

1. Inventory all current dashboards and report queries.
2. Produce the complete metric catalog.
3. Produce the exact role/scope/report access matrix.
4. Identify branch attribution for each metric.
5. Reconcile organization totals with branch totals.
6. Define timezone/range/granularity behavior.
7. Define financial inclusion/exclusion rules.
8. Define audit global/branch attribution behavior.
9. Decide whether a migration is required later.
10. Define API decomposition and response context.

## ADR requirements

Create or update a focused ADR covering:

- report module ownership;
- source-module query contracts;
- role/scope/branch access matrix;
- organization/branch comparison semantics;
- metric definitions and reconciliation;
- timezone/date ranges;
- currency;
- dashboard endpoint decomposition;
- audit branch filtering;
- CSV scope/security;
- consistency guarantees;
- cache decision;
- migration/index decision process;
- explicit non-goals.

## Authorized production scope

Minimal contracts only, such as:

```text
ReportingScope
ReportingRange
ReportingGranularity
BranchReportingSelection
FinancialSummary
MembershipSummary
AccessSummary
OperationsSummary
BranchComparisonItem
DashboardSummary
```

Do not create every suggested type automatically. Reuse existing reporting contracts where possible.

## Required tests

- reporting-scope authorization pure policy;
- role/scope visibility matrix;
- range/granularity validation;
- timezone validation;
- branch selection normalization;
- currency-grouping policy;
- metric inclusion/exclusion contracts;
- reconciliation rules;
- audit branch-visibility policy;
- CSV scope policy;
- no framework/persistence leakage;
- public package boundaries;
- no multi-tenant/full-accounting contract.

## Explicit exclusions

- no migration;
- no production aggregate SQL;
- no controller;
- no audit adapter changes;
- no CSV implementation;
- no index changes;
- no frontend.

## Validation

```powershell
.\gradlew compileJava compileTestJava
```

Run focused Block 1 tests, current reporting/audit/security unit regressions, and Spring Modulith verification if contracts change.

## Completion criteria

- every metric has one authoritative definition;
- access matrix is explicit;
- report contracts are minimal and frontend-compatible;
- migration decision is evidence-based;
- Codex reports and stops.

---

# Block 2: Financial and membership reporting queries

- **Status:** `NOT_STARTED`
- **Depends on:** Block 1
- **Target progress:** 25%

## Objective

Implement efficient, branch-authorized PostgreSQL aggregation queries for financial, client, and membership metrics with exact reconciliation and no double counting.

## Required production scope

- payment reporting query adapter/contract;
- client reporting query adapter/contract;
- membership reporting query adapter/contract;
- financial trends;
- plan/coverage distribution;
- organization and branch scopes;
- bounded range/granularity;
- safe data-access failures.

## Financial requirements

- confirmed revenue;
- paid count;
- voided count/amount;
- refunded count/amount;
- payment-method distribution;
- average qualifying payment;
- daily/weekly/monthly trend;
- exact currency grouping;
- payment branch attribution;
- no attempts/history/receipt/email duplication.

## Client/membership requirements

- total/new clients;
- active/inactive clients;
- active/new memberships;
- active/new/expiring periods;
- plan distribution;
- coverage-scope distribution;
- registration/home-branch distinction;
- no multiplier from coverage association tables.

## Required tests

- deterministic two/multi-branch fixtures;
- organization total reconciliation;
- single-branch totals;
- authorized branch-set totals;
- PAID/VOIDED/REFUNDED correctness;
- PaymentAttempt excluded;
- currency handling;
- time boundaries/timezones;
- empty ranges;
- plan/membership coverage no double count;
- range/granularity bounds;
- SQL parameterization;
- read-only transactions;
- query plans/index evidence;
- data-access failure translation;
- current financial reporting regression.

## Explicit exclusions

- no access/equipment reporting;
- no dashboard controller;
- no audit changes;
- no CSV;
- no speculative cache.

## Validation

Compile, focused PostgreSQL/reporting tests, payment/correction/membership regressions, query-plan checks, and Spring Modulith verification.

## Completion criteria

- financial and membership aggregates are exact;
- organization totals reconcile;
- queries are bounded/indexed;
- no business state is mutated;
- Codex reports and stops.

---

# Block 3: Access and operations reporting queries

- **Status:** `COMPLETE`
- **Depends on:** Blocks 1 and 2
- **Target progress:** 39%

### ADR-012 notification reporting deferral

Operational notification aggregate reporting is **DEFERRED BY ADR-012** pending
an explicit future authorization and metric contract. Existing notification
reads remain scoped to the authenticated recipient; Block 3 does not broaden
notification visibility or add organization-wide, branch-wide, or
recipient-notification aggregates. Do not fabricate notification metrics from
audit entries, recipients, email deliveries, or unrelated operational events.
This deferral does not block completion of the remaining authorized Block 3
scope and does not remove notification reporting from the product roadmap.

## Objective

Implement efficient, branch-authorized aggregation queries for access, equipment, incidents, maintenance, notifications, and email-delivery operations.

## Access requirements

- total/allowed/denied;
- allowed rate;
- manual/QR sources;
- denial reasons;
- branch trend;
- hourly/day buckets;
- duplicate-scan denials;
- payment-required denials;
- membership-not-valid-at-branch denials;
- no QR/token exposure.

## Operations requirements

- equipment by status;
- out-of-service equipment;
- open/resolved incidents;
- incident distribution where supported;
- maintenance by status;
- overdue maintenance;
- operational notification reporting: DEFERRED BY ADR-012;
- current branch ownership consistency.

## Email requirements

- logical durable deliveries by status/type;
- failures by safe code;
- retries;
- account-activation ledger outcomes if supported;
- no attempt-as-delivery double count;
- no fabricated metrics for ephemeral identity emails.

## Required tests

- organization/branch reconciliation;
- access source/result/reason counts;
- timezones/buckets;
- duplicate and policy-denial counts;
- equipment/incident/maintenance states;
- overdue boundary;
- notification regression: recipient-scoped reads and no aggregate visibility expansion;
- email logical delivery versus attempts;
- Gmail `SENT` semantics;
- branch authorization;
- empty data;
- query plans/index evidence;
- no PII/secrets;
- current operational/reporting regression.

## Explicit exclusions

- no dashboard composition;
- no audit/CSV changes;
- no frontend;
- no predictive analytics.

## Validation

Compile, focused PostgreSQL/reporting tests, access/operations/email regressions, query-plan checks, and Spring Modulith verification.

## Completion criteria

- access and operations metrics are exact and branch-safe;
- email metrics reflect observable persisted truth only;
- queries remain bounded and efficient;
- Codex reports and stops.

---

# Block 4: Dashboard composition and branch comparison

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 3
- **Target progress:** 54%

## Objective

Compose source-module projections into stable dashboard summaries, focused trends, and branch comparisons with exact authorization and response context.

## Application behavior

1. Resolve actor scope and authorized branches.
2. Validate requested reporting scope/filter.
3. Normalize range/timezone/granularity.
4. Call source-module reporting queries.
5. Compose immutable response.
6. Include scope/range/timezone/currency/generated-at.
7. Never broaden authority during composition.
8. Use read-only transaction behavior appropriate to repository architecture.

## Dashboard requirements

- concise organization/branch summary;
- no unbounded detail rows;
- distinct financial/operational sections;
- stable empty/zero semantics;
- optional previous-period comparison only if ADR approved;
- exact role-based field/section visibility.

## Branch comparison requirements

- organization ADMIN or approved multi-assigned branch ADMIN;
- bounded branch count;
- same metric definitions;
- deterministic ordering;
- inactive branch inclusion only by explicit authorized request;
- no unauthorized branch name leakage.

## Required tests

- organization ADMIN organization summary;
- organization ADMIN branch summary;
- branch ADMIN assigned summary;
- RECEPTIONIST approved operational summary;
- unauthorized branch denial;
- active-branch default;
- branch comparison bounds;
- empty/zero behavior;
- previous-period semantics if used;
- source failure translation;
- no cross-branch leakage;
- read-only/architecture tests;
- response serialization.

## Explicit exclusions

- no controllers if reserved for Block 7;
- no audit filters;
- no CSV;
- no caching unless ADR explicitly approved it.

## Validation

Compile, focused service/authorization tests, source-query integration regressions, and Spring Modulith verification.

## Completion criteria

- dashboard composition is exact and role-aware;
- branch comparison is bounded and authorized;
- frontend receives explicit context;
- Codex reports and stops.

---

# Block 5: Branch-aware audit query and detail authorization

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 4
- **Target progress:** 67%

## Objective

Apply organization/branch scope to audit list and detail queries, preserve metadata sanitization, and add only evidence-supported schema/index changes.

## Required workflow

1. Inspect actual audit branch representation.
2. Determine whether SQL branch filtering is efficient and complete.
3. If migration is needed, use database-migration-safety workflow.
4. Preserve historical entries without deterministic branch attribution as organization-global/unknown according to ADR.
5. Never fabricate branch IDs.

## Query behavior

- organization ADMIN: organization or authorized branch filters;
- branch ADMIN: assigned branches only if blueprint permits audit access;
- RECEPTIONIST: denied unless narrowly approved;
- branch predicate applied in SQL;
- detail independently checks branch visibility;
- organization-global actions follow action/resource allowlist;
- pagination/filter/sort/range bounds preserved;
- sanitizer runs before response.

## Potential migration

Only if required:

- nullable branch snapshot column;
- deterministic backfill from approved metadata/resources;
- restrictive FK or immutable UUID snapshot according to historical needs;
- index on branch/time/action;
- no rewrite of ambiguous rows.

## Required tests

- organization audit;
- branch-filtered audit;
- branch ADMIN assigned/unassigned;
- detail IDOR;
- global actions visibility;
- historical unknown branch behavior;
- actor/action/resource/result/date filters combined with branch;
- pagination/deterministic order;
- sanitizer deny-by-default;
- secrets/tokens/bodies absent;
- query plan/index contract;
- migration clean/current if added;
- current audit regression.

## Explicit exclusions

- no CSV functional or branch-scope changes; only the user-approved temporary
  organization-scope authorization guard described below is allowed in Block 5;
- no report controller;
- no mutation of audit entries;
- no broad metadata expansion.

### Approved interim CSV authorization clarification

The existing audit CSV endpoint is temporarily restricted, fail-closed, to
`ADMIN + ORGANIZATION`. This is an interim security control, not the final
product permission model. Block 5 does not implement branch-scoped CSV export.
Block 6 must apply authoritative SQL-level branch filtering and validate
branch authorization before restoring CSV export access to `ADMIN + BRANCH`.

## Validation

Compile, focused audit persistence/application/security tests, PostgreSQL/migration tests if applicable, CSV sanitizer regression, and Spring Modulith verification.

## Completion criteria

- audit query/detail enforce branch scope;
- global actions are handled explicitly;
- historical entries remain truthful;
- sanitization remains strict;
- Codex reports and stops.

---

# Block 6: Branch-aware secure audit CSV streaming export

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 and 5
- **Target progress:** 78%

## Objective

Extend the existing audit CSV export with exact branch scope, shared filters, streaming, injection defense, overflow rejection, and export-operation auditing.

## Export behavior

- reuse audit query specification/authorization;
- mandatory bounded date range;
- organization export only for organization ADMIN;
- branch export only for authorized branches;
- fixed stable columns;
- include safe branch ID/code columns where approved;
- sanitized metadata;
- no unsanitized nested values;
- stream directly to response;
- no temporary files;
- maximum rows;
- reject overflow before/while streaming according to existing design;
- no silent truncation.

## Download headers

```text
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="audit-export-<safe-range>.csv"
Cache-Control: private, no-store
Pragma: no-cache
X-Content-Type-Options: nosniff
```

## Export audit

Audit the export operation safely with:

- actor;
- scope;
- branch filters;
- date range;
- row count;
- occurred-at.

Do not include exported content.

Prevent recursive/cutoff ambiguity by defining whether the export audit entry appears only in later exports.

## Required tests

- organization export;
- branch export;
- unauthorized branch denial;
- branch ADMIN policy;
- mandatory range;
- row limit/overflow;
- streaming/no temporary file;
- UTF-8;
- quoting/newlines;
- formula-injection defense;
- sanitized metadata;
- branch columns;
- headers/filename;
- export audit cardinality;
- no recursive leakage;
- memory/bounded behavior;
- current CSV regression.

## Explicit exclusions

- no financial CSV unless explicitly approved;
- no XLSX/PDF;
- no frontend;
- no email delivery of exports.

## Validation

Compile, focused CSV/export/security tests, large bounded dataset test, audit query regression, OpenAPI contracts where affected, and architecture verification.

## Completion criteria

- CSV respects exact branch authorization;
- formulas and sensitive metadata are neutralized;
- export is bounded and streamed;
- operation is audited safely;
- Codex reports and stops.

---

# Block 7: REST API, security, OpenAPI, indexes, and performance

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 6
- **Target progress:** 91%

## Objective

Expose final dashboard/report endpoints, update audit APIs, apply exact HTTP security, stabilize OpenAPI, and validate query performance and indexes.

## Controller requirements

- thin delegation;
- actor from authenticated principal;
- no client-side authority;
- strict filter DTOs;
- bounded branch/range/granularity inputs;
- no SQL or aggregation in controllers;
- no response-body logging;
- no mutation/CSRF requirement for read-only reports;
- export remains authenticated and authorized.

## Security matrix

- anonymous: `401`;
- organization ADMIN: organization and authorized branch reports;
- branch ADMIN: assigned branch reports only;
- RECEPTIONIST: approved operational views only;
- audit/export exact policy;
- no unsupported role;
- no cross-branch existence leaks.

## OpenAPI requirements

Document all semantics from Section 34, including exact role visibility and metric context.

## Performance work

- run representative PostgreSQL query plans;
- add only justified indexes through safe migration if not already added;
- verify no sequential scan on critical large-data path unless justified;
- verify no N+1;
- verify bounded bucket/branch/row counts;
- measure local p95 for dashboard, financial trend, access summary, audit query, and audit CSV startup;
- document dataset/environment;
- no false production SLA claim.

## Required tests

- route/method contracts;
- DTO allowlists;
- all report endpoints;
- role/scope/branch matrix;
- invalid range/timezone/granularity;
- safe ProblemDetail;
- audit branch query/detail/export APIs;
- download headers;
- OpenAPI paths/schemas/security/descriptions;
- query plans/indexes;
- representative performance baseline;
- existing API compatibility.

## Explicit exclusions

- no frontend;
- no cache introduction without amended ADR;
- no deployment;
- no unrelated hardening.

## Validation

Compile, reporting/audit web suites, security tests, OpenAPI tests, PostgreSQL query-plan tests, local performance checks, and Spring Modulith verification.

## Completion criteria

- APIs are secure and frontend-ready;
- report semantics are explicit;
- critical queries meet documented local targets or have nonblocking notes;
- indexes are justified and nonredundant;
- Codex reports and stops.

---

# Block 8: End-to-end reconciliation, isolation, regression, and final gate

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 7
- **Target progress:** 100%

## Objective

Complete the branch with full two/multi-branch reconciliation, authorization isolation, audit/export security, performance, architecture, regression, repository hygiene, next-branch readiness, and final quality gate.

## Deterministic dataset

Create controlled fixtures with:

```text
Organization
├── Branch A
├── Branch B
└── Inactive Branch C

Users
├── Organization ADMIN
├── Branch A ADMIN
├── Branch B ADMIN
├── Multi-assigned branch ADMIN if policy permits
├── Branch A RECEPTIONIST
└── Branch B RECEPTIONIST
```

Include representative:

- clients;
- plans/coverage;
- memberships/periods;
- PAID/VOIDED/REFUNDED payments;
- payment attempts;
- manual/QR access attempts and denial reasons;
- equipment/incidents/maintenance;
- notifications;
- durable email deliveries and attempts;
- identity activation metadata;
- audits with branch/global/unknown attribution.

## Reconciliation requirements

Verify:

- organization confirmed revenue equals sum of qualifying branch revenue;
- counts reconcile where metric semantics permit;
- void/refund values not double counted;
- membership counts not multiplied by coverage;
- access counts equal canonical attempt rows;
- logical email deliveries not multiplied by attempts;
- inactive branch history remains reportable;
- current dashboard totals remain compatible with prior implementation;
- zero/empty ranges behave consistently.

## Isolation requirements

For every endpoint/export:

- branch A actor sees A only;
- branch B UUID/filter cannot bypass authorization;
- organization ADMIN sees approved organization/all-branch data;
- RECEPTIONIST sees only approved sections;
- detail/export follows the same scope;
- no unauthorized branch names or totals leak through comparison/validation errors.

## Concurrency/consistency

- reports during payment correction;
- reports during access insertion;
- audit export during new audit entries;
- stable ordering/cutoff;
- no mutation or lock contention regression;
- no report cache leakage;
- PostgreSQL committed-state behavior documented.

## Security/privacy final review

- role/scope matrix;
- IDOR;
- sanitized audit metadata;
- CSV injection;
- safe exports;
- no Gmail/OAuth/login/invitation/recovery secrets;
- no QR/Stripe/card/storage-path leakage;
- no high-cardinality sensitive metrics;
- safe ProblemDetail;
- no real external network.

## Architecture final review

- source modules own report queries;
- reporting composes public projections only;
- audit owns audit query/export;
- no infrastructure/entity imports across modules;
- no giant cross-domain SQL;
- controllers thin;
- transactions read-only;
- Spring Modulith passes.

## Next-branch readiness

Confirm `feature/multi-branch-hardening` can validate the completed backend:

- final branch-aware reporting;
- final branch-aware audit/CSV;
- Gmail transport;
- invitations/account lifecycle;
- performance baselines;
- authorization matrix;
- OpenAPI/frontend contract.

Update the blueprint and frontend contract guide with the final reporting/audit behavior.

## Repository hygiene

Reject:

- generated reports/CSVs;
- database dumps;
- temporary performance scripts unless repository-approved and intentional;
- `.env` files;
- secrets;
- test output artifacts;
- ZIP files;
- build output;
- BOM characters;
- HTML entities in source;
- debug logs;
- broad unrelated formatting;
- frontend code;
- placeholder tenant fields.

## Required validation sequence

```powershell
.\gradlew compileJava compileTestJava
```

Run all multi-branch reporting/audit tests.

Run regressions for:

- identity/staff/organization/branches;
- clients/plans/memberships;
- payments/corrections/Stripe;
- credentials/manual/QR access;
- equipment/incidents/maintenance/notifications;
- Gmail email delivery;
- invitations/recovery;
- audit query/CSV;
- security/OpenAPI/storage.

Run:

```powershell
.\gradlew test `
  --tests "*ApplicationModuleStructureTests.verifiesModuleBoundaries" `
  --rerun-tasks
```

Run query-plan/performance checks.

Run:

```powershell
git diff --check
```

Finally:

```powershell
.\gradlew clean check
```

## Blueprint closure

Codex must report:

- all eight block statuses;
- final metric catalog;
- role/scope reporting matrix;
- reconciliation evidence;
- audit branch-attribution strategy;
- CSV limits/security;
- migration/index changes, if any;
- local performance baseline;
- exact blueprint sections implemented;
- required blueprint updates;
- next branch: `feature/multi-branch-hardening`;
- deferred final hardening/frontend work.

## Completion criteria

The branch reaches 100% only when:

- all eight blocks pass;
- organization and branch dashboards are accurate;
- financial totals reconcile;
- memberships/access/operations metrics are exact;
- branch-scoped actors cannot access other branches' data;
- audit list/detail enforce branch scope;
- CSV export enforces branch scope and injection defense;
- historical branch attribution remains truthful;
- report queries are bounded and index-supported;
- OpenAPI/frontend contract is aligned;
- Spring Modulith passes;
- Flyway validates;
- repository hygiene passes;
- `clean check` succeeds;
- final verdict is `PASS` or `PASS WITH NON-BLOCKING NOTES`;
- Codex stops.

---

# 38. Complete test matrix

## Metric-policy tests

- metric definitions;
- role/scope visibility;
- branch selection;
- date/timezone/range;
- currency;
- reconciliation rules;
- granularity.

## Financial tests

- PAID/VOIDED/REFUNDED;
- attempts excluded;
- payment methods;
- averages/trends;
- branch/organization reconciliation;
- no join multiplication.

## Client/membership tests

- home versus registration branch;
- active/new/expiring;
- plans and coverage scopes;
- no coverage-row multiplication;
- renewals where supported.

## Access tests

- allowed/denied;
- manual/QR;
- denial reasons;
- physical branch;
- duplicate/payment/coverage denials;
- time buckets.

## Operations tests

- equipment status;
- incidents;
- maintenance/overdue;
- notifications;
- inactive-branch history.

## Email tests

- logical deliveries;
- attempts/retries;
- Gmail accepted semantics;
- activation ledger;
- no fabricated ephemeral metrics;
- privacy.

## Audit tests

- organization/global/branch scope;
- list/detail IDOR;
- metadata sanitizer;
- historical unknown branch;
- filters/pagination/sorts.

## CSV tests

- scope;
- row limits;
- streaming;
- UTF-8;
- quotes/newlines;
- formula injection;
- safe metadata;
- headers/filename;
- export audit.

## HTTP/OpenAPI tests

- report endpoints;
- role/scope matrix;
- validation/errors;
- comparison limits;
- audit/export APIs;
- schemas/security/descriptions.

## Performance/architecture tests

- query plans;
- index use/nonredundancy;
- no N+1;
- bounded memory;
- local p95 baseline;
- read-only services;
- module boundaries;
- Spring Modulith.

# 39. Definition of Done

- [ ] Block 1 completed and validated
- [ ] Block 2 completed and validated
- [ ] Block 3 completed and validated
- [ ] Block 4 completed and validated
- [ ] Block 5 completed and validated
- [ ] Block 6 completed and validated
- [ ] Block 7 completed and validated
- [ ] Block 8 completed and validated
- [ ] ADR and metric catalog completed
- [ ] Reporting access matrix completed
- [ ] Financial reporting by organization and branch
- [ ] Client and membership reporting
- [ ] Access reporting and denial reasons
- [ ] Equipment/incident/maintenance reporting
- [ ] Safe email operational reporting
- [ ] Dashboard summary
- [ ] Branch comparison
- [ ] Date/timezone/currency semantics
- [ ] Organization/branch reconciliation
- [ ] Branch-aware audit query and detail
- [ ] Branch-aware audit CSV
- [ ] CSV injection defense
- [ ] Export limits and streaming
- [ ] IDOR and role/scope tests
- [ ] Query plans and indexes verified
- [ ] Local performance baseline recorded
- [ ] OpenAPI aligned
- [ ] Frontend contract updated
- [ ] No full-accounting claim
- [ ] No multi-tenant behavior
- [ ] No real external network in standard tests
- [ ] Spring Modulith passes
- [ ] Flyway history valid
- [ ] Repository hygiene passes
- [ ] `clean check` passes
- [ ] Blueprint update requirements identified

# 40. Risks and mitigations

## Risk: organization totals do not equal branch totals

**Mitigation:** metric catalog, identical qualifying sets, branch snapshots, deterministic fixtures, and explicit reconciliation tests.

## Risk: joins multiply payments or memberships

**Mitigation:** canonical source queries, `EXISTS`/pre-aggregation, no unrestricted many-to-many joins, and fixture-based count assertions.

## Risk: branch user retrieves another branch's metrics

**Mitigation:** server-authoritative reporting scope, SQL branch predicates, IDOR tests, and no Java post-filtering.

## Risk: historical data is attributed using current branch

**Mitigation:** persisted immutable branch snapshots and explicit metric-attribution rules.

## Risk: audit entries lack deterministic branch

**Mitigation:** explicit global/unknown behavior; evidence-based nullable branch snapshot migration; never fabricate attribution.

## Risk: CSV exposes spreadsheet formulas

**Mitigation:** proven injection neutralizer, leading-whitespace handling, fixed columns, and malicious-value tests.

## Risk: exports exhaust memory or database

**Mitigation:** mandatory ranges, row limits, streaming, deterministic SQL, rejection without silent truncation, and bounded tests.

## Risk: reporting queries slow operational traffic

**Mitigation:** source-owned indexed queries, query plans, bounded ranges/buckets, no speculative broad joins, and measured local baselines.

## Risk: report cache leaks cross-branch data

**Mitigation:** no new cache by default; if approved later, complete security-sensitive key and tests.

## Risk: financial dashboard is mistaken for accounting software

**Mitigation:** explicit product/docs language: revenue and operational finance reporting, not ledger, taxes, payroll, or bank reconciliation.

## Risk: high-volume report reads flood audit

**Mitigation:** audit exports and sensitive actions, not every dashboard read, unless policy explicitly requires otherwise.

## Risk: Gmail ephemeral identity emails are counted inaccurately

**Mitigation:** report only persisted observable truth; do not invent historical counts for unpersisted sends.

# 41. Deferred work

The following belongs to later work:

- final multisucursal hardening;
- frontend dashboards and charts;
- frontend audit and CSV UI;
- scheduled report emails;
- financial/operations CSV beyond approved scope;
- XLSX/PDF reports;
- advanced forecasting;
- data warehouse/BI;
- materialized views/partitioning based on future measurements;
- full accounting;
- multi-tenant reporting;
- production deployment.

Expected next branch:

```text
feature/multi-branch-hardening
```

# 42. Short prompts for Codex

## Start Block 1

```text
Read the active plan:
docs/branch-plans/multi-branch-reporting-audit-plan.md

Implement only Block 1: Discovery, metric catalog, ADR, access matrix, and report contracts.
Do not implement Blocks 2 through 8.
Follow AGENTS.md and the applicable skills, validate the authorized block, report evidence, and stop.
```

## Continue with a later block

```text
Review the current repository, approved ADR/metric catalog, and completed previous blocks.
Implement only Block <N>: <EXACT BLOCK NAME> from:
docs/branch-plans/multi-branch-reporting-audit-plan.md

Do not implement later blocks. Validate, report, and stop.
```

## Repair without advancing

```text
The authorized scope remains Block <N>.
Diagnose and fix the failure below without starting the next block:

<PASTE FAILURE>

Rerun the smallest failing scope, then the block validation. Report evidence and stop.
```
