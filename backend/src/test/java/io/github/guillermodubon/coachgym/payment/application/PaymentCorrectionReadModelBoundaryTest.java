package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryPage;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentCorrectionReadModelBoundaryTest {

    @Test
    void correctionReadModelsRemainImmutableRecords() {
        assertThat(PaymentCorrectionDetails.class.isRecord()).isTrue();
        assertThat(PaymentStatusHistoryPage.class.isRecord()).isTrue();
    }

    @Test
    void historyPageDoesNotExposeSpringPagination() {
        String components = Arrays.stream(
                        PaymentStatusHistoryPage.class.getRecordComponents())
                .map(RecordComponent::getType)
                .map(Class::getName)
                .collect(Collectors.joining(" "));

        assertThat(components)
                .doesNotContain("org.springframework.data.domain")
                .doesNotContain("Pageable")
                .doesNotContain("PageImpl");
    }

    @Test
    void applicationPortsContainNoPrematureStripePdfOrEmailConcepts() {
        String methods = Set.of(
                        PaymentCorrectionStore.class,
                        PaymentCorrectionQuery.class,
                        PaymentStatusHistoryQuery.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(Object::toString)
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));

        assertThat(methods)
                .doesNotContain("stripe")
                .doesNotContain("checkout")
                .doesNotContain("paymentintent")
                .doesNotContain("pdf")
                .doesNotContain("receipt")
                .doesNotContain("email");
    }
}
