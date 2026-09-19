package io.github.guillermodubon.coachgym.notification.infrastructure.resend;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.EmailProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResendEmailSenderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsSuccessfulResponseWithoutLeakingProviderPayload() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = "{\"id\":\"re_123\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        EmailSendResult result = sender().send(message());

        assertThat(result.result()).isEqualTo(io.github.guillermodubon.coachgym.notification.EmailAttemptResult.SENT);
        assertThat(result.providerMessageId()).isEqualTo("re_123");
        assertThat(authorization.get()).isEqualTo("Bearer resend-secret");
        assertThat(body.get()).contains("receipt.pdf").doesNotContain("resend-secret");
    }

    @Test
    void mapsAuthenticationAndQuotaRejectionsToSafeProviderNeutralFailures() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/emails", exchange -> {
            exchange.sendResponseHeaders(401, 0);
            exchange.close();
        });
        server.start();
        assertThat(sender().send(message()).failureCode())
                .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED);

        server.removeContext("/emails");
        server.createContext("/emails", exchange -> {
            exchange.sendResponseHeaders(429, 0);
            exchange.close();
        });
        assertThat(sender().send(message()).failureCode())
                .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_REJECTED);
    }

    @Test
    void mapsProviderTimeoutToAnAmbiguousOutcome() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/emails", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        EmailSendResult result = sender(Duration.ofMillis(50)).send(message());

        assertThat(result.result())
                .isEqualTo(io.github.guillermodubon.coachgym.notification.EmailAttemptResult.AMBIGUOUS);
        assertThat(result.failureCode())
                .isEqualTo(EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME);
    }

    private ResendEmailSender sender() {
        return sender(Duration.ofSeconds(2));
    }

    private ResendEmailSender sender(Duration requestTimeout) {
        int port = server.getAddress().getPort();
        EmailProperties email = new EmailProperties(
                true, "resend", "Coach Gym", "v1", "no-reply@example.com", "Coach Gym",
                null, "localhost", 1025, null, null, false, false,
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254, 3, Duration.ofMinutes(15));
        ResendProperties resend = new ResendProperties(
                "resend-secret", "http://localhost:" + port + "/emails",
                Duration.ofSeconds(1), requestTimeout);
        return new ResendEmailSender(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                new ObjectMapper(), email, resend);
    }

    private static EmailMessage message() {
        return new EmailMessage(
                "ana@example.com", "no-reply@example.com", "Coach Gym", null,
                "Receipt", "Plain body", "<p>HTML body</p>",
                new EmailAttachment("receipt.pdf", "application/pdf", new byte[]{1, 2, 3}));
    }
}
