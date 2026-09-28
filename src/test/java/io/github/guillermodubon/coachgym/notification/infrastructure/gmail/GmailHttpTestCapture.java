package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bounded request capture whose diagnostic representation never includes secrets or payloads. */
final class GmailHttpTestCapture {

    private static final int MAX_REQUEST_BYTES = 16 * 1024 * 1024 + 64 * 1024;

    private GmailHttpTestCapture() {}

    static CapturedRequest capture(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IOException("Local Gmail test request exceeded its capture bound.");
        }
        return new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Accept"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body);
    }

    record CapturedRequest(
            String method,
            String path,
            String authorization,
            String accept,
            String contentType,
            byte[] body) {

        CapturedRequest {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        String bodyUtf8() {
            return new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return "CapturedRequest[method=" + method
                    + ", path=" + path
                    + ", authorizationPresent=" + (authorization != null)
                    + ", accept=" + accept
                    + ", contentType=" + contentType
                    + ", bodyBytes=" + body.length + ']';
        }
    }
}
