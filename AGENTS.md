# Coach Gym Management System: Codex Operating Rules

## Mission

Bring the backend to production-grade completion while preserving the product blueprint, the modular-monolith architecture, financial integrity, security, privacy, testability, and repository hygiene.

Correctness is necessary but not sufficient. Changes must also be maintainable, explicit, cohesive, testable, secure, and consistent with the current codebase.

## Repository layout and rule scope

- `backend/` owns the Spring Boot application, Gradle wrapper, Dockerfile, Compose, migrations, tests, private local configuration and runtime data.
- `frontend/` is reserved for the future frontend. F00 prepares documentation only; application implementation requires separate authorization.
- `docs/`, `.github/` and `.codex/skills/` remain shared at the repository root.
- User authorization, Git ownership, block protocol, privacy and repository hygiene apply across the repository.
- The Java architecture, database, payment and backend validation requirements below apply to backend work. Frontend-specific instructions live in `frontend/AGENTS.md` and must preserve the backend's business and security contracts.
- Execute backend tools from `backend/`. The root `gradlew` and `gradlew.bat` delegate there for compatibility; they do not build a frontend.
- Never run backend tasks from the root with the backend wrapper directly unless its project and working directories are explicitly resolved. Use the root delegate or enter `backend/`.

## Sources of truth

Use these sources in this order:

1. The current user request and its explicitly authorized scope.
2. The active branch plan under `docs/branch-plans/`.
3. The current blueprint under `docs/blueprint/`.
4. Current production code, migrations, tests, configuration, and OpenAPI.
5. The closest current analogous implementation.
6. Historical Git evidence when migration history or prior decisions matter.

A complete branch plan provides context. It does not authorize implementation of every block. Implement only the block explicitly requested by the user.

If sources conflict, do not guess. Inspect the repository and report the contradiction before changing production code.

## Mandatory project re-onboarding

This repository has evolved significantly. Never rely on old conversational memory or an earlier snapshot of the project.

At the beginning of a new Codex work cycle, use `$project-reonboarding` before implementing features. Reconstruct the current architecture and conventions from the repository itself.

After re-onboarding, continue to re-check the current files relevant to each request. The repository is the source of truth, not remembered structure.

## Block protocol

When an active branch plan divides work into blocks:

1. Read the complete plan for context and dependencies.
2. Identify the exact block authorized by the current request.
3. Inspect completed earlier blocks in the current repository state.
4. Implement only the authorized block.
5. Do not implement files or behavior exclusive to later blocks.
6. Validate the block with focused tests and relevant regression tests.
7. Stop after the block completion report.
8. Wait for explicit authorization before starting another block.

The user may write prompts in any structure. Infer the request from the current prompt, repository state, blueprint, and active plan without requiring a rigid prompt template.

## User-owned Git workflow

The user controls staging, commits, rebases, pushes, pull requests, merges, branch deletion, and synchronization with `main`.

Do not run:

- `git add`
- `git commit`
- `git push`
- `git rebase`
- `git reset`
- `git clean`
- `git restore` on user changes
- branch switching or deletion
- PR creation or merge commands

Read-only Git inspection is allowed and encouraged, including `git status`, `git diff`, `git log`, `git show`, `git grep`, `git ls-files`, `git merge-base`, and `git branch --contains`.

## Repository inspection rules

Never invent package names, database objects, migration versions, method signatures, event contracts, test fixtures, authentication conventions, security names, exception mappings, or ProblemDetail codes.

Before editing:

- inspect the owning module and `package-info.java`;
- inspect analogous current implementation and tests;
- inspect all affected migrations and mappings;
- inspect security and OpenAPI conventions;
- inspect current Git status and preserve unrelated changes.

Prefer focused edits to replacing large existing files. Never create temporary patch scripts in the repository unless the user explicitly requests them.

## Architecture

The application is a Spring Boot modular monolith.

- Preserve Spring Modulith boundaries.
- Cross-module contracts belong in packages exposed by the owning module.
- A module must not depend on another module's internal `application`, `web`, or `infrastructure` packages unless current architecture explicitly allows it.
- Keep controllers thin and HTTP DTOs separate from internal models.
- Use constructor injection.
- Put use-case orchestration and transaction boundaries in application services.
- Use ports and adapters for external integrations.
- Keep provider SDK types inside infrastructure adapters.
- Use privacy-safe events for cross-module side effects.
- Prefer immutable public contracts.
- Avoid cycles, service locators, hidden global state, and unrelated refactors.

