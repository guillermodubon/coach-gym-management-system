package io.github.guillermodubon.coachgym.user;

import java.util.Base64;
import java.util.regex.Pattern;

/** Input bounds for opaque one-time staff identity tokens. */
public final class StaffTokenPolicy {

    public static final int TOKEN_LENGTH = 43;
    private static final Pattern URL_SAFE_UNPADDED_TOKEN =
            Pattern.compile("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}");

    private StaffTokenPolicy() {
    }

    /**
     * Validates the encoded shape only; this method does not authenticate,
     * fingerprint, hash, or persist a token.
     */
    public static String requirePresentedToken(String value) {
        if (value == null || !URL_SAFE_UNPADDED_TOKEN.matcher(value).matches()) {
            throw new StaffIdentityValidationException("One-time token is invalid.");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != 32
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
                throw new StaffIdentityValidationException("One-time token is invalid.");
            }
        } catch (IllegalArgumentException exception) {
            throw new StaffIdentityValidationException("One-time token is invalid.");
        }
        return value;
    }
}
