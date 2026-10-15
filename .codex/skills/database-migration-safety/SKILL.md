---
name: database-migration-safety
description: Design, review, troubleshoot, and test PostgreSQL Flyway changes for Coach Gym. Use for tables, columns, constraints, indexes, triggers, functions, financial integrity, idempotency, or migration validation failures. Never repair or rewrite migration history automatically.
---

# Database Migration Safety

## Before SQL

1. List migrations numerically.
2. Detect duplicate versions.
3. Search all Git history and branches for the proposed version.
4. Inspect original DDL and every later alteration.
5. Inspect constraints, indexes, functions, triggers, mappings, adapters, and analogous tests.
6. Check compatibility with existing rows.
7. Consider transaction and flush order.

## Design

Use a genuinely available version, descriptive filename, named constraints, deterministic PostgreSQL SQL, restrictive historical relationships, and only necessary indexes.

Protect critical invariants in PostgreSQL, including validity, uniqueness, state transitions, positive amounts, currency or format rules, idempotency, and append-only history when applicable.

## Never

- edit or renumber an applied migration;
- create a placeholder for missing history;
- modify `flyway_schema_history`;
- run repair automatically;
- disable validation to hide errors;
- assume the working tree contains all prior migrations;
- destroy a non-disposable database.

## Validation

Add source-contract tests, schema inspection, direct constraint or trigger tests, rollback tests, clean-database migration coverage, and current-history Flyway validation as applicable.

For validation failures, extract the exact message and database URL, inspect local migrations and Git history, restore the exact original missing file, compare hashes or checksums, and rerun the smallest affected test.
