package io.github.guillermodubon.coachgym.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AuthenticationPublicRegistrationAbsentContractTest {

    @Test
    void authenticationSurfaceHasNoPublicStaffRegistrationRoute() throws Exception {
        Class<?> controller = Class.forName(
                "io.github.guillermodubon.coachgym.auth.web.AuthenticationController");
        RequestMapping baseMapping = controller.getAnnotation(RequestMapping.class);
        assertThat(baseMapping).isNotNull();
        assertThat(List.of(baseMapping.value())).contains("/api/v1/auth");

        List<String> postOperations = Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .map(Method::getName)
                .toList();

        assertThat(postOperations).containsExactlyInAnyOrder("login", "logout");
        assertThat(postOperations)
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("register")
                        || name.toLowerCase(java.util.Locale.ROOT).contains("signup"));
    }
}
