package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentDashboardSqlContractTest {

    @Test
    void queryCountsAndSumsOnlyEffectivePaidRowsWithinHalfOpenPeriod() {
        String sql = normalized(JdbcPaymentDashboardQuery.SQL);

        assertThat(sql)
                .contains("count(*) as paid_count")
                .contains("coalesce(sum(p.amount), 0.00)")
                .contains("from gym.payments p")
                .contains("p.status = 'paid'")
                .contains("p.paid_at >= :frominclusive")
                .contains("p.paid_at < :untilexclusive")
                .doesNotContain("voided")
                .doesNotContain("refunded")
                .doesNotContain("select *");
    }

    @Test
    void queryDetectsDistinctCurrenciesInsteadOfMixingThem() {
        String sql = normalized(JdbcPaymentDashboardQuery.SQL);

        assertThat(sql)
                .contains("count(distinct p.currency)")
                .contains("min(p.currency)")
                .contains("string_agg(distinct p.currency")
                .doesNotContain("::float")
                .doesNotContain("::double");
    }

    @Test
    void providerAttemptsCannotContributeToRevenueBeforePaymentMaterialization() {
        String sql = normalized(JdbcPaymentDashboardQuery.SQL);

        assertThat(sql)
                .contains("from gym.payments p")
                .contains("p.status = 'paid'")
                .doesNotContain("payment_attempts")
                .doesNotContain("processing")
                .doesNotContain("succeeded");
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
