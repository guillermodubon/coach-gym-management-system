package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import java.util.Optional;
import java.util.UUID;

/** Internal port for changing a staff password without exposing credentials. */
public interface StaffPasswordStore {

    Optional<String> findPasswordHash(UUID userId);

    StaffSelfProfileDetails updatePassword(
            UUID userId,
            String encodedPassword,
            long expectedProfileVersion);
}
