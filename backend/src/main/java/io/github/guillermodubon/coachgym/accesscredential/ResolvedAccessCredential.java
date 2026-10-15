package io.github.guillermodubon.coachgym.accesscredential;

import java.util.UUID;

/**
 * Minimal identity returned to a future access check-in workflow.
 *
 * <p>Only an active credential can be represented. Unknown, revoked, and
 * otherwise inactive credentials are represented by an empty result from
 * {@link AccessCredentialResolver}; they are never exposed as a partially
 * trusted identity. The contract contains no raw token, QR payload,
 * fingerprint, storage key, or persistence type.</p>
 */
public record ResolvedAccessCredential(
        UUID credentialId,
        UUID clientId,
        AccessCredentialStatus status) {

    public ResolvedAccessCredential {
        if (credentialId == null) {
            throw new IllegalArgumentException(
                    "Resolved credential identifier is required.");
        }
        if (clientId == null) {
            throw new IllegalArgumentException(
                    "Resolved client identifier is required.");
        }
        if (status != AccessCredentialStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Only active credentials can be resolved.");
        }
    }
}
