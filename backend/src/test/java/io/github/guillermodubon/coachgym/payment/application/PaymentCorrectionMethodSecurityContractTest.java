package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PaymentCorrectionMethodSecurityContractTest {

    @Test
    void mutationsAreAdministratorOnly() {
        assertExpression("voidPayment", "hasRole('ADMIN')");
        assertExpression("refundPayment", "hasRole('ADMIN')");
    }

    @Test
    void readsAllowBothCurrentRoles() {
        assertExpression(
                "findCorrection",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertExpression(
                "findStatusHistory",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }

    @Test
    void removedMaintenanceRoleIsAbsent() {
        String expressions = Arrays.stream(
                        PaymentCorrectionApplicationService.class
                                .getDeclaredMethods())
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .map(PreAuthorize::value)
                .collect(java.util.stream.Collectors.joining(" "));

        assertThat(expressions)
                .doesNotContain("MAINTENANCE")
                .doesNotContain("ROLE_MAINTENANCE");
    }

    private static void assertExpression(
            String methodName,
            String expected) {
        Method method = Arrays.stream(
                        PaymentCorrectionApplicationService.class
                                .getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
