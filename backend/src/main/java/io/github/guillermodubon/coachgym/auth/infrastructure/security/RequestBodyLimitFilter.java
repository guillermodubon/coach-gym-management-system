package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Enforces bounded request bodies before MVC deserializes or stores them. */
class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final RequestBodyLimitProperties properties;
    private final JsonMapper jsonMapper;

    RequestBodyLimitFilter(RequestBodyLimitProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long limit = limitFor(request);
        if (request.getContentLengthLong() > limit) {
            writeTooLarge(response);
            return;
        }
        try {
            filterChain.doFilter(new LimitedBodyRequest(request, limit), response);
        } catch (RequestBodyLimitExceededException exception) {
            if (!response.isCommitted()) {
                writeTooLarge(response);
            } else {
                throw exception;
            }
        }
    }

    private long limitFor(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return properties.maxMultipartBodyBytes();
        }
        if (contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            return properties.maxJsonBodyBytes();
        }
        return properties.maxFormBodyBytes();
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiProblemFactory.create(
                HttpStatus.CONTENT_TOO_LARGE,
                "REQUEST_BODY_TOO_LARGE",
                "The request body exceeds the configured limit."));
    }

    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream inputStream;

        private LimitedBodyRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), limit);
            }
            return inputStream;
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            return new java.io.BufferedReader(new java.io.InputStreamReader(
                    getInputStream(), getCharacterEncoding() == null
                            ? java.nio.charset.StandardCharsets.UTF_8
                            : java.nio.charset.Charset.forName(getCharacterEncoding())));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        private LimitedServletInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = delegate.read(bytes, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }

        private void count(long amount) throws RequestBodyLimitExceededException {
            read += amount;
            if (read > limit) {
                throw new RequestBodyLimitExceededException();
            }
        }
    }

    private static final class RequestBodyLimitExceededException extends IOException {
    }
}
