# Backend Workspace Rules

These instructions supplement the root `AGENTS.md`; all existing backend architecture, security, payment, Flyway, testing and completion requirements remain in force.

- Treat this directory as the Spring Boot application's working directory.
- Run the original Gradle wrapper here, or use the root compatibility delegates.
- Java source, resources, migrations and tests retain their paths relative to this directory.
- Shared documentation lives in `../docs/`; CI lives in `../.github/`; project skills live in `../.codex/skills/`.
- Keep `.env`, `.env.local`, `data/`, `build/` and `.gradle/` private and ignored.
- Docker builds use this directory as context. Preserve the existing Compose project and volume names.
- Never change migration contents to accommodate the relocation.
- Frontend work consumes HTTP contracts and must not import or depend on Java implementation packages.
- Classify unavailable Docker as infrastructure failure. Do not use a real external database or provider to bypass failing isolated tests.
- Update current documentation for operational paths; keep historical evidence identifiable as historical.
