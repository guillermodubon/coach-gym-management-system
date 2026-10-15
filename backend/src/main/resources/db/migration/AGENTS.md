# Flyway Migration Rules

These instructions apply to this directory in addition to the root `AGENTS.md`.

Before writing SQL, use `$database-migration-safety` and inspect migration versions, Git history, original DDL, later alterations, mapped code, and current integration tests.

Never modify an applied migration, reuse a version, create a missing-history placeholder, run repair automatically, edit `flyway_schema_history`, suppress validation errors, or add a redundant index.

Use named constraints. Protect critical invariants in PostgreSQL. Consider existing rows, nullability, uniqueness, valid transitions, idempotency, foreign-key delete behavior, transaction order, and JPA or JDBC flush order.

Every critical migration needs source-contract coverage, PostgreSQL schema verification, direct constraint or trigger tests, clean-database migration coverage, and compatibility with current migration history.
