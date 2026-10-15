package io.github.guillermodubon.coachgym.accesscredential.application;

/** Indicates that a secure access-credential token could not be generated. */
public class AccessCredentialTokenGenerationException extends RuntimeException {

    public AccessCredentialTokenGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
