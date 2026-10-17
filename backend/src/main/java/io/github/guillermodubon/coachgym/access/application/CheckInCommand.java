package io.github.guillermodubon.coachgym.access.application;

/**
 * Command carrying the raw identifier string presented at the gym entrance.
 *
 * <p>The service normalises the value via
 * {@link io.github.guillermodubon.coachgym.access.domain.AccessIdentifier#of(String)}.
 * Any structural validation (null/blank) happens there, not at the HTTP boundary,
 * so the domain rejection is the single authoritative source.</p>
 */
public record CheckInCommand(String rawIdentifier) {
}
