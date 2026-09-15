package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentCorrectionPersistenceSqlContractTest {

    @Test
    void mutationLocksPaymentAndChecksVersionAndPaidState() {
        String lock = normalized(JdbcPaymentCorrectionAdapter.LOCK_PAYMENT_SQL);
        String update = normalized(JdbcPaymentCorrectionAdapter.UPDATE_STATUS_SQL);

        assertThat(lock)
                .contains("from gym.payments")
                .contains("where id = :paymentid")
                .contains("for update");
        assertThat(update)
                .contains("version = version + 1")
                .contains("version = :expectedversion")
                .contains("status = 'paid'")
                .doesNotContain("delete from");
    }

    @Test
    void refundUsesOriginalSnapshotAndHistoryIsInserted() {
        String refund = normalized(JdbcPaymentCorrectionAdapter.INSERT_REFUND_SQL);
        String history = normalized(JdbcPaymentCorrectionAdapter.INSERT_HISTORY_SQL);

        assertThat(refund)
                .contains("insert into gym.payment_refunds")
                .contains(":amount")
                .contains(":currency")
                .contains(":refundmethod")
                .contains("returning refund_code")
                .doesNotContain("stripe");
        assertThat(history)
                .contains("insert into gym.payment_status_history")
                .contains("'paid'")
                .contains(":newstatus")
                .contains(":actorid");
    }

    @Test
    void readQueriesArePaginatedAndNewestFirst() {
        assertThat(normalized(JdbcPaymentStatusHistoryQuery.SELECT_SQL))
                .contains("order by occurred_at desc, id desc")
                .contains("limit :limit offset :offset")
                .doesNotContain("update gym")
                .doesNotContain("delete from");
        assertThat(normalized(JdbcPaymentCorrectionQuery.SQL))
                .contains("new_status in ('voided', 'refunded')")
                .contains("p.status in ('voided', 'refunded')")
                .doesNotContain("select *");
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("\s+", " ")
                .strip();
    }
}
