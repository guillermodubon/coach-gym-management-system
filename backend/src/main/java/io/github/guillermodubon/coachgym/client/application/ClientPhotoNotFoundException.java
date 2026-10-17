package io.github.guillermodubon.coachgym.client.application;

public class ClientPhotoNotFoundException extends RuntimeException {

    public ClientPhotoNotFoundException(String message) {
        super(message);
    }

    public ClientPhotoNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
