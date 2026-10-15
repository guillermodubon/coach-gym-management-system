package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptNumberGenerator;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates opaque, unique receipt numbers without exposing database identifiers. */
@Component
class UuidPaymentReceiptNumberGenerator implements PaymentReceiptNumberGenerator {

    private static final String PREFIX = "REC-";

    @Override
    public String next() {
        return PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 28)
                .toUpperCase(Locale.ROOT);
    }
}
