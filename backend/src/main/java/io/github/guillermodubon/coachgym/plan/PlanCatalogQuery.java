package io.github.guillermodubon.coachgym.plan;

import java.util.List;
import java.util.UUID;

/**
 * Public read boundary for modules that need plan catalog data.
 */
public interface PlanCatalogQuery {

    List<PlanDetails> findByIds(
            java.util.Set<UUID> planIds);
}
