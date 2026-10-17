package io.github.guillermodubon.coachgym.payment.infrastructure.storage;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorage;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageKey;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "gym.storage", name = "provider", havingValue = "local", matchIfMissing = true)
class LocalPaymentReceiptStorage implements PaymentReceiptStorage {

    private final Path root;

    LocalPaymentReceiptStorage(PaymentReceiptStorageProperties properties) {
        this.root = Objects.requireNonNull(properties)
                .getDirectory()
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public String generateStorageKey(UUID receiptId) {
        if (receiptId == null) {
            throw new PaymentReceiptStorageException("Receipt id is required for storage.");
        }
        return PaymentReceiptStorageKey.forReceipt(receiptId);
    }

    @Override
    public void store(String storageKey, PaymentReceiptDocument document) {
        Objects.requireNonNull(document, "Receipt document is required.");
        requirePdfSignature(document.bytes());
        Path target = resolve(storageKey);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".receipt-", ".tmp");
            Files.write(temporary, document.bytes());
            try {
                Files.move(temporary, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw new PaymentReceiptStorageException(
                    "Payment receipt document could not be stored.", exception);
        }
    }

    @Override
    public PaymentReceiptDocument load(
            String storageKey,
            String contentType,
            String checksumSha256) {
        Path source = resolve(storageKey);
        try {
            long size = Files.size(source);
            if (size < 1 || size > PaymentReceiptDocument.MAX_SIZE_BYTES) {
                throw new PaymentReceiptStorageException(
                        "Stored payment receipt document size is invalid.");
            }
            byte[] bytes = Files.readAllBytes(source);
            requirePdfSignature(bytes);
            return new PaymentReceiptDocument(contentType, bytes, checksumSha256);
        } catch (NoSuchFileException exception) {
            throw new PaymentReceiptStorageException(
                    "Payment receipt document could not be found.");
        } catch (PaymentReceiptStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PaymentReceiptStorageException(
                    "Payment receipt document could not be read.", exception);
        } catch (IllegalArgumentException exception) {
            throw new PaymentReceiptStorageException(
                    "Stored payment receipt document is invalid.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new PaymentReceiptStorageException(
                    "Payment receipt document could not be deleted.", exception);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new PaymentReceiptStorageException("Payment receipt storage key is required.");
        }
        String normalizedKey = storageKey.strip();
        if (!normalizedKey.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw new PaymentReceiptStorageException("Payment receipt storage key must be a PDF.");
        }
        Path resolved = root.resolve(normalizedKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new PaymentReceiptStorageException("Invalid payment receipt storage key.");
        }
        return resolved;
    }

    private static void requirePdfSignature(byte[] bytes) {
        if (bytes == null || bytes.length < 5
                || bytes[0] != '%'
                || bytes[1] != 'P'
                || bytes[2] != 'D'
                || bytes[3] != 'F'
                || bytes[4] != '-') {
            throw new PaymentReceiptStorageException(
                    "Payment receipt document is not a PDF.");
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original failure; no sensitive path is logged.
        }
    }
}
