package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EmailDeliveryMethodSecurityContractTest {

    @Test
    void everyDeliveryOperationRequiresCurrentStaffRoles() {
        for (String methodName : new String[] {
            "requestPaymentReceiptEmail",
            "requestAccessCredentialEmail",
            "retryEmailDelivery",
            "findById",
            "findAll",
            "findAttempts"
        }) {
            Method method = java.util.Arrays.stream(
                            TransactionalEmailDeliveryApplicationService.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
            assertThat(method.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        }
    }
}
