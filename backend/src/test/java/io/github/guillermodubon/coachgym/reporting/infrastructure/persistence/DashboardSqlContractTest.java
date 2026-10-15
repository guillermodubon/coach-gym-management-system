package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DashboardSqlContractTest {

    @Test
    void membershipQueryUsesAggregatesLatestPeriodAndNamedParameters() {
        String sql = normalized(JdbcMembershipDashboardQuery.SQL);

        assertThat(sql)
                .contains("count(*) filter")
                .contains("from gym.memberships")
                .contains("gym.membership_periods")
                .contains("order by mp.period_number desc")
                .contains("limit 1")
                .contains(":operationaldate")
                .contains(":expirationuntil")
                .doesNotContain("select *");
    }

    @Test
    void accessQueryUsesActualSchemaAndHalfOpenInterval() {
        String sql =
                normalized(
                        JdbcAccessDashboardQuery.SQL);

        assertThat(sql)
                .contains(
                        "from gym.access_records")
                .contains(
                        "ar.decision = 'allowed'")
                .contains(
                        "ar.decision = 'denied'")
                .contains(
                        "ar.occurred_at >= :dayfrominclusive")
                .contains(
                        "ar.occurred_at < :dayuntilexclusive")
                .doesNotContain(
                        "checked_in_at")
                .doesNotContain(
                        "ar.result")
                .doesNotContain(
                        "select *");
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
