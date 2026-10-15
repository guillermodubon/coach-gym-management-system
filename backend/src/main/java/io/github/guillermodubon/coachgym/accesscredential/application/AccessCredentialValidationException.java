package io.github.guillermodubon.coachgym.accesscredential.application;

/** Indicates structurally invalid access-credential input. */
public class AccessCredentialValidationException extends RuntimeException {

    public AccessCredentialValidationException(String message) {
        super(message);
    }
}
