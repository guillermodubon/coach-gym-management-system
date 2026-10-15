package io.github.guillermodubon.coachgym.organization;

import java.util.Optional;

/**
 * Public read contract for organization-owned identity data.
 *
 * <p>Consumers such as payment receipts and transactional email may depend on
 * this contract without reaching into organization application or persistence
 * packages. Deployment-only configuration remains outside this contract.</p>
 */
public interface OrganizationIdentityQuery {

    Optional<OrganizationDetails> findCanonical();
}
