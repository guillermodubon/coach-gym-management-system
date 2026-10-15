package io.github.guillermodubon.coachgym.client.application;

/** Raised when a client update or lifecycle command is structurally invalid. */
public class ClientValidationException extends RuntimeException {

    public ClientValidationException(String message) {
        super(message);
    }
}
