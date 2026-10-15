package io.github.guillermodubon.coachgym.plan;

import java.util.Optional;
import java.util.UUID;

/**
 * Public read boundary for modules that need an active plan.
 */
public interface PlanQuery {

    Optional<PlanDetails> findActiveById(
            UUID id);
}
