package io.github.guillermodubon.coachgym.user.application;

import java.time.Duration;
import java.time.Instant;

/** Shared persistent counters for bounded identity-abuse controls. */
public interface StaffIdentityAbuseStore {

    boolean consume(
            StaffIdentityAbuseBucket bucket,
            String subject,
            int maximum,
            Duration window,
            Instant occurredAt);

    boolean isAllowed(
            StaffIdentityAbuseBucket bucket,
            String subject,
            int maximum,
            Instant occurredAt);

    void recordFailure(
            StaffIdentityAbuseBucket bucket,
            String subject,
            int maximum,
            Duration window,
            Instant occurredAt);
}
