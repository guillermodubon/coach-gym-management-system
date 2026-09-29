package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

/** Safe, bounded OAuth outcomes; provider response text is intentionally absent. */
enum GoogleOAuthFailureCode {
    CONFIGURATION_INVALID,
    AUTHENTICATION_FAILED,
    RATE_LIMITED,
    TIMEOUT,
    UNAVAILABLE,
    INVALID_RESPONSE,
    REFRESH_WAIT_TIMEOUT,
    INTERRUPTED
}
