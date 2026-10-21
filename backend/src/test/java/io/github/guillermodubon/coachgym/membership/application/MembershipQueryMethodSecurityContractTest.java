package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class MembershipQueryMethodSecurityContractTest {

    @Test
    void membershipListRequiresOperationalRoleAndReadOnlyTransaction() throws Exception {
        Method method = MembershipQueryApplicationService.class.getMethod(
                "findPage", MembershipSearchQuery.class,
                io.github.guillermodubon.coachgym.user.AuthenticatedActor.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertThat(method.getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}
