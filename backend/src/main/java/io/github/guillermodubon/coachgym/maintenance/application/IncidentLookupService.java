package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Exposes incident lookup through the maintenance module's public boundary. */
@Component
public class IncidentLookupService implements IncidentLookup {

    private final IncidentStore incidentStore;

    public IncidentLookupService(IncidentStore incidentStore) {
        this.incidentStore = incidentStore;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IncidentDetails> findById(UUID incidentId) {
        if (incidentId == null) {
            return Optional.empty();
        }
        return incidentStore.findById(incidentId);
    }
}
