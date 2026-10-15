package io.github.guillermodubon.coachgym.client.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class ClientProfileMethodSecurityContractTest {

    @Test
    void readsAndUpdateAllowCurrentRolesWhileLifecycleRequiresAdmin() throws Exception {
        assertSecurity("findAll", "hasAnyRole('ADMIN', 'RECEPTIONIST')",
                io.github.guillermodubon.coachgym.client.ClientSearchQuery.class);
        assertSecurity("findProfile", "hasAnyRole('ADMIN', 'RECEPTIONIST')", UUID.class);
        assertSecurity("update", "hasAnyRole('ADMIN', 'RECEPTIONIST')",
                UUID.class, UpdateClientCommand.class,
                io.github.guillermodubon.coachgym.user.AuthenticatedActor.class);
        assertSecurity("deactivate", "hasRole('ADMIN')",
                UUID.class, DeactivateClientCommand.class,
                io.github.guillermodubon.coachgym.user.AuthenticatedActor.class);
        assertSecurity("reactivate", "hasRole('ADMIN')",
                UUID.class, ReactivateClientCommand.class,
                io.github.guillermodubon.coachgym.user.AuthenticatedActor.class);
    }

    @Test
    void everyPublicUseCaseIsTransactional() {
        assertThat(ClientProfileApplicationService.class.getDeclaredMethods())
                .filteredOn(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class))
                        .as(method.getName()).isNotNull());
    }

    private static void assertSecurity(
            String name, String expression, Class<?>... parameters) throws Exception {
        Method method = ClientProfileApplicationService.class.getMethod(name, parameters);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(expression);
    }
}
