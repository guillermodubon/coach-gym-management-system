package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MembershipReportingSqlContractTest {

    @Test
    void separatesMembershipOriginFromPeriodOriginAndBoundsAllEventMetrics() {
        String members = normalized(JdbcMembershipReportingQuery.MEMBERSHIP_SUMMARY_BRANCH_SQL);
        String periods = normalized(JdbcMembershipReportingQuery.PERIOD_SUMMARY_BRANCH_SQL);

        assertThat(members)
                .contains("m.registered_at_branch_id in (:branchids)")
                .contains("m.created_at >= :frominclusive")
                .contains("m.created_at < :toexclusive")
                .contains("m.status = 'active'", "m.status = 'frozen'",
                        "m.status = 'expired'", "m.status = 'cancelled'");
        assertThat(periods)
                .contains("p.registered_at_branch_id in (:branchids)")
                .contains("p.created_at >= :frominclusive")
                .contains("p.created_at < :toexclusive")
                .contains("p.effective_ends_on >= :expiringfrom")
                .contains("p.effective_ends_on < :expiringuntil")
                .contains("newer.period_number > p.period_number");
        assertThat(normalized(JdbcMembershipReportingQuery.PERIOD_SUMMARY_ORGANIZATION_SQL))
                .doesNotContain(":branchids");
    }

    @Test
    void usesOneLatestPeriodAndItsSnapshotInsteadOfJoiningCoverageMembers() {
        String plans = normalized(JdbcMembershipReportingQuery.PLAN_DISTRIBUTION_BRANCH_SQL);
        String coverage = normalized(JdbcMembershipReportingQuery.COVERAGE_DISTRIBUTION_BRANCH_SQL);

        assertThat(plans)
                .contains("select distinct on (m.id)", "p.period_number desc",
                        "m.registered_at_branch_id in (:branchids)",
                        "p.plan_code_snapshot", "p.plan_name_snapshot")
                .doesNotContain("membership_period_branch_coverage", "select *");
        assertThat(coverage)
                .contains("select distinct on (m.id)", "p.period_number desc",
                        "m.registered_at_branch_id in (:branchids)",
                        "membership_period_coverage_snapshots",
                        "snapshot.coverage_scope_snapshot")
                .doesNotContain("membership_period_branch_coverage", "select *");
        assertThat(normalized(JdbcMembershipReportingQuery.COVERAGE_DISTRIBUTION_ORGANIZATION_SQL))
                .doesNotContain(":branchids");
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
