package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentAttemptSchemaMigrationContractTest {

    @Test
    void migrationProtectsAttemptLineageHistoryAndProviderEventIdentity() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/"
                + "V19__add_stripe_payment_attempts.sql"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("create table gym.payment_attempts")
                .contains("create table gym.payment_attempt_status_history")
                .contains("create table gym.processed_payment_provider_events")
                .contains("uq_payment_attempts_provider_checkout_reference")
                .contains("uq_payment_attempts_provider_payment_reference")
                .contains("payment attempt status history is append-only")
                .contains("succeeded payment attempt must link to matching paid card payment")
                .contains("unique (provider, provider_event_reference)")
                .doesNotContain("raw_payload")
                .doesNotContain("signature")
                .doesNotContain("secret")
                .doesNotContain("card_number")
                .doesNotContain("cvc");
    }
}
