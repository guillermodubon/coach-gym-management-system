package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentReceiptPersistenceSqlContractTest {

    @Test
    void usesParameterizedReceiptSqlAndCanonicalLookupIndexes() {
        assertThat(JdbcPaymentReceiptAdapter.INSERT)
                .contains("insert into gym.payment_receipts")
                .contains(":paymentId")
                .contains(":amount")
                .contains("on conflict (payment_id) do nothing")
                .contains("returning")
                .doesNotContain("?paymentId")
                .doesNotContain("' +");
        assertThat(JdbcPaymentReceiptAdapter.SELECT)
                .contains("from gym.payment_receipts")
                .contains("payment_id");
    }

    @Test
    void snapshotQueryJoinsAuthoritativePaymentMembershipAndSettingsRows() {
        assertThat(JdbcPaymentReceiptSnapshotQuery.PAYMENT_SNAPSHOT_SQL)
                .contains("from gym.payments")
                .contains("join gym.clients")
                .contains("join gym.memberships")
                .contains("join gym.membership_periods")
                .contains(":paymentId");
        assertThat(JdbcPaymentReceiptSnapshotQuery.ORGANIZATION_SQL)
                .contains("from gym.gym_settings")
                .contains("where id = 1");
    }
}
