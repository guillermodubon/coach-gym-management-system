package io.github.guillermodubon.coachgym.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageClient;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageProperties;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoContent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SupabaseStaffProfilePhotoStorageTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void storesLoadsAndDeletesThroughThePrivateSharedStorageClient() throws Exception {
        byte[] bytes = pngBytes();
        String checksum = checksum(bytes);
        CopyOnWriteArrayList<String> methods = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
                "/storage/v1/object/coach-gym-private/staff-profiles/",
                exchange -> {
                    methods.add(exchange.getRequestMethod());
                    if ("GET".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } else {
                        exchange.sendResponseHeaders(200, 0);
                    }
                    exchange.close();
                });
        server.start();

        SupabaseStorageProperties properties = new SupabaseStorageProperties(
                "http://localhost:" + server.getAddress().getPort(),
                "private-service-key", "coach-gym-private",
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2));
        SupabaseStaffProfilePhotoStorage storage =
                new SupabaseStaffProfilePhotoStorage(new SupabaseStorageClient(
                        properties, HttpClient.newHttpClient()));
        StaffProfilePhotoContent content = new StaffProfilePhotoContent(
                "image/png", bytes, checksum);
        String key = storage.generateStorageKey(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                content.contentType());

        storage.store(key, content);
        assertThat(storage.load(
                key, content.contentType(), content.sizeBytes(), content.checksumSha256())
                .bytes()).containsExactly(bytes);
        storage.delete(key);

        assertThat(methods).containsExactly("PUT", "GET", "DELETE");
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }

    private static String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
