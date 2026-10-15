package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Request to revoke the current credential owned by one client. */
public record RevokeClientAccessCredentialCommand(
        UUID clientId,
        String reason,
        long expectedVersion) {

    public RevokeClientAccessCredentialCommand {
        clientId = AccessCredentialCommandValidation.identifier(clientId, "Client id");
        reason = AccessCredentialCommandValidation.reason(reason);
        expectedVersion = AccessCredentialCommandValidation.expectedVersion(expectedVersion);
    }
}
