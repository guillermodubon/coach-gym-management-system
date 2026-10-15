# Branch Plan: feature/<branch-name>

## 1. Branch identity

- Branch:
- Blueprint file and version:
- Roadmap phase:
- Previous completed branch:
- Next planned branch:
- Date:

## 2. Problem statement

Describe the problem and why the branch exists.

## 3. Goals

- Goal 1
- Goal 2

## 4. Non-goals

- Explicit exclusion 1
- Explicit exclusion 2

## 5. Current repository evidence

List inspected modules, classes, migrations, tests, configuration, and analogous implementations.

## 6. Architecture

- Owning module:
- Public contracts:
- Application services:
- Ports:
- Adapters:
- Cross-module events:
- Transaction boundaries:
- Expected module dependencies:

## 7. Business rules

1. Rule
2. Rule

## 8. Security and privacy

### Authorization matrix

- Anonymous:
- ADMIN:
- RECEPTIONIST:

### CSRF

- Decision:
- Reason:

### Sensitive data

- Inputs:
- Outputs:
- Events:
- Audit:
- Logs:

## 9. Database and Flyway

- Tables:
- Columns:
- Constraints:
- Indexes:
- Functions or triggers:
- Idempotency:
- Proposed migration version:
- Existing-data compatibility:

## 10. HTTP and OpenAPI

For each endpoint:

- Method and path:
- Request:
- Response:
- Status codes:
- ProblemDetail codes:
- Security:

## 11. Events and audit

- Event:
- Public package:
- Consumers:
- Safe fields:
- Prohibited fields:
- Audit action and metadata:

## 12. Reporting impact

Describe operational or financial reporting changes and required tests.

## 13. External integration behavior

- Provider:
- Port:
- Adapter:
- Timeout:
- Retry policy:
- Signature verification:
- Idempotency:
- Test fake:
- Network policy:

## 14. Block dependency graph

```text
Block 1
  -> Block 2
  -> Block 3
```

## 15. Detailed blocks

### Block 1: <name>

- Status: `NOT_STARTED`
- Objective:
- Depends on:
- Authorized production scope:
- Expected files:
- Database changes:
- Security review:
- Tests:
- Validation commands:
- Explicit exclusions:
- Completion criteria:
- Deferred work:

### Block 2: <name>

- Status: `NOT_STARTED`
- Objective:
- Depends on:
- Authorized production scope:
- Expected files:
- Database changes:
- Security review:
- Tests:
- Validation commands:
- Explicit exclusions:
- Completion criteria:
- Deferred work:

## 16. Test matrix

### Unit

- Scenario

### PostgreSQL integration

- Scenario

### HTTP and security

- Scenario

### Events and audit

- Scenario

### OpenAPI and reporting

- Scenario

### Architecture

- Scenario

## 17. Definition of Done

- [ ] All authorized blocks are completed
- [ ] Production and test compilation pass
- [ ] Focused tests pass
- [ ] Affected-module regression passes
- [ ] PostgreSQL invariants are verified
- [ ] Authorization and CSRF are verified
- [ ] Events and audit are privacy-safe
- [ ] OpenAPI is aligned
- [ ] Reporting impact is verified
- [ ] Spring Modulith verification passes
- [ ] Tests use no real external network
- [ ] Repository hygiene passes
- [ ] `clean check` passes
- [ ] Blueprint is updated if required

## 18. Risks and mitigations

- Risk:
- Mitigation:

## 19. Deferred work

List work intentionally postponed to later branches.
