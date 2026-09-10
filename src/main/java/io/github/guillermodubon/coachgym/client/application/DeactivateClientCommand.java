package io.github.guillermodubon.coachgym.client.application;

/** Administrative command for blocking a currently active client. */
public record DeactivateClientCommand(String reason, long expectedVersion) {

    public DeactivateClientCommand {
        reason = ClientCommandValidation.required(
                reason, "Client deactivation reason", 2000);
        ClientCommandValidation.version(expectedVersion);
    }
}
