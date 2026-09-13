package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import java.util.UUID;

/** Technology-neutral port for canonical access-credential PNG storage. */
public interface AccessCredentialStorage {

    String generateStorageKey(UUID credentialId);

    AccessCredentialStoredDocument store(
            String storageKey,
            AccessCredentialDocument document,
            String rendererVersion);

    AccessCredentialDocument load(
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256);

    /** Idempotent cleanup used to compensate a staged artifact on failure. */
    void delete(String storageKey);
}
