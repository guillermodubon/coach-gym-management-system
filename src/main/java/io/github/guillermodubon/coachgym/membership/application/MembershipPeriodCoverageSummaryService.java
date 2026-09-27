package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummaryQuery;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the privacy-minimized immutable entitlement summary for a period. */
@Service
public class MembershipPeriodCoverageSummaryService {

    private final MembershipPeriodCoverageSummaryQuery coverageQuery;

    public MembershipPeriodCoverageSummaryService(
            MembershipPeriodCoverageSummaryQuery coverageQuery) {
        this.coverageQuery = Objects.requireNonNull(coverageQuery);
    }

    @Transactional(readOnly = true)
    public MembershipPeriodCoverageSummary findRequired(UUID membershipPeriodId) {
        return coverageQuery.findCoverageSummary(
                        Objects.requireNonNull(membershipPeriodId))
                .orElseThrow(MembershipPeriodCoverageSummaryUnavailableException::new);
    }
}
