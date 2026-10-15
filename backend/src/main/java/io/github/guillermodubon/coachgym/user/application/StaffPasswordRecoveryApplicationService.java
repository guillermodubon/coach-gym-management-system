package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmailSender;
import io.github.guillermodubon.coachgym.user.CompletePasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.PasswordRecoveryPolicy;
import io.github.guillermodubon.coachgym.user.RequestPasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Non-enumerating application boundary; controller and OpenAPI work belongs to Block 7. */
@Service
public class StaffPasswordRecoveryApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            StaffPasswordRecoveryApplicationService.class);

    private final StaffIdentityAbuseService abuseControls;
    private final StaffPasswordRecoveryTransactionService transactions;
    private final StaffPasswordRecoveryEmailSender emailSender;

    public StaffPasswordRecoveryApplicationService(
            StaffIdentityAbuseService abuseControls,
            StaffPasswordRecoveryTransactionService transactions,
            StaffPasswordRecoveryEmailSender emailSender) {
        this.abuseControls = Objects.requireNonNull(abuseControls);
        this.transactions = Objects.requireNonNull(transactions);
        this.emailSender = Objects.requireNonNull(emailSender);
    }

    /** Returns the same exact acknowledgement for unknown, inactive, throttled, or eligible accounts. */
    public String requestRecovery(
            RequestPasswordRecoveryCommand command,
            String serverObservedClientAddress) {
        Objects.requireNonNull(command, "Password recovery request is required.");
        if (abuseControls.allowRecoveryRequest(command.email(), serverObservedClientAddress)) {
            transactions.request(command).ifPresent(message -> {
                try {
                    emailSender.sendPasswordRecoveryLink(message);
                } catch (RuntimeException deliveryFailure) {
                    LOGGER.warn("Password recovery email could not be queued.");
                }
            });
        }
        return PasswordRecoveryPolicy.GENERIC_PUBLIC_RESPONSE;
    }

    /** Completes once and maps all unavailable-token states to the same safe conflict. */
    public void completeRecovery(
            CompletePasswordRecoveryCommand command,
            String serverObservedClientAddress) {
        Objects.requireNonNull(command, "Password recovery completion is required.");
        String token = command.token();
        if (!abuseControls.recoveryAttemptAllowed(token, serverObservedClientAddress)) {
            throw unavailable();
        }
        boolean completed;
        try {
            completed = transactions.complete(command);
        } catch (StaffIdentityStateConflictException | StaffIdentityVersionConflictException conflict) {
            abuseControls.recordRecoveryFailure(token, serverObservedClientAddress);
            throw unavailable();
        }
        if (!completed) {
            abuseControls.recordRecoveryFailure(token, serverObservedClientAddress);
            throw unavailable();
        }
    }

    private static StaffIdentityStateConflictException unavailable() {
        return new StaffIdentityStateConflictException("Password recovery request is not available.");
    }
}
