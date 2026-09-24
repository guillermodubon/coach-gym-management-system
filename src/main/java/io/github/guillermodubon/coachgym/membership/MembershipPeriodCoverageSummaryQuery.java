package io.github.guillermodubon.coachgym.membership;

import java.util.Optional;
import java.util.UUID;

/** Safe read boundary for a period's immutable coverage summary; branch IDs are deliberately omitted. */
@FunctionalInterface
public interface MembershipPeriodCoverageSummaryQuery {

    Optional<MembershipPeriodCoverageSummary> findCoverageSummary(UUID membershipPeriodId);
}
