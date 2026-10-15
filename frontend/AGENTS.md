# Frontend Workspace Rules

These instructions supplement the root `AGENTS.md`.

## Current authorization boundary

F00 prepares this directory with documentation only. Do not initialize an application, create a package manifest, install dependencies or implement screens until the user authorizes the appropriate frontend scope.

## Future implementation

- Read the current frontend blueprint, active branch plan and backend contracts before implementation.
- Implement only the explicitly authorized branch block.
- Keep all frontend source, tooling, dependencies and generated output inside this directory.
- Use TypeScript, React, Ant Design and the approved charting library. Pin compatible versions when foundation work is authorized.
- Consume `/api/v1` HTTP contracts; never access PostgreSQL, private storage, Gmail or backend internals directly.
- Preserve server-side sessions, CSRF, role/scope authorization, branch isolation, provider payment truth and privacy requirements.
- Never persist passwords, session identifiers, identity tokens or QR content in browser storage, logs or fixtures with real data.
- Public frontend configuration must not contain provider secrets. Private backend configuration belongs in `../backend/`.
- Share UI patterns through cohesive components, theme and API infrastructure; keep features organized by business capability.
- Build and test both light and dark themes and relevant responsive/accessible flows.
- Define real frontend quality commands in the authorized foundation plan; root Gradle delegates validate the backend only.
- User-owned Git rules remain unchanged. Do not stage, commit, push, switch branches or create pull requests.
