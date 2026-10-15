package io.github.guillermodubon.coachgym.access.application;

import java.util.UUID;

/**
 * Thrown when a requested access record does not exist.
 * Maps to HTTP 404 in the controller.
 */
public class AccessRecordNotFoundException extends RuntimeException {

    public AccessRecordNotFoundException(UUID id) {
        super("Access record not found: " + id);
    }
}
