package io.github.guillermodubon.coachgym.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessIdentifierType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QrAccessCheckInResultTest {

    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-15T20:00:00Z");

    @Test
    void exposesOnlyTheSafeQrDecisionProjection() {
        QrAccessCheckInResult result = allowedResult();

        assertThat(result.identificationSource())
                .isEqualTo(AccessIdentifierType.QR_CREDENTIAL);
        assertThat(result.credentialId()).isEqualTo(CREDENTIAL_ID);
        assertThat(result.clientId()).isEqualTo(CLIENT_ID);
        assertThat(result.membershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(result.membershipPeriodId()).isEqualTo(PERIOD_ID);
        assertThat(result.toString())
                .doesNotContain("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                .doesNotContain("cgac:v1:");
    }

    @Test
    void requiresTheQrSourceAndAnAuthoritativeClient() {
        assertThatThrownBy(() -> new QrAccessCheckInResult(
                CREDENTIAL_ID,
                AccessIdentifierType.CLIENT_CODE,
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                PERIOD_ID,
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                "Membership is active.",
                EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("QR results must use QR_CREDENTIAL as their source.");

        assertThatThrownBy(() -> new QrAccessCheckInResult(
                CREDENTIAL_ID,
                AccessIdentifierType.QR_CREDENTIAL,
                null,
                null,
                null,
                null,
                null,
                AccessResult.DENIED,
                AccessReasonCode.MEMBERSHIP_NOT_FOUND,
                "No current membership was found.",
                EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resolved client identifier must be provided.");
    }

    private static QrAccessCheckInResult allowedResult() {
        return new QrAccessCheckInResult(
                CREDENTIAL_ID,
                AccessIdentifierType.QR_CREDENTIAL,
                CLIENT_ID,
                " CLI-000001 ",
                MEMBERSHIP_ID,
                " MEM-000001 ",
                PERIOD_ID,
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                "Membership is active.",
                EVALUATED_AT);
    }
}
