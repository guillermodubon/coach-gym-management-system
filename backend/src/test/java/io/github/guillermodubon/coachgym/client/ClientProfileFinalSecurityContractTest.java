package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.client.application.ClientPhotoApplicationService;
import io.github.guillermodubon.coachgym.client.application.ClientProfileApplicationService;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

/** Final method-security and transaction contract for client profile operations. */
class ClientProfileFinalSecurityContractTest {

    @Test
    void profileReadsAndUpdatesAllowCurrentStaffRoles() {
        assertAuthorizationContains(
                ClientProfileApplicationService.class,
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }

    @Test
    void lifecycleOperationsRemainAdministratorOnly() {
        assertMethodAuthorization(
                ClientProfileApplicationService.class,
                "deactivate",
                "hasRole('ADMIN')");
        assertMethodAuthorization(
                ClientProfileApplicationService.class,
                "reactivate",
                "hasRole('ADMIN')");
    }

    @Test
    void photoDeletionRemainsAdministratorOnly() {
        assertMethodAuthorization(
                ClientPhotoApplicationService.class,
                "delete",
                "hasRole('ADMIN')");
    }

    @Test
    void profileApplicationServiceUsesTransactionalBoundaries() {
        boolean transactional = ClientProfileApplicationService.class
                .isAnnotationPresent(Transactional.class)
                || Arrays.stream(ClientProfileApplicationService.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(Transactional.class));

        assertThat(transactional).isTrue();
    }

    @Test
    void removedMaintenanceRoleIsNotUsedByClientProfileServices() {
        String annotations = Arrays.stream(new Class<?>[] {
                        ClientProfileApplicationService.class,
                        ClientPhotoApplicationService.class})
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(Method::getAnnotations)
                .flatMap(Arrays::stream)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining(" "));

        assertThat(annotations)
                .doesNotContain("ROLE_MAINTENANCE")
                .doesNotContain("hasRole('MAINTENANCE')")
                .doesNotContain("hasAnyRole('MAINTENANCE'");
    }

    private static void assertAuthorizationContains(
            Class<?> serviceType,
            String expression) {
        boolean found = Arrays.stream(serviceType.getDeclaredMethods())
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .map(PreAuthorize::value)
                .anyMatch(expression::equals);

        assertThat(found)
                .as("Expected authorization expression %s in %s", expression,
                        serviceType.getSimpleName())
                .isTrue();
    }

    private static void assertMethodAuthorization(
            Class<?> serviceType,
            String methodName,
            String expression) {
        boolean found = Arrays.stream(serviceType.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .map(PreAuthorize::value)
                .anyMatch(expression::equals);

        assertThat(found)
                .as("Expected %s on %s.%s", expression,
                        serviceType.getSimpleName(), methodName)
                .isTrue();
    }
}
