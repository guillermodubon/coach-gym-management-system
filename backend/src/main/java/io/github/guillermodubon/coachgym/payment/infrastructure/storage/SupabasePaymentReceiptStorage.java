package io.github.guillermodubon.coachgym.payment.infrastructure.storage;

import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageClient;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorage;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageKey;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Shared-object-storage receipt adapter used by the Supabase deployment profile. */
@Component
@ConditionalOnProperty(prefix = "gym.storage", name = "provider", havingValue = "supabase")
class SupabasePaymentReceiptStorage implements PaymentReceiptStorage {

    private static final String CONTENT_TYPE = "application/pdf";
    private final SupabaseStorageClient client;

    SupabasePaymentReceiptStorage(SupabaseStorageClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public String generateStorageKey(UUID receiptId) {
        return PaymentReceiptStorageKey.forReceipt(receiptId);
    }

    @Override
    public void store(String storageKey, PaymentReceiptDocument document) {
        Objects.requireNonNull(document, "Receipt document is required.");
        requirePdf(document.bytes());
        try {
            client.put(requireKey(storageKey), CONTENT_TYPE, document.bytes());
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new PaymentReceiptStorageException(
                    "Payment receipt document could not be stored.", exception);
        }
    }

    @Override
    public PaymentReceiptDocument load(
            String storageKey, String contentType, String checksumSha256) {
        if (!CONTENT_TYPE.equals(contentType)) {
            throw new PaymentReceiptStorageException("Stored payment receipt content type is invalid.");
        }
        try {
            byte[] bytes = client.get(
                    requireKey(storageKey), CONTENT_TYPE,
                    expectedSize(checksumSha256), checksumSha256);
            requirePdf(bytes);
            return new PaymentReceiptDocument(CONTENT_TYPE, bytes, checksumSha256);
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new PaymentReceiptStorageException(
                    "Payment receipt document could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.delete(requireKey(storageKey));
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new PaymentReceiptStorageException(
                    "Payment receipt document could not be deleted.", exception);
        }
    }

    private static String requireKey(String storageKey) {
        if (storageKey == null || !storageKey.matches("receipts/[0-9a-fA-F-]{36}\\.pdf")) {
            throw new PaymentReceiptStorageException("Invalid payment receipt storage key.");
        }
        return storageKey.strip();
    }

    private static long expectedSize(String checksum) {
        // The receipt port historically supplies only checksum metadata. The
        // provider verifies the checksum and the domain validates the bound.
        // A bounded sentinel prevents accepting an unbounded remote response.
        if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
            throw new PaymentReceiptStorageException("Stored payment receipt checksum is invalid.");
        }
        return -1;
    }

    private static void requirePdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5
                || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new PaymentReceiptStorageException("Payment receipt document is not a PDF.");
        }
    }
}
