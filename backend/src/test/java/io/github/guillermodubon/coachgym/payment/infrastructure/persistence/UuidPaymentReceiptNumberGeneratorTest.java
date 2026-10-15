package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UuidPaymentReceiptNumberGeneratorTest {

    @Test
    void generatesOpaqueUniqueNumbersWithinDatabaseLimit() {
        UuidPaymentReceiptNumberGenerator generator = new UuidPaymentReceiptNumberGenerator();
        Set<String> numbers = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            String number = generator.next();
            assertThat(number).startsWith("REC-");
            assertThat(number).hasSize(32);
            assertThat(number).matches("REC-[0-9A-F]{28}");
            numbers.add(number);
        }

        assertThat(numbers).hasSize(100);
    }
}
