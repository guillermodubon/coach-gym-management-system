package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmail;
import io.github.guillermodubon.coachgym.user.CompletePasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.PasswordRecoveryStatus;
import io.github.guillermodubon.coachgym.user.RequestPasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffPasswordReset;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class StaffPasswordRecoveryTransactionServiceTest {

    private static final UUID USER_ID = UUID.fromString("85000000-0000-0000-0000-000000000001");
    private static final UUID RECOVERY_ID = UUID.fromString("85000000-0000-0000-0000-000000000002");
    private static final String EMAIL = "staff@example.test";
    private static final String TOKEN = "A".repeat(43);
    private static final String PASSWORD = "new-long-test-password-123";
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private StaffPasswordRecoveryAccountStore accounts;
    private StaffPasswordRecoveryPersistence recoveries;
    private StaffOneTimeTokenGenerator tokens;
    private StaffTokenProtector tokenProtector;
    private PasswordEncoder passwordEncoder;
    private ApplicationEventPublisher events;
    private StaffPasswordRecoveryTransactionService service;
    private StaffTokenFingerprint fingerprint;
    private StaffPasswordRecoveryRecord recovery;
    private StaffPasswordRecoveryAccount account;

    @BeforeEach
    void setUp() {
        accounts = mock(StaffPasswordRecoveryAccountStore.class);
        recoveries = mock(StaffPasswordRecoveryPersistence.class);
        tokens = mock(StaffOneTimeTokenGenerator.class);
        tokenProtector = mock(StaffTokenProtector.class);
        passwordEncoder = mock(PasswordEncoder.class);
        events = mock(ApplicationEventPublisher.class);
        fingerprint = new StaffTokenFingerprint(
                "c".repeat(64), StaffTokenPurpose.PASSWORD_RECOVERY.schemeVersion());
        when(tokens.generate()).thenReturn(TOKEN);
        when(tokenProtector.fingerprint(TOKEN, StaffTokenPurpose.PASSWORD_RECOVERY))
                .thenReturn(fingerprint);
        account = new StaffPasswordRecoveryAccount(
                USER_ID, EMAIL, "Ada Lovelace", "{bcrypt}old-hash", StaffAccountStatus.ACTIVE, 4);
        recovery = new StaffPasswordRecoveryRecord(
                RECOVERY_ID, USER_ID, fingerprint, PasswordRecoveryStatus.PENDING,
                NOW.minusSeconds(1), NOW.plusSeconds(900), null, null, null, 0, 3);
        service = new StaffPasswordRecoveryTransactionService(
                accounts,
                recoveries,
                tokens,
                tokenProtector,
                passwordEncoder,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void requestRotatesOldPendingTokenAndReturnsOnlyAnEphemeralRedactedEmail() {
        when(accounts.lockByNormalizedEmail(EMAIL)).thenReturn(Optional.of(account));

        Optional<StaffPasswordRecoveryEmail> result = service.request(new RequestPasswordRecoveryCommand(EMAIL));

        assertThat(result).isPresent();
        assertThat(result.get().recipient()).isEqualTo(EMAIL);
        assertThat(result.get().token()).isEqualTo(TOKEN);
        assertThat(result.get().toString()).doesNotContain(EMAIL, TOKEN);
        org.mockito.ArgumentCaptor<StaffPasswordRecoveryDraft> draft =
                org.mockito.ArgumentCaptor.forClass(StaffPasswordRecoveryDraft.class);
        var order = inOrder(accounts, recoveries, tokens, tokenProtector);
        order.verify(accounts).lockByNormalizedEmail(EMAIL);
        order.verify(recoveries).revokePendingForUser(USER_ID, NOW);
        order.verify(tokens).generate();
        order.verify(tokenProtector).fingerprint(TOKEN, StaffTokenPurpose.PASSWORD_RECOVERY);
        verify(recoveries).create(draft.capture());
        assertThat(draft.getValue().tokenFingerprint()).isEqualTo(fingerprint);
        assertThat(draft.getValue().toString()).doesNotContain(TOKEN, EMAIL);
    }

    @Test
    void unknownAndIneligibleAccountsCreateNoTokenOrDelivery() {
        when(accounts.lockByNormalizedEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(service.request(new RequestPasswordRecoveryCommand(EMAIL))).isEmpty();

        verify(recoveries, never()).create(any());
        verify(tokens, never()).generate();
    }

    @Test
    void suspendedAndDeactivatedAccountsCannotRequestRecovery() {
        for (StaffAccountStatus status : List.of(
                StaffAccountStatus.SUSPENDED, StaffAccountStatus.DEACTIVATED)) {
            when(accounts.lockByNormalizedEmail(EMAIL)).thenReturn(Optional.of(
                    new StaffPasswordRecoveryAccount(
                            USER_ID, EMAIL, "Ada Lovelace", "{bcrypt}old-hash", status, 4)));

            assertThat(service.request(new RequestPasswordRecoveryCommand(EMAIL))).isEmpty();
        }

        verify(recoveries, never()).create(any());
        verify(tokens, never()).generate();
    }

    @Test
    void resetChangesPasswordConsumesTokenAndPublishesOnlySafePostCommitFact() {
        when(recoveries.findPendingByFingerprint(fingerprint)).thenReturn(Optional.of(recovery));
        when(accounts.lockById(USER_ID)).thenReturn(Optional.of(account));
        when(recoveries.lockPendingByFingerprint(fingerprint)).thenReturn(Optional.of(recovery));
        when(passwordEncoder.matches(PASSWORD, "{bcrypt}old-hash")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{bcrypt}new-hash");

        assertThat(service.complete(command())).isTrue();

        var order = inOrder(accounts, recoveries, passwordEncoder, events);
        order.verify(recoveries).findPendingByFingerprint(fingerprint);
        order.verify(accounts).lockById(USER_ID);
        order.verify(recoveries).lockPendingByFingerprint(fingerprint);
        order.verify(passwordEncoder).matches(PASSWORD, "{bcrypt}old-hash");
        order.verify(passwordEncoder).encode(PASSWORD);
        order.verify(accounts).updatePassword(USER_ID, 4, "{bcrypt}new-hash", NOW);
        order.verify(recoveries).transition(RECOVERY_ID, PasswordRecoveryStatus.USED, NOW, 3);
        org.mockito.ArgumentCaptor<StaffPasswordReset> event =
                org.mockito.ArgumentCaptor.forClass(StaffPasswordReset.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(USER_ID);
        assertThat(event.getValue().toString()).doesNotContain(TOKEN, EMAIL, PASSWORD, "old-hash", "new-hash");
    }

    @Test
    void expiredTokenIsFinalizedButNeverChangesPassword() {
        StaffPasswordRecoveryRecord expired = new StaffPasswordRecoveryRecord(
                RECOVERY_ID, USER_ID, fingerprint, PasswordRecoveryStatus.PENDING,
                NOW.minusSeconds(901), NOW, null, null, null, 0, 3);
        when(recoveries.findPendingByFingerprint(fingerprint)).thenReturn(Optional.of(expired));
        when(accounts.lockById(USER_ID)).thenReturn(Optional.of(account));
        when(recoveries.lockPendingByFingerprint(fingerprint)).thenReturn(Optional.of(expired));

        assertThat(service.complete(command())).isFalse();

        verify(recoveries).transition(RECOVERY_ID, PasswordRecoveryStatus.EXPIRED, NOW, 3);
        verify(passwordEncoder, never()).encode(any());
        verify(accounts, never()).updatePassword(any(), anyLong(), any(), any());
    }

    @Test
    void unknownTokenDoesNotLockOrReadAnAccount() {
        when(recoveries.findPendingByFingerprint(fingerprint)).thenReturn(Optional.empty());

        assertThat(service.complete(command())).isFalse();

        verify(accounts, never()).lockById(any());
        verify(recoveries, never()).lockPendingByFingerprint(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void nonactiveAccountCannotConsumeTheCredential() {
        when(recoveries.findPendingByFingerprint(fingerprint)).thenReturn(Optional.of(recovery));
        when(accounts.lockById(USER_ID)).thenReturn(Optional.of(new StaffPasswordRecoveryAccount(
                USER_ID, EMAIL, "Ada Lovelace", "{bcrypt}old-hash", StaffAccountStatus.SUSPENDED, 4)));
        when(recoveries.lockPendingByFingerprint(fingerprint)).thenReturn(Optional.of(recovery));

        assertThat(service.complete(command())).isFalse();

        verify(passwordEncoder, never()).matches(any(), any());
        verify(recoveries, never()).transition(any(), any(), any(), anyLong());
        verify(recoveries, never()).recordFailedAttempt(any(), any(), anyLong());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void passwordReuseIsRecordedAndCannotConsumeTheRecoveryToken() {
        when(recoveries.findPendingByFingerprint(fingerprint)).thenReturn(Optional.of(recovery));
        when(accounts.lockById(USER_ID)).thenReturn(Optional.of(account));
        when(recoveries.lockPendingByFingerprint(fingerprint)).thenReturn(Optional.of(recovery));
        when(passwordEncoder.matches(PASSWORD, "{bcrypt}old-hash")).thenReturn(true);

        assertThat(service.complete(command())).isFalse();

        verify(recoveries).recordFailedAttempt(RECOVERY_ID, NOW, 3);
        verify(accounts, never()).updatePassword(any(), anyLong(), any(), any());
        verify(recoveries, never()).transition(any(), any(), any(), anyLong());
        verify(passwordEncoder, never()).encode(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    private static CompletePasswordRecoveryCommand command() {
        return new CompletePasswordRecoveryCommand(TOKEN, PASSWORD, PASSWORD);
    }
}
