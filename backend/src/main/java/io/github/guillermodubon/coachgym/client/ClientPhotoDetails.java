package io.github.guillermodubon.coachgym.client;

import java.time.Instant;
import java.util.UUID;

/** Safe photo metadata. Internal storage paths are intentionally excluded. */
public record ClientPhotoDetails(
        UUID photoId,
        String contentType,
        long sizeBytes,
        Instant updatedAt,
        long version) {

    public ClientPhotoDetails {
        if (photoId == null) {
            throw new IllegalArgumentException("Client photo id is required.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Client photo content type is required.");
        }
        contentType = contentType.strip().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("image/jpeg", "image/png", "image/webp")
                .contains(contentType)) {
            throw new IllegalArgumentException("Unsupported client photo content type.");
        }
        if (sizeBytes < 1 || sizeBytes > 5L * 1024L * 1024L) {
            throw new IllegalArgumentException(
                    "Client photo size must be between 1 byte and 5 MB.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException(
                    "Client photo update timestamp is required.");
        }
        if (version < 0) {
            throw new IllegalArgumentException(
                    "Client photo version must not be negative.");
        }
    }
}
