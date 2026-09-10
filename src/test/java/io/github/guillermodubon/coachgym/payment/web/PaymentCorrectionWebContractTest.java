package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class PaymentCorrectionWebContractTest {

    @Test
    void controllerUsesPaymentResourceBasePath() {
        assertThat(PaymentCorrectionController.class
                .isAnnotationPresent(RestController.class)).isTrue();
        RequestMapping mapping = PaymentCorrectionController.class
                .getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/api/v1/payments");
    }

    @Test
    void exposesExpectedCorrectionOperationsOnly() {
        assertPost("voidPayment", "/{paymentId}/void");
        assertPost("refundPayment", "/{paymentId}/refund");
        assertGet("findCorrection", "/{paymentId}/correction");
        assertGet("findStatusHistory", "/{paymentId}/status-history");
    }

    private static void assertPost(String name, String path) {
        Method method = method(name);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly(path);
    }

    private static void assertGet(String name, String path) {
        Method method = method(name);
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly(path);
    }

    private static Method method(String name) {
        return Arrays.stream(PaymentCorrectionController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
