package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Write and lifecycle-locking port for durable access credentials. */
public interface AccessCredentialStore {

    /** Locks the client row so an issue or replacement has a stable scope. */
    void lockClient(UUID clientId);

    /** Loads one credential while holding a pessimistic row lock. */
    Optional<AccessCredentialDetails> findByIdForUpdate(UUID credentialId);

    /** Loads the active credential for a client while holding a row lock. */
    Optional<AccessCredentialDetails> findActiveByClientIdForUpdate(UUID clientId);

    /** Persists one active credential and returns its safe metadata. */
    AccessCredentialDetails insert(AccessCredentialPersistenceCommand command);

    /** Revokes an active credential using optimistic version checking. */
    AccessCredentialDetails revoke(
            UUID credentialId,
            String reason,
            UUID actorId,
            Instant revokedAt,
            long expectedVersion);

    /** Attaches the active replacement to a final revoked credential. */
    AccessCredentialDetails attachReplacement(
            UUID credentialId,
            UUID replacementCredentialId,
            long expectedVersion);
}
