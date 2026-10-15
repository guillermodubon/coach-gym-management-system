package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentCorrectionEventContractTest {

    @Test
    void eventsAreImmutableRecords() {
        assertThat(PaymentVoided.class.isRecord()).isTrue();
        assertThat(PaymentRefunded.class.isRecord()).isTrue();
    }

    @Test
    void eventsContainNoReasonReferenceCardOrProviderSecrets() {
        Set<String> fields = Set.of(
                        PaymentVoided.class,
                        PaymentRefunded.class)
                .stream()
                .flatMap(type ->
                        Arrays.stream(
                                type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        assertThat(fields)
                .doesNotContain(
                        "reason",
                        "externalreference",
                        "cardnumber",
                        "cvc",
                        "stripe",
                        "paymentintent",
                        "checkoutsession",
                        "password",
                        "token");

        assertThat(fields)
                .contains("externalreferencepresent");
    }
}
