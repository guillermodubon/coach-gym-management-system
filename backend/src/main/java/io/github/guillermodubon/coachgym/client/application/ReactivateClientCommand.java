package io.github.guillermodubon.coachgym.client.application;

/** Administrative command for restoring a currently inactive client. */
public record ReactivateClientCommand(String reason, long expectedVersion) {

    public ReactivateClientCommand {
        reason = ClientCommandValidation.required(
                reason, "Client reactivation reason", 2000);
        ClientCommandValidation.version(expectedVersion);
    }
}
