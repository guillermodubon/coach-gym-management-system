package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigurationContractTest {

    @Test
    void configuresCredentialedCorsWithAnExactOriginAllowList() {
        CorsConfigurationSource source = new SecurityConfiguration()
                .corsConfigurationSource(new CorsProperties(
                        List.of("https://frontend.example"),
                        Duration.ofMinutes(30)));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://frontend.example");
        assertThat(configuration.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).contains("X-XSRF-TOKEN", "Content-Type");
        assertThat(configuration.getExposedHeaders())
                .contains("Location", "Content-Disposition", "X-Correlation-ID");
        assertThat(configuration.checkOrigin("https://frontend.example"))
                .isEqualTo("https://frontend.example");
        assertThat(configuration.checkOrigin("https://evil.example")).isNull();
    }
}
