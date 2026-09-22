package io.github.guillermodubon.coachgym.user;

import java.util.Optional;
import java.util.UUID;

/** Public read port for current staff authorization facts. */
public interface StaffScopeQuery {

    Optional<StaffScopeDetails> findScope(UUID userId);

    Optional<StaffAuthorizationContext> findAuthorizationContext(UUID userId);
}
