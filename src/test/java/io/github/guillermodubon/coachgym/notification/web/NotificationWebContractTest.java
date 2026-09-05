package io.github.guillermodubon.coachgym.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class NotificationWebContractTest {

    @Test
    void exposesRecipientScopedInboxRoutes() {
        RequestMapping root = NotificationController.class
                .getAnnotation(RequestMapping.class);
        assertThat(root).isNotNull();
        assertThat(root.value()).containsExactly("/api/v1/notifications");

        assertMapping("findAll", GetMapping.class);
        assertMapping("findById", GetMapping.class, "/{id}");
        assertMapping("unreadCount", GetMapping.class, "/unread-count");
        assertMapping("markAsRead", PostMapping.class, "/{id}/read");
        assertMapping("markAllAsRead", PostMapping.class, "/read-all");
    }

    @Test
    void doesNotExposeNotificationCreationOrDeletion() {
        assertThat(Arrays.stream(NotificationController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .map(method -> method.getAnnotation(PostMapping.class).value())
                .flatMap(Arrays::stream)
                .toList())
                .containsExactlyInAnyOrder("/{id}/read", "/read-all");
    }

    private static void assertMapping(
            String methodName,
            Class<? extends Annotation> annotationType,
            String... expectedPaths) {
        Method method = Arrays.stream(NotificationController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] paths = annotation instanceof GetMapping mapping
                ? mapping.value()
                : ((PostMapping) annotation).value();
        assertThat(paths).containsExactly(expectedPaths);
    }
}
