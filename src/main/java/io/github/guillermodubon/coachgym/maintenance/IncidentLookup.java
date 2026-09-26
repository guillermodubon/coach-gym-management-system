package io.github.guillermodubon.coachgym.maintenance;

import java.util.Optional;
import java.util.UUID;

/** Public read-only boundary for linking work orders to incidents. */
public interface IncidentLookup {
    Optional<IncidentDetails> findById(UUID incidentId);

    default Optional<IncidentDetails> findById(UUID incidentId, UUID branchId) {
        return findById(incidentId);
    }
}
