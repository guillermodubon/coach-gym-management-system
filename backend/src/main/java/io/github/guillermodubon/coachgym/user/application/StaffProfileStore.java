package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import java.util.UUID;

/** Write port for optimistic, actor-derived self-profile updates. */
public interface StaffProfileStore {

    StaffSelfProfileDetails update(
            UUID userId,
            UpdateStaffSelfProfileCommand command);
}
