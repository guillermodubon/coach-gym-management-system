package io.github.guillermodubon.coachgym.user.application;

import java.util.UUID;

/**
 * Provider-neutral port for private staff-photo objects.
 *
 * <p>Implementations generate server-controlled keys and must not expose
 * provider SDK types, public URLs, or filesystem paths.</p>
 */
public interface StaffProfilePhotoStorage {

    String generateStorageKey(UUID userId, String contentType);

    void store(String storageKey, StaffProfilePhotoContent content);

    StaffProfilePhotoContent load(
            String storageKey,
            String contentType,
            long expectedSizeBytes,
            String expectedChecksumSha256);

    void delete(String storageKey);
}
