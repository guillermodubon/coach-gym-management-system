package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class StaffBranchAssignmentMethodSecurityContractTest {

    @Test
    void administrativeMutationsRequireAdminAndTransactions() {
        Map<String, String> methods = Map.of(
                "assign", "hasRole('ADMIN')",
                "end", "hasRole('ADMIN')",
                "changeScope", "hasRole('ADMIN')");

        methods.forEach((name, expression) -> {
            Method method = find(name);
            PreAuthorize security = method.getAnnotation(PreAuthorize.class);
            assertThat(security).as("%s must be method-secured", name).isNotNull();
            assertThat(security.value()).isEqualTo(expression);
            assertThat(method.getAnnotation(Transactional.class))
                    .as("%s must be transactional", name)
                    .isNotNull();
        });
    }

    private static Method find(String name) {
        return java.util.Arrays.stream(StaffBranchAssignmentApplicationService.class
                        .getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
