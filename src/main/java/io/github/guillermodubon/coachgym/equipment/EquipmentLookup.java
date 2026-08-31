package io.github.guillermodubon.coachgym.equipment;

import java.util.Optional;
import java.util.UUID;

/**
 * Minimal public read boundary for other modules that need to verify equipment existence
 * or retrieve a lightweight equipment projection.
 *
 * <p>Only the queries strictly required by cross-module consumers are exposed here.
 * Internal query operations remain inside the {@code application} package.
 */
public interface EquipmentLookup {

    /**
     * Returns a lightweight details projection for the given equipment ID,
     * or empty if no equipment with that ID exists.
     *
     * @param id the equipment UUID
     * @return an optional containing the details, or empty
     */
    Optional<EquipmentDetails> findById(UUID id);
}
