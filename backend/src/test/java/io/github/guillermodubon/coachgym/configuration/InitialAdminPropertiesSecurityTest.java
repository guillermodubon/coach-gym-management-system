package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InitialAdminPropertiesSecurityTest {

    @Test
    void doesNotExposeBootstrapPasswordInDiagnosticText() {
        InitialAdminProperties properties = new InitialAdminProperties(
                true,
                "admin",
                "admin@example.com",
                "super-secret-password",
                "Coach",
                "Admin");

        assertThat(properties.toString())
                .doesNotContain("super-secret-password")
                .doesNotContain("password=super-secret-password")
                .contains("passwordPresent=true");
    }
}
