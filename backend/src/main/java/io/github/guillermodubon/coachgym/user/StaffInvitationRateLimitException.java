package io.github.guillermodubon.coachgym.user;

/** Safe rejection raised when an invitation or reauthentication abuse bound is reached. */
public final class StaffInvitationRateLimitException extends RuntimeException {

    public StaffInvitationRateLimitException() {
        super("The staff invitation rate limit has been reached. Try again later.");
    }
}
