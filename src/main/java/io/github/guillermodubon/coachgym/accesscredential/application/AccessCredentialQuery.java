package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import java.util.Optional;
import java.util.UUID;

/** Read-only metadata and protected-token lookup port. */
public interface AccessCredentialQuery {

    Optional<AccessCredentialDetails> findById(UUID credentialId);

    Optional<AccessCredentialDetails> findActiveByClientId(UUID clientId);

    /**
     * Resolves an active credential by a previously derived fingerprint.
     * The fingerprint itself is never returned.
     */
    Optional<AccessCredentialDetails> findActiveByTokenFingerprint(String tokenFingerprint);

    /**
     * Resolves an active credential and locks its row for the surrounding
     * transaction. The lock is used to serialize access decisions for one
     * credential without introducing a database-wide lock.
     */
    Optional<AccessCredentialDetails> findActiveByTokenFingerprintForUpdate(
            String tokenFingerprint);

    /**
     * Returns the persisted, nonsecret artifact metadata for one credential.
     *
     * <p>Artifact bytes remain behind the storage port and are never exposed by
     * this query.</p>
     */
    Optional<AccessCredentialStoredDocument> findArtifactByCredentialId(UUID credentialId);
}
