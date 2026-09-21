package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void normalizesExplicitOriginsWithoutAllowingWildcards() {
        CorsProperties properties = new CorsProperties(
                List.of(" http://localhost:3000/ ", "http://localhost:5173"),
                Duration.ofMinutes(30));

        assertThat(properties.allowedOrigins())
                .containsExactly("http://localhost:3000", "http://localhost:5173");
        assertThat(properties.isValid()).isTrue();
        assertThat(new CorsProperties(List.of("https://*.vercel.app"), Duration.ofMinutes(30)).isValid())
                .isFalse();
    }

    @Test
    void rejectsOriginsWithPathsAndUnboundedPreflightCache() {
        assertThat(new CorsProperties(List.of("https://frontend.example/app"), Duration.ofMinutes(30)).isValid())
                .isFalse();
        assertThat(new CorsProperties(List.of("https://frontend.example"), Duration.ofDays(2)).isValid())
                .isFalse();
    }
}
