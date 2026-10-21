package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class StaffDirectoryMethodSecurityContractTest {

    @Test
    void directoryReadsRequireAdminAndUseReadOnlyTransactions() {
        List.of("findPage", "findDetails").forEach(name -> {
            Method method = java.util.Arrays.stream(StaffDirectoryApplicationService.class
                            .getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name))
                    .findFirst()
                    .orElseThrow();
            PreAuthorize security = method.getAnnotation(PreAuthorize.class);
            assertThat(security).as("%s must be method-secured", name).isNotNull();
            assertThat(security.value()).isEqualTo("hasRole('ADMIN')");
            assertThat(method.getAnnotation(Transactional.class))
                    .as("%s must be transactional", name)
                    .satisfies(transaction -> assertThat(transaction.readOnly()).isTrue());
        });
    }
}
