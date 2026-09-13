package io.github.guillermodubon.coachgym.accesscredential.application;

/** Safe application failure raised when a credential QR cannot be rendered. */
public class AccessCredentialRenderException extends RuntimeException {

    public AccessCredentialRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
