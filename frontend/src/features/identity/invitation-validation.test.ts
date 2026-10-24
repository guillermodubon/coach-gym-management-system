import { describe, expect, it } from "vitest";
import { validateInvitationAcceptance } from "./invitation-validation";

describe("validateInvitationAcceptance", () => {
  it("normalizes profile names and preserves password whitespace", () => {
    const result = validateInvitationAcceptance({
      firstName: "  Ana   María ",
      lastName: " Pérez ",
      password: "  synthetic password  ",
      passwordConfirmation: "  synthetic password  ",
    });

    expect(result).toMatchObject({
      valid: true,
      values: {
        firstName: "Ana María",
        lastName: "Pérez",
        password: "  synthetic password  ",
        passwordConfirmation: "  synthetic password  ",
      },
      errors: {},
    });
  });

  it("rejects invalid names, short passwords, and mismatched confirmation", () => {
    const result = validateInvitationAcceptance({
      firstName: "\n",
      lastName: " ",
      password: "too short",
      passwordConfirmation: "another password",
    });

    expect(result.valid).toBe(false);
    expect(result.errors).toMatchObject({
      firstName: expect.any(String),
      lastName: expect.any(String),
      password: "Usa al menos 12 caracteres.",
      passwordConfirmation: "Las contraseñas no coinciden.",
    });
  });

  it("enforces the backend's 100 character names and 256 character passwords", () => {
    const result = validateInvitationAcceptance({
      firstName: "A".repeat(101),
      lastName: "B",
      password: "p".repeat(257),
      passwordConfirmation: "p".repeat(257),
    });

    expect(result.valid).toBe(false);
    expect(result.errors.firstName).toContain("100 caracteres");
    expect(result.errors.password).toContain("256 caracteres");
    expect(result.errors.passwordConfirmation).toContain("256 caracteres");
  });
});
