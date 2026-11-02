import { describe, expect, it } from "vitest";
import {
  parseBranch,
  parseBranchPage,
  parseOrganization,
} from "./organization-contracts";

const organizationId = "00000000-0000-4000-8000-000000000001";
const branchId = "00000000-0000-4000-8000-000000000002";
const updatedAt = "2026-10-31T12:00:00Z";

function branchResponse(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    id: branchId,
    code: "PRINCIPAL",
    name: "Coach Gym Principal",
    addressLine1: null,
    addressLine2: null,
    city: "San Salvador",
    stateOrDepartment: null,
    postalCode: null,
    countryCode: "SV",
    phone: null,
    email: null,
    timezone: "America/El_Salvador",
    status: "ACTIVE",
    initialBranch: true,
    version: 3,
    updatedAt,
    ...overrides,
  };
}

describe("F03 organization and branch decoders", () => {
  it("accepts the safe receptionist organization projection with omitted details", () => {
    expect(
      parseOrganization({
        id: organizationId,
        code: "COACH-GYM",
        brandName: "Coach Gym",
        defaultCurrency: "USD",
        status: "ACTIVE",
      }),
    ).toEqual({
      id: organizationId,
      code: "COACH-GYM",
      brandName: "Coach Gym",
      defaultCurrency: "USD",
      status: "ACTIVE",
    });
  });

  it("retains the full admin organization projection and safe optional fields", () => {
    expect(
      parseOrganization({
        id: organizationId,
        code: "COACH-GYM",
        legalName: "Coach Gym, S.A.",
        brandName: "Coach Gym",
        supportEmail: "support@example.invalid",
        supportPhone: "+503 2000 0000",
        defaultTimezone: "America/El_Salvador",
        defaultCurrency: "USD",
        status: "ACTIVE",
        version: 7,
        updatedAt,
      }),
    ).toMatchObject({
      legalName: "Coach Gym, S.A.",
      supportEmail: "support@example.invalid",
      defaultTimezone: "America/El_Salvador",
      version: 7,
      updatedAt,
    });
  });

  it.each([
    { id: "invalid" },
    { id: organizationId, code: "X", brandName: "X", defaultCurrency: "USD" },
    {
      id: organizationId,
      code: "X",
      brandName: "X",
      defaultCurrency: "USD",
      status: "PENDING",
    },
    {
      id: organizationId,
      code: "X",
      brandName: "X",
      defaultCurrency: "USD",
      status: "ACTIVE",
      version: -1,
    },
  ])("rejects an incompatible organization response", (response) => {
    expect(() => parseOrganization(response)).toThrow();
  });

  it("accepts nullable branch contact fields and projects only known response data", () => {
    expect(
      parseBranch(branchResponse({ unexpectedInternalField: "discard" })),
    ).toEqual({
      id: branchId,
      code: "PRINCIPAL",
      name: "Coach Gym Principal",
      addressLine1: null,
      addressLine2: null,
      city: "San Salvador",
      stateOrDepartment: null,
      postalCode: null,
      countryCode: "SV",
      phone: null,
      email: null,
      timezone: "America/El_Salvador",
      status: "ACTIVE",
      initialBranch: true,
      version: 3,
      updatedAt,
    });
  });

  it("accepts absent optional branch fields but rejects malformed core data", () => {
    const minimal = branchResponse();
    delete minimal.addressLine1;
    delete minimal.phone;
    expect(parseBranch(minimal)).not.toHaveProperty("addressLine1");
    expect(() => parseBranch(branchResponse({ version: 1.5 }))).toThrow();
    expect(() =>
      parseBranch(branchResponse({ initialBranch: "true" })),
    ).toThrow();
    expect(() => parseBranch(branchResponse({ status: "PENDING" }))).toThrow();
  });

  it("parses page metadata and rejects page shapes outside the backend contract", () => {
    expect(
      parseBranchPage({
        items: [branchResponse()],
        page: 0,
        size: 25,
        totalElements: 1,
        totalPages: 1,
      }),
    ).toMatchObject({ items: [{ id: branchId }], page: 0, size: 25 });
    expect(() =>
      parseBranchPage({
        items: [],
        page: 0,
        size: 101,
        totalElements: 0,
        totalPages: 0,
      }),
    ).toThrow();
    expect(() =>
      parseBranchPage({
        items: {},
        page: 0,
        size: 25,
        totalElements: 0,
        totalPages: 0,
      }),
    ).toThrow();
  });
});
