# Architecture

## Decision

Coach Gym is a domain-oriented modular monolith. It runs as one Spring Boot application backed by one PostgreSQL database. The design intentionally avoids microservices and distributed infrastructure until a real operational need appears.

The backend is an independent Gradle application under `backend/`. Its paths below are relative to that directory. Shared documentation and CI live at the repository root; the future web application has its own `frontend/` directory and consumes HTTP contracts only.

## Module map

The package `io.github.guillermodubon.coachgym` is the application root. Every direct package below it is a Spring Modulith application module.

| Module | Responsibility |
| --- | --- |
| `auth` | Employee authentication and session entry points. |
| `user` | Employee accounts, roles, and account state. |
| `organization` | Canonical gym organization, branches and branch lifecycle. |
| `client` | Gym client records and emergency contacts. |
| `plan` | Commercial membership plans. |
| `promotion` | Discount eligibility and validity rules. |
| `membership` | Membership lifecycle and historical periods. |
| `payment` | Payment recording, corrections, and provider payment attempts. |
| `accesscredential` | Client access credential lifecycle and canonical QR documents. |
| `access` | Check-in authorization and access records. |
| `equipment` | Operational equipment catalog and state. |
| `maintenance` | Incidents and preventive/corrective maintenance. |
| `audit` | Sensitive business-action history. |
| `reporting` | Read models, KPIs, and operational reports. |
| `notification` | Internal operational alerts. |
| `shared` | Deliberately small, genuinely cross-cutting types only. |
| `configuration` | Framework and application configuration. |

Each module is declared with `@ApplicationModule`. Spring Modulith verifies that no circular dependencies or imports into another module's internal implementation are introduced.

## Internal layout of a feature

The first vertical slice in a module creates only the directories it needs, following this direction:

```text
<module>/
  domain/          # invariants, state transitions, value objects, ports
  application/     # use-case orchestration and transaction boundaries
  infrastructure/  # JPA, adapters, technical configuration
  web/             # HTTP DTOs, controllers, input validation
```

The package at the module root is the public module interface. Other modules must not import types from its internal packages. A concrete public query, command, event, or port is added only when another module genuinely needs it; placeholder interfaces are intentionally avoided.

## Dependency rules

- A module owns its domain rules and never reaches directly into another module's repository or infrastructure package.
- A synchronous decision uses a public module contract.
- Audit, reporting, and notification react to domain/application events whenever they are secondary effects.
- The `shared` module does not contain domain behavior or generic helpers.
- A controller calls an application use case; it never contains business rules or talks to a repository.
- Cross-module operations are explicit, transactional where business atomicity requires it, and covered by tests.

## Data and HTTP boundaries

- PostgreSQL is the only supported relational database.
- Flyway is the schema authority. Migration scripts live in `src/main/resources/db/migration` and Hibernate validates mappings only.
- UUIDs are used for primary entity identifiers. Human-facing codes are separate from primary keys.
- Money uses `BigDecimal` in Java and `NUMERIC`/`DECIMAL` in PostgreSQL.
- HTTP contracts use request/response DTOs. JPA entities are never exposed directly.
- API routes will be versioned below `/api/v1` and action endpoints describe business transitions, for example `POST /api/v1/memberships/{id}/freeze`.

## Security baseline

Authentication uses server-side sessions and secure cookie settings. Method security is enabled, application routes are denied by default, and only explicitly listed health, authentication, and API-documentation routes are public. State-changing staff requests retain CSRF protection. Provider webhooks may receive a narrow CSRF exclusion only when their signatures are verified and processing is persistently idempotent.

## Automated verification

`ApplicationModuleStructureTests` verifies the Spring Modulith module graph. `LayeringArchitectureTests` reserves the rule that web code must not depend on infrastructure code. New architectural rules are added here alongside the structural change they protect.
