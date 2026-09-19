package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
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
}
