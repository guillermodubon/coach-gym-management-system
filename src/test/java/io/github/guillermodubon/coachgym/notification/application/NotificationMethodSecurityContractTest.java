package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class NotificationMethodSecurityContractTest {

    @Test
    void inboxOperationsAllowOnlyCurrentStaffRoles() {
        assertAuthorized("findAll", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertAuthorized("findById", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertAuthorized("countUnread", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertAuthorized("markAsRead", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertAuthorized("markAllAsRead", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }

    @Test
    void doesNotReferenceRemovedMaintenanceRole() {
        for (Method method : NotificationApplicationService.class.getDeclaredMethods()) {
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            if (authorization != null) {
                assertThat(authorization.value()).doesNotContain("MAINTENANCE");
            }
        }
    }

    private static void assertAuthorized(String methodName, String expression) {
        Method method = java.util.Arrays.stream(
                        NotificationApplicationService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo(expression);
    }
}
