---
name: quality-gate-review
description: Perform an independent senior-quality review of a Coach Gym backend block or branch. Use before completion, after substantial fixes, before user-managed commits or PRs, and after synchronizing with main. Review code, architecture, migrations, security, tests, OpenAPI, reporting, repository hygiene, and command evidence.
---

# Quality Gate Review

Review independently. Focused tests alone are not proof of branch quality.

## Code

Check naming, cohesion, small methods, duplication, constructor injection, immutable contracts, null handling, specific exceptions, safe messages, thin controllers, DTO boundaries, provider isolation, and absence of unrelated refactors or temporary code.

## Architecture

Check Spring Modulith boundaries, public cross-module contracts, no internal-package dependency, no cycles, ports and adapters, application transactions, and events for cross-module effects.

## Database

Use `$database-migration-safety`. Check sequence, Git history, compatibility, constraints, idempotency, optimistic locking, foreign keys, indexes, rollback, and absence of partial writes.

## Security

Use `$security-and-privacy-review`.

## Test matrix

Confirm relevant coverage for happy path, validation, not found, roles, CSRF, conflicts, locking, idempotency, database constraints, rollback, audit, privacy, OpenAPI, reporting, and architecture.

## Hygiene

Inspect:

```powershell
git status --short
git diff --check
git diff --stat
```

Report unexpected `data/`, `build/`, `.env`, credentials, uploads, scripts, ZIPs, BOM, HTML entities, binaries, generated output, or broad line-ending changes. Do not delete user files automatically.

## Validation

For a block, run compile, focused tests, relevant regression, and architecture verification where applicable. For the final branch, run `clean check`.

Return one verdict:

- `PASS`
- `PASS WITH NON-BLOCKING NOTES`
- `BLOCKED`
- `FAIL`

Support the verdict with executed-command evidence and remaining actions. Never perform Git mutations.
