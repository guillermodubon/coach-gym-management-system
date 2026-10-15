# ADR-010: Branch policies for membership coverage and access

- **Status:** Accepted for `feature/branch-policies-memberships-access`
- **Date:** 2026-09-23
- **Decision owners:** Coach Gym backend maintainers

## Context

The organization, branch identity, staff branch scope, and immutable
operational origins are already established. Membership plans do not yet
express eligible branches, membership periods have no immutable coverage
snapshot, and access still evaluates the existing lifecycle, duplicate, and
payment rules without branch entitlement. This branch must define those
contracts before schema, persistence, or application integration changes.

The current baseline was inspected, including ADR-004, ADR-009, Flyway V31–V36,
the plan and membership module boundaries, `AccessPolicy`, QR duplicate
handling, and the confirmed-payment query. In particular:

- V33 assigns existing membership and period registration branches from the
  authoritative client/membership branch origin and records the canonical
  initial branch for the original single-branch data set.
- The current manual denial order is defined by `AccessPolicy`.
- QR duplicate detection is branch-aware and is evaluated before payment; a
  recent duplicate overrides the otherwise evaluated QR membership result.
- The payment query accepts the exact client, membership, and period, and only
  treats a persisted `PAID` payment as confirmed.

## Decisions

### 1. Plan coverage scope

Every plan has exactly one scope:

| Scope | Plan-definition branch set | Cardinality |
| --- | --- | --- |
| `SINGLE_BRANCH` | Explicit branch IDs | Exactly one active branch |
| `SELECTED_BRANCHES` | Explicit branch IDs | At least two active branches |
| `ALL_BRANCHES` | No explicit branch IDs | All currently active branches in the canonical organization |

Plan-definition `ALL_BRANCHES` is dynamic: a newly opened active branch is
eligible for new sales and renewals without rewriting the plan. All referenced
branches must be active branches of the canonical organization. Promotions do
not expand coverage. Plan coverage changes are versioned, atomic, and never
rewrite existing membership-period snapshots.

### 2. Membership entitlement snapshot

Coverage is snapshotted per membership period at creation or renewal. The
snapshot stores the source scope and the exact, sorted, non-empty branch set
eligible for that period. This also applies to an `ALL_BRANCHES` plan: the
period captures the active branch set at that moment, so a later branch
opening does not silently expand an already purchased period. A renewal may
capture the then-current active set.

Access uses only the current membership period's snapshot. Client home branch
and registration branch remain operational origins; neither independently
expands the purchased entitlement. Snapshot rows are immutable and
historically explainable. Membership creation and renewal require a
server-resolved active operational branch, and the selected plan must be
eligible at that branch. The service, not the request body, resolves the exact
snapshot branch set from the plan and active branch catalog.

### 3. Existing data

The current V33 ownership migration establishes the canonical initial branch
as the baseline for pre-existing single-branch data, assigning periods from
their membership's authoritative registered branch. New coverage migration
work must preserve those relationships and existing access, payment, and QR
history. Before migration, inspect the actual rows and later ownership changes.
If a record cannot be mapped unambiguously from authoritative ownership, the
migration must fail for review rather than guess, rewrite financial history,
or silently assign another branch.

### 4. Plan coverage authorization and concurrency

Only `ADMIN + ORGANIZATION` may create or mutate plan coverage. `ADMIN + BRANCH`
and `RECEPTIONIST + BRANCH` may read only plans eligible in their authorized
active branch; neither may mutate organization plan coverage. This is the
stricter current blueprint policy because delegated branch plan administration
is not explicitly approved. Every mutation requires the expected plan version
and a CSRF-protected staff request when exposed over HTTP.

### 5. Branch lifecycle

An inactive branch cannot be selected as the staff's active context, used for
membership sale/renewal, or used for check-in. Branch deactivation never
deletes plan associations or rewrites period snapshots. Deactivation must be
rejected if it would leave an active plan with no active eligible branches or
would violate that plan scope's minimum cardinality; an administrator must
first adjust coverage or deactivate the plan explicitly. Existing period
entitlements remain intact. A member can use another active branch only if
that branch is already in the period snapshot. No membership is automatically
transferred, terminated, or expanded.

### 6. Cross-branch duplicate policy

