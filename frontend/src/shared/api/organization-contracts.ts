import type { components } from "./generated/f03-schema";
import { ApiError, isRecord } from "./errors";

type Schemas = components["schemas"];

export type OrganizationStatus = "ACTIVE" | "INACTIVE";
export type BranchStatus = "ACTIVE" | "INACTIVE";
export type BranchSortField =
  "CODE" | "NAME" | "CITY" | "CREATED_AT" | "UPDATED_AT";
export type BranchSortDirection = "ASC" | "DESC";

export interface BranchCatalogQuery {
  status?: BranchStatus;
  search?: string;
  city?: string;
  countryCode?: string;
  page?: number;
  size?: number;
  sort?: BranchSortField;
  direction?: BranchSortDirection;
}

export type OrganizationRecord = {
  id: string;
  code: string;
  brandName: string;
  defaultTimezone?: string | null;
  defaultCurrency: string;
  status: OrganizationStatus;
  legalName?: string | null;
  supportEmail?: string | null;
  supportPhone?: string | null;
  version?: number;
  updatedAt?: string;
};

export type BranchRecord = {
  id: string;
  code: string;
  name: string;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateOrDepartment?: string | null;
  postalCode?: string | null;
  countryCode?: string | null;
  phone?: string | null;
  email?: string | null;
  timezone: string;
  status: BranchStatus;
  initialBranch: boolean;
  version: number;
  updatedAt: string;
};

export type BranchPage = {
  items: BranchRecord[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CreateBranchInput = Readonly<Schemas["CreateBranchRequest"]>;
export type UpdateBranchInput = Readonly<
  Omit<Schemas["UpdateBranchRequest"], "timezone"> & { timezone: string }
>;
export type BranchStatusChangeInput = Readonly<
  Schemas["BranchStatusChangeRequest"]
>;

function invalid(): never {
  throw new ApiError("invalid-response");
}

function text(value: unknown): string {
  return typeof value === "string" ? value : invalid();
}

function uuid(value: unknown): string {
  const result = text(value);
  return /^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(result)
    ? result
    : invalid();
}

function status(value: unknown): OrganizationStatus {
  if (value === "ACTIVE" || value === "INACTIVE") return value;
  return invalid();
}

function nonNegativeInteger(value: unknown): number {
  return Number.isSafeInteger(value) && Number(value) >= 0
    ? Number(value)
    : invalid();
}

function timestamp(value: unknown): string {
  const result = text(value);
  return Number.isFinite(Date.parse(result)) ? result : invalid();
}

function optionalText(
  record: Record<string, unknown>,
  key: string,
): string | null | undefined {
  if (!Object.hasOwn(record, key)) return undefined;
  const value = record[key];
  if (value === null) return null;
  return text(value);
}

function optionalTextProperty(
  record: Record<string, unknown>,
  key: string,
): Record<string, string | null> {
  const value = optionalText(record, key);
  return value === undefined ? {} : { [key]: value };
}

export function parseOrganization(value: unknown): OrganizationRecord {
  if (!isRecord(value)) return invalid();
  const version =
    value.version === undefined ? undefined : nonNegativeInteger(value.version);
  const updatedAt =
    value.updatedAt === undefined ? undefined : timestamp(value.updatedAt);
  return {
    id: uuid(value.id),
    code: text(value.code),
    brandName: text(value.brandName),
    defaultCurrency: text(value.defaultCurrency),
    status: status(value.status),
    ...optionalTextProperty(value, "defaultTimezone"),
    ...optionalTextProperty(value, "legalName"),
    ...optionalTextProperty(value, "supportEmail"),
    ...optionalTextProperty(value, "supportPhone"),
    ...(version !== undefined ? { version } : {}),
    ...(updatedAt !== undefined ? { updatedAt } : {}),
  };
}

export function parseBranch(value: unknown): BranchRecord {
  if (!isRecord(value)) return invalid();
  if (typeof value.initialBranch !== "boolean") return invalid();
  return {
    id: uuid(value.id),
    code: text(value.code),
    name: text(value.name),
    ...optionalTextProperty(value, "addressLine1"),
    ...optionalTextProperty(value, "addressLine2"),
    ...optionalTextProperty(value, "city"),
    ...optionalTextProperty(value, "stateOrDepartment"),
    ...optionalTextProperty(value, "postalCode"),
    ...optionalTextProperty(value, "countryCode"),
    ...optionalTextProperty(value, "phone"),
    ...optionalTextProperty(value, "email"),
    timezone: text(value.timezone),
    status: status(value.status),
    initialBranch: value.initialBranch,
    version: nonNegativeInteger(value.version),
    updatedAt: timestamp(value.updatedAt),
  };
}

export function parseBranchPage(value: unknown): BranchPage {
  if (!isRecord(value) || !Array.isArray(value.items)) return invalid();
  const page = nonNegativeInteger(value.page);
  if (page > 2_147_483_647) return invalid();
  const size = nonNegativeInteger(value.size);
  if (size < 1 || size > 100) return invalid();
  return {
    items: value.items.map(parseBranch),
    page,
    size,
    totalElements: nonNegativeInteger(value.totalElements),
    totalPages: nonNegativeInteger(value.totalPages),
  };
}

export function parseEmptyResponse(value: undefined): undefined {
  if (value !== undefined) return invalid();
  return undefined;
}
