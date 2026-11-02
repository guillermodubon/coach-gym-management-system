import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { BranchContextSummary } from "./BranchContextSummary";

const identity = structuredClone(identityFixture) as CurrentUser;

describe("BranchContextSummary", () => {
  it("shows the confirmed business context and accepts a shared selector", () => {
    render(
      <BranchContextSummary
        selector={<button>Sucursal operativa</button>}
        user={{ ...identity, activeBranch: branchFixture }}
      />,
    );

    expect(
      screen.getByRole("heading", { level: 2, name: "Contexto operativo" }),
    ).toBeVisible();
    expect(
      screen.getByText(/Zona horaria de negocio:.*America\/El_Salvador/),
    ).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Sucursal operativa" }),
    ).toBeVisible();
  });

  it("supports a read-only refresh action without owning context mutations", () => {
    const onRefresh = vi.fn();
    render(
      <BranchContextSummary
        onRefresh={onRefresh}
        user={{ ...identity, activeBranch: branchFixture }}
      />,
    );

    fireEvent.click(
      screen.getByRole("button", { name: "Actualizar contexto" }),
    );
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });
});
