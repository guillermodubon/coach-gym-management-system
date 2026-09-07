package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class DashboardMethodSecurityContractTest {

    @Test
    void dashboardRequiresCurrentOperationalRolesAndReadOnlyTransaction()
            throws Exception {
        Method method = DashboardApplicationService.class.getMethod(
                "getDashboard", DashboardQuery.class, AuthenticatedActor.class);

        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
                .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')")
                .doesNotContain("MAINTENANCE");

        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
