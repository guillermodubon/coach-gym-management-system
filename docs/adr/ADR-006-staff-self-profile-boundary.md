# ADR-006: Staff self-profile ownership and privacy boundary

**Status:** Accepted
**Date:** 2026-09-19
**Scope:** Authenticated staff self-profile contracts and policy

## Context

The expanded blueprint adds self-service profile management for the two
supported staff roles, `ADMIN` and `RECEPTIONIST`. The current user module owns
the `gym.users` identity record. It stores `username`, `email`, `first_name`,
`last_name`, `password_hash`, `status`, login timestamps, and an optimistic
`version`. Authentication accepts either username or email, and the current
session is a server-side `HttpSession` with CSRF and an absolute timeout.

The current schema does not contain a staff phone number, preferred locale,
profile-photo metadata, organization scope, or branch assignments. Client
photos already establish bounded image metadata and private storage patterns,
but client-specific ports are not reused as a staff domain contract.

## Decisions

1. **Ownership and target selection.** The `user` module owns staff profile
   identity and profile metadata. A self-profile use case derives the target
   user id exclusively from the authenticated principal. No self-service route,
   request DTO, or command accepts a target user id. Administrative user
   management remains a separate capability and boundary.

2. **Editable fields.** The only editable fields in this branch are
   `firstName` and `lastName`, which map to the existing non-privileged columns
   `first_name` and `last_name`. Display name is derived from those values.
   The command is an explicit record, not a generic patch map, and carries an
   expected optimistic-lock version. Phone, preferred locale, and display
   preferences are not stored by the current schema and are therefore not
   silently accepted.

3. **Identity and privileged fields.** `username` and `email` are read-only
   projections. Both are accepted authentication identifiers today, so changing
   either would require an explicit reverification and identifier-rotation
   workflow that is outside this branch. Role, account status, password hash,
   organization scope, branch assignments, permissions, user id, creation
   metadata, security-lock state, failed-login counters, version, and audit
   history are never self-editable.

4. **Profile photo.** Staff photos are personal data. The technology-neutral
   storage port uses server-generated keys and bounded JPEG, PNG, or WebP
   metadata, with checksum and defensive byte-copy guarantees. Later storage
   work will use private local/Supabase-backed objects and server-mediated
   access; storage keys, provider URLs, filesystem paths, and image bytes do
   not appear in the profile projection. Signature inspection, persistence
   coordination, replacement cleanup, and HTTP download behavior are deferred
   to later blocks.

5. **Optimistic locking.** Profile updates carry the expected `users.version`.
   A stale version is a conflict and must not be resolved with last-write-wins.
   Photo metadata will participate in the same canonical profile concurrency
   decision when persistence is implemented.

6. **Password change.** A password command requires the current password, a
   new password, and confirmation. It enforces the existing bootstrap/login
   boundaries (minimum 12 characters and maximum 256 characters), rejects exact
   reuse, and never exposes values through `toString`, responses, events, or
   audit metadata. The later application service will verify and encode with
   the configured server-side `PasswordEncoder`.

7. **Session behavior.** The current repository has server-side sessions,
   session-fixation protection, CSRF, and absolute timeout, but no reliable
   cross-instance session registry or selective invalidation port. Later
   implementation must not claim that other sessions are invalidated until
   such a seam exists. If reliable enumeration remains unavailable, a password
   change must require reauthentication of the current session and document
   that concurrent sessions are not selectively revocable by the current
   infrastructure.

8. **Audit minimization.** Later profile, photo, and password events will
   expose only action, actor, timestamp, and safe changed-field/presence
   metadata. Passwords, hashes, complete old/new personal values, photo bytes,
   storage keys, paths, session ids, and provider responses are never emitted.

9. **Future branches.** Organization scope, branch assignments, active-branch
   selection, branch-aware permissions, and staff directories remain absent
   from these contracts. Later branches may extend the self-profile/session
   projection backward-compatibly without inventing placeholder branch values.

## Consequences

- The public boundary is small, immutable, and technology-neutral.
- The frontend can later consume a safe identity projection without receiving
  credentials or storage internals.
- Existing administrative controls and authentication identifiers remain
  protected while personal-name editing is enabled in a later block.
- The absence of a session registry is explicit rather than hidden behind an
  unreliable best-effort invalidation claim.

## Non-goals

- database migrations or persistence adapters;
- password update execution, password recovery, or MFA;
- image signature inspection, upload/download HTTP endpoints, or storage
  provider implementation;
- audit events/listeners, controllers, OpenAPI, or frontend code;
- organization/branch modeling, assignment management, or multi-tenancy;
- email/username changes, phone/locale collection, or client-profile changes.
