package io.github.guillermodubon.coachgym.accesscredential.application;

/** Safe application-facing translation of credential persistence failures. */
public class AccessCredentialDataAccessException extends RuntimeException {

    public AccessCredentialDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
