import { describe, expect, it } from "vitest";
import { validatePasswordRecovery } from "./password-recovery-validation";

describe("password recovery validation", () => {
  it("requires matching passwords between 12 and 256 characters", () => {
    expect(
      validatePasswordRecovery({
        newPassword: "long-enough-password",
        passwordConfirmation: "long-enough-password",
      }),
    ).toEqual({});
    expect(
      validatePasswordRecovery({
        newPassword: "short",
        passwordConfirmation: "short",
      }).newPassword,
    ).toContain("12");
    expect(
      validatePasswordRecovery({
        newPassword: "long-enough-password",
        passwordConfirmation: "different-password",
      }).passwordConfirmation,
    ).toBe("Las contraseñas no coinciden.");
  });

  it("does not trim password characters", () => {
    const value = "  exact password  ";
    expect(
      validatePasswordRecovery({
        newPassword: value,
        passwordConfirmation: value,
      }),
    ).toEqual({});
  });
});
