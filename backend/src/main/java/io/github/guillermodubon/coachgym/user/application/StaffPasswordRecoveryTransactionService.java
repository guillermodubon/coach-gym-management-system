package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmail;
import io.github.guillermodubon.coachgym.user.CompletePasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.PasswordRecoveryPolicy;
import io.github.guillermodubon.coachgym.user.PasswordRecoveryStatus;
import io.github.guillermodubon.coachgym.user.RequestPasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffPasswordReset;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic password recovery issuance and completion; never returns token data to an API. */
@Service
class StaffPasswordRecoveryTransactionService {

    private final StaffPasswordRecoveryAccountStore accounts;
    private final StaffPasswordRecoveryPersistence recoveries;
    private final StaffOneTimeTokenGenerator tokenGenerator;
    private final StaffTokenProtector tokenProtector;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    StaffPasswordRecoveryTransactionService(
            StaffPasswordRecoveryAccountStore accounts,
            StaffPasswordRecoveryPersistence recoveries,
            StaffOneTimeTokenGenerator tokenGenerator,
            StaffTokenProtector tokenProtector,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.recoveries = Objects.requireNonNull(recoveries);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.tokenProtector = Objects.requireNonNull(tokenProtector);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    Optional<StaffPasswordRecoveryEmail> request(RequestPasswordRecoveryCommand command) {
        Objects.requireNonNull(command, "Password recovery request is required.");
        Optional<StaffPasswordRecoveryAccount> candidate = accounts.lockByNormalizedEmail(command.email());
        if (candidate.isEmpty() || candidate.get().status() != StaffAccountStatus.ACTIVE) {
            return Optional.empty();
        }

        StaffPasswordRecoveryAccount account = candidate.get();
        Instant requestedAt = clock.instant();
        recoveries.revokePendingForUser(account.userId(), requestedAt);
        String token = io.github.guillermodubon.coachgym.user.StaffTokenPolicy
                .requirePresentedToken(tokenGenerator.generate());
        var fingerprint = tokenProtector.fingerprint(token, StaffTokenPurpose.PASSWORD_RECOVERY);
        Instant expiresAt = requestedAt.plus(PasswordRecoveryPolicy.DEFAULT_LIFETIME);
        recoveries.create(new StaffPasswordRecoveryDraft(
                UUID.randomUUID(), account.userId(), fingerprint, requestedAt, expiresAt));
        return Optional.of(new StaffPasswordRecoveryEmail(
                account.normalizedEmail(), account.displayName(), token, expiresAt));
    }

    /**
     * Returns false for every unavailable token/account state; expiry is committed
     * before the caller maps the result to the generic public response.
     */
    @Transactional
    boolean complete(CompletePasswordRecoveryCommand command) {
        Objects.requireNonNull(command, "Password recovery completion is required.");
        String token = command.token();
        var fingerprint = tokenProtector.fingerprint(token, StaffTokenPurpose.PASSWORD_RECOVERY);
        Optional<StaffPasswordRecoveryRecord> initiallyFound =
                recoveries.findPendingByFingerprint(fingerprint);
        if (initiallyFound.isEmpty()) {
            return false;
        }

        StaffPasswordRecoveryRecord initial = initiallyFound.get();
        Optional<StaffPasswordRecoveryAccount> lockedAccount = accounts.lockById(initial.userId());
        if (lockedAccount.isEmpty()) {
            return false;
        }
        Optional<StaffPasswordRecoveryRecord> lockedRecovery =
                recoveries.lockPendingByFingerprint(fingerprint);
        if (lockedRecovery.isEmpty() || !initial.recoveryId().equals(lockedRecovery.get().recoveryId())) {
            return false;
        }

        StaffPasswordRecoveryAccount account = lockedAccount.get();
        StaffPasswordRecoveryRecord recovery = lockedRecovery.get();
        if (!account.userId().equals(recovery.userId())
                || account.status() != StaffAccountStatus.ACTIVE) {
            return false;
        }

        Instant occurredAt = clock.instant();
        if (!occurredAt.isBefore(recovery.expiresAt())) {
            recoveries.transition(
                    recovery.recoveryId(), PasswordRecoveryStatus.EXPIRED,
                    occurredAt, recovery.version());
            return false;
        }
        if (passwordEncoder.matches(command.newPassword(), account.passwordHash())) {
            recoveries.recordFailedAttempt(
                    recovery.recoveryId(), occurredAt, recovery.version());
            return false;
        }

        String encodedPassword = passwordEncoder.encode(command.newPassword());
        accounts.updatePassword(
                account.userId(), account.securityVersion(), encodedPassword, occurredAt);
        recoveries.transition(
                recovery.recoveryId(), PasswordRecoveryStatus.USED,
                occurredAt, recovery.version());
        eventPublisher.publishEvent(new StaffPasswordReset(account.userId(), occurredAt));
        return true;
    }
}
