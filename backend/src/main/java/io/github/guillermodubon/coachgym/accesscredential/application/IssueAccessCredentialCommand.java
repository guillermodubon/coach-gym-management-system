package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Server-authorized request to issue the canonical active credential for a client. */
public record IssueAccessCredentialCommand(UUID clientId) {

    public IssueAccessCredentialCommand {
        clientId = AccessCredentialCommandValidation.identifier(clientId, "Client id");
    }
}
