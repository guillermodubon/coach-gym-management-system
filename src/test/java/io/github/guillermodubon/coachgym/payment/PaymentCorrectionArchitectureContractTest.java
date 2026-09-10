package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionStore;
import io.github.guillermodubon.coachgym.payment.application.PaymentStatusHistoryQuery;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentCorrectionArchitectureContractTest {

    @Test
    void crossModuleEventsRemainPublicPaymentContracts() {
        assertThat(PaymentVoided.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.payment");
        assertThat(PaymentRefunded.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.payment");
        assertThat(PaymentVoided.class.isRecord()).isTrue();
        assertThat(PaymentRefunded.class.isRecord()).isTrue();
    }

    @Test
    void applicationPortsRemainInterfacesAndServiceRemainsSeparate() {
        assertThat(List.of(
                PaymentCorrectionStore.class,
                PaymentCorrectionQuery.class,
                PaymentStatusHistoryQuery.class))
                .allSatisfy(type -> assertThat(type.isInterface()).isTrue());
        assertThat(PaymentCorrectionApplicationService.class.isInterface())
                .isFalse();
    }

    @Test
    void eventsDoNotExposeReasonReferenceValueOrProviderSecrets() {
        String components = List.of(PaymentVoided.class, PaymentRefunded.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));

        assertThat(components)
                .doesNotContain(" reason ")
                .doesNotContain("externalreference ")
                .doesNotContain("cardnumber")
                .doesNotContain("cvc")
                .doesNotContain("paymentintent")
                .doesNotContain("checkoutsession")
                .doesNotContain("token");
    }

    @Test
    void paymentProductionCodeContainsNoPrematureStripeDependency()
            throws Exception {
        Path paymentRoot = Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/payment");
        String source;
        try (var paths = Files.walk(paymentRoot)) {
            source = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(PaymentCorrectionArchitectureContractTest::read)
                    .collect(Collectors.joining("\n"))
                    .toLowerCase(Locale.ROOT);
        }

        assertThat(source)
                .doesNotContain("import com.stripe")
                .doesNotContain("paymentintent")
                .doesNotContain("checkoutsession")
                .doesNotContain("stripe-java");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
