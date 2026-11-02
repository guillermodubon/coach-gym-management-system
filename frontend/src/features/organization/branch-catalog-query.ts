import type {
  BranchCatalogQuery,
  BranchSortDirection,
  BranchSortField,
  BranchStatus,
} from "../../shared/api/organization-contracts";

export type BranchCatalogValues = Required<
  Pick<BranchCatalogQuery, "page" | "size" | "sort" | "direction">
> &
  Pick<BranchCatalogQuery, "status" | "search" | "city" | "countryCode">;

export type BranchCatalogParseResult =
  | { valid: true; query: BranchCatalogValues }
  | { valid: false; message: string };

function hasControlCharacters(value: string): boolean {
  for (const character of value) {
    const codePoint = character.codePointAt(0);
    if (
      codePoint !== undefined &&
      (codePoint <= 0x1f || (codePoint >= 0x7f && codePoint <= 0x9f))
    ) {
      return true;
    }
  }
  return false;
}

const sortFields: readonly BranchSortField[] = [
  "CODE",
  "NAME",
  "CITY",
  "CREATED_AT",
  "UPDATED_AT",
];
const directions: readonly BranchSortDirection[] = ["ASC", "DESC"];
const statuses: readonly BranchStatus[] = ["ACTIVE", "INACTIVE"];
const recognizedKeys = [
  "status",
  "search",
  "city",
  "countryCode",
  "page",
  "size",
  "sort",
  "direction",
] as const;

function normalizedFilter(value: string | null): string | undefined {
  if (value === null) return undefined;
  const normalized = value.trim().replace(/\s+/g, " ");
  if (!normalized) return undefined;
  if (normalized.length > 120 || hasControlCharacters(normalized)) {
    throw new Error("Un filtro supera el formato permitido.");
  }
  return normalized;
}

function integerParameter(
  params: URLSearchParams,
  name: "page" | "size",
  fallback: number,
): number {
  const raw = params.get(name);
  if (raw === null) return fallback;
  if (!/^\d+$/.test(raw))
    throw new Error("La paginación de la URL no es válida.");
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value > 2_147_483_647) {
    throw new Error("La paginación de la URL no es válida.");
  }
  if (name === "size" && (value < 1 || value > 100)) {
    throw new Error("El tamaño de página debe estar entre 1 y 100.");
  }
  return value;
}

export function parseBranchCatalogSearch(
  search: string,
): BranchCatalogParseResult {
  const params = new URLSearchParams(search);
  for (const key of recognizedKeys) {
    if (params.getAll(key).length > 1) {
      return { valid: false, message: "La URL contiene filtros duplicados." };
    }
  }

  try {
    const rawStatus = params.get("status")?.toUpperCase();
    if (rawStatus && !statuses.includes(rawStatus as BranchStatus)) {
      throw new Error("El estado indicado en la URL no es válido.");
    }
    const rawSort = (params.get("sort") ?? "CODE").toUpperCase();
    if (!sortFields.includes(rawSort as BranchSortField)) {
      throw new Error("El orden indicado en la URL no es válido.");
    }
    const rawDirection = (params.get("direction") ?? "ASC").toUpperCase();
    if (!directions.includes(rawDirection as BranchSortDirection)) {
      throw new Error("La dirección de orden no es válida.");
    }
    const countryCode = normalizedFilter(
      params.get("countryCode"),
    )?.toUpperCase();
    const search = normalizedFilter(params.get("search"));
    const city = normalizedFilter(params.get("city"));
    if (countryCode && !/^[A-Z]{2}$/.test(countryCode)) {
      throw new Error("El país debe indicarse con un código de dos letras.");
    }

    return {
      valid: true,
      query: {
        page: integerParameter(params, "page", 0),
        size: integerParameter(params, "size", 25),
        sort: rawSort as BranchSortField,
        direction: rawDirection as BranchSortDirection,
        ...(rawStatus ? { status: rawStatus as BranchStatus } : {}),
        ...(search ? { search } : {}),
        ...(city ? { city } : {}),
        ...(countryCode ? { countryCode } : {}),
      },
    };
  } catch (error) {
    return {
      valid: false,
      message:
        error instanceof Error
          ? error.message
          : "Los filtros indicados en la URL no son válidos.",
    };
  }
}

export function branchCatalogSearch(query: BranchCatalogValues): string {
  const params = new URLSearchParams();
  if (query.status) params.set("status", query.status);
  if (query.search) params.set("search", query.search);
  if (query.city) params.set("city", query.city);
  if (query.countryCode) params.set("countryCode", query.countryCode);
  if (query.page > 0) params.set("page", String(query.page));
  if (query.size !== 25) params.set("size", String(query.size));
  if (query.sort !== "CODE") params.set("sort", query.sort);
  if (query.direction !== "ASC") params.set("direction", query.direction);
  const serialized = params.toString();
  return serialized ? `?${serialized}` : "";
}

export function normalizeBranchFilter(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  const normalized = value.trim().replace(/\s+/g, " ");
  return normalized ? normalized : undefined;
}
