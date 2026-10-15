package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RequestBodyLimitFilterTest {

    @Test
    void rejectsJsonBodiesAboveTheConfiguredLimitBeforeMvc() throws ServletException, IOException {
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
                new RequestBodyLimitProperties(10, 20, 100),
                JsonMapper.builder().build());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType("application/json");
        request.setContent("12345678901".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_BODY_TOO_LARGE");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void permitsBodiesWithinTheirContentTypeSpecificLimit() throws ServletException, IOException {
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
                new RequestBodyLimitProperties(10, 20, 100),
                JsonMapper.builder().build());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType("application/json");
        request.setContent("1234567890".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsChunkedJsonWhenStreamExceedsLimitWithoutLeakingBody() throws Exception {
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
                new RequestBodyLimitProperties(10, 20, 100),
                JsonMapper.builder().build());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/auth/login") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContentType("application/json");
        request.setContent("12345678901".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean downstreamCompleted = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletRequest.getInputStream().readAllBytes();
            downstreamCompleted.set(true);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString())
                .contains("REQUEST_BODY_TOO_LARGE")
                .doesNotContain("12345678901");
        assertThat(downstreamCompleted).isFalse();
    }
}
