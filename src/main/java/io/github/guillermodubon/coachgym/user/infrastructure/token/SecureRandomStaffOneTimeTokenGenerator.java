package io.github.guillermodubon.coachgym.user.infrastructure.token;

import io.github.guillermodubon.coachgym.user.StaffTokenPolicy;
import io.github.guillermodubon.coachgym.user.application.StaffOneTimeTokenGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.security.SecureRandom;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Generates 256-bit opaque tokens using a fresh per-instance secure random source. */
@Component
class SecureRandomStaffOneTimeTokenGenerator implements StaffOneTimeTokenGenerator {

    static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    @Autowired
    SecureRandomStaffOneTimeTokenGenerator() {
        this(new SecureRandom());
    }

    SecureRandomStaffOneTimeTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "Secure random is required.");
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        try {
            secureRandom.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            return StaffTokenPolicy.requirePresentedToken(token);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
