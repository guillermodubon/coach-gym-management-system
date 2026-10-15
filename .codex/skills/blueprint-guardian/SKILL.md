---
name: blueprint-guardian
description: Validate Coach Gym backend requests, branch plans, architecture, roles, persistence, integrations, tests, and completion claims against the current authoritative blueprint. Use before implementing a branch or block, when scope is ambiguous, and before declaring a branch complete.
---

# Blueprint Guardian

1. Locate the current blueprint under `docs/blueprint/` and identify its version.
2. Read the complete active branch plan.
3. Read all blueprint sections relevant to the current request.
4. Extract required scope, non-goals, actors, permissions, invariants, persistence, integration boundaries, audit, HTTP, OpenAPI, reporting, tests, and Definition of Done.
5. Compare those requirements with current code, migrations, tests, and documentation.
6. Report conflicts before implementation.
7. Treat only the current user request or explicitly named block as authorized.
8. Read future blocks only to preserve compatibility.

## Non-negotiable checks

- modular monolith and Spring Modulith boundaries;
- only ADMIN and RECEPTIONIST;
- no client authentication unless an approved revision says otherwise;
- no real external networks in tests;
- no card storage or Stripe Live Mode;
- redirects do not confirm payments;
- provider webhooks require verified signatures and idempotency;
- important history is preserved;
- critical invariants receive database protection;
- cross-module types are public contracts of the owning module.

## Stop conditions

Stop and report when the request conflicts with the blueprint, belongs to a future phase, requires an unknown contract, weakens security or integrity, or requires unavailable secrets or real networks.
