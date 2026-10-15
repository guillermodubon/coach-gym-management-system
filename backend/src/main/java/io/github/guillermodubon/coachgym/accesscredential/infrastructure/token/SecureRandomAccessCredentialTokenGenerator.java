package io.github.guillermodubon.coachgym.accesscredential.infrastructure.token;

import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialTokenGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Cryptographically strong, non-global generator for opaque credential tokens. */
@Component
class SecureRandomAccessCredentialTokenGenerator implements AccessCredentialTokenGenerator {

    static final int TOKEN_BYTES = 32;
    static final int ENCODED_LENGTH = 43;

    private final SecureRandom secureRandom;

    @Autowired
    SecureRandomAccessCredentialTokenGenerator() {
        this(new SecureRandom());
    }

    SecureRandomAccessCredentialTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "Secure random is required.");
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        try {
            secureRandom.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (token.length() != ENCODED_LENGTH || !token.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalStateException(
                        "Secure token generation produced an invalid value.");
            }
            return token;
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
