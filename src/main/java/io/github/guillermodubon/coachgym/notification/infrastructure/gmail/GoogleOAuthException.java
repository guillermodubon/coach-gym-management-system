package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

/** OAuth error translated to a stable, non-sensitive internal outcome. */
final class GoogleOAuthException extends RuntimeException {

    private final GoogleOAuthFailureCode failureCode;

    GoogleOAuthException(GoogleOAuthFailureCode failureCode) {
        super(safeMessage(failureCode));
        this.failureCode = failureCode;
    }

    GoogleOAuthFailureCode failureCode() {
        return failureCode;
    }

    private static String safeMessage(GoogleOAuthFailureCode code) {
        return switch (code) {
            case CONFIGURATION_INVALID -> "Google OAuth configuration is invalid.";
            case AUTHENTICATION_FAILED -> "Google OAuth authentication was rejected.";
            case RATE_LIMITED -> "Google OAuth temporarily limited token requests.";
            case TIMEOUT -> "Google OAuth token request timed out.";
            case UNAVAILABLE -> "Google OAuth is temporarily unavailable.";
            case INVALID_RESPONSE -> "Google OAuth returned an invalid response.";
            case REFRESH_WAIT_TIMEOUT -> "Timed out waiting for the OAuth token refresh.";
            case INTERRUPTED -> "Google OAuth token refresh was interrupted.";
        };
    }
}
