package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentReceiptSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V22__create_payment_receipts.sql");

    @Test
    void migrationDefinesImmutableCanonicalReceiptMetadata() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("create table gym.payment_receipts")
                .contains("id uuid primary key")
                .contains("receipt_number varchar(32) not null")
                .contains("payment_id uuid not null")
                .contains("uq_payment_receipts_receipt_number unique (receipt_number)")
                .contains("uq_payment_receipts_payment_id unique (payment_id)")
                .contains("uq_payment_receipts_storage_key unique (storage_key)")
                .contains("payment_status_snapshot = 'paid'")
                .contains("payment receipts are immutable")
                .contains("before update or delete on gym.payment_receipts");
    }

    @Test
    void migrationProtectsFinancialAndDocumentInvariants() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("amount > 0")
                .contains("amount = list_price - discount_amount")
                .contains("currency ~ '^[a-z]{3}$'")
                .contains("payment_method in ('cash', 'card', 'bank_transfer', 'other')")
                .contains("content_type = 'application/pdf'")
                .contains("size_bytes <= 10485760")
                .contains("checksum_sha256 ~ '^[0-9a-f]{64}$'")
                .contains("generated_at >= paid_at")
                .contains("version >= 0")
                .doesNotContain("bytea")
                .doesNotContain("card_number")
                .doesNotContain("cvc")
                .doesNotContain("pin")
                .doesNotContain("secret");
    }

    @Test
    void migrationUsesRestrictivePaymentAndActorForeignKeysAndEligibilityTrigger()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .containsSubsequence(
                        "foreign key (payment_id)",
                        "references gym.payments (id)",
                        "on delete restrict")
                .containsSubsequence(
                        "foreign key (generated_by_user_id)",
                        "references gym.users (id)",
                        "on delete restrict")
                .contains("validate_payment_receipt()")
                .contains("payment receipt requires a paid payment")
                .contains("payment receipt snapshot does not match its payment")
                .doesNotContain("on delete cascade");
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
