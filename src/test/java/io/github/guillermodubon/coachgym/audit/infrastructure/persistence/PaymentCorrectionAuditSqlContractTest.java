package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentCorrectionAuditSqlContractTest {

    @Test
    void auditPersistenceUsesParameterizedJsonbAndSafeActions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/audit/"
                        + "infrastructure/persistence/AuditEntryJpaRepository.java"))
                .toLowerCase(Locale.ROOT);

        assertThat(source)
                .contains("payment_voided")
                .contains("payment_refunded")
                .contains("cast(:metadata as jsonb)")
                .contains("externalreferencepresent")
                .doesNotContain("event.externalreference()")
                .doesNotContain("event.reason()")
                .doesNotContain("com.stripe");
    }
}
