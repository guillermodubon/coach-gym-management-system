import { describe, expect, it } from "vitest";
import { safeReturnPath } from "./return-path";

describe("safeReturnPath", () => {
  it("allows only implemented private destinations", () => {
    expect(safeReturnPath("/mi-perfil")).toBe("/mi-perfil");
    expect(safeReturnPath("/mi-cuenta")).toBe("/mi-cuenta");
    expect(safeReturnPath("/mi-cuenta/contrasena")).toBe(
      "/mi-cuenta/contrasena",
    );
    expect(safeReturnPath("/")).toBe("/");
  });

  it.each([
    "https://example.invalid/",
    "//example.invalid/",
    "/mi-perfil?token=secret",
    "/mi-perfil#fragment",
    "/mi-cuenta/contrasena?token=secret",
    "/mi-cuenta/contrasena#fragment",
    "/accept-invitation",
    "/%6di-perfil",
    "/no-implementado",
    null,
    1,
  ])("falls back safely for %s", (candidate) => {
    expect(safeReturnPath(candidate)).toBe("/");
  });
});
