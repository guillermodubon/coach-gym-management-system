# ADR-002: Permanent client access-credential boundary

- Status: Accepted
- Date: 2026-09-12
- Scope: `feature/client-access-credentials`, Block 1

## Context

Coach Gym needs a permanent QR credential that a client can carry and staff
can later present to the access-check-in flow. The credential must identify a
client without becoming a client account, embedding personal information, or
making the QR itself an authorization decision.

The current application is a Spring Modulith modular monolith. The `access`
module owns check-in decisions and access records. Credential lifecycle is a
separate capability and must remain reusable by that module without exposing
persistence, rendering, storage, or cryptographic implementation details.

## Decision

1. The new `accesscredential` module owns credential lifecycle contracts and
   policy. The existing `access` module remains the owner of check-in
   evaluation and access-attempt records. Future cross-module communication
   uses public contracts only.
2. A credential token is generated from 32 cryptographically secure random
   bytes and encoded with URL-safe Base64 without padding. This provides 256
   bits of entropy and carries no client, membership, role, status, or
   database information.
3. The versioned QR payload is `cgac:v1:<opaque-token>`. The `v1` marker is
   part of the contract so a future check-in adapter can reject unsupported
   formats safely.
4. The raw token exists only in memory during issuance or replacement, long
   enough for the later renderer and storage ports to consume it. It is never
   persisted, logged, emitted in events, written to audit metadata, or
   returned by normal metadata contracts.
5. Later persistence will store a versioned SHA-256 fingerprint of the
   normalized payload/token representation for deterministic lookup and
   uniqueness. Because the token has 256 bits of entropy, the value is not
   intended to be a password substitute or a user authentication secret.
   The scheme version leaves room for a future keyed representation without
   rewriting the public contract.
6. No key-rotation mechanism is introduced for the initial unkeyed fingerprint
   scheme. If the threat model changes to require HMAC protection, a new
   versioned scheme and migration must be approved explicitly.
7. There can be at most one `ACTIVE` credential for a client. Repeated issue
   requests must return the canonical active credential or the approved stable
   conflict; they must never silently create a second active credential.
8. The blueprint's authoritative status vocabulary is `ACTIVE` and `REVOKED`.
   Revocation is final. Replacement is an operation that atomically revokes
   the old credential, records its `replacedByCredentialId`, and issues a new
   active credential; it does not introduce a separate `REPLACED` status.
9. Revocation and replacement require a nonblank reason and a nonnegative
   optimistic version. No transition from `REVOKED` back to `ACTIVE` is
   allowed.
10. The canonical QR artifact is a PNG produced and stored by later ports. Its
    storage key is server-generated, and the existing artifact is retained for
    lifecycle history unless a later approved policy requires secure deletion.
11. Public metadata contracts contain identifiers, lifecycle status, safe
    timestamps, actor identifiers, replacement linkage, and optimistic
    version only. They do not contain the raw token, QR payload, fingerprint,
    digest, storage path, or provider types. Binary document contracts make
    defensive copies of their byte arrays.
12. Credential lifecycle is deliberately separate from QR check-in. This
    block introduces no token resolution, membership eligibility at scan time,
    access decision, anti-passback, duplicate-scan window, or access attempt.

## Considered alternatives

### Store the raw token

Rejected. A database or log compromise would immediately disclose every
credential. The later check-in flow can derive the protected lookup value
without plaintext persistence.

### Reuse password hashing

Rejected. Password hashing is intentionally slow and normally salted, while
the future QR lookup needs deterministic derivation. A versioned fingerprint
contract is the appropriate boundary for a high-entropy opaque token.

### Encode client data in the QR payload

Rejected. Names, codes, contacts, membership details, and mutable claims are
unnecessary for lookup and would leak personal or authorization data whenever
the QR is copied or scanned.

### Make credentials part of the `access` module

Rejected for ownership. `access` owns the decision to admit and the resulting
access record; separating lifecycle keeps credential issuance and replacement
independent from check-in policy and avoids coupling future rendering or
storage concerns to access evaluation.

### Add a `REPLACED` status

Rejected for the current blueprint. The authoritative status list contains
only `ACTIVE` and `REVOKED`; replacement is represented by a final revocation
plus an explicit replacement relationship and history entry.

## Consequences

- Block 2 will add PostgreSQL tables, a partial unique active-client index,
  fingerprint uniqueness, transition checks, and append-only history.
- Block 3 will implement persistence, secure random generation, and the
  versioned fingerprint adapter behind application ports.
- Blocks 4 and 5 will render/store PNGs and orchestrate issue, revoke, and
  replacement transactions with compensation and public lifecycle events.
- Later blocks will add audit integration and secured staff HTTP endpoints.
- The subsequent QR check-in branch may consume the public verification
  contract without importing this module's infrastructure packages.
