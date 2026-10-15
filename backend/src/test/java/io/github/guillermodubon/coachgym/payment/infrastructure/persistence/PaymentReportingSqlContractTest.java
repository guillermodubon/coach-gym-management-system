package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentReportingSqlContractTest {

    @Test
    void financialAggregatesUseCanonicalRowsAndAvoidCorrectionJoinMultiplication() {
        String sql = normalized(JdbcPaymentReportingQuery.SUMMARY_BRANCH_SQL);

        assertThat(sql)
                .contains("from gym.payments p")
                .contains("p.status = 'paid'")
                .contains("p.paid_at >= :frominclusive")
                .contains("p.paid_at < :toexclusive")
                .contains("p.registered_at_branch_id in (:branchids)")
                .contains("exists (")
                .contains("from gym.payment_status_history correction")
                .contains("correction.new_status = 'voided'")
                .contains("from gym.payment_refunds refund")
                .contains("sum(refund.amount)")
                .contains("union")
                .doesNotContain("payment_attempts", "payment_receipts", "email_deliveries",
                        "::float", "::double", "select *");
    }

    @Test
    void everyFinancialCurrencySubqueryKeepsTheBranchPredicateBeforeAggregation() {
        String sql = normalized(JdbcPaymentReportingQuery.SUMMARY_BRANCH_SQL);

        assertThat(occurrences(sql, "p.registered_at_branch_id in (:branchids)"))
                .isEqualTo(3);
        assertThat(sql.indexOf("p.registered_at_branch_id in (:branchids)"))
                .isLessThan(sql.indexOf("group by p.currency"));
        assertThat(sql.lastIndexOf("p.registered_at_branch_id in (:branchids)"))
                .isLessThan(sql.lastIndexOf("group by refund.currency"));
        assertThat(normalized(JdbcPaymentReportingQuery.SUMMARY_ORGANIZATION_SQL))
                .doesNotContain(":branchids");
    }

    @Test
    void methodAndTrendQueriesRemainPaidOnlyParameterizedAndTimezoneAware() {
        String methods = normalized(JdbcPaymentReportingQuery.PAYMENT_METHOD_BRANCH_SQL);
        String trend = normalized(JdbcPaymentReportingQuery.TREND_BRANCH_SQL);

        assertThat(methods)
                .contains("p.status = 'paid'", "group by p.currency, p.payment_method",
                        "p.registered_at_branch_id in (:branchids)")
                .doesNotContain("payment_attempts", "select *");
        assertThat(trend)
                .contains("date_trunc(:bucketunit", "p.paid_at at time zone :timezone",
                        "p.paid_at >= :frominclusive", "p.paid_at < :toexclusive",
                        "p.registered_at_branch_id in (:branchids)")
                .doesNotContain("payment_attempts", "select *");
    }

    private static int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
