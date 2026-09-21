package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.GymBranchSummary;
import java.util.Optional;
import java.util.UUID;

/** Read port for branches owned by the canonical organization. */
public interface GymBranchQuery {

    Optional<GymBranchDetails> findById(UUID id);

    /**
     * Returns the requested branch only when it belongs to the canonical
     * organization and is currently active.
     */
    Optional<GymBranchSummary> findActiveById(UUID id);

    Optional<GymBranchDetails> findByCode(String code);

    GymBranchPage findAll(GymBranchSearchQuery query);
}
