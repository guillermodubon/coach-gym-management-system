package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;

/** Technology-neutral port for rendering the canonical access QR artifact. */
public interface AccessCredentialQrRenderer {

    /** Renders one deterministic PNG for the supplied opaque payload. */
    AccessCredentialDocument render(AccessCredentialQrPayload payload);

    /** Stable renderer identifier persisted with the artifact metadata. */
    String rendererVersion();
}
