package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialResolver;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialTokenProtector;
import io.github.guillermodubon.coachgym.accesscredential.ResolvedAccessCredential;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialQuery;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves QR credentials through the protected persistence representation.
 *
 * <p>The adapter is the only place that combines the canonical payload with
 * the token protector and the credential query. Neither the raw payload nor
 * its derived fingerprint crosses the public resolver boundary.</p>
 */
@Repository
class AccessCredentialResolverAdapter implements AccessCredentialResolver {

    private static final String FINGERPRINT_PATTERN = "[0-9a-f]{64}";

    private final AccessCredentialQuery credentialQuery;
    private final AccessCredentialTokenProtector tokenProtector;

    AccessCredentialResolverAdapter(
            AccessCredentialQuery credentialQuery,
            AccessCredentialTokenProtector tokenProtector) {
        this.credentialQuery = credentialQuery;
        this.tokenProtector = tokenProtector;
    }

    /**
     * Resolves only an active credential using the indexed fingerprint lookup.
     *
     * <p>A null or malformed payload is treated as an unusable credential and
     * never reaches persistence. Unknown, revoked, and otherwise inactive
     * credentials are represented by the same empty result.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedAccessCredential> resolve(
            AccessCredentialQrPayload payload) {
        return resolve(payload, false);
    }

    @Override
    @Transactional
    public Optional<ResolvedAccessCredential> resolveAndLock(
            AccessCredentialQrPayload payload) {
        return resolve(payload, true);
    }

    private Optional<ResolvedAccessCredential> resolve(
            AccessCredentialQrPayload payload,
            boolean lock) {
        if (payload == null) {
            return Optional.empty();
        }

        final String fingerprint;
        try {
            fingerprint = tokenProtector.fingerprint(payload.value());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (fingerprint == null || !fingerprint.matches(FINGERPRINT_PATTERN)) {
            return Optional.empty();
        }

        Optional<AccessCredentialDetails> details = lock
                ? credentialQuery.findActiveByTokenFingerprintForUpdate(fingerprint)
                : credentialQuery.findActiveByTokenFingerprint(fingerprint);

        return details
                .filter(AccessCredentialResolverAdapter::isActive)
                .map(AccessCredentialResolverAdapter::toResolved);
    }

    private static boolean isActive(AccessCredentialDetails details) {
        return details.status() == AccessCredentialStatus.ACTIVE;
    }

    private static ResolvedAccessCredential toResolved(
            AccessCredentialDetails details) {
        return new ResolvedAccessCredential(
                details.id(),
                details.clientId(),
                details.status());
    }
}
