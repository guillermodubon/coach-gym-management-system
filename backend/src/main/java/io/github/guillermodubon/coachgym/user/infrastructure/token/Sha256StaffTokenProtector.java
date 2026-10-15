package io.github.guillermodubon.coachgym.user.infrastructure.token;

import io.github.guillermodubon.coachgym.user.StaffTokenPolicy;
import io.github.guillermodubon.coachgym.user.application.StaffTokenFingerprint;
import io.github.guillermodubon.coachgym.user.application.StaffTokenProtector;
import io.github.guillermodubon.coachgym.user.application.StaffTokenPurpose;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Purpose-separated SHA-256 fingerprints for high-entropy one-time tokens. */
@Component
class Sha256StaffTokenProtector implements StaffTokenProtector {

    @Override
    public StaffTokenFingerprint fingerprint(String token, StaffTokenPurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("One-time token purpose is required.");
        }
        String canonicalToken = StaffTokenPolicy.requirePresentedToken(token);
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest((purpose.domainSeparator() + canonicalToken)
                            .getBytes(StandardCharsets.US_ASCII));
            return new StaffTokenFingerprint(HexFormat.of().formatHex(value), purpose.schemeVersion());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Staff token protection is unavailable.");
        }
    }

    @Override
    public boolean matches(
            String token,
            StaffTokenFingerprint expected,
            StaffTokenPurpose purpose) {
        if (expected == null || purpose == null
                || !purpose.schemeVersion().equals(expected.schemeVersion())) {
            return false;
        }
        try {
            String actual = fingerprint(token, purpose).value();
            return MessageDigest.isEqual(
                    actual.getBytes(StandardCharsets.US_ASCII),
                    expected.value().getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
