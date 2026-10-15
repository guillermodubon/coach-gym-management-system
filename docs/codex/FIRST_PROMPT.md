# First Prompt for a New Work Cycle

Use `$project-reonboarding` and `$blueprint-guardian`.

Relearn the project from the current repository before acting. Read and obey all
applicable `AGENTS.md` files. Inspect the current Git branch and working tree,
the authoritative blueprint under `docs/blueprint`, all current or recent plans
under `docs/branch-plans`, module `package-info.java` files, production packages,
Flyway migrations, security and OpenAPI configuration, Gradle configuration,
test infrastructure, and representative implementations and tests.

Use repository evidence instead of conversational memory. Report the current
architecture, public module contracts, security model, migration state, testing
conventions, completed capabilities, pending roadmap, active plan and block
statuses, repository hygiene, contradictions, and readiness for the next
request.

Do not modify production code, start a block, or perform Git mutations. Stop
after the readiness report and wait for the next prompt. Future prompts may use
any structure; infer their authorized scope from the current request, repository,
active plan, and blueprint.
