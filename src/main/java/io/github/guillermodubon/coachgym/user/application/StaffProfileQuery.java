package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import java.util.Optional;
import java.util.UUID;

/** Read port for the user module's authenticated self-profile projection. */
public interface StaffProfileQuery {

    Optional<StaffSelfProfileDetails> findByUserId(UUID userId);
}
