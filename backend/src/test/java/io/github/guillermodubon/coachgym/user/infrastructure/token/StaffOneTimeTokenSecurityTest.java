package io.github.guillermodubon.coachgym.user.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.StaffIdentityValidationException;
import io.github.guillermodubon.coachgym.user.StaffTokenPolicy;
import io.github.guillermodubon.coachgym.user.application.StaffTokenFingerprint;
import io.github.guillermodubon.coachgym.user.application.StaffTokenPurpose;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaffOneTimeTokenSecurityTest {

    private static final String CANONICAL_TEST_TOKEN = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(new byte[SecureRandomStaffOneTimeTokenGenerator.TOKEN_BYTES]);

    @Test
    void generatorReturnsDistinctCanonicalTokensWithTwoHundredFiftySixBits() {
        SecureRandomStaffOneTimeTokenGenerator generator =
                new SecureRandomStaffOneTimeTokenGenerator(new CounterSecureRandom());
        List<String> tokens = new ArrayList<>();

        for (int index = 0; index < 64; index++) {
            String token = generator.generate();
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
            assertThat(decoded).hasSize(32);
            assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded))
                    .isEqualTo(token);
            tokens.add(token);
            Arrays.fill(decoded, (byte) 0);
        }

        assertThat(new HashSet<>(tokens)).hasSize(tokens.size());
    }

    @Test
    void protectorMatchesKnownVectorAndSeparatesTokenPurposes() {
        Sha256StaffTokenProtector protector = new Sha256StaffTokenProtector();

        StaffTokenFingerprint invitation = protector.fingerprint(
                CANONICAL_TEST_TOKEN, StaffTokenPurpose.INVITATION);
        StaffTokenFingerprint recovery = protector.fingerprint(
                CANONICAL_TEST_TOKEN, StaffTokenPurpose.PASSWORD_RECOVERY);

        assertThat(invitation.value())
                .isEqualTo("ccb7dfe4eaafc4cc7d8c0a0c51650cfe1937379c184049360645c065b106acf0");
        assertThat(invitation.schemeVersion())
                .isEqualTo("staff-invitation-sha256-v1");
        assertThat(recovery.value()).isNotEqualTo(invitation.value());
        assertThat(protector.matches(CANONICAL_TEST_TOKEN, invitation, StaffTokenPurpose.INVITATION)).isTrue();
        assertThat(protector.matches(CANONICAL_TEST_TOKEN, recovery, StaffTokenPurpose.INVITATION)).isFalse();
        assertThat(protector.matches(nonCanonicalToken(), invitation,
                StaffTokenPurpose.INVITATION)).isFalse();
    }

    @Test
    void tokenMaterialIsRedactedAndNonCanonicalEncodingIsRejected() {
        Sha256StaffTokenProtector protector = new Sha256StaffTokenProtector();
        StaffTokenFingerprint fingerprint = protector.fingerprint(
                CANONICAL_TEST_TOKEN, StaffTokenPurpose.INVITATION);

        assertThat(fingerprint.toString()).doesNotContain(fingerprint.value());
        String invalidToken = nonCanonicalToken();
        assertThatThrownBy(() -> StaffTokenPolicy.requirePresentedToken(invalidToken))
                .isInstanceOf(StaffIdentityValidationException.class)
                .hasMessageNotContaining(invalidToken);
    }

    private static String nonCanonicalToken() {
        return CANONICAL_TEST_TOKEN.substring(0, StaffTokenPolicy.TOKEN_LENGTH - 1) + "B";
    }

    private static final class CounterSecureRandom extends SecureRandom {

        private int value;

        @Override
        public void nextBytes(byte[] bytes) {
            int invocation = value++;
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) ((invocation * 37 + index) & 0xff);
            }
        }
    }
}
