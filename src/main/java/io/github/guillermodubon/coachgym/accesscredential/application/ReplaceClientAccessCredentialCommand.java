package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Request to replace the current credential owned by one client. */
public record ReplaceClientAccessCredentialCommand(
        UUID clientId,
        String reason,
        long expectedVersion) {

    public ReplaceClientAccessCredentialCommand {
        clientId = AccessCredentialCommandValidation.identifier(clientId, "Client id");
        reason = AccessCredentialCommandValidation.reason(reason);
        expectedVersion = AccessCredentialCommandValidation.expectedVersion(expectedVersion);
    }
}
