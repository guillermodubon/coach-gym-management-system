---
name: project-reonboarding
description: Relearn the current Coach Gym repository after substantial evolution or stale prior context. Use at the start of a new Codex work cycle, a new branch session, or whenever earlier model knowledge may be outdated. Inspect without changing production code and produce a readiness report for future prompts in any structure.
---

# Project Re-onboarding

## Purpose

Discard stale assumptions and reconstruct the current project model from repository evidence.

## Rules

- Do not modify production code during re-onboarding.
- Do not start a feature block.
- Do not perform Git mutations.
- Do not rely on old conversation memory.
- Report evidence, contradictions, and unknowns explicitly.

## Inspection sequence

1. Read root and applicable nested `AGENTS.md` files.
2. Inspect `git status --short`, current branch, recent history, and branch divergence with `main` using read-only commands.
3. Locate and read the current blueprint.
4. Read `docs/branch-plans/README.md` and all current or recent branch plans.
5. Inspect Gradle configuration and Java version.
6. Map top-level Spring Modulith modules and read their `package-info.java` files.
7. Identify exposed cross-module contracts and event packages.
8. Inspect security configuration, principals, roles, CSRF, entry point, access-denied handling, and method security.
9. List Flyway migrations numerically and inspect the latest migrations and schema conventions.
10. Inspect representative current implementations for controllers, DTOs, application services, ports, adapters, events, audit, ProblemDetail, OpenAPI, and reporting.
11. Inspect test base classes, Testcontainers conventions, authentication fixtures, architecture tests, and `clean check` configuration.
12. Inspect external integration boundaries and verify that tests do not require real networks.
13. Inspect repository hygiene and unexpected untracked or generated files.

## Required readiness report

Return:

- branch and working-tree state;
- blueprint and roadmap status;
- current module map;
- public cross-module contracts;
- layering and naming conventions;
- roles, authentication, authorization, and CSRF;
- database and Flyway state;
- transactions, locking, idempotency, events, and audit patterns;
- REST, ProblemDetail, OpenAPI, and reporting conventions;
- test infrastructure and quality gates;
- completed capabilities;
- pending backend capabilities;
- active plan and block statuses;
- contradictions, stale docs, missing migrations, or risks;
- readiness verdict.

End by stating that the session is ready for future prompts in any structure and that no implementation was started.
