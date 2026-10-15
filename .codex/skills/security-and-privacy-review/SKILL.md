---
name: security-and-privacy-review
description: Review Coach Gym backend work for authentication, role authorization, CSRF, sensitive data, event and audit privacy, files, secrets, external integrations, safe logging, and ProblemDetail behavior. Use for endpoints, mutations, events, audit, uploads, Stripe, email, QR, and branch completion.
---

# Security and Privacy Review

## Authorization

For every operation identify anonymous, ADMIN, and RECEPTIONIST behavior, HTTP security, method security, expected 401 or 403, and resource restrictions. Never restore MAINTENANCE.

## CSRF

Staff-initiated state changes require CSRF. A provider webhook may be excluded only with narrow routing, verified signatures, and duplicate or replay safety.

## Data minimization

Review request DTOs, responses, events, audit metadata, logs, exceptions, and OpenAPI.

Do not expose secrets, password hashes, card data, CVC, PIN, expiration, QR tokens, complete signatures, provider secrets, raw sensitive payloads, SQL, stack traces, or internal paths.

Prefer safe presence indicators when the actual value is unnecessary.

## External integrations

Verify port and adapter boundaries, timeouts, safe exception translation, configuration-sourced secrets, idempotency, signature verification, no SDK leakage, and no real network in tests. Secondary delivery failures must not incorrectly corrupt or roll back committed primary state.

## Files

Verify size limits, signature or content validation, server-generated names, path traversal prevention, storage abstraction, safe content types, download headers, and no committed local uploads.

## Output

Report authorization matrix, CSRF decision, sensitive inputs and outputs, event and audit privacy, logging and integration risks, required tests, and PASS, BLOCKED, or FAIL.
