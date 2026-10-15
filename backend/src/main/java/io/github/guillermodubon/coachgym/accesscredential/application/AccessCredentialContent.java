package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import java.util.Objects;

/** Canonical credential metadata and its validated PNG content. */
public record AccessCredentialContent(
        AccessCredentialDetails details,
        AccessCredentialDocument document) {

    public AccessCredentialContent {
        Objects.requireNonNull(details, "Access credential details are required.");
        Objects.requireNonNull(document, "Access credential document is required.");
    }
}
