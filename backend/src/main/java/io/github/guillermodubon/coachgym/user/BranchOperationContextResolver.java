package io.github.guillermodubon.coachgym.user;

import java.util.UUID;

/** Resolves authoritative branch facts for one staff operation. */
public interface BranchOperationContextResolver {

    BranchOperationContext resolveOperation(UUID userId);
}
