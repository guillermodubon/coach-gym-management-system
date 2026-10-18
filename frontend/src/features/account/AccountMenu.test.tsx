import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import { identityFixture } from "../../test/identity-fixtures";
import { AccountMenu } from "./AccountMenu";

const userFixture = structuredClone(identityFixture) as CurrentUser;

describe("AccountMenu", () => {
  it("shows only account identity and the real logout action", async () => {
    const user = userEvent.setup();
    const onLogout = vi.fn();
    render(<AccountMenu onLogout={onLogout} user={userFixture} />);

    const trigger = screen.getByRole("button", {
      name: "Menú de cuenta de Operador de prueba",
    });
    expect(trigger).toHaveTextContent("Operador de prueba");
    expect(trigger.querySelector(".cg-account-avatar")).toHaveAttribute(
      "data-initials",
      "OP",
    );
    await user.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "true");

    expect(
      await screen.findByText("Administrador · Organización"),
    ).toBeInTheDocument();
    const logoutItem = screen.getByRole("menuitem", { name: "Cerrar sesión" });
    expect(logoutItem).toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: /perfil|ajustes/i }),
    ).toBeNull();
    fireEvent.click(logoutItem);
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it("opens from the keyboard and returns focus after Escape", async () => {
    const user = userEvent.setup();
    render(<AccountMenu onLogout={vi.fn()} user={userFixture} />);
    const trigger = screen.getByRole("button", {
      name: "Menú de cuenta de Operador de prueba",
    });

    trigger.focus();
    await user.keyboard("{Enter}");
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(
      await screen.findByRole("menuitem", { name: "Cerrar sesión" }),
    ).toBeInTheDocument();
    await user.keyboard("{Escape}");

    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(trigger).toHaveFocus();
  });
});
