package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class PaymentAttemptApplicationContractTest {

    @Test
    void checkoutMutationsAllowBothStaffRoles() {
        assertExpression("createCheckout", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertExpression("cancel", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertExpression("findById", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }

    @Test
    void providerMutationsAreNotWrappedInAnApplicationTransaction() {
        for (String methodName : new String[] {"createCheckout", "cancel"}) {
            Method method = method(methodName);
            assertThat(method.getAnnotation(Transactional.class)).isNull();
        }
    }

    @Test
    void onlyTheReadMethodUsesReadOnlyTransaction() {
        Method method = method("findById");
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
    }

    private static Method method(String name) {
        return Arrays.stream(PaymentAttemptApplicationService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static void assertExpression(String methodName, String expected) {
        PreAuthorize annotation = method(methodName).getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
