package io.github.guillermodubon.coachgym.configuration;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "coach-gym.bootstrap.admin")
public record InitialAdminProperties(
        boolean enabled,
        String username,
        String email,
        String password,
        String firstName,
        String lastName) {

    @AssertTrue(message = "all initial administrator properties must be provided and the password must contain at least 12 characters when bootstrap is enabled")
    public boolean isCompleteWhenEnabled() {
        return !enabled || (hasText(username)
                && hasText(email)
                && hasText(firstName)
                && hasText(lastName)
                && password != null
                && password.length() >= 12);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
