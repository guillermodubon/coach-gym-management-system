# ADR-007: Single-organization and multi-branch foundation

- **Status:** Accepted for `feature/organization-branch-foundation`
- **Date:** 2026-09-20

## Context

Coach Gym currently operates as one business with a singleton `gym.gym_settings`
row and deployment properties for the application clock and default currency.
The approved roadmap adds physical branches before staff assignments and before
operational data becomes branch-scoped. The system must not silently become a
multi-tenant product or duplicate organization identity in several writable
places.

## Decisions

1. The product models one canonical Coach Gym organization that may own many
   physical branches. It is multisucursal, not SaaS multi-tenant: there is no
   tenant switching, tenant billing, tenant identifier propagation, or unrelated
   gym isolation in this roadmap.
2. A dedicated `organization` Spring Modulith module owns public organization
   and branch contracts. Persistence and HTTP implementation remain in later
   blocks. The root package is the only supported cross-module boundary.
3. Organization legal name, brand name, support contact, default IANA timezone,
   and default ISO 4217 currency become canonical runtime business data in the
   organization aggregate. The existing settings row remains a compatibility
   source for current features until Block 5 reconciles consumers; no new code
   may create a second writable source of truth.
4. The migration will seed one deterministic organization and one initial branch
   representing the historical single-location context. It will not rewrite
   existing business tables or assign branch foreign keys before the later
   operational-scoping branch.
5. Organization and branch codes are normalized uppercase stable identifiers
   and immutable after creation. Human-readable names preserve accents while
   normalizing surrounding and repeated whitespace.
6. Organization and branch lifecycle uses `ACTIVE` and `INACTIVE`. The one
   canonical organization cannot be deactivated while it is the only operational
   organization. The initial branch cannot be deactivated while it is the only
   active branch. Reactivation is allowed; deletion is not a business operation.
   Status changes require a bounded reason and an expected version.
7. Organization defaults apply to future branch creation and future behavior;
   they never rewrite historical payment, receipt, audit, or email snapshots.
   A branch may inherit the organization timezone at creation or explicitly use
   another valid IANA identifier. Currency remains one organization default; no
   multi-currency workflow is introduced.
8. Public summaries contain only stable identifiers, display values, timezone,
   currency, status, and the initial-branch marker needed by future staff
   assignments. Commands omit actor, timestamps, organization selection,
   status ownership, initial markers, tenant identifiers, and assignment data.

## Consequences

- Block 1 provides immutable, framework-free contracts and pure lifecycle rules.
- Block 2 owns the Flyway schema and deterministic seed.
- Blocks 3–6 add persistence, services, audit reconciliation, REST, security,
  and OpenAPI without broad branch scoping.
- Staff organization/branch assignments, active-branch sessions, operational
  branch ownership, branch-aware audit/reporting, and frontend behavior remain
  deferred to their approved branches.

## Security and privacy

Only `ADMIN` and `RECEPTIONIST` remain supported roles. This block introduces no
authorization endpoint and no session branch context. Future mutations must be
ADMIN-only and CSRF-protected; future reads must implement the exact blueprint
role policy. Contracts intentionally exclude credentials, secrets, raw tokens,
tenant selectors, and unbounded personal data.
