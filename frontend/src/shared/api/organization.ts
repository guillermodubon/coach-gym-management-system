import type { ApiClient } from "./client";
import { ApiError, isRecord } from "./errors";
import type {
  BranchCatalogQuery,
  BranchPage,
  BranchRecord,
  BranchStatusChangeInput,
  CreateBranchInput,
  OrganizationRecord,
  UpdateBranchInput,
} from "./organization-contracts";
import {
  parseBranch,
  parseBranchPage,
  parseOrganization,
} from "./organization-contracts";

export interface RequestControl {
  signal?: AbortSignal;
}

const uuidPattern = /^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i;
const createFields = [
  "code",
  "name",
  "addressLine1",
  "addressLine2",
  "city",
  "stateOrDepartment",
  "postalCode",
  "countryCode",
  "phone",
  "email",
  "timezone",
] as const;
const updateFields = [
  "name",
  "addressLine1",
  "addressLine2",
  "city",
  "stateOrDepartment",
  "postalCode",
  "countryCode",
  "phone",
  "email",
  "timezone",
  "version",
] as const;

function branchPath(id: string): string {
  if (!uuidPattern.test(id)) throw new ApiError("invalid-request");
  return `/api/v1/branches/${id}`;
}

function exactPayload(
  value: unknown,
  fields: readonly string[],
): Record<string, unknown> {
  if (!isRecord(value) || Object.getPrototypeOf(value) !== Object.prototype) {
    throw new ApiError("invalid-request");
  }
  const payload: Record<string, unknown> = {};
  for (const field of fields) {
    if (Object.hasOwn(value, field) && value[field] !== undefined) {
      payload[field] = value[field];
    }
  }
  return payload;
}

function lifecyclePath(id: string, action: "activate" | "deactivate"): string {
  return `${branchPath(id)}/${action}`;
}

/** Direct, allowlisted F03 calls through the shared session and CSRF client. */
export function createOrganizationApi(client: ApiClient) {
  return {
    organization: (control: RequestControl = {}) =>
      client.request<OrganizationRecord>({
        path: "/api/v1/organization",
        method: "GET",
        responseType: "json",
        expectedStatus: 200,
        parse: parseOrganization,
        ...control,
      }),
    listBranches: (
      query: BranchCatalogQuery = {},
      control: RequestControl = {},
    ) =>
      client.request<BranchPage>({
        path: "/api/v1/branches",
        method: "GET",
        query,
        responseType: "json",
        expectedStatus: 200,
        parse: parseBranchPage,
        ...control,
      }),
    branch: (id: string, control: RequestControl = {}) =>
      client.request<BranchRecord>({
        path: branchPath(id),
        method: "GET",
        responseType: "json",
        expectedStatus: 200,
        parse: parseBranch,
        ...control,
      }),
    createBranch: (input: CreateBranchInput, control: RequestControl = {}) =>
      client.request<BranchRecord>({
        path: "/api/v1/branches",
        method: "POST",
        bodyType: "json",
        body: exactPayload(input, createFields),
        responseType: "json",
        expectedStatus: 201,
        parse: parseBranch,
        ...control,
      }),
    updateBranch: (
      id: string,
      input: UpdateBranchInput,
      control: RequestControl = {},
    ) =>
      client.request<BranchRecord>({
        path: branchPath(id),
        method: "PUT",
        bodyType: "json",
        body: exactPayload(input, updateFields),
        responseType: "json",
        expectedStatus: 200,
        parse: parseBranch,
        ...control,
      }),
    activateBranch: (
      id: string,
      input: BranchStatusChangeInput,
      control: RequestControl = {},
    ) =>
      client.request<BranchRecord>({
        path: lifecyclePath(id, "activate"),
        method: "POST",
        bodyType: "json",
        body: exactPayload(input, ["reason", "version"]),
        responseType: "json",
        expectedStatus: 200,
        parse: parseBranch,
        ...control,
      }),
    deactivateBranch: (
      id: string,
      input: BranchStatusChangeInput,
      control: RequestControl = {},
    ) =>
      client.request<BranchRecord>({
        path: lifecyclePath(id, "deactivate"),
        method: "POST",
        bodyType: "json",
        body: exactPayload(input, ["reason", "version"]),
        responseType: "json",
        expectedStatus: 200,
        parse: parseBranch,
        ...control,
      }),
  };
}
