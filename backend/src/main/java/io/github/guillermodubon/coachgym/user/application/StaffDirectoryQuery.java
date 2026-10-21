package io.github.guillermodubon.coachgym.user.application;

import java.util.Optional;
import java.util.UUID;

/** Read port for administrative staff directory projections. */
public interface StaffDirectoryQuery {

    StaffDirectoryPage findPage(StaffDirectorySearchQuery query);

    Optional<StaffDirectoryDetails> findById(UUID userId);
}
