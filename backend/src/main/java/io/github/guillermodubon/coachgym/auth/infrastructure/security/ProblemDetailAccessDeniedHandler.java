package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    ProblemDetailAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        boolean isInvalidCsrfToken = accessDeniedException instanceof CsrfException;
        String code = isInvalidCsrfToken ? "CSRF_TOKEN_INVALID" : "ACCESS_DENIED";
        String detail = isInvalidCsrfToken
                ? "The CSRF token is missing or invalid."
                : "You are not allowed to perform this operation.";
        jsonMapper.writeValue(response.getOutputStream(), ApiProblemFactory.create(HttpStatus.FORBIDDEN, code, detail));
    }
}
