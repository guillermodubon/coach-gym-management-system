package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentReceiptPublicContractTest {

    @Test
    void receiptContractsAreImmutableRecordsInThePublicPaymentPackage() {
        assertThat(List.of(
                PaymentReceiptDetails.class,
                PaymentReceiptDocument.class,
                PaymentReceiptSnapshot.class,
                PaymentReceiptGenerated.class))
                .allSatisfy(type -> {
                    assertThat(type.getPackageName())
                            .isEqualTo("io.github.guillermodubon.coachgym.payment");
                    assertThat(type.isRecord()).isTrue();
                });
    }

    @Test
    void publicContractsDoNotExposeInfrastructureOrSensitiveProviderData() {
        String components = List.of(
                        PaymentReceiptDetails.class,
                        PaymentReceiptDocument.class,
                        PaymentReceiptSnapshot.class,
                        PaymentReceiptGenerated.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));

        assertThat(components)
                .doesNotContain("storagepath")
                .doesNotContain("externalreference")
                .doesNotContain("payload")
                .doesNotContain("signature")
                .doesNotContain("secret")
                .doesNotContain("cardnumber")
                .doesNotContain("cvc")
                .doesNotContain("pin")
                .doesNotContain("password")
                .doesNotContain("stripe");
    }
}
