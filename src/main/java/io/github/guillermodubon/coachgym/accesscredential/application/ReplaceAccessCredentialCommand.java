package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Request to atomically revoke the current credential and issue its replacement. */
public record ReplaceAccessCredentialCommand(
        UUID credentialId,
        String reason,
        long expectedVersion) {

    public ReplaceAccessCredentialCommand {
        credentialId = AccessCredentialCommandValidation.identifier(
                credentialId, "Credential id");
        reason = AccessCredentialCommandValidation.reason(reason);
        expectedVersion = AccessCredentialCommandValidation.expectedVersion(expectedVersion);
    }
}
