package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import java.util.Optional;

/** Read port for the single canonical organization. */
public interface OrganizationQuery {

    Optional<OrganizationDetails> findCanonical();
}
