package io.github.guillermodubon.coachgym.auth.application;

/** Application-facing boundary for per-identifier login abuse control. */
public interface LoginAttemptGuard {

    boolean isAllowed(String identifier);

    void recordFailure(String identifier);

    void clear(String identifier);

    long retryAfterSeconds(String identifier);
}
