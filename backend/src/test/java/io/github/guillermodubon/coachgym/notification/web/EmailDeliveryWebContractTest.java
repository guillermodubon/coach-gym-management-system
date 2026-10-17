package io.github.guillermodubon.coachgym.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class EmailDeliveryWebContractTest {

    @Test
    void exposesOnlyTheApprovedDeliveryRoutes() {
        RequestMapping root = EmailDeliveryController.class
                .getAnnotation(RequestMapping.class);
        assertThat(root).isNotNull();
        assertThat(root.value()).containsExactly("/api/v1/email-deliveries");

        assertMapping("requestPaymentReceipt", PostMapping.class, "/payment-receipts/{paymentId}");
        assertMapping("requestAccessCredential", PostMapping.class, "/access-credentials/{clientId}");
        assertMapping("findAll", GetMapping.class);
        assertMapping("findById", GetMapping.class, "/{id}");
        assertMapping("findAttempts", GetMapping.class, "/{id}/attempts");
        assertMapping("retry", PostMapping.class, "/{id}/retry");
    }

    @Test
    void doesNotExposeRecipientOrAttachmentMutationEndpoints() {
        assertThat(Arrays.stream(EmailDeliveryController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .map(method -> method.getAnnotation(PostMapping.class).value())
                .flatMap(Arrays::stream)
                .toList())
                .containsExactlyInAnyOrder(
                        "/payment-receipts/{paymentId}",
                        "/access-credentials/{clientId}",
                        "/{id}/retry");

        assertThat(RetryEmailDeliveryRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("expectedVersion");
        assertThat(RequestEmailRequest.class.getRecordComponents()).isEmpty();
    }

    private static void assertMapping(
            String methodName,
            Class<? extends Annotation> annotationType,
            String... expectedPaths) {
        Method method = Arrays.stream(EmailDeliveryController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] paths = annotation instanceof GetMapping mapping
                ? mapping.value()
                : ((PostMapping) annotation).value();
        assertThat(paths).containsExactly(expectedPaths);
    }
}
