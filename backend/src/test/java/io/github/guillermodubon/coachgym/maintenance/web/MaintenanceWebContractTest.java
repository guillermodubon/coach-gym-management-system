package io.github.guillermodubon.coachgym.maintenance.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class MaintenanceWebContractTest {

    @Test
    void exposesExpectedMaintenanceRoutes() {
        RequestMapping root =
                MaintenanceController.class.getAnnotation(
                        RequestMapping.class);

        assertThat(root).isNotNull();
        assertThat(root.value())
                .containsExactly("/api/v1/maintenances");

        assertMapping(
                "schedule",
                PostMapping.class);

        assertMapping(
                "update",
                PutMapping.class,
                "/{id}");

        assertMapping(
                "findById",
                GetMapping.class,
                "/{id}");

        assertMapping(
                "findAll",
                GetMapping.class);

        assertMapping(
                "history",
                GetMapping.class,
                "/{id}/history");

        assertMapping(
                "start",
                PostMapping.class,
                "/{id}/start");

        assertMapping(
                "complete",
                PostMapping.class,
                "/{id}/complete");

        assertMapping(
                "cancel",
                PostMapping.class,
                "/{id}/cancel");
    }

    private static void assertMapping(
            String methodName,
            Class<? extends Annotation> annotationType,
            String... expectedPaths) {

        Method method =
                Arrays.stream(
                                MaintenanceController.class
                                        .getDeclaredMethods())
                        .filter(candidate ->
                                candidate.getName()
                                        .equals(methodName))
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError(
                                        "Controller method not found: "
                                                + methodName));

        Annotation annotation =
                method.getAnnotation(annotationType);

        assertThat(annotation)
                .as(
                        "%s must be annotated with %s",
                        methodName,
                        annotationType.getSimpleName())
                .isNotNull();

        String[] actualPaths =
                extractPaths(annotation);

        assertThat(actualPaths)
                .as(
                        "Unexpected mapping for method %s",
                        methodName)
                .containsExactly(expectedPaths);
    }

    private static String[] extractPaths(
            Annotation annotation) {

        if (annotation instanceof GetMapping mapping) {
            return mapping.value();
        }

        if (annotation instanceof PostMapping mapping) {
            return mapping.value();
        }

        if (annotation instanceof PutMapping mapping) {
            return mapping.value();
        }

        throw new AssertionError(
                "Unsupported mapping annotation: "
                        + annotation.annotationType()
                        .getSimpleName());
    }
}