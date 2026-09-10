package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentCorrectionSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V18__enforce_payment_correction_integrity.sql");

    @Test
    void migrationAllowsOnlyRegistrationVoidAndFullRefundTransitions()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("previous_status is null and new_status = 'paid'")
                .contains("previous_status = 'paid'")
                .contains("new_status in ('voided', 'refunded')")
                .contains("reason is not null")
                .contains("btrim(reason) <> ''")
                .contains("alter column changed_by_user_id set not null")
                .doesNotContain("previous_status = 'voided'")
                .doesNotContain("previous_status = 'refunded'");
    }

    @Test
    void migrationMakesHistoryAndRefundsImmutable() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("before update or delete on gym.payment_status_history")
                .contains("payment status history is append-only")
                .contains("before update or delete on gym.payment_refunds")
                .contains("payment refunds are immutable");
    }

    @Test
    void migrationEnforcesFullRefundAgainstOriginalPayment()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("new.amount <> original_amount")
                .contains("new.currency <> original_currency")
                .contains("new.refund_method <> original_method")
                .contains("original_status not in ('paid', 'refunded')")
                .doesNotContain("stripe")
                .doesNotContain("paymentintent")
                .doesNotContain("checkoutsession");
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
