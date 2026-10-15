package io.github.guillermodubon.coachgym.client.application;

public class ClientPhotoTooLargeException extends ClientPhotoValidationException {

    public ClientPhotoTooLargeException(long actual, long maximum) {
        super("Client photo exceeds the maximum size of " + maximum + " bytes.");
    }
}
