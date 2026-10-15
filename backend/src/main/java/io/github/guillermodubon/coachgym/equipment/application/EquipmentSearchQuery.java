package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentValidationException;
import java.util.Locale;
import java.util.UUID;

/**
 * Validated query for listing equipment with allowlisted filters and stable sorting.
 *
 * <p>Construct via {@link #from(UUID, String, String, String, int, int, String, String)}
 * which parses and validates all string parameters and applies defaults. The compact
 * constructor enforces page/size/sort integrity.
 *
 * <h3>Approved filters</h3>
 * <ul>
 *   <li>{@code categoryId} — UUID FK to {@code gym.equipment_categories}</li>
 *   <li>{@code status} — one of the {@link EquipmentStatus} values</li>
 *   <li>{@code search} — ILIKE match against {@code name}; blank normalises to null</li>
 *   <li>{@code location} — ILIKE match against {@code location}; blank normalises to null</li>
 * </ul>
 *
 * <h3>Sort defaults</h3>
 * Default: {@code NAME ASC}. Secondary sort: {@code id ASC} always appended by the adapter.
 *
 * <h3>Pagination defaults</h3>
 * {@code page=0}, {@code size=25}, {@code maxSize=100}.
 */
public record EquipmentSearchQuery(
        UUID categoryId,
        EquipmentStatus status,
        String search,
        String location,
        int page,
        int size,
        EquipmentSortField sortField,
        EquipmentSortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public EquipmentSearchQuery {
        if (page < 0) {
            throw new EquipmentValidationException("Page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new EquipmentValidationException(
                    "Page size must be between 1 and " + MAX_SIZE + ".");
        }
        if (sortField == null) {
            throw new EquipmentValidationException("Sort field must be provided.");
        }
        if (direction == null) {
            throw new EquipmentValidationException("Sort direction must be provided.");
        }
        search = normaliseOptional(search);
        location = normaliseOptional(location);
    }

    /**
     * Factory method that parses string inputs, applies defaults, and constructs a
     * validated query. All string-to-enum conversions are case-insensitive.
     *
     * @param categoryId UUID filter, or null for no category filter
     * @param status     status name (case-insensitive), or null/blank for no filter
     * @param search     name search term, or null/blank for no filter
     * @param location   location search term, or null/blank for no filter
     * @param page       zero-based page index
     * @param size       page size (1–100); defaults to 25 when ≤ 0
     * @param sort       sort field name (case-insensitive); defaults to {@code NAME}
     * @param direction  sort direction (case-insensitive); defaults to {@code ASC}
     */
    public static EquipmentSearchQuery from(
            UUID categoryId,
            String status,
            String search,
            String location,
            int page,
            int size,
            String sort,
            String direction) {
        return new EquipmentSearchQuery(
                categoryId,
                parseStatus(status),
                search,
                location,
                page,
                size < 1 ? DEFAULT_SIZE : size,
                parseSortField(sort),
                parseSortDirection(direction));
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private static EquipmentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EquipmentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EquipmentValidationException(
                    "Unsupported equipment status filter: " + value.trim() + ".");
        }
    }

    private static EquipmentSortField parseSortField(String value) {
        String normalised = (value == null || value.isBlank())
                ? "NAME"
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return EquipmentSortField.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new EquipmentValidationException(
                    "Unsupported equipment sort field: " + value.trim() + ".");
        }
    }

    private static EquipmentSortDirection parseSortDirection(String value) {
        String normalised = (value == null || value.isBlank())
                ? "ASC"
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return EquipmentSortDirection.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new EquipmentValidationException(
                    "Unsupported sort direction: " + value.trim() + ".");
        }
    }

    private static String normaliseOptional(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
