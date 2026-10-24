import { describe, expect, it } from "vitest";
import {
  normalizeProfileName,
  validateProfileNames,
} from "./profile-validation";

describe("profile name validation", () => {
  it("normalizes surrounding and repeated whitespace to match the backend", () => {
    expect(normalizeProfileName("  Ana   María\tRíos  ")).toBe(
      "Ana María Ríos",
    );
    expect(
      validateProfileNames({ firstName: " Ana   María ", lastName: " Ríos " }),
    ).toMatchObject({
      valid: true,
      values: { firstName: "Ana María", lastName: "Ríos" },
    });
  });

  it.each([
    { firstName: "   ", lastName: "Ríos", field: "firstName" },
    { firstName: "Ana", lastName: "\t", field: "lastName" },
    { firstName: "A\u0000na", lastName: "Ríos", field: "firstName" },
    { firstName: "A".repeat(101), lastName: "Ríos", field: "firstName" },
    { firstName: "Ana", lastName: "R".repeat(101), field: "lastName" },
  ])("rejects invalid values in $field", ({ firstName, lastName }) => {
    expect(validateProfileNames({ firstName, lastName }).valid).toBe(false);
  });
});
