package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import java.time.Instant;

/** Write port for canonical organization lifecycle data. */
public interface OrganizationStore {

    OrganizationDetails update(UpdateOrganizationCommand command, Instant occurredAt);

    OrganizationDetails changeStatus(
            OrganizationStatus requestedStatus,
            long expectedVersion,
            Instant occurredAt);
}
