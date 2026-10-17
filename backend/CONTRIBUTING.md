# Contributing

## Working agreement

- Keep branches short and use Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, and `docs:`.
- Keep each change focused on one responsibility.
- Do not add a dependency without a concrete problem and a short justification in the pull request.
- Never commit `.env`, database passwords, access tokens, or real member data.
- Write source code, packages, database objects, endpoints, commits, and branches in English. The administrative UI may be in Spanish.

## Before submitting a pull request

1. Run the relevant unit and architecture tests.
2. Run `./gradlew check` when Docker is available.
3. Add or update a Flyway migration for every schema change.
4. Add tests for each observable business rule or bug fix.
5. Update API and architecture documentation when a public contract or module boundary changes.

## Vertical slices

Implement features through a complete thin slice: migration, domain rule, application use case, HTTP contract, authorization, user-facing feedback, and tests. Avoid creating all entities or all controllers before a working use case exists.

## Definition of done

A change is done only when it preserves business history, validates permissions on the backend, handles expected errors safely, respects module boundaries, and passes applicable quality checks.
