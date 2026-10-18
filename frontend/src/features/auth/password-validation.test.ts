import { describe, expect, it } from "vitest";
import { validatePasswordChange } from "./password-validation";

function values(
  overrides: Partial<{
    currentPassword: string;
    newPassword: string;
    newPasswordConfirmation: string;
  }> = {},
) {
  return {
    currentPassword: "old",
    newPassword: "replacement-12",
    newPasswordConfirmation: "replacement-12",
    ...overrides,
  };
}

describe("mandatory password change validation", () => {
  it("accepts a short current password and the exact minimum new length", () => {
    expect(
      validatePasswordChange(
        values({
          newPassword: "x".repeat(12),
          newPasswordConfirmation: "x".repeat(12),
        }),
      ),
    ).toEqual({});
  });

  it.each([11, 257])("rejects a new password of %i UTF-16 units", (length) => {
    const candidate = "x".repeat(length);
    expect(
      validatePasswordChange(
        values({
          newPassword: candidate,
          newPasswordConfirmation: candidate,
        }),
      ).newPassword,
    ).toBeDefined();
  });

  it("accepts exactly 256 UTF-16 units and counts surrogate pairs like Java", () => {
    const maximum = "x".repeat(256);
    expect(
      validatePasswordChange(
        values({ newPassword: maximum, newPasswordConfirmation: maximum }),
      ),
    ).toEqual({});

    const pairedSurrogates = "😀".repeat(6);
    expect(pairedSurrogates.length).toBe(12);
    expect(
      validatePasswordChange(
        values({
          newPassword: pairedSurrogates,
          newPasswordConfirmation: pairedSurrogates,
        }),
      ),
    ).toEqual({});
  });

  it("requires confirmation and compares it exactly", () => {
    expect(
      validatePasswordChange(values({ newPasswordConfirmation: "" }))
        .newPasswordConfirmation,
    ).toBeDefined();
    expect(
      validatePasswordChange(
        values({ newPasswordConfirmation: "replacement-13" }),
      ).newPasswordConfirmation,
    ).toBe("Las contraseñas no coinciden.");
  });

  it("requires the current password without applying the new-password minimum", () => {
    expect(
      validatePasswordChange(
        values({
          currentPassword: "",
          newPassword: "x".repeat(12),
          newPasswordConfirmation: "x".repeat(12),
        }),
      ).currentPassword,
    ).toBe("Escribe tu contraseña actual.");
  });

  it("rejects a replacement equal to the current password", () => {
    expect(
      validatePasswordChange(
        values({
          currentPassword: "replacement-12",
          newPassword: "replacement-12",
          newPasswordConfirmation: "replacement-12",
        }),
      ).newPassword,
    ).toBe("La contraseña nueva debe ser distinta de la actual.");
  });

  it("leaves blank-space policy to the backend without trimming values", () => {
    expect(
      validatePasswordChange(
        values({
          currentPassword: " ",
          newPassword: " ".repeat(12),
          newPasswordConfirmation: " ".repeat(12),
        }),
      ),
    ).toEqual({});
  });

  it("enforces the maximum on the current password and confirmation", () => {
    expect(
      validatePasswordChange(
        values({
          currentPassword: "x".repeat(257),
          newPasswordConfirmation: "y".repeat(257),
        }),
      ),
    ).toMatchObject({
      currentPassword: expect.any(String),
      newPasswordConfirmation: expect.any(String),
    });
  });
});
