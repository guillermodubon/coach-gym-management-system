import { describe, expect, it } from "vitest";
import { identityFixture } from "../../test/identity-fixtures";
import type { CurrentUser } from "../../shared/api/contracts";
import { getAccountDisplayName, getAuthorityLabel } from "./account-labels";
import { getAccountInitials } from "./initials";

function account(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return { ...structuredClone(identityFixture), ...overrides } as CurrentUser;
}

describe("account labels", () => {
  it.each([
    ["ADMIN", "ORGANIZATION", "Administrador · Organización"],
    ["ADMIN", "BRANCH", "Administrador · Sucursales asignadas"],
    ["RECEPTIONIST", "BRANCH", "Recepción · Sucursales asignadas"],
  ])("labels supported authority %s / %s", (role, scope, expected) => {
    expect(
      getAuthorityLabel(
        account({
          roles: [role],
          organizationScope: scope as CurrentUser["organizationScope"],
        }),
      ),
    ).toBe(expected);
  });

  it("does not invent a label for unsupported or multiple authorities", () => {
    expect(getAuthorityLabel(account({ roles: ["UNKNOWN"] }))).toBeNull();
    expect(
      getAuthorityLabel(account({ roles: ["ADMIN", "RECEPTIONIST"] })),
    ).toBeNull();
  });

  it("uses a non-empty full name, then username, then a neutral fallback", () => {
    expect(getAccountDisplayName(account())).toBe("Operador de prueba");
    expect(getAccountDisplayName(account({ fullName: "  " }))).toBe(
      "operador-prueba",
    );
    expect(
      getAccountDisplayName(account({ fullName: "  ", username: "  " })),
    ).toBe("Usuario");
  });
});

describe("account initials", () => {
  it.each([
    ["María Elena López", "ML"],
    ["Sócrates", "S"],
    ["李 雷", "李雷"],
    ["123 Nombre", "N"],
    ["  ", "O"],
    ["🙂", "?"],
  ])("creates initials for %s", (fullName, expected) => {
    expect(getAccountInitials(account({ fullName }))).toBe(expected);
  });
});
