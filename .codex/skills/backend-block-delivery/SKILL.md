---
name: backend-block-delivery
description: Implement exactly one explicitly authorized block of a Coach Gym Spring Boot backend branch as a production-quality vertical slice. Use for models, policies, ports, persistence, application services, events, audit, REST, OpenAPI, reporting, and tests. Stop before later blocks and never perform Git mutations.
---

# Backend Block Delivery

## Inspect

- Read applicable `AGENTS.md` files, the current blueprint, and the complete active branch plan.
- Use `$blueprint-guardian`.
- Inspect current Git status and preserve unrelated changes.
- Inspect the owning module, analogous current code, migrations, security, OpenAPI, reporting, and tests.
- Identify exact current-block files and explicitly deferred future-block files.

## Plan

Before editing, state the authorized block, required behavior, files, database implications, security implications, tests, validation, and deferred work.

Do not ask for confirmation unless a real ambiguity blocks safe implementation.

## Implement

- Keep controllers thin and DTOs separate.
- Use constructor injection and explicit application transactions.
- Use ports and adapters.
- Keep provider SDK types inside infrastructure.
- Use parameterized SQL and database constraints for critical invariants.
- Use optimistic locking and idempotency where concurrency or duplicate delivery applies.
- Publish privacy-safe public events for cross-module effects.
- Preserve history and unrelated behavior.
- Avoid broad refactors and temporary repository scripts.

## Validate

1. Compile production and test code.
2. Run the smallest focused tests.
3. Run persistence or web integration tests as applicable.
4. Run affected-module regression.
5. Run Spring Modulith verification when boundaries change.
6. On the final block, invoke `$quality-gate-review` and run `clean check`.

Diagnose failures by category before changing code: production, test expectation, database or migration state, architecture, security setup, encoding, or infrastructure.

## Stop

Produce the completion report required by root `AGENTS.md`, then stop. Never start the next block automatically.
