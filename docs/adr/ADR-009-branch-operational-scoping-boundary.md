# ADR-009: Branch operational scoping and ownership boundary

- **Status:** Accepted for `feature/branch-operational-scoping`
- **Date:** 2026-09-22
- **Decision owners:** Coach Gym backend maintainers

## Context

Coach Gym has one organization with multiple physical branches. The
organization and staff-assignment branches already provide canonical branch
identity, staff scope, active assignments, and a server-side active-branch
preference. Operational modules still contain records that were created before
branch ownership existed, so this branch must establish the ownership contract
before changing their persistence or application adapters.

Branch selection is a request-time context, never an authorization grant. A
resource UUID, a body field, a path variable, or a frontend-selected branch
must not bypass the authenticated staff member's current scope, active
assignments, or branch lifecycle state.

## Decisions

### 1. Ownership matrix

| Resource or relationship | Ownership classification | Contract and immutability decision |
| --- | --- | --- |
| Organization and branch | organization-global / branch identity | Owned by the `organization` module. Only active canonical branches are eligible for new operational work. |
| Client | branch-owned home branch | A client receives `homeBranchId` at registration. It is an operational origin, not membership coverage. Changes require a future explicit audited operation; no generic transfer endpoint exists here. |
| Membership and membership period | immutable branch-origin snapshot | Store the branch where the membership or period was registered. Final cross-branch validity belongs to `feature/branch-policies-memberships-access`. |
| Payment and correction/history | immutable branch-origin snapshot | The registration branch remains authoritative for financial reads, corrections, receipts, and history. It is never recalculated from the current actor. |
| Stripe payment attempt | immutable initiation snapshot | The branch that initiated the attempt is persisted and used by webhook/background processing. Provider input cannot change it. |
| Payment receipt | inherited payment branch or immutable snapshot | Authorization follows the source payment. A duplicate snapshot is allowed only when a later query/storage requirement justifies it. |
| Access credential | client-owned, not branch-owned | Credential format and token protection remain unchanged. Credential lifecycle authorization uses the client's authorized operational context. |
| Access attempt | direct physical branch ownership | The scanning/check-in branch is assigned server-side and is immutable. It is distinct from credential origin. |
| Equipment | direct branch ownership | Equipment belongs to one branch; generic transfer is deferred. |
| Incident | direct or equipment-inherited branch snapshot | Equipment-related incidents must match the equipment branch and remain immutable. |
| Maintenance operation | equipment/incident-derived branch snapshot | A mismatch is rejected before side effects. History is not reassigned. |
| Notification | branch-owned only when resource-related | Global staff/system notifications remain organization-global; resource notifications inherit the source branch. |
| Transactional email delivery | immutable source-branch snapshot | Initial authorization follows the source resource. Retries use persisted ownership, never the current actor session. |
| Audit entry/event metadata | safe branch snapshot when applicable | New branch-owned actions may record branch ID and bounded code only. Historical entries are never rewritten; final branch search/export UX is deferred. |
| Storage/document artifact | source-resource ownership | Database ownership authorizes reads before storage access. Existing keys remain compatible and are never an authorization boundary. |

### 2. Server-derived branch authority

The authoritative decision is derived from:

```text
authenticated staff identity
+ current persisted role and organizational scope
+ current active branch assignments
+ active branch lifecycle state
+ server-side active branch context when the operation requires one
```

The user module owns the framework-free authorization contracts. The
organization module owns branch lifecycle and branch projections. Operational
modules own their own branch relationship and persistence. No shared
cross-module repository is introduced.

For a branch-owned creation, the application service must resolve and validate
the effective active branch in the same transaction as the resource write. A
conflicting request branch is rejected; a missing branch is never inferred
from frontend state or from a related resource loaded without authorization.

### 3. Active context and organization-wide access

Ordinary detail, lifecycle, mutation, and branch-scoped list operations require
an active, authorized branch context. The target resource branch must match
that context.

An active `ADMIN + ORGANIZATION` may explicitly address another active branch
through an allowlisted administrative branch target or filter. The target must
still belong to the canonical organization and the current active branch
projection. Organization-wide list behavior remains bounded and is implemented
by later REST/query blocks; this ADR does not add a controller or report.

