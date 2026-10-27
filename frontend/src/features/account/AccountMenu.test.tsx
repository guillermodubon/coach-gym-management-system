import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import { identityFixture } from "../../test/identity-fixtures";
import { AccountMenu } from "./AccountMenu";

const userFixture = structuredClone(identityFixture) as CurrentUser;

function renderAccountMenu(onLogout = vi.fn()) {
  return render(
    <MemoryRouter>
      <AccountMenu onLogout={onLogout} user={userFixture} />
    </MemoryRouter>,
  );
}

describe("AccountMenu", () => {
  it("shows only account identity and the real logout action", async () => {
    const user = userEvent.setup();
    const onLogout = vi.fn();
    renderAccountMenu(onLogout);

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
      within(
        await screen.findByRole("region", { name: "Opciones de cuenta" }),
      ).getByRole("menu", { name: "Cuenta" }),
    ).toBeInTheDocument();

    expect(
      await screen.findByText("Administrador · Organización"),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("link", { name: "Mi perfil" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("link", { name: "Ajustes de mi cuenta" }),
    ).toHaveAttribute("href", "/mi-cuenta");
    const logoutItem = screen.getByRole("menuitem", { name: "Cerrar sesión" });
    expect(logoutItem).toBeInTheDocument();
    fireEvent.click(logoutItem);
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it("opens from the keyboard and returns focus after Escape", async () => {
    const user = userEvent.setup();
    renderAccountMenu();
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
