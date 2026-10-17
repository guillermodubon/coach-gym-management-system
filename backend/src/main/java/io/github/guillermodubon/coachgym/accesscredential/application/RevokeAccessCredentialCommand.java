package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Request to irreversibly revoke an active access credential. */
public record RevokeAccessCredentialCommand(
        UUID credentialId,
        String reason,
        long expectedVersion) {

    public RevokeAccessCredentialCommand {
        credentialId = AccessCredentialCommandValidation.identifier(
                credentialId, "Credential id");
        reason = AccessCredentialCommandValidation.reason(reason);
        expectedVersion = AccessCredentialCommandValidation.expectedVersion(expectedVersion);
    }
}
