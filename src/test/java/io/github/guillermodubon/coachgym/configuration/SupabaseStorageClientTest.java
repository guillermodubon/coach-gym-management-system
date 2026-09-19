package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageClient;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageProperties;
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
}
