package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

/** Maps HTTP status only; OAuth response bodies are never copied to diagnostics. */
final class GoogleOAuthErrorMapper {

    GoogleOAuthException fromStatus(int statusCode) {
        if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
            return new GoogleOAuthException(GoogleOAuthFailureCode.AUTHENTICATION_FAILED);
        }
        if (statusCode == 408) {
            return new GoogleOAuthException(GoogleOAuthFailureCode.TIMEOUT);
        }
        if (statusCode == 429) {
            return new GoogleOAuthException(GoogleOAuthFailureCode.RATE_LIMITED);
        }
        if (statusCode >= 500) {
            return new GoogleOAuthException(GoogleOAuthFailureCode.UNAVAILABLE);
        }
        return new GoogleOAuthException(GoogleOAuthFailureCode.INVALID_RESPONSE);
    }
}
