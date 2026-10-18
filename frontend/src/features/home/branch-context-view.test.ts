import { describe, expect, it } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { getBranchContextView } from "./branch-context-view";

function user(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return { ...structuredClone(identityFixture), ...overrides } as CurrentUser;
}

describe("branch context view", () => {
  it("shows the active branch, code and business time zone", () => {
    expect(getBranchContextView(user({ activeBranch: branchFixture }))).toEqual(
      {
        kind: "active-branch",
        headline: "Sucursal: Sucursal de prueba",
        code: "TEST",
        timezone: "America/El_Salvador",
        availableNames: ["Sucursal de prueba"],
      },
    );
  });

  it("distinguishes an organization scope with and without branches", () => {
    expect(getBranchContextView(user()).kind).toBe(
      "organization-with-branches",
    );
    expect(
      getBranchContextView(user({ availableBranches: [], activeBranch: null }))
        .kind,
    ).toBe("organization-without-branches");
  });

  it("does not infer a branch when the response has no active selection", () => {
    const branchUser = user({
      organizationScope: "BRANCH",
      availableBranches: [branchFixture],
      activeBranch: null,
    });
    const view = getBranchContextView(branchUser);
    expect(view.kind).toBe("branch-without-selection");
    expect(view.headline).toBe("Sin sucursal activa");
    expect(view.availableNames).toEqual(["Sucursal de prueba"]);
  });

  it("renders an empty branch assignment as unavailable, never global access", () => {
    expect(
      getBranchContextView(
        user({
          organizationScope: "BRANCH",
          availableBranches: [],
          activeBranch: null,
        }),
      ),
    ).toMatchObject({
      kind: "inconsistent-context",
      headline: "Contexto de sucursal no disponible",
    });
  });
});
