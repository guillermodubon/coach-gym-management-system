package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialTokenProtector;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialQuery;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AccessCredentialResolverAdapterTest {

    private static final String TOKEN =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String PAYLOAD = "cgac:v1:" + TOKEN;
    private static final String FINGERPRINT = "a".repeat(64);
    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000911");
    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000912");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000913");
    private static final Instant ISSUED_AT = Instant.parse(
            "2026-09-14T16:00:00Z");

    @Mock
    private AccessCredentialQuery credentialQuery;

    @Mock
    private AccessCredentialTokenProtector tokenProtector;

    private AccessCredentialResolverAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccessCredentialResolverAdapter(
                credentialQuery,
                tokenProtector);
    }

    @Test
    void resolvesAnActiveCredentialUsingTheIssuanceFingerprintContract() {
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.parse(PAYLOAD);
        when(tokenProtector.fingerprint(PAYLOAD)).thenReturn(FINGERPRINT);
        when(credentialQuery.findActiveByTokenFingerprint(FINGERPRINT))
                .thenReturn(Optional.of(activeDetails()));

        assertThat(adapter.resolve(payload))
                .hasValueSatisfying(resolved -> {
                    assertThat(resolved.credentialId()).isEqualTo(CREDENTIAL_ID);
                    assertThat(resolved.clientId()).isEqualTo(CLIENT_ID);
                    assertThat(resolved.status()).isEqualTo(AccessCredentialStatus.ACTIVE);
                    assertThat(resolved.toString())
                            .doesNotContain(TOKEN, PAYLOAD, FINGERPRINT);
                });
        verify(tokenProtector).fingerprint(eq(PAYLOAD));
        verify(credentialQuery).findActiveByTokenFingerprint(eq(FINGERPRINT));
    }

    @Test
    void unknownCredentialReturnsAnEmptySafeResult() {
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.parse(PAYLOAD);
        when(tokenProtector.fingerprint(PAYLOAD)).thenReturn(FINGERPRINT);
        when(credentialQuery.findActiveByTokenFingerprint(FINGERPRINT))
                .thenReturn(Optional.empty());

        assertThat(adapter.resolve(payload)).isEmpty();
    }

    @Test
    void resolvesAndLocksAnActiveCredentialForTransactionalCheckIn() {
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.parse(PAYLOAD);
        when(tokenProtector.fingerprint(PAYLOAD)).thenReturn(FINGERPRINT);
        when(credentialQuery.findActiveByTokenFingerprintForUpdate(FINGERPRINT))
                .thenReturn(Optional.of(activeDetails()));

        assertThat(adapter.resolveAndLock(payload))
                .hasValueSatisfying(resolved -> {
                    assertThat(resolved.credentialId()).isEqualTo(CREDENTIAL_ID);
                    assertThat(resolved.clientId()).isEqualTo(CLIENT_ID);
                });
        verify(credentialQuery)
                .findActiveByTokenFingerprintForUpdate(FINGERPRINT);
    }

    @Test
    void revokedCredentialCannotCrossTheResolverBoundary() {
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.parse(PAYLOAD);
        when(tokenProtector.fingerprint(PAYLOAD)).thenReturn(FINGERPRINT);
        when(credentialQuery.findActiveByTokenFingerprint(FINGERPRINT))
                .thenReturn(Optional.of(revokedDetails(null)));

        assertThat(adapter.resolve(payload)).isEmpty();
    }

    @Test
    void replacedCredentialCannotCrossTheResolverBoundary() {
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.parse(PAYLOAD);
        when(tokenProtector.fingerprint(PAYLOAD)).thenReturn(FINGERPRINT);
        when(credentialQuery.findActiveByTokenFingerprint(FINGERPRINT))
                .thenReturn(Optional.of(revokedDetails(UUID.randomUUID())));

        assertThat(adapter.resolve(payload)).isEmpty();
    }

    @Test
    void nullOrMalformedPayloadNeverReachesPersistence() {
        assertThat(adapter.resolve(null)).isEmpty();
        verifyNoInteractions(tokenProtector, credentialQuery);

        AccessCredentialQrPayload malformed = mock(AccessCredentialQrPayload.class);
        when(malformed.value()).thenReturn("malformed");
        when(tokenProtector.fingerprint("malformed"))
                .thenThrow(new IllegalArgumentException("invalid payload"));

        assertThat(adapter.resolve(malformed)).isEmpty();
        verifyNoInteractions(credentialQuery);
    }

    @Test
    void invalidProtectedRepresentationNeverReachesPersistence() {
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.parse(PAYLOAD);
        when(tokenProtector.fingerprint(PAYLOAD)).thenReturn("not-a-fingerprint");

        assertThat(adapter.resolve(payload)).isEmpty();
        verifyNoInteractions(credentialQuery);
    }

    @Test
    void resolutionUsesAReadOnlyTransaction() throws Exception {
        Method method = AccessCredentialResolverAdapter.class.getMethod(
                "resolve", AccessCredentialQrPayload.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void lockedResolutionUsesAWriteCapableTransaction() throws Exception {
        Method method = AccessCredentialResolverAdapter.class.getMethod(
                "resolveAndLock", AccessCredentialQrPayload.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private static AccessCredentialDetails activeDetails() {
        return new AccessCredentialDetails(
                CREDENTIAL_ID,
                CLIENT_ID,
                "AC-RESOLUTION-001",
                AccessCredentialStatus.ACTIVE,
                "v1",
                ISSUED_AT,
                ACTOR_ID,
                null,
                null,
                null,
                0);
    }

    private static AccessCredentialDetails revokedDetails(UUID replacementId) {
        return new AccessCredentialDetails(
                CREDENTIAL_ID,
                CLIENT_ID,
                "AC-RESOLUTION-001",
                AccessCredentialStatus.REVOKED,
                "v1",
                ISSUED_AT,
                ACTOR_ID,
                ISSUED_AT.plusSeconds(60),
                ACTOR_ID,
                replacementId,
                1);
    }
}