Do not introduce microservices or a new architecture without an approved plan and repository evidence.

## Roles and authorization

The supported staff roles are only:

- `ADMIN`
- `RECEPTIONIST`

Never introduce or restore `MAINTENANCE` as an application role.

For every protected operation, review:

- anonymous behavior;
- ADMIN behavior;
- RECEPTIONIST behavior;
- HTTP authorization;
- method authorization;
- expected 401 and 403 behavior;
- CSRF for staff-initiated mutations.

Clients are not application users unless an approved blueprint revision explicitly changes that rule.

## Security and privacy

Never store, expose, log, or commit passwords, password hashes, SMTP passwords, provider secrets, Stripe secret keys, webhook secrets, full signatures, full card numbers, CVC, PIN, card expiration, auth tokens, QR tokens, local uploads, `.env` files, or production credentials.

- Staff-initiated state-changing HTTP requests require CSRF.
- Provider webhooks may be excluded from CSRF only when signature verification and replay or duplicate protection are implemented.
- Browser redirects do not confirm payments.
- Automated tests must not call real external network services.
- Minimize data in events, audit metadata, logs, responses, and OpenAPI.
- Prefer boolean presence indicators over sensitive values when the value is unnecessary.
- API errors must not expose SQL, stack traces, secrets, or internal paths.

## Database and Flyway

PostgreSQL is authoritative for critical persisted invariants.

Before creating a Flyway migration:

1. List all migrations in numeric order.
2. Search Git history and branches for the proposed version.
3. Inspect original DDL and every later alteration.
4. Inspect constraints, indexes, functions, and triggers.
5. Inspect entities, repositories, adapters, and tests.
6. Check compatibility with existing rows and transaction ordering.

Never edit or renumber an applied migration, create a placeholder for missing history, run Flyway repair automatically, modify `flyway_schema_history`, disable validation to hide a mismatch, or add redundant indexes.

Critical financial and historical invariants require database protection when appropriate. Prefer explicit constraint names and restrictive foreign keys for financial and historical records.

## Payment rules

- Confirmed `Payment` and provider `PaymentAttempt` are different concepts.
- Browser redirects never confirm card payments.
- A verified provider webhook is authoritative for future provider confirmation.
- Do not store card data.
- Do not implement partial refunds without an approved blueprint revision.
- Preserve confirmed payments, corrections, refunds, attempts, provider events, and status history.
- Provider SDK types must not escape infrastructure.

## Testing

Tests are part of implementation.

Apply the relevant matrix:

- happy path;
- validation and normalization;
- not found;
- authentication and authorization;
- CSRF;
- state conflict;
- optimistic locking;
- idempotency and duplicate delivery;
- PostgreSQL constraints and triggers;
- rollback and absence of partial writes;
- event publication and audit persistence;
- privacy;
- OpenAPI;
- reporting;
- Spring Modulith boundaries.

Use the repository's current PostgreSQL and Testcontainers conventions. Classify Docker or environment failures as infrastructure blockers, not product defects.

Do not weaken tests merely to make them pass. Determine whether the issue is production code, test expectation, migration state, architecture, encoding, or infrastructure.

## Progressive validation

Start with:

```powershell
.\gradlew compileJava compileTestJava
```

Then run the smallest relevant tests, affected-module regression, and Spring Modulith verification when boundaries are affected.

At branch completion run:

```powershell
.\gradlew clean check
```

Never claim success without command evidence.

## Repository hygiene

Do not leave build output, generated data, uploaded test images, ZIP files, temporary scripts, secrets, debug prints, commented-out code, obsolete TODOs, BOM characters, HTML entities copied into source, or accidental broad line-ending rewrites.

Write UTF-8 without BOM.

At the end of each block inspect:

```powershell
git status --short
git diff --check
git diff --stat
```

Do not delete user files automatically. Report unexpected files.

## Block completion report

End every implementation response with:

1. Block completed
2. Scope implemented
3. Files created
4. Files modified
5. Database changes
6. Security and privacy review
7. Tests added or updated
8. Commands executed
9. Validation results
10. Known limitations or deferred work
11. Repository status
12. Next block
13. `Stopped after the authorized block. Waiting for explicit authorization to continue.`

Do not include commit, push, PR, merge, or pull commands unless the user explicitly asks after implementation.
