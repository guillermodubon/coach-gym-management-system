package io.github.guillermodubon.coachgym.auth.application;

import io.github.guillermodubon.coachgym.shared.security.CurrentPasswordVerifier;
import io.github.guillermodubon.coachgym.user.AuthenticatedUser;
import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Auth-owned current-password verification with a persistent per-actor failure bound. */
@Service
public class CurrentPasswordVerificationService implements CurrentPasswordVerifier {

    public static final int MAX_FAILED_CHECKS = 5;
    public static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_PASSWORD_LENGTH = 1024;

    private final AuthenticationUserQuery users;
    private final PasswordEncoder passwordEncoder;
    private final AdminReauthenticationAttemptStore attempts;
    private final Clock clock;

    public CurrentPasswordVerificationService(
            AuthenticationUserQuery users,
            PasswordEncoder passwordEncoder,
            AdminReauthenticationAttemptStore attempts,
            Clock clock) {
        this.users = Objects.requireNonNull(users);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.attempts = Objects.requireNonNull(attempts);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verify(UUID actorId, String actorUsername, String currentPassword) {
        if (actorId == null || actorUsername == null || actorUsername.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        if (!attempts.beginCheck(actorId, now)) {
            return false;
        }

        boolean matches = false;
        if (currentPassword != null
                && !currentPassword.isBlank()
                && currentPassword.length() <= MAX_PASSWORD_LENGTH) {
            AuthenticatedUser actor = users.findActiveUserByIdentifier(actorUsername.strip())
                    .filter(user -> actorId.equals(user.id()))
                    .orElse(null);
            matches = actor != null && passwordEncoder.matches(currentPassword, actor.passwordHash());
        }

        if (matches) {
            attempts.clearFailures(actorId);
        } else {
            attempts.recordFailure(actorId, now);
        }
        return matches;
    }
}
