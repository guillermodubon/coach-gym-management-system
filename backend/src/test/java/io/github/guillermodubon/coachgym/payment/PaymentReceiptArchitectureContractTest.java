package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptOrganizationQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptRenderer;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptSnapshotQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorage;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentReceiptArchitectureContractTest {

    @Test
    void receiptEventRemainsAnImmutablePublicPaymentContract() {
        assertThat(PaymentReceiptGenerated.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.payment");
        assertThat(PaymentReceiptGenerated.class.isRecord()).isTrue();

        String components = Arrays.stream(PaymentReceiptGenerated.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
        assertThat(components)
                .doesNotContain("bytes", "storage", "path", "secret", "stripe",
                        "cardnumber", "cvc", "pin", "password", "payload");
    }

    @Test
    void receiptTechnologyBoundariesRemainApplicationPorts() {
        assertThat(List.of(
                PaymentReceiptStore.class,
                PaymentReceiptQuery.class,
                PaymentReceiptSnapshotQuery.class,
                PaymentReceiptOrganizationQuery.class,
                PaymentReceiptRenderer.class,
                PaymentReceiptStorage.class))
                .allSatisfy(type -> assertThat(type.isInterface()).isTrue());
    }

    @Test
    void pdfLibraryTypesStayInsideThePdfInfrastructureAdapter() throws Exception {
        Path paymentRoot = Path.of("src/main/java/io/github/guillermodubon/coachgym/payment");
        List<Path> productionFiles;
        try (var paths = Files.walk(paymentRoot)) {
            productionFiles = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        String outsidePdfInfrastructure = productionFiles.stream()
                .filter(path -> !path.toString().replace('\\', '/')
                        .contains("/payment/infrastructure/pdf/"))
                .map(PaymentReceiptArchitectureContractTest::read)
                .collect(Collectors.joining("\n"))
                .toLowerCase(Locale.ROOT);

        assertThat(outsidePdfInfrastructure)
                .doesNotContain("import org.apache.pdfbox", "pdfbox");
    }

    @Test
    void receiptControllerDoesNotImportInfrastructureAdapters() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/payment/web/PaymentReceiptController.java"));

        assertThat(controller)
                .doesNotContain("payment.infrastructure", "LocalPaymentReceiptStorage",
                        "PdfBoxPaymentReceiptRenderer", "NamedParameterJdbcTemplate");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
