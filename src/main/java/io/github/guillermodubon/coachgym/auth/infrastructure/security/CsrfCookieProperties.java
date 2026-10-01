package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coach-gym.security.csrf-cookie")
record CsrfCookieProperties(String sameSite, boolean secure) {

    CsrfCookieProperties {
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalArgumentException("CSRF cookie SameSite policy is required.");
        }
        sameSite = switch (sameSite.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException(
                    "CSRF cookie SameSite policy must be Strict, Lax, or None.");
        };
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException(
                    "CSRF cookies using SameSite=None must be Secure.");
        }
    }
}
