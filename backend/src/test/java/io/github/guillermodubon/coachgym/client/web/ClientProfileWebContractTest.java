package io.github.guillermodubon.coachgym.client.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ClientProfileWebContractTest {

    @Test
    void exposesSearchProfileLifecycleAndHistoryRoutes() {
        RequestMapping root = ClientProfileController.class
                .getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/api/v1/clients");

        assertMapping("findAll", GetMapping.class, "");
        assertMapping("findProfile", GetMapping.class, "/{id}/profile");
        assertMapping("findStatusHistory", GetMapping.class, "/{id}/status-history");
        assertMapping("update", PutMapping.class, "/{id}");
        assertMapping("deactivate", PostMapping.class, "/{id}/deactivate");
        assertMapping("reactivate", PostMapping.class, "/{id}/reactivate");
    }

    private static void assertMapping(
            String methodName,
            Class<? extends java.lang.annotation.Annotation> annotationType,
            String expectedPath) {
        Method method = Arrays.stream(ClientProfileController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        java.lang.annotation.Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] paths;
        if (annotation instanceof GetMapping mapping) {
            paths = mapping.value();
        } else if (annotation instanceof PutMapping mapping) {
            paths = mapping.value();
        } else {
            paths = ((PostMapping) annotation).value();
        }
        if (expectedPath.isEmpty()) {
            assertThat(paths).isEmpty();
        } else {
            assertThat(paths).containsExactly(expectedPath);
        }
    }
}
