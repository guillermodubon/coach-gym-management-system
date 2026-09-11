package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentAttemptCheckoutMigrationContractTest {

    @Test
    void migrationAddsValidatedCheckoutExpirationWithoutSensitiveFields() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/"
                + "V20__persist_payment_attempt_checkout_expiration.sql"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("add column checkout_expires_at timestamptz")
                .contains("ck_payment_attempts_checkout_expiration")
                .contains("checkout expiration is immutable")
                .doesNotContain("raw_payload")
                .doesNotContain("signature")
                .doesNotContain("secret")
                .doesNotContain("card_number")
                .doesNotContain("cvc");
    }
}
