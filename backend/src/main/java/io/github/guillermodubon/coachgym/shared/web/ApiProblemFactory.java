package io.github.guillermodubon.coachgym.shared.web;

import java.net.URI;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ApiProblemFactory {

    private static final String PROBLEM_TYPE_PREFIX = "urn:coach-gym:problem:";

    private ApiProblemFactory() {
    }

    public static ProblemDetail create(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + code.toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String requestUri = attributes.getRequest().getRequestURI();
            if (requestUri != null && !requestUri.isBlank()) {
                try {
                    problem.setInstance(URI.create(requestUri));
                } catch (IllegalArgumentException ignored) {
                    // Keep error handling safe even for an invalid servlet URI.
                }
            }
        }
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
