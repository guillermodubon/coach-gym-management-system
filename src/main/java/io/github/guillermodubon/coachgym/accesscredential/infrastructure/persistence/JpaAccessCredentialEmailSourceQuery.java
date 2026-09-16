package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialEmailSource;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialEmailSourceQuery;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialQuery;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorage;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStoredDocument;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Composes active credential metadata and the credential-owned PNG storage port. */
@Repository
class JpaAccessCredentialEmailSourceQuery implements AccessCredentialEmailSourceQuery {

    private final AccessCredentialQuery credentialQuery;
    private final AccessCredentialStorage credentialStorage;

    JpaAccessCredentialEmailSourceQuery(
            AccessCredentialQuery credentialQuery,
            AccessCredentialStorage credentialStorage) {
        this.credentialQuery = Objects.requireNonNull(credentialQuery);
        this.credentialStorage = Objects.requireNonNull(credentialStorage);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessCredentialEmailSource> findByCredentialId(UUID credentialId) {
        if (credentialId == null) {
            throw new IllegalArgumentException("Credential id is required.");
        }
        Optional<AccessCredentialDetails> credential = credentialQuery.findById(credentialId)
                .filter(details -> details.status() == AccessCredentialStatus.ACTIVE);
        return compose(credential);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessCredentialEmailSource> findActiveByClientId(UUID clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("Client id is required.");
        }
        return compose(credentialQuery.findActiveByClientId(clientId));
    }

    private Optional<AccessCredentialEmailSource> compose(
            Optional<AccessCredentialDetails> credential) {
        Optional<AccessCredentialDetails> activeCredential = credential
                .filter(details -> details.status() == AccessCredentialStatus.ACTIVE);
        if (activeCredential.isEmpty()) {
            return Optional.empty();
        }
        AccessCredentialDetails details = activeCredential.get();
        Optional<AccessCredentialStoredDocument> metadata =
                credentialQuery.findArtifactByCredentialId(details.id());
        if (metadata.isEmpty()) {
            throw new IllegalStateException("Access credential artifact metadata is unavailable.");
        }
        AccessCredentialStoredDocument artifact = metadata.get();
        var document = credentialStorage.load(
                artifact.storageKey(),
                artifact.contentType(),
                artifact.sizeBytes(),
                artifact.checksumSha256());
        return Optional.of(new AccessCredentialEmailSource(
                details,
                artifact.contentType(),
                artifact.sizeBytes(),
                artifact.checksumSha256(),
                artifact.rendererVersion(),
                document));
    }
}
