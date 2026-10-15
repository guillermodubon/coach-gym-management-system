package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentAttemptSuccessMigrationContractTest {

    @Test
    void qualifiesPaymentColumnsInTheSuccessTriggerWithoutSensitiveFields() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/"
                + "V21__fix_payment_attempt_success_trigger.sql"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("create or replace function gym.validate_payment_attempt()")
                .contains("from gym.payments as p")
                .contains("p.payment_method")
                .contains("p.status")
                .doesNotContain("raw_payload")
                .doesNotContain("signature")
                .doesNotContain("secret")
                .doesNotContain("card_number")
                .doesNotContain("cvc");
    }
}
