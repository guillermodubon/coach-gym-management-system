package io.github.guillermodubon.coachgym.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class AccessPaymentPolicyMethodSecurityContractTest {

    @Test
    void policyAdministrationRequiresAdminRole() throws Exception {
        assertSecurity("findCurrent", "hasRole('ADMIN')",
                AccessPaymentPolicyActor.class);
        assertSecurity("update", "hasRole('ADMIN')",
                UpdateAccessPaymentPolicyCommand.class,
                AccessPaymentPolicyActor.class);
    }

    @Test
    void publicUseCasesDeclareTheirTransactionSemantics() throws Exception {
        Method read = AccessPaymentPolicyApplicationService.class
                .getMethod("findCurrent", AccessPaymentPolicyActor.class);
        Method update = AccessPaymentPolicyApplicationService.class
                .getMethod("update", UpdateAccessPaymentPolicyCommand.class,
                        AccessPaymentPolicyActor.class);

        assertThat(read.getAnnotation(Transactional.class)).isNotNull();
        assertThat(read.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(update.getAnnotation(Transactional.class)).isNotNull();
        assertThat(update.getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    private static void assertSecurity(
            String name, String expression, Class<?>... parameters) throws Exception {
        Method method = AccessPaymentPolicyApplicationService.class
                .getMethod(name, parameters);
        assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo(expression)
                .doesNotContain("MAINTENANCE");
    }
}
