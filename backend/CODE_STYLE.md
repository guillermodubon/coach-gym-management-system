# Code Style

## Java

- Prefer small cohesive classes and constructor injection.
- Controllers only translate HTTP; they do not call repositories or implement business rules.
- Use records for immutable request/response DTOs and value-oriented data where appropriate.
- Use `BigDecimal` for money and `java.time` types with the correct temporal semantics.
- Do not return `null` for collections or use `System.out`/`printStackTrace`.
- Use specific exceptions and translate them centrally to `ProblemDetail` when HTTP endpoints are introduced.

## Modules

- Keep public module contracts in the module root.
- Keep implementation in `domain`, `application`, `infrastructure`, and `web` when a use case needs those layers.
- Do not access another module's internal packages or repositories.
- Do not add generic `Utils`, `Manager`, `Helper`, or `Common` classes.

## Database

- Flyway migrations are forward-only after they are applied to a persistent environment.
- Add constraints to PostgreSQL when they represent an invariant.
- Do not use `ddl-auto=update`, float, or double for business amounts.
