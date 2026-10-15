package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.shared.security.StaffIdentityAbuseLimits;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaffIdentityAbuseServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final String TOKEN = "A".repeat(43);

    private StaffIdentityAbuseStore store;
    private StaffTokenProtector tokenProtector;
    private StaffIdentityAbuseService service;
    private StaffTokenFingerprint fingerprint;

    @BeforeEach
    void setUp() {
        store = mock(StaffIdentityAbuseStore.class);
        tokenProtector = mock(StaffTokenProtector.class);
        fingerprint = new StaffTokenFingerprint(
                "a".repeat(64), StaffTokenPurpose.INVITATION.schemeVersion());
        when(tokenProtector.fingerprint(TOKEN, StaffTokenPurpose.INVITATION)).thenReturn(fingerprint);
        when(tokenProtector.fingerprint(TOKEN, StaffTokenPurpose.PASSWORD_RECOVERY))
                .thenReturn(new StaffTokenFingerprint(
                        "b".repeat(64), StaffTokenPurpose.PASSWORD_RECOVERY.schemeVersion()));
        service = new StaffIdentityAbuseService(
                store,
                StaffIdentityAbuseLimits.defaults(),
                tokenProtector,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recoveryRequestsConsumeEmailIpAndGlobalWindowsBeforeAccountLookup() {
        when(store.consume(any(), any(), anyInt(), any(), eq(NOW)))
                .thenReturn(true, false, true);

        assertThat(service.allowRecoveryRequest("member@example.test", "127.0.0.1")).isFalse();

        verify(store).consume(
                StaffIdentityAbuseBucket.RECOVERY_EMAIL_REQUEST,
                "member@example.test", 3, java.time.Duration.ofHours(1), NOW);
        verify(store).consume(
                StaffIdentityAbuseBucket.RECOVERY_IP_REQUEST,
                "127.0.0.1", 10, java.time.Duration.ofMinutes(15), NOW);
        verify(store).consume(
                StaffIdentityAbuseBucket.RECOVERY_GLOBAL_REQUEST,
                "GLOBAL", 100, java.time.Duration.ofHours(1), NOW);
    }

    @Test
    void invitationTokenAndObservedIpAreCheckedWithoutCountingSuccessfulInspections() {
        when(store.isAllowed(
                StaffIdentityAbuseBucket.INVITATION_IP_FAILURE,
                "127.0.0.1", 10, NOW)).thenReturn(true);
        when(store.isAllowed(
                StaffIdentityAbuseBucket.INVITATION_TOKEN_FAILURE,
                fingerprint.value(), 5, NOW)).thenReturn(false);

        assertThat(service.invitationAttemptAllowed(TOKEN, "127.0.0.1")).isFalse();

        verify(store).isAllowed(
                StaffIdentityAbuseBucket.INVITATION_TOKEN_FAILURE,
                fingerprint.value(), 5, NOW);
    }

    @Test
    void malformedRemoteAddressFailsIntoOneSharedBucketInsteadOfTrustingForwardedData() {
        when(store.consume(any(), any(), anyInt(), any(), eq(NOW))).thenReturn(true);

        assertThat(service.allowRecoveryRequest("member@example.test", "forged.example, 1.2.3.4"))
                .isTrue();

        verify(store).consume(
                eq(StaffIdentityAbuseBucket.RECOVERY_IP_REQUEST),
                eq("unresolved-client"),
                eq(10),
                eq(java.time.Duration.ofMinutes(15)),
                eq(NOW));
    }

    @Test
    void recordingTokenFailuresStoresOnlyFingerprintAndCanonicalIpKeys() {
        service.recordInvitationFailure(TOKEN, "127.0.0.1");

        verify(store).recordFailure(
                StaffIdentityAbuseBucket.INVITATION_IP_FAILURE,
                "127.0.0.1", 10, java.time.Duration.ofMinutes(15), NOW);
        verify(store).recordFailure(
                StaffIdentityAbuseBucket.INVITATION_TOKEN_FAILURE,
                fingerprint.value(), 5, java.time.Duration.ofHours(48), NOW);
    }
}
