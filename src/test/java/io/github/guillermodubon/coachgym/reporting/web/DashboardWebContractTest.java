package io.github.guillermodubon.coachgym.reporting.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DashboardWebContractTest {

    @Test
    void exposesOnlyExpectedReadOnlyRoute() throws Exception {
        RequestMapping root = DashboardController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/api/v1/reporting");

        Method method = java.util.Arrays.stream(DashboardController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("dashboard"))
                .findFirst().orElseThrow();
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/dashboard");
        for (Method candidate : DashboardController.class.getDeclaredMethods()) {
            assertThat(candidate.getAnnotations())
                    .noneMatch(annotation -> annotation.annotationType().getSimpleName()
                            .matches("PostMapping|PutMapping|DeleteMapping|PatchMapping"));
        }
    }
}
