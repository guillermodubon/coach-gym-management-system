package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class EmailDeliveryReportingSqlContractTest {

    @Test
    void keepsLogicalDeliveriesSeparateFromAttemptAndRetryFacts() {
        String sql = normalize(JdbcEmailDeliveryReportingQuery.SUMMARY_BRANCH_SQL);

        assertThat(sql)
                .contains("from gym.email_deliveries d")
                .contains("d.branch_id in (:branchids)")
                .contains("d.requested_at >= :frominclusive")
                .contains("d.requested_at < :toexclusive")
                .contains("from gym.email_delivery_attempts attempt")
                .contains("attempt.started_at >= :frominclusive")
                .contains("attempt.started_at < :toexclusive")
                .contains("count(*) filter (where attempt_number > 1)")
                .contains("result in ('failed', 'ambiguous')")
                .contains("count(*) as total_deliveries")
                .contains("group by failure_code")
                .doesNotContain("recipient_snapshot", "provider_message_id", "failure_message",
                        "subject_snapshot", "attachment", "oauth", "staff_account_activation_deliveries",
                        "gym.notifications", "gym.audit_entries", "select *");
        assertThat(Arrays.asList(EmailDeliveryType.values()))
                .containsExactly(EmailDeliveryType.PAYMENT_RECEIPT, EmailDeliveryType.ACCESS_CREDENTIAL);
    }

    @Test
    void allAggregateQueriesApplyBranchFilterBeforeGroupingAndNeverInventIdentitySends() {
        String byBranch = normalize(JdbcEmailDeliveryReportingQuery.BRANCHES_FILTERED_SQL);
        String organization = normalize(JdbcEmailDeliveryReportingQuery.SUMMARY_ORGANIZATION_SQL);

        assertThat(byBranch)
                .contains("d.branch_id in (:branchids)")
                .contains("group by branch_id, delivery_type")
                .contains("from gym.email_delivery_attempts attempt")
                .doesNotContain("staff_invitation", "password_recovery", "identity_email");
        assertThat(organization)
                .doesNotContain(":branchids", "staff_account_activation_deliveries",
                        "gym.notifications", "gym.audit_entries");
    }

    private static String normalize(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
