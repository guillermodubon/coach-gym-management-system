package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes expired email, IP, and token-fingerprint rate subjects from the database. */
@Component
class StaffIdentityAbuseWindowCleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffIdentityAbuseWindowCleanup.class);

    private final JdbcStaffIdentityAbuseAdapter abuseWindows;
    private final Clock clock;

    StaffIdentityAbuseWindowCleanup(JdbcStaffIdentityAbuseAdapter abuseWindows, Clock clock) {
        this.abuseWindows = Objects.requireNonNull(abuseWindows);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(fixedDelay = 600_000)
    void pruneExpiredWindows() {
        try {
            abuseWindows.pruneExpiredWindows(clock.instant());
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Expired identity abuse windows could not be pruned.");
        }
    }
}
