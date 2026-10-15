package io.github.guillermodubon.coachgym.client.application;

public class ClientPhotoStorageException extends RuntimeException {

    public ClientPhotoStorageException(String message) {
        super(message);
    }

    public ClientPhotoStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
