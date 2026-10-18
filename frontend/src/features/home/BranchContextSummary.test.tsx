import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { BranchContextSummary } from "./BranchContextSummary";

const identity = structuredClone(identityFixture) as CurrentUser;

describe("BranchContextSummary", () => {
  it("keeps the authorized branch code accessible in the header", () => {
    render(
      <BranchContextSummary
        mode="header"
        user={{ ...identity, activeBranch: branchFixture }}
      />,
    );

    expect(
      screen.getByRole("group", {
        name: "Sucursal: Sucursal de prueba · Código: TEST",
      }),
    ).toBeVisible();
  });

  it("shows the business time zone and invokes a read-only refresh action", () => {
    const onRefresh = vi.fn();
    render(
      <BranchContextSummary
        onRefresh={onRefresh}
        user={{ ...identity, activeBranch: branchFixture }}
      />,
    );

    expect(
      screen.getByText(/Zona horaria de negocio:.*America\/El_Salvador/),
    ).toBeVisible();
    fireEvent.click(
      screen.getByRole("button", { name: "Actualizar contexto" }),
    );
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });
});
