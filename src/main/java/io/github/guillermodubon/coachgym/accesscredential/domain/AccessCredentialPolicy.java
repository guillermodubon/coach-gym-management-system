package io.github.guillermodubon.coachgym.accesscredential.domain;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialEligibilityException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStateConflictException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialValidationException;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import java.util.UUID;

/** Stateless business policy for the permanent client access-credential lifecycle. */
public final class AccessCredentialPolicy {

    private AccessCredentialPolicy() {
    }

    /** Requires an existing active client before a credential can be issued. */
    public static void requireIssueAllowed(UUID clientId, ClientStatus clientStatus) {
        if (clientId == null) {
            throw new AccessCredentialValidationException("Client id is required.");
        }
        if (clientStatus == null) {
            throw new AccessCredentialValidationException("Client status is required.");
        }
        if (clientStatus != ClientStatus.ACTIVE) {
            throw new AccessCredentialEligibilityException(clientId, clientStatus);
        }
    }

    /** Requires an active credential before an irreversible revocation. */
    public static void requireRevocationAllowed(
            UUID credentialId,
            AccessCredentialStatus currentStatus) {
        requireActive(credentialId, currentStatus);
    }

    /** Requires an active credential before an atomic replacement. */
    public static void requireReplacementAllowed(
            UUID credentialId,
            AccessCredentialStatus currentStatus) {
        requireActive(credentialId, currentStatus);
    }

    public static boolean isIssueEligible(ClientStatus clientStatus) {
        return clientStatus == ClientStatus.ACTIVE;
    }

    public static boolean isFinal(AccessCredentialStatus status) {
        return status == AccessCredentialStatus.REVOKED;
    }

    private static void requireActive(
            UUID credentialId,
            AccessCredentialStatus currentStatus) {
        if (credentialId == null) {
            throw new AccessCredentialValidationException("Credential id is required.");
        }
        if (currentStatus == null) {
            throw new AccessCredentialValidationException("Credential status is required.");
        }
        if (currentStatus != AccessCredentialStatus.ACTIVE) {
            throw new AccessCredentialStateConflictException(
                    credentialId,
                    currentStatus,
                    AccessCredentialStatus.REVOKED);
        }
    }
}
