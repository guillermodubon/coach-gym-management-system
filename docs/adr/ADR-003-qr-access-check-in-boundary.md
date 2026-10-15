# ADR-003: QR access check-in boundary and duplicate-scan policy

- Status: Accepted
- Date: 2026-09-14
- Scope: `feature/qr-access-check-in`, Block 1

## Context

Coach Gym already has two manual access identifiers and a separate
`accesscredential` module that owns permanent opaque QR credentials. The
missing capability is the bridge from scanner output to the existing access
decision workflow. A QR value identifies a client, but it is not itself an
authorization decision and must not carry client, membership, payment, or role
data.

The current blueprint requires strict versioned payloads, active-credential
resolution, duplicate-scan protection, preservation of the manual flow, and
no confirmed-payment policy in this branch.

## Decision

1. The `access` module owns QR check-in orchestration, access-policy
   evaluation, duplicate decisions, and access-attempt recording. The
   `accesscredential` module owns payload-compatible token protection and
   credential persistence. `access` may consume only the public
   `accesscredential` contracts; it must not depend on its application,
   persistence, storage, QR-renderer, or token-adapter packages.
2. The accepted payload is the canonical `cgac:v1:<opaque-token>` value
   already defined by `AccessCredentialQrPayload`. `v1` is strict, the token
   is URL-safe unpadded Base64 for 32 random bytes, and the canonical payload
   is bounded to the parser's maximum length. Unknown versions, malformed
   values, control characters, and oversized values are rejected before any
   database lookup.
3. `QrAccessCheckInCommand.parse` is the application boundary for parsing.
   It returns the canonical payload object or a constant-message
   `QrAccessPayloadValidationException`; malformed input is never echoed.
4. `AccessCredentialResolver` is the only cross-module lookup contract. It
   accepts a canonical payload and returns an `Optional<ResolvedAccessCredential>`.
   An empty result intentionally covers unknown and inactive credentials. A
   resolved projection contains only the credential ID, authoritative client
   ID, and `ACTIVE` status. It never exposes a raw token, payload string,
   fingerprint, storage key, or persistence type.
5. The raw token and canonical payload may exist only in memory for the
   duration required by parsing and protected lookup. They must not be
   persisted, logged, placed in events or audit metadata, returned in normal
   responses, used as exception text, or attached to metric dimensions.
6. Once a credential resolves, the QR workflow must delegate to the existing
   `AccessPolicy` and access-record path. It must not duplicate client,
   membership, period, freeze, or future payment-policy rules. Manual client
   and membership code check-ins remain unchanged.
7. Duplicate detection is represented by `DuplicateScanPolicy` and
   `DuplicateScanResult`. The window is mandatory input; this ADR does not
   invent a numeric default because the current blueprint and settings schema
   define no duration. The policy uses one server-clock timestamp and a
   half-open interval: elapsed age `>= 0` and `< window` is a duplicate, while
   the exact boundary is outside the window. The database is authoritative for
   selecting the most recent equivalent attempt and for concurrency.
8. The selected duplicate behavior is a deterministic denied access decision
   with reason `DUPLICATE_CHECK_IN`, recorded through the normal access path
   once the persistence contract supports the QR source. It must not rely on
   process-local memory or return a successful duplicate check-in.
9. Malformed payloads are structural validation failures and are not access
   attempts. Unknown or inactive credentials are pre-resolution security
   rejections and are not persisted until a later migration defines a
   privacy-safe unresolved representation; no raw token or reversible value
   may be used as a substitute. Resolved business denials continue to be
   recorded using the existing access semantics.
10. QR check-in remains a staff operation. Both `ADMIN` and `RECEPTIONIST`
    may use it, anonymous callers receive `401`, and state-changing HTTP
    requests require CSRF. No public scanner or client authentication route
    is introduced.
11. This branch does not query payments or require a confirmed payment for
    access. The `feature/access-payment-policy` branch owns that decision.

## Consequences

- Block 1 supplies the public source enum, QR command, safe validation
  exception, resolver boundary, resolved identity projection, and pure
  duplicate-window policy.
- Later blocks will implement protected lookup, access-policy orchestration,
  the minimal PostgreSQL representation and index, transaction/concurrency
  behavior, audit, and the secured HTTP endpoint.
- The absence of a configured duration is explicit and must be resolved by an
  approved setting or blueprint decision before the duplicate policy is wired
  to production persistence.

## Rejected alternatives

### Put QR resolution in the access module

Rejected because it would couple access decisions to credential persistence,
cryptographic adapters, and rendering/storage concerns.

### Treat a QR payload as a normal access identifier

Rejected because the payload is a versioned secret-bearing value, not a
human-facing code. It must be parsed and resolved through the credential
contract without normalization drift or logging.

### Use an in-memory duplicate cache

Rejected because multiple application instances and concurrent requests need
the PostgreSQL state to be authoritative.

### Add a guessed duplicate duration

Rejected because the blueprint and current `gym_settings` schema contain no
approved numeric window. A later block must supply the value explicitly.

### Confirm access from payment state in this branch

Rejected because payment-confirmation access is a separate planned capability
and would violate the branch boundary.
