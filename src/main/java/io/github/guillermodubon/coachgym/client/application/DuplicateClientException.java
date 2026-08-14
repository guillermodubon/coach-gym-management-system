package io.github.guillermodubon.coachgym.client.application;

public class DuplicateClientException extends RuntimeException {

    public DuplicateClientException() {
        super("A client with the provided email already exists.");
    }
}
