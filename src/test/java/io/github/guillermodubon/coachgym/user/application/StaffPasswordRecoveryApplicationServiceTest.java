package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmailSender;
import io.github.guillermodubon.coachgym.user.CompletePasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.PasswordRecoveryPolicy;
import io.github.guillermodubon.coachgym.user.RequestPasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaffPasswordRecoveryApplicationServiceTest {

    private static final String EMAIL = "staff@example.test";
    private static final String TOKEN = "A".repeat(43);
    private static final String PASSWORD = "new-long-test-password-123";
    private static final String ADDRESS = "127.0.0.1";

    private StaffIdentityAbuseService abuse;
    private StaffPasswordRecoveryTransactionService transactions;
    private StaffPasswordRecoveryEmailSender sender;
    private StaffPasswordRecoveryApplicationService service;

    @BeforeEach
    void setUp() {
        abuse = org.mockito.Mockito.mock(StaffIdentityAbuseService.class);
        transactions = org.mockito.Mockito.mock(StaffPasswordRecoveryTransactionService.class);
        sender = org.mockito.Mockito.mock(StaffPasswordRecoveryEmailSender.class);
        service = new StaffPasswordRecoveryApplicationService(abuse, transactions, sender);
        when(abuse.allowRecoveryRequest(EMAIL, ADDRESS)).thenReturn(true);
    }

    @Test
    void unknownAndEligibleEmailsReceiveExactlyTheSameAcknowledgement() {
        when(transactions.request(new RequestPasswordRecoveryCommand(EMAIL)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new StaffPasswordRecoveryEmail(
                        EMAIL, "Ada Lovelace", TOKEN, Instant.parse("2026-09-25T12:15:00Z"))));

        String unknown = service.requestRecovery(new RequestPasswordRecoveryCommand(EMAIL), ADDRESS);
        String eligible = service.requestRecovery(new RequestPasswordRecoveryCommand(EMAIL), ADDRESS);

        assertThat(unknown).isEqualTo(PasswordRecoveryPolicy.GENERIC_PUBLIC_RESPONSE);
        assertThat(eligible).isEqualTo(unknown);
        verify(sender).sendPasswordRecoveryLink(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void throttlingAndEmailQueueFailureDoNotChangeTheGenericAcknowledgement() {
        when(abuse.allowRecoveryRequest(EMAIL, ADDRESS)).thenReturn(false, true);
        when(transactions.request(new RequestPasswordRecoveryCommand(EMAIL)))
                .thenReturn(Optional.of(new StaffPasswordRecoveryEmail(
                        EMAIL, "Ada Lovelace", TOKEN, Instant.parse("2026-09-25T12:15:00Z"))));
        doThrow(new IllegalStateException("secret delivery details"))
                .when(sender).sendPasswordRecoveryLink(org.mockito.ArgumentMatchers.any());

        String throttled = service.requestRecovery(new RequestPasswordRecoveryCommand(EMAIL), ADDRESS);
        String failedDelivery = service.requestRecovery(new RequestPasswordRecoveryCommand(EMAIL), ADDRESS);

        assertThat(throttled).isEqualTo(PasswordRecoveryPolicy.GENERIC_PUBLIC_RESPONSE);
        assertThat(failedDelivery).isEqualTo(throttled);
        verify(transactions, org.mockito.Mockito.times(1))
                .request(new RequestPasswordRecoveryCommand(EMAIL));
    }

    @Test
    void invalidOrExpiredCompletionUsesOneSafeConflictAndRecordsTheAttempt() {
        when(abuse.recoveryAttemptAllowed(TOKEN, ADDRESS)).thenReturn(true);
        when(transactions.complete(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        assertThatThrownBy(() -> service.completeRecovery(command(), ADDRESS))
                .isInstanceOf(StaffIdentityStateConflictException.class)
                .hasMessage("Password recovery request is not available.")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining(PASSWORD);

        verify(abuse).recordRecoveryFailure(TOKEN, ADDRESS);
    }

    @Test
    void alreadyThrottledCompletionDoesNotPerformTokenOrPasswordWork() {
        when(abuse.recoveryAttemptAllowed(TOKEN, ADDRESS)).thenReturn(false);

        assertThatThrownBy(() -> service.completeRecovery(command(), ADDRESS))
                .isInstanceOf(StaffIdentityStateConflictException.class)
                .hasMessage("Password recovery request is not available.");

        verify(transactions, never()).complete(org.mockito.ArgumentMatchers.any());
        verify(abuse, never()).recordRecoveryFailure(TOKEN, ADDRESS);
    }

    private static CompletePasswordRecoveryCommand command() {
        return new CompletePasswordRecoveryCommand(TOKEN, PASSWORD, PASSWORD);
    }
}
