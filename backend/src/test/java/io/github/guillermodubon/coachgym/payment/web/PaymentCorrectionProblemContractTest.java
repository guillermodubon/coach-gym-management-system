package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class PaymentCorrectionProblemContractTest {

    @Test
    void adviceIsRestrictedToCorrectionController() {
        RestControllerAdvice advice = PaymentCorrectionProblemHandler.class
                .getAnnotation(RestControllerAdvice.class);
        assertThat(advice).isNotNull();
        assertThat(advice.assignableTypes())
                .containsExactly(PaymentCorrectionController.class);
    }

    @Test
    void everyHandlerReturnsResponseEntity() {
        assertThat(Arrays.stream(
                        PaymentCorrectionProblemHandler.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("handle"))
                .map(Method::getReturnType))
                .allMatch(ResponseEntity.class::equals);
    }

    @Test
    void handlerSourceContainsStableProblemCodes() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/payment/web/"
                        + "PaymentCorrectionProblemHandler.java"));
        assertThat(source)
                .contains("PAYMENT_CORRECTION_VALIDATION_FAILED")
                .contains("PAYMENT_NOT_FOUND")
                .contains("PAYMENT_VERSION_CONFLICT")
                .contains("PAYMENT_STATE_CONFLICT")
                .contains("PAYMENT_REFUND_CONFLICT")
                .contains("PAYMENT_CORRECTION_DATA_ACCESS_FAILED")
                .doesNotContain("com.stripe");
    }
}
