package io.github.guillermodubon.coachgym.client.application;

/** Wraps an unexpected failure while reading client search or profile data. */
public class ClientProfileDataAccessException extends RuntimeException {
    public ClientProfileDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
