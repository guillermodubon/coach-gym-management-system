package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import jakarta.validation.constraints.AssertTrue;
import java.net.URI;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/** Trusted frontend entry points for staff identity links; never derived from request headers. */
@Validated
@ConfigurationProperties(prefix = "coach-gym.identity-email")
public record IdentityEmailProperties(
        String invitationAcceptanceUrl,
        String passwordRecoveryUrl) {

    private static final String DEFAULT_INVITATION_URL = "http://localhost:5173/accept-invitation";
    private static final String DEFAULT_RECOVERY_URL = "http://localhost:5173/recover-password";

    @ConstructorBinding
    public IdentityEmailProperties {
        invitationAcceptanceUrl = invitationAcceptanceUrl == null || invitationAcceptanceUrl.isBlank()
                ? DEFAULT_INVITATION_URL
                : invitationAcceptanceUrl.strip();
        passwordRecoveryUrl = passwordRecoveryUrl == null || passwordRecoveryUrl.isBlank()
                ? DEFAULT_RECOVERY_URL
                : passwordRecoveryUrl.strip();
    }

    public IdentityEmailProperties(String invitationAcceptanceUrl) {
        this(invitationAcceptanceUrl, DEFAULT_RECOVERY_URL);
    }

    @AssertTrue(message = "Identity email URL must be a trusted HTTPS acceptance route")
    public boolean isInvitationAcceptanceUrlValid() {
        return isTrustedRoute(invitationAcceptanceUrl, "/accept-invitation");
    }

    @AssertTrue(message = "Identity email URL must be a trusted HTTPS recovery route")
    public boolean isPasswordRecoveryUrlValid() {
        return isTrustedRoute(passwordRecoveryUrl, "/recover-password");
    }

    private static boolean isTrustedRoute(String value, String expectedPath) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean secure = scheme.equals("https")
                    || (scheme.equals("http")
                        && (host.equals("localhost") || host.equals("127.0.0.1")
                            || host.equals("[::1]") || host.equals("::1")));
            return secure
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && expectedPath.equals(uri.getRawPath())
                    && (uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65_535))
                    && value.length() <= 1000;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
