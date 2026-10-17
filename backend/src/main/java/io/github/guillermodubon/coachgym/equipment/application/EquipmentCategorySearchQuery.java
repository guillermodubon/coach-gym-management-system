package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentValidationException;
import java.util.Locale;

/**
 * Validated query for listing equipment categories.
 *
 * <h3>Approved filters</h3>
 * <ul>
 *   <li>{@code active} — boolean filter; null means no filter (return all)</li>
 * </ul>
 *
 * <h3>Sort defaults</h3>
 * Default: {@code NAME ASC}. Secondary sort: {@code id ASC} always appended by the adapter.
 *
 * <h3>Pagination defaults</h3>
 * {@code page=0}, {@code size=25}, {@code maxSize=100}.
 */
public record EquipmentCategorySearchQuery(
        Boolean active,
        int page,
        int size,
        EquipmentCategorySortField sortField,
        EquipmentSortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public EquipmentCategorySearchQuery {
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
    }

    /**
     * Factory method that parses string inputs, applies defaults, and constructs a
     * validated query.
     *
     * @param active    "true"/"false" (case-insensitive), or null/blank for no filter
     * @param page      zero-based page index
     * @param size      page size (1–100); defaults to 25 when ≤ 0
     * @param sort      sort field name (case-insensitive); defaults to {@code NAME}
     * @param direction sort direction (case-insensitive); defaults to {@code ASC}
     */
    public static EquipmentCategorySearchQuery from(
            String active,
            int page,
            int size,
            String sort,
            String direction) {
        return new EquipmentCategorySearchQuery(
                parseActive(active),
                page,
                size < 1 ? DEFAULT_SIZE : size,
                parseSortField(sort),
                parseSortDirection(direction));
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private static Boolean parseActive(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalised)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalised)) {
            return Boolean.FALSE;
        }
        throw new EquipmentValidationException(
                "Invalid value for 'active' filter; expected 'true' or 'false'.");
    }

    private static EquipmentCategorySortField parseSortField(String value) {
        String normalised = (value == null || value.isBlank())
                ? "NAME"
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return EquipmentCategorySortField.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new EquipmentValidationException(
                    "Unsupported category sort field: " + value.trim() + ".");
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
}
