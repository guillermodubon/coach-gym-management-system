package io.github.guillermodubon.coachgym.auth.web;

public record CsrfTokenResponse(String token, String headerName, String parameterName) {
}
