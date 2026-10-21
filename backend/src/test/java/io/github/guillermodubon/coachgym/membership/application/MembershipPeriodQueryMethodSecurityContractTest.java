package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class MembershipPeriodQueryMethodSecurityContractTest {

    @Test
    void pageAndDetailRequireOperationalRoleAndReadOnlyTransactions() throws Exception {
        assertProtectedRead("findPage", java.util.UUID.class, MembershipPeriodSearchQuery.class,
                io.github.guillermodubon.coachgym.user.AuthenticatedActor.class);
        assertProtectedRead("findById", java.util.UUID.class, java.util.UUID.class,
                io.github.guillermodubon.coachgym.user.AuthenticatedActor.class);
    }

    private static void assertProtectedRead(String name, Class<?>... parameterTypes)
            throws Exception {
        Method method = MembershipPeriodQueryApplicationService.class.getMethod(name, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertThat(method.getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}
