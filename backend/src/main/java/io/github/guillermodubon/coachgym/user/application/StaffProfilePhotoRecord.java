package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.StaffProfilePhotoDetails;

/** Internal persistence projection retaining the storage key outside public APIs. */
public record StaffProfilePhotoRecord(
        StaffProfilePhotoDetails details,
        String storageKey,
        String checksumSha256) {

    private static final int MAX_STORAGE_KEY_LENGTH = 500;

    public StaffProfilePhotoRecord {
        if (details == null || storageKey == null || storageKey.isBlank()
                || storageKey.strip().length() > MAX_STORAGE_KEY_LENGTH
                || checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
            throw new StaffProfileValidationException(
                    "Stored staff profile photo metadata is invalid.");
        }
        storageKey = storageKey.strip();
    }
}
