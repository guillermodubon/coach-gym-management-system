# Test Source Rules

These instructions apply to all tests in addition to the root `AGENTS.md`.

Tests must verify observable behavior and critical invariants without real external network calls.

For protected functionality, cover anonymous, ADMIN, RECEPTIONIST, method security, valid CSRF, missing CSRF, and safe ProblemDetail responses as applicable.

For persisted behavior, cover PostgreSQL constraints, optimistic locking, idempotency, duplicate requests, rollback, absence of partial writes, immutable history, exact financial values, and restrictive relationships as applicable.

Follow the current repository's PostgreSQL and Testcontainers conventions. Report Docker failures as infrastructure blockers.

Use deterministic values where practical. Avoid shared mutable state. Write UTF-8 without BOM. Never paste HTML markup or entities into Java, SQL, JSON, or YAML source.
