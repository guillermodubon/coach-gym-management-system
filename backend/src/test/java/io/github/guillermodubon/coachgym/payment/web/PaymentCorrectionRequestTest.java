package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentCorrectionRequestTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void convertsValidRequestsToCommands() {
        UUID paymentId = UUID.randomUUID();

        assertThat(new VoidPaymentRequest("Registered twice", 2)
                .toCommand(paymentId).paymentId()).isEqualTo(paymentId);
        assertThat(new RefundPaymentRequest(
                "Approved refund", "REF-001", 3)
                .toCommand(paymentId).externalReference())
                .isEqualTo("REF-001");
    }

    @Test
    void validatesReasonReferenceAndVersion() {
        assertThat(validator.validate(new VoidPaymentRequest("No", 0)))
                .isNotEmpty();
        assertThat(validator.validate(new VoidPaymentRequest("Valid reason", -1)))
                .isNotEmpty();
        assertThat(validator.validate(new RefundPaymentRequest(
                "Approved refund", "x".repeat(129), 0)))
                .isNotEmpty();
    }

    @Test
    void refundRequestDoesNotAcceptServerControlledFields() {
        Set<String> fields = Arrays.stream(
                        RefundPaymentRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        assertThat(fields)
                .containsExactlyInAnyOrder(
                        "reason", "externalreference", "version")
                .doesNotContain(
                        "amount", "currency", "status", "actor",
                        "timestamp", "provider", "stripe",
                        "paymentintentid", "checkoutsessionid");
    }
}
