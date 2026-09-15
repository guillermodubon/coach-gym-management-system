package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentCorrectionPublicContractTest {

    @Test
    void correctionModelsAreImmutableRecords() {
        assertThat(Set.of(
                PaymentCorrectionDetails.class,
                PaymentRefundDetails.class,
                PaymentStatusHistoryDetails.class))
                .allSatisfy(type -> assertThat(type.isRecord()).isTrue());
    }

    @Test
    void correctionModelsDoNotExposeStripeOrCardSecrets() {
        String components = Set.of(
                        PaymentCorrectionDetails.class,
                        PaymentRefundDetails.class,
                        PaymentStatusHistoryDetails.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));

        assertThat(components)
                .doesNotContain("cardnumber")
                .doesNotContain("cvc")
                .doesNotContain("stripe")
                .doesNotContain("paymentintent")
                .doesNotContain("checkoutsession")
                .doesNotContain("password")
                .doesNotContain("token");
    }
}
