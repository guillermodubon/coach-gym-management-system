package io.github.guillermodubon.coachgym.shared.storage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Small bounded HTTP adapter for the private Supabase Storage object API.
 * Provider HTTP details and the service key do not escape configuration.
 */
@Component
public class SupabaseStorageClient {

    private static final int MAX_ERROR_BYTES = 8 * 1024;
    private static final int MAX_OBJECT_BYTES = 12 * 1024 * 1024;

    private final SupabaseStorageProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public SupabaseStorageClient(SupabaseStorageProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(properties.connectionTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public SupabaseStorageClient(
            SupabaseStorageProperties properties,
            HttpClient httpClient) {
        this.properties = Objects.requireNonNull(properties, "Storage properties are required.");
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client is required.");
    }

    public void put(String storageKey, String contentType, byte[] content) {
        requireReady();
        if (content == null || content.length == 0 || content.length > MAX_OBJECT_BYTES) {
            throw new StorageProviderException("Storage object size is invalid.");
        }
        send("PUT", storageKey, contentType, content, true);
    }

    public byte[] get(
            String storageKey,
            String contentType,
            long expectedSize,
            String expectedChecksum) {
        requireReady();
        byte[] bytes = send("GET", storageKey, contentType, null, false);
        if ((expectedSize >= 0 && bytes.length != expectedSize)
                || expectedChecksum == null
                || !checksum(bytes).equals(expectedChecksum)) {
            throw new StorageProviderException("Stored document metadata does not match.");
        }
        return bytes;
    }

    public void delete(String storageKey) {
        requireReady();
        send("DELETE", storageKey, null, null, false);
    }

    private byte[] send(
            String method,
            String storageKey,
            String contentType,
            byte[] content,
            boolean requireEmptySuccessBody) {
        URI uri = objectUri(storageKey);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + properties.serviceKey())
                .header("apikey", properties.serviceKey());
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        if ("PUT".equals(method)) {
            builder.header("x-upsert", "false")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.GET();
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new StorageProviderException(safeFailure(response.statusCode(), response.body()));
            }
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (body.length > MAX_OBJECT_BYTES) {
                throw new StorageProviderException("Storage provider returned an oversized object.");
            }
            if (requireEmptySuccessBody && body.length > MAX_ERROR_BYTES) {
                throw new StorageProviderException("Storage provider returned an invalid response.");
            }
            return body;
        } catch (HttpTimeoutException exception) {
            throw new StorageProviderException("Storage provider request timed out.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StorageProviderException("Storage provider request was interrupted.", exception);
        } catch (IOException exception) {
            throw new StorageProviderException("Storage provider could not be reached.", exception);
        }
    }

    private URI objectUri(String storageKey) {
        if (storageKey == null || storageKey.isBlank()
                || storageKey.contains("\r") || storageKey.contains("\n")
                || storageKey.contains("..")) {
            throw new StorageProviderException("Invalid storage key.");
        }
        StringBuilder encodedKey = new StringBuilder();
        for (String segment : storageKey.strip().split("/", -1)) {
            if (segment.isBlank()) {
                throw new StorageProviderException("Invalid storage key.");
            }
            if (encodedKey.length() > 0) {
                encodedKey.append('/');
            }
            encodedKey.append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
        }
        String endpoint = properties.endpoint().replaceAll("/+$", "");
        return URI.create(endpoint + "/storage/v1/object/"
                + URLEncoder.encode(properties.bucket(), StandardCharsets.UTF_8)
                + "/" + encodedKey);
    }

    private void requireReady() {
        if (!properties.isValid()) {
            throw new StorageProviderException("Supabase Storage configuration is invalid.");
        }
    }

    private static String safeFailure(int status, byte[] body) {
        return status == 404
                ? "Stored document was not found."
                : "Storage provider rejected the request (status " + status + ").";
    }

    private static String checksum(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    /** Safe provider-neutral storage failure. */
    public static final class StorageProviderException extends RuntimeException {
        public StorageProviderException(String message) {
            super(message);
        }

        public StorageProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
