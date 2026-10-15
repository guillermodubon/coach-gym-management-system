package io.github.guillermodubon.coachgym.accesscredential.application;

/** Safe application failure raised by the credential artifact storage boundary. */
public class AccessCredentialStorageException extends RuntimeException {

    public AccessCredentialStorageException(String message) {
        super(message);
    }

    public AccessCredentialStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
