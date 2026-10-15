package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Safe metadata for a staff profile photo.
 *
 * <p>Storage keys, provider URLs, filesystem paths, and image bytes are
 * intentionally outside this projection.</p>
 */
public record StaffProfilePhotoDetails(
        UUID photoId,
        String contentType,
        long sizeBytes,
        Instant updatedAt,
        long version) {

    public static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    public StaffProfilePhotoDetails {
        if (photoId == null) {
            throw new IllegalArgumentException("Staff profile photo id is required.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException(
                    "Staff profile photo content type is required.");
        }
        contentType = contentType.strip().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported staff profile photo content type.");
        }
        if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Staff profile photo size is outside the allowed bounds.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException(
                    "Staff profile photo update timestamp is required.");
        }
        if (version < 0) {
            throw new IllegalArgumentException(
                    "Staff profile photo version must not be negative.");
        }
    }
}
