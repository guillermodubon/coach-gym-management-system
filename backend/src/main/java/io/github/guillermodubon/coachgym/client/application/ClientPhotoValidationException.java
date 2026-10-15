package io.github.guillermodubon.coachgym.client.application;

public class ClientPhotoValidationException extends RuntimeException {

    public ClientPhotoValidationException(String message) {
        super(message);
    }

    public ClientPhotoValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
