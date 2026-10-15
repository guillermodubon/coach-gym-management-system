package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientPhotoDetails;

public record ClientPhotoRecord(
        ClientPhotoDetails details,
        String storageKey,
        String checksumSha256) {

    public ClientPhotoRecord {
        if (details == null || storageKey == null || storageKey.isBlank()
                || checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
            throw new ClientPhotoValidationException("Stored client photo metadata is invalid.");
        }
    }
}
