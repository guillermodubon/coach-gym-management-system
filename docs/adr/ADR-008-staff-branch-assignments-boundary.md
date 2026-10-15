# ADR-008: Staff scope and branch-assignment boundary

- **Status:** Accepted for `feature/staff-branch-assignments`
- **Date:** 2026-09-21
- **Decision owners:** Coach Gym backend maintainers

## Context

Coach Gym has one organization with multiple branches. Staff authorization is
already role based (`ADMIN` and `RECEPTIONIST`), but branch ownership and staff
scope have not yet been made explicit. Later operational modules need a safe,
stable contract for determining which branches a staff member may use without
making a selected branch a source of authority.

The current branch is contract and policy work only. It must not introduce a
database migration, persistence adapter, session implementation, HTTP endpoint,
event, audit record, or operational query filter.

## Decision

### Scope and role matrix

Each staff account has exactly one organizational scope:

| Role | Organization scope | Branch scope |
| --- | --- | --- |
| `ADMIN` | allowed | allowed |
| `RECEPTIONIST` | forbidden | allowed |

The combination is validated at the public contract boundary. An account may
have multiple existing roles only if the resulting combination remains
compatible with this matrix; no new role is introduced.

### Assignment lifecycle

Branch assignments are append-only lifecycle facts. An assignment transitions
from `ACTIVE` to `ENDED`; it is never deleted or rewritten. Ending an
assignment records a reason, actor, timestamp, and optimistic version. A
future database migration must prohibit duplicate active assignments for the
same staff member and branch and must enforce restrictive ownership rules.

An active branch-scoped staff member must retain at least one active branch
assignment. A valid scope transition may end the final assignment atomically;
otherwise the transition is rejected. The last active organization
administrator cannot be demoted, lose organization scope, or be removed.

### Administrative authority

Only an active `ADMIN` with `ORGANIZATION` scope may assign staff, end
assignments, or change another staff member's scope. Self-assignment and
self-scope changes are rejected. Branch-scoped administrators and
receptionists can operate only within their already assigned branches in later
branch-aware features; they cannot manage staff scope or assignments.

### Existing-account migration

The next persistence block will migrate current accounts deterministically:

- existing `ADMIN` accounts become organization-scoped;
- existing `RECEPTIONIST` accounts become branch-scoped and receive a
  deterministic initial branch assignment;
- the migration actor is a system/bootstrap actor, not an end user;
- no account is left with an incompatible role/scope combination.

The migration must be forward-only and must not rewrite historical audit data.

### Active branch context

The selected branch is a preference/context value, not an authorization grant.
The future implementation will store it in the existing server-side
`HttpSession` rather than in a client-controlled unsigned cookie or header.
Every security-sensitive consumer must still resolve current role, scope,
assignment, and branch status from authoritative state. A context is cleared or
rejected when the branch is inactive, the assignment ends, or the staff scope
changes. Horizontal deployment therefore requires shared session state or the
single-instance constraint already present in the current deployment target.

Organization-scoped administrators may select any active canonical branch;
branch-scoped staff may select only an active branch to which they are assigned.
No selection expands the set of branches a caller may access.

### Public boundary

The `user` root package owns technology-neutral immutable contracts for scope,
assignment details, safe branch summaries, context, commands, query/context
ports, and pure policies. Contracts do not expose Spring, JPA, JDBC, servlet,
session, provider, password, contact, or storage implementation types. Branch
existence and lifecycle remain owned by the `organization` module and are
consumed through safe projections in later blocks.

## Consequences

- Future application services can enforce the role/scope and anti-lockout
  rules consistently before persistence is added.
- The public API is ready for later persistence, session, and HTTP work without
  coupling the user module to organization internals.
- Operational branch filtering, assignment storage, migration, and session
  invalidation remain deliberately deferred to later blocks.
- Database uniqueness, restrictive foreign keys, append-only triggers, and
  optimistic version checks must be implemented before this feature is
  considered complete.

## Security and privacy boundaries

No raw credentials, passwords, session identifiers, branch contact details, or
provider data are represented by these contracts. A branch id received from a
client is only a requested preference; authoritative authorization must be
re-evaluated for every protected operation. Errors must be mapped by a later
HTTP layer without exposing persistence or internal implementation details.

## Non-goals for this branch

- no database migration or schema change;
- no assignment/scope application service or persistence adapter;
- no session attribute implementation or browser cookie/header contract;
- no controller, OpenAPI change, event, audit record, or operational branch
  filter;
- no frontend or external infrastructure provisioning.