The existing same-branch QR duplicate rule remains unchanged: it is based on
the same credential and only a previously allowed QR entry, using the
configured half-open window. Additionally, an allowed entry by the same client
at a different branch is a duplicate within that window. A prior denied entry
does not consume the cross-branch window. The exact window boundary is not a
duplicate. The future adapter must query authoritative PostgreSQL history,
use server timestamps, persist the physical branch, and serialize competing
decisions using database-backed coordination; an in-memory-only lock is not
authoritative. The existing `DUPLICATE_CHECK_IN` code is retained; the denial
must not disclose the other branch or its timestamp.

### 7. Effective access-payment policy

Keep the existing organization-level boolean as the default and add an
optional branch override with `INHERIT`, `REQUIRED`, and `NOT_REQUIRED` modes.
`INHERIT` follows the current organization default and is not equivalent to
`NOT_REQUIRED`. The effective policy is resolved for the physical check-in
branch. `ADMIN + ORGANIZATION` manages the default and overrides;
`ADMIN + BRANCH` may read its effective policy but cannot change it;
`RECEPTIONIST` cannot manage policy. Default and override updates use expected
versions and safe audit metadata.

Only a persisted `PAID` payment for the exact client, membership, and current
membership-period tuple qualifies. Payment attempts, voided payments, and
refunded payments do not qualify. A failed policy/payment read remains a safe
system error and must not be converted into a payment denial or fail-open
allow. Policy changes do not rewrite prior access attempts.

### 8. Access denial precedence

Authorization and branch-lifecycle checks are preconditions: an unauthenticated
or unauthorized actor, missing active branch context, or inactive physical
branch must not receive membership or coverage details and is not represented
as a membership eligibility reason.

When persisted access denials compete, use this highest-to-lowest precedence:

1. `ACCESS_CREDENTIAL_INVALID` (credential resolution fails before client
   disclosure).
2. `DUPLICATE_CHECK_IN` (preserves current QR behavior: it overrides the
   otherwise evaluated membership result and remains ahead of payment lookup).
3. `IDENTIFIER_NOT_FOUND`.
4. `CLIENT_INACTIVE`.
5. `MEMBERSHIP_NOT_FOUND`.
6. Existing membership lifecycle/date order: `MEMBERSHIP_CANCELLED`,
   `MEMBERSHIP_FROZEN`, `MEMBERSHIP_EXPIRED`,
   `MEMBERSHIP_PERIOD_EXPIRED`, then `MEMBERSHIP_NOT_STARTED`.
7. `MEMBERSHIP_NOT_VALID_AT_BRANCH`.
8. `PAYMENT_REQUIRED`.
9. `ACCESS_ALLOWED` when there is no denial candidate.

Branch coverage is evaluated only after the current client, membership, and
period lifecycle/date checks pass. This avoids using coverage denial to reveal
entitlement about an otherwise ineligible membership. A coverage denial uses a
generic safe message and never returns the covered-branch list. Payment is
evaluated only after other eligibility checks; for QR, duplicate detection
remains ahead of payment as it is today. `MEMBERSHIP_NOT_VALID_AT_BRANCH` is a
public application contract for a later integration block; it is not emitted
or persisted until a later Flyway migration adds it to the database constraint.

### 9. Audit and privacy

Access records continue to store the physical branch already established by
V35. New access denials record only the safe reason and ordinary attempt
snapshot; they do not include the complete entitlement set, another branch's
identity, payment records, provider details, QR payloads, or tokens. Coverage
administration audit events may include actor, plan/branch identity, scope
before/after, versions, timestamp, and bounded change counts, but not client
membership details. No raw credentials or payment-provider data are added to
logs, events, or public contracts.

### 10. Reporting deferral

Consolidated cross-branch reports, branch-filtered audit search/export, and
coverage analytics remain outside this branch. They are deferred to
`feature/multi-branch-reporting-audit`; this is a delivery boundary, not a
permanent architectural prohibition.

## Block boundary

Block 1 adds only technology-neutral coverage/policy contracts and pure
validation/precedence policies. It does not add Flyway migrations,
persistence adapters, plan or membership application-service changes, access
integration, audit listeners, controllers, or reporting behavior. Those remain
in their explicitly sequenced later blocks.

## Consequences

- Plan definitions can evolve without retroactively changing a purchased
  period's branch entitlement.
- Existing membership lifecycle and same-branch duplicate behavior remain
  authoritative and testable.
- Branch-specific payment behavior is explicit while preserving the current
  organization default and exact-period payment boundary.
- A new access denial code requires schema support before any later block can
  persist it.
- Query and write adapters must honor these contracts in their own authorized
  blocks; this ADR does not imply those behaviors are already integrated.
