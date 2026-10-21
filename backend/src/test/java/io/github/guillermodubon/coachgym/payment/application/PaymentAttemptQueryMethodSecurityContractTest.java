package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class PaymentAttemptQueryMethodSecurityContractTest {

    @Test
    void pageQueryAllowsOnlyTheSupportedStaffRoles() throws Exception {
        PreAuthorize authorization = findPage().getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }

    @Test
    void pageQueryUsesAReadOnlyTransaction() throws Exception {
        Transactional transaction = findPage().getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
    }

    private static Method findPage() throws NoSuchMethodException {
        return PaymentAttemptQueryApplicationService.class.getDeclaredMethod(
                "findPage", PaymentAttemptSearchQuery.class,
                io.github.guillermodubon.coachgym.user.AuthenticatedActor.class);
    }
}
