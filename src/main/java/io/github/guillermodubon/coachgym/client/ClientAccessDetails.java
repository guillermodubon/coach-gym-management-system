package io.github.guillermodubon.coachgym.client;

import java.util.UUID;

/**
 * Minimal client projection required by the access check-in use case.
 *
 * <p>Exposes only what the access module needs to evaluate client status.
 * No personal contact data (email, phone, name, date of birth) is included.</p>
 */
public record ClientAccessDetails(
        UUID id,
        String clientCode,
        ClientStatus status) {
}
