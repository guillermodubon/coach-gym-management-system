package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageClient;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SupabaseStorageClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesPrivateObjectApiAndVerifiesDownloadedMetadataWithAnOfflineServer() throws Exception {
        byte[] content = "private-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/storage/v1/object/coach-gym-private/receipts/", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            } else {
                exchange.sendResponseHeaders(200, 0);
            }
            exchange.close();
        });
        server.start();

        SupabaseStorageProperties properties = new SupabaseStorageProperties(
                "http://localhost:" + server.getAddress().getPort(),
                "service-role-secret", "coach-gym-private",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        SupabaseStorageClient client = new SupabaseStorageClient(
                properties, HttpClient.newHttpClient());

        client.put("receipts/00000000-0000-0000-0000-000000000001.pdf",
                "application/pdf", content);
        assertThat(client.get(
                "receipts/00000000-0000-0000-0000-000000000001.pdf",
                "application/pdf", content.length, checksum)).containsExactly(content);
        client.delete("receipts/00000000-0000-0000-0000-000000000001.pdf");

        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(authorization.get()).isEqualTo("Bearer service-role-secret");
    }

    @Test
    void cancelsAnOversizedObjectResponseBeforeReadingTheFullBody() throws Exception {
        long responseLength = 32L * 1024L * 1024L;
        AtomicLong bytesWritten = new AtomicLong();
        CountDownLatch responseFinished = new CountDownLatch(1);
        startStreamingResponse(
                200,
                "/storage/v1/object/coach-gym-private/photos/",
                responseLength,
                bytesWritten,
                responseFinished);

        SupabaseStorageClient client = clientForServer();

        assertThatThrownBy(() -> client.get(
                "photos/00000000-0000-0000-0000-000000000001.png",
                "image/png", -1, "0".repeat(64)))
                .isInstanceOf(SupabaseStorageClient.StorageProviderException.class)
                .hasMessage("Storage provider returned an oversized response.");

        assertThat(responseFinished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(bytesWritten.get()).isLessThan(responseLength);
    }

    @Test
    void boundsProviderErrorBodiesWithoutExposingTheirContents() throws Exception {
        long responseLength = 1024L * 1024L;
        AtomicLong bytesWritten = new AtomicLong();
        CountDownLatch responseFinished = new CountDownLatch(1);
        startStreamingResponse(
                503,
                "/storage/v1/object/coach-gym-private/photos/",
                responseLength,
                bytesWritten,
                responseFinished);

        SupabaseStorageClient client = clientForServer();

        assertThatThrownBy(() -> client.get(
                "photos/00000000-0000-0000-0000-000000000001.png",
                "image/png", -1, "0".repeat(64)))
                .isInstanceOf(SupabaseStorageClient.StorageProviderException.class)
                .hasMessage("Storage provider returned an oversized response.");

        assertThat(responseFinished.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void rejectsDotSegmentBucketConfigurationBeforeMakingARequest() {
        SupabaseStorageProperties properties = new SupabaseStorageProperties(
                "https://storage.example.test", "service-role-secret", "..",
                Duration.ofSeconds(1), Duration.ofSeconds(2));

        assertThat(properties.isValid()).isFalse();
    }

    private SupabaseStorageClient clientForServer() {
        SupabaseStorageProperties properties = new SupabaseStorageProperties(
                "http://localhost:" + server.getAddress().getPort(),
                "service-role-secret", "coach-gym-private",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        return new SupabaseStorageClient(properties, HttpClient.newHttpClient());
    }

    private void startStreamingResponse(
            int status,
            String contextPath,
            long responseLength,
            AtomicLong bytesWritten,
            CountDownLatch responseFinished) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(contextPath, exchange -> {
            try {
                exchange.sendResponseHeaders(status, responseLength);
                byte[] chunk = new byte[64 * 1024];
                try (OutputStream output = exchange.getResponseBody()) {
                    while (bytesWritten.get() < responseLength) {
                        int length = (int) Math.min(
                                chunk.length, responseLength - bytesWritten.get());
                        output.write(chunk, 0, length);
                        output.flush();
                        bytesWritten.addAndGet(length);
                    }
                }
            } catch (IOException ignored) {
                // Client cancellation is expected once the response limit is exceeded.
            } finally {
                exchange.close();
                responseFinished.countDown();
            }
        });
        server.start();
    }
}
