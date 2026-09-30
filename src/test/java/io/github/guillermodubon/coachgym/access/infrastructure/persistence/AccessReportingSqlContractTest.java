package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class AccessReportingSqlContractTest {

    @Test
    void receptionistOperationalDayProjectionHasOnlyDecisionCountsAndSqlBranchFilter() {
        String branchSql = normalize(JdbcAccessReportingQuery.OPERATIONAL_DAY_BRANCH_SQL);

        assertThat(branchSql)
                .contains("from gym.access_records ar")
                .contains("ar.branch_id in (:branchids)")
                .contains("ar.occurred_at >= :frominclusive")
                .contains("ar.occurred_at < :toexclusive")
                .contains("count(*) filter (where ar.decision = 'allowed')")
                .contains("count(*) filter (where ar.decision = 'denied')")
                .doesNotContain("reason_code", "identification_source", "entered_code",
                        "access_credential_id", "client_id", "select *");
    }

    @Test
    void aggregatesCanonicalPhysicalAttemptsWithHalfOpenBranchAndTimeFilters() {
        String sql = normalize(JdbcAccessReportingQuery.SUMMARY_BRANCH_SQL);

        assertThat(sql)
                .contains("from gym.access_records ar")
                .contains("ar.branch_id in (:branchids)")
                .contains("ar.occurred_at >= :frominclusive")
                .contains("ar.occurred_at < :toexclusive")
                .contains("decision = 'allowed'")
                .contains("decision = 'denied'")
                .contains("group by reason_code")
                .contains("identification_source in ('client_code', 'membership_code')")
                .contains("identification_source = 'qr_credential'")
                .contains("identification_source = 'unknown'")
                .doesNotContain("entered_code", "access_credential_id", "token_fingerprint",
                        "client_id", "email", "select *");
        assertThat(normalize(JdbcAccessReportingQuery.SUMMARY_ORGANIZATION_SQL))
                .doesNotContain(":branchids");
    }

    @Test
    void localDailySeriesUsesExplicitTimezoneAndIncludesEmptyDays() {
        String sql = normalize(JdbcAccessReportingQuery.DAILY_TREND_BRANCH_SQL);

        assertThat(sql)
                .contains("generate_series(")
                .contains("ar.occurred_at at time zone :timezone")
                .contains("ar.branch_id in (:branchids)")
                .contains("left join daily using (bucket_day)")
                .contains("coalesce(daily.total_attempts, 0)")
                .contains("ar.occurred_at < :toexclusive");
    }

    private static String normalize(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
