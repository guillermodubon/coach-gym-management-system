package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.organization.OrganizationIdentityQuery;
import java.lang.reflect.Field;
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
    void snapshotQueryUsesThePublicOrganizationIdentityBoundary() throws Exception {
        assertThat(JdbcPaymentReceiptSnapshotQuery.PAYMENT_SNAPSHOT_SQL)
                .contains("from gym.payments")
                .contains("join gym.clients")
                .contains("join gym.memberships")
                .contains("join gym.membership_periods")
                .contains(":paymentId");
        Field organizationQuery = JdbcPaymentReceiptSnapshotQuery.class
                .getDeclaredField("organizationQuery");
        assertThat(organizationQuery.getType()).isEqualTo(OrganizationIdentityQuery.class);
    }
}
