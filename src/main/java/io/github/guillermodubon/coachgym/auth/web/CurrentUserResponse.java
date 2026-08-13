package io.github.guillermodubon.coachgym.auth.web;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(UUID id, String username, String fullName, List<String> roles) {
}