`ADMIN + BRANCH` and `RECEPTIONIST + BRANCH` are restricted to their active
assigned branches. Selecting a different assigned branch changes context only;
it never expands permissions. An inactive branch or ended assignment is not
eligible even if a stale session value or valid UUID is supplied.

### 4. Cross-branch denial semantics

The application must authorize after loading the authoritative resource
relationship and before returning details, performing a mutation, or reading a
storage artifact. A cross-organization or unauthorized branch must result in a
safe denial without branch names, assignments, storage keys, SQL, or resource
existence details.

The later HTTP layer will map ordinary resource IDOR denials to the repository's
security-safe not-found semantics where existence disclosure is possible. An
explicit organization-admin endpoint may use `403` when its purpose already
authorizes existence disclosure. The contract layer exposes only generic safe
exceptions; it does not choose HTTP status codes.

### 5. Migration and historical policy

Operational ownership is introduced through ordered forward-only migrations:

1. establish or confirm the deterministic initial organization and branch;
2. add nullable ownership columns only where a staged backfill is required;
3. backfill existing rows to the initial branch using authoritative relations;
4. validate row counts, nulls, orphan references, and relationship consistency;
5. add restrictive foreign keys, non-null constraints, and evidence-based indexes;
6. enforce new-creation and immutable-snapshot rules in application/database
   boundaries.

Existing timestamps, status, versions, financial values, access history,
receipts, files, email deliveries, events, and audit entries remain unchanged.
No event, receipt, file, email, or audit record is regenerated during backfill.
Applied migrations are immutable and no migration repair or history rewrite is
allowed.

### 6. Events, audit, and storage

When a public event represents a branch-owned resource, it carries the minimal
persisted `branchId`; a bounded `branchCode` snapshot is optional only when a
consumer demonstrably needs it. Address, phone, assignment lists, and other
contact/personal data are excluded. Consumers use the persisted resource branch
instead of the current actor session.

Audit metadata may preserve the same safe branch reference. Existing audit
entries are append-only and untouched. Branch filtering and CSV/reporting
experience remain deferred to the approved reporting/audit branch.

Storage authorization always occurs against the database resource before the
storage adapter reads an object. Storage keys are opaque implementation data,
are not returned by ordinary APIs, and are never treated as an authorization
boundary. Existing keys remain readable during later namespace changes.

### 7. Transactions and concurrency

Branch authorization and a branch-owned mutation share one application
transaction boundary. The decision must observe committed scope, assignment,
and branch lifecycle state. Optimistic locking, idempotency, append-only
history, and restrictive foreign keys remain in force. No process-local cache
may grant stale branch access, and external I/O must not be placed inside
avoidable database locks. For a write transaction with an active branch, the
identity authorization query holds a PostgreSQL shared row lock on that active
branch; branch-scoped staff also lock the active assignment row. A concurrent
branch deactivation or assignment end therefore serializes with the operation:
the already-authorized mutation may commit first, or the later operation sees
the inactive/ended state and fails closed. Read-only requests do not acquire
these lifecycle locks. The server-side selected branch remains a session
preference and is revalidated from persisted authorization for each operation.

## Consequences

- Block 1 introduces only immutable, technology-neutral contracts and pure
  policy rules in the public `user` module.
- Blocks 2–6 add ownership columns, backfills, adapters, events, audit/storage
  propagation, and cross-module consistency.
- Block 7 adds HTTP/search/OpenAPI behavior and query-plan evidence.
- Block 8 performs full migration, isolation, concurrency, architecture, and
  regression validation.
- Membership-plan coverage, branch-specific access/payment policies,
  consolidated reporting, final audit/CSV filters, transfers, frontend branch
  navigation, and multi-tenancy remain explicitly deferred.

## Security and privacy boundaries

Only `ADMIN` and `RECEPTIONIST` remain supported roles. The contracts contain
identifiers and scope facts only; they contain no passwords, tokens, session
IDs, storage keys, contact data, provider payloads, or secrets. Staff mutations
remain CSRF-protected at the HTTP layer. Standard tests must remain local and
must not call external providers.

## Non-goals for this branch block

- no Flyway migration or broad persistence change;
- no operational adapter, query, controller, OpenAPI, report, or audit-filter
  implementation;
- no frontend code or external infrastructure;
- no membership coverage or branch-specific payment/access policy;
- no branch transfer or historical reassignment endpoint.
