package io.github.guillermodubon.coachgym.payment.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPaymentReceiptStorageTest {

    @TempDir
    Path directory;

    @Test
    void generatesSafeKeyStoresReadsChecksumAndDeletesAtomically() throws Exception {
        LocalPaymentReceiptStorage storage = storage();
        UUID receiptId = UUID.fromString("00000000-0000-0000-0000-000000000931");
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII));

        String key = storage.generateStorageKey(receiptId);
        storage.store(key, document);

        assertThat(key).isEqualTo("receipts/00000000-0000-0000-0000-000000000931.pdf");
        assertThat(storage.load(key, document.contentType(), document.checksumSha256()).bytes())
                .containsExactly(document.bytes());
        assertThat(Files.exists(directory.resolve(key))).isTrue();
        storage.delete(key);
        assertThat(Files.exists(directory.resolve(key))).isFalse();
    }

    @Test
    void rejectsTraversalNonPdfKeysAndMissingDocumentsWithoutExposingPaths() {
        LocalPaymentReceiptStorage storage = storage();
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> storage.store("../outside.pdf", document))
                .isInstanceOf(PaymentReceiptStorageException.class)
                .hasMessage("Invalid payment receipt storage key.");
        assertThatThrownBy(() -> storage.store("receipts/file.txt", document))
                .isInstanceOf(PaymentReceiptStorageException.class)
                .hasMessageContaining("must be a PDF");
        assertThatThrownBy(() -> storage.load(
                "receipts/missing.pdf", document.contentType(), document.checksumSha256()))
                .isInstanceOf(PaymentReceiptStorageException.class)
                .hasMessage("Payment receipt document could not be found.")
                .hasMessageNotContaining(directory.toString());
    }

    @Test
    void rejectsChecksumMismatchAndCleansTemporaryFiles() throws Exception {
        LocalPaymentReceiptStorage storage = storage();
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        String key = storage.generateStorageKey(UUID.randomUUID());
        storage.store(key, document);

        assertThatThrownBy(() -> storage.load(key, document.contentType(), "0".repeat(64)))
                .isInstanceOf(PaymentReceiptStorageException.class)
                .hasMessage("Stored payment receipt document is invalid.");
        try (var paths = Files.walk(directory)) {
            assertThat(paths.noneMatch(path -> path.getFileName().toString().startsWith(".receipt-")))
                    .isTrue();
        }
    }

    @Test
    void rejectsOversizedStoredFilesBeforeReadingThem() throws Exception {
        LocalPaymentReceiptStorage storage = storage();
        String key = storage.generateStorageKey(UUID.randomUUID());
        Path file = directory.resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[(int) PaymentReceiptDocument.MAX_SIZE_BYTES + 1]);

        assertThatThrownBy(() -> storage.load(
                key, "application/pdf", "0".repeat(64)))
                .isInstanceOf(PaymentReceiptStorageException.class)
                .hasMessage("Stored payment receipt document size is invalid.");
    }

    private LocalPaymentReceiptStorage storage() {
        PaymentReceiptStorageProperties properties = new PaymentReceiptStorageProperties();
        properties.setDirectory(directory);
        return new LocalPaymentReceiptStorage(properties);
    }
}
