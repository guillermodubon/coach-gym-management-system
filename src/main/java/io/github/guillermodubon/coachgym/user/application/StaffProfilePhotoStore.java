package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoDetails;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for staff photo metadata and its profile-version guard.
 * Object bytes remain behind {@link StaffProfilePhotoStorage}.
 */
public interface StaffProfilePhotoStore {

    Optional<StaffProfilePhotoRecord> findPhotoByUserId(UUID userId);

    StaffProfilePhotoDetails save(
            UUID userId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            long expectedProfileVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    String delete(UUID userId, long expectedProfileVersion);
}
