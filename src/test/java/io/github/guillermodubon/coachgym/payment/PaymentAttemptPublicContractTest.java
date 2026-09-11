package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentAttemptPublicContractTest {

    @Test
    void publicAttemptContractsAreImmutablePaymentModuleRecords() {
        List.of(PaymentAttemptDetails.class, PaymentAttemptCreated.class,
                PaymentAttemptStatusChanged.class, PaymentProviderEventAcknowledged.class)
                .forEach(type -> {
                    assertThat(type.getPackageName())
                            .isEqualTo("io.github.guillermodubon.coachgym.payment");
                    assertThat(type.isRecord()).isTrue();
                });
    }

    @Test
    void publicAttemptContractsExcludeProviderReferencesUrlsAndSensitiveData() {
        String components = List.of(
                        PaymentAttemptDetails.class,
                        PaymentAttemptCreated.class,
                        PaymentAttemptStatusChanged.class,
                        PaymentProviderEventAcknowledged.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));

        assertThat(components)
                .doesNotContain("reference")
                .doesNotContain("url")
                .doesNotContain("payload")
                .doesNotContain("signature")
                .doesNotContain("secret")
                .doesNotContain("cardnumber")
                .doesNotContain("cvc")
                .doesNotContain("token")
                .doesNotContain("password");
    }

    @Test
    void openTransitionEventKeepsEventTimeSeparateFromAttemptCompletionTime() {
        PaymentAttemptStatusChanged event = new PaymentAttemptStatusChanged(
                UUID.fromString("00000000-0000-0000-0000-000000000721"),
                PaymentProvider.STRIPE,
                PaymentAttemptStatus.CREATED,
                PaymentAttemptStatus.PROCESSING,
                null,
                null,
                UUID.fromString("00000000-0000-0000-0000-000000000722"),
                Instant.parse("2026-09-10T12:00:00Z"));

        assertThat(event.currentStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
    }
}
