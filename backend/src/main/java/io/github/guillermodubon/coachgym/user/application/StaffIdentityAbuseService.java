package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.security.StaffIdentityAbuseLimits;
import io.github.guillermodubon.coachgym.user.StaffIdentityValidationException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Coordinates bounded, database-backed counters without logging their subjects. */
@Service
public class StaffIdentityAbuseService {

    private static final String GLOBAL_SUBJECT = "GLOBAL";
    private static final String UNRESOLVED_CLIENT = "unresolved-client";

    private final StaffIdentityAbuseStore store;
    private final StaffIdentityAbuseLimits limits;
    private final StaffTokenProtector tokenProtector;
    private final Clock clock;

    public StaffIdentityAbuseService(
            StaffIdentityAbuseStore store,
            StaffIdentityAbuseLimits limits,
            StaffTokenProtector tokenProtector,
            Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.limits = Objects.requireNonNull(limits);
        this.tokenProtector = Objects.requireNonNull(tokenProtector);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Counts every request before account lookup, including unknown or ineligible emails. */
    public boolean allowRecoveryRequest(String normalizedEmail, String serverObservedAddress) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new StaffIdentityValidationException("Recovery request identity is invalid.");
        }
        Instant now = clock.instant();
        String clientAddress = clientAddressKey(serverObservedAddress);
        boolean emailAllowed = store.consume(
                StaffIdentityAbuseBucket.RECOVERY_EMAIL_REQUEST,
                normalizedEmail,
                limits.maxRecoveryRequestsPerEmailPerHour(),
                limits.recoveryEmailRequestWindow(),
                now);
        boolean ipAllowed = store.consume(
                StaffIdentityAbuseBucket.RECOVERY_IP_REQUEST,
                clientAddress,
                limits.maxRecoveryRequestsPerIpPer15Minutes(),
                limits.recoveryRequestIpWindow(),
                now);
        boolean globalAllowed = store.consume(
                StaffIdentityAbuseBucket.RECOVERY_GLOBAL_REQUEST,
                GLOBAL_SUBJECT,
                limits.maxRecoveryRequestsGloballyPerHour(),
                limits.recoveryGlobalWindow(),
                now);
        return emailAllowed && ipAllowed && globalAllowed;
    }

    public boolean invitationAttemptAllowed(String presentedToken, String serverObservedAddress) {
        Instant now = clock.instant();
        String address = clientAddressKey(serverObservedAddress);
        if (!store.isAllowed(
                StaffIdentityAbuseBucket.INVITATION_IP_FAILURE,
                address,
                limits.maxInvitationTokenFailuresPerIpPer15Minutes(),
                now)) {
            return false;
        }
        return fingerprint(presentedToken, StaffTokenPurpose.INVITATION)
                .map(value -> store.isAllowed(
                        StaffIdentityAbuseBucket.INVITATION_TOKEN_FAILURE,
                        value,
                        limits.maxTokenFailuresPerFingerprint(),
                        now))
                .orElse(true);
    }

    public void recordInvitationFailure(String presentedToken, String serverObservedAddress) {
        Instant now = clock.instant();
        store.recordFailure(
                StaffIdentityAbuseBucket.INVITATION_IP_FAILURE,
                clientAddressKey(serverObservedAddress),
                limits.maxInvitationTokenFailuresPerIpPer15Minutes(),
                limits.tokenIpFailureWindow(),
                now);
        fingerprint(presentedToken, StaffTokenPurpose.INVITATION).ifPresent(value ->
                store.recordFailure(
                        StaffIdentityAbuseBucket.INVITATION_TOKEN_FAILURE,
                        value,
                        limits.maxTokenFailuresPerFingerprint(),
                        limits.tokenFingerprintFailureWindow(),
                        now));
    }

    public boolean recoveryAttemptAllowed(String presentedToken, String serverObservedAddress) {
        Instant now = clock.instant();
        String address = clientAddressKey(serverObservedAddress);
        if (!store.isAllowed(
                StaffIdentityAbuseBucket.RECOVERY_IP_FAILURE,
                address,
                limits.maxRecoveryTokenFailuresPerIpPer15Minutes(),
                now)) {
            return false;
        }
        return fingerprint(presentedToken, StaffTokenPurpose.PASSWORD_RECOVERY)
                .map(value -> store.isAllowed(
                        StaffIdentityAbuseBucket.RECOVERY_TOKEN_FAILURE,
                        value,
                        limits.maxTokenFailuresPerFingerprint(),
                        now))
                .orElse(false);
    }

    public void recordRecoveryFailure(String presentedToken, String serverObservedAddress) {
        Instant now = clock.instant();
        store.recordFailure(
                StaffIdentityAbuseBucket.RECOVERY_IP_FAILURE,
                clientAddressKey(serverObservedAddress),
                limits.maxRecoveryTokenFailuresPerIpPer15Minutes(),
                limits.tokenIpFailureWindow(),
                now);
        fingerprint(presentedToken, StaffTokenPurpose.PASSWORD_RECOVERY).ifPresent(value ->
                store.recordFailure(
                        StaffIdentityAbuseBucket.RECOVERY_TOKEN_FAILURE,
                        value,
                        limits.maxTokenFailuresPerFingerprint(),
                        limits.tokenFingerprintFailureWindow(),
                        now));
    }

    private Optional<String> fingerprint(String token, StaffTokenPurpose purpose) {
        try {
            String normalized = io.github.guillermodubon.coachgym.user.StaffTokenPolicy
                    .requirePresentedToken(token);
            return Optional.of(tokenProtector.fingerprint(normalized, purpose).value());
        } catch (RuntimeException invalidToken) {
            return Optional.empty();
        }
    }

    private static String clientAddressKey(String address) {
        if (address == null || address.isBlank() || address.length() > 64) {
            return UNRESOLVED_CLIENT;
        }
        String value = address.strip();
        try {
            if (value.indexOf(':') >= 0 && value.matches("[0-9A-Fa-f:.]+")) {
                InetAddress parsed = InetAddress.getByName(value);
                if (!(parsed instanceof Inet6Address)) {
                    return UNRESOLVED_CLIENT;
                }
                return parsed.getHostAddress().toLowerCase(java.util.Locale.ROOT);
            }
            if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
                return UNRESOLVED_CLIENT;
            }
            InetAddress parsed = InetAddress.getByName(value);
            byte[] octets = parsed.getAddress();
            if (octets.length != 4) {
                return UNRESOLVED_CLIENT;
            }
            return (octets[0] & 0xff) + "." + (octets[1] & 0xff) + "."
                    + (octets[2] & 0xff) + "." + (octets[3] & 0xff);
        } catch (UnknownHostException invalidAddress) {
            return UNRESOLVED_CLIENT;
        }
    }
}
